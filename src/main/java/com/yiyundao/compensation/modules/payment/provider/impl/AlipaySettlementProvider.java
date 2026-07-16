package com.yiyundao.compensation.modules.payment.provider.impl;

import com.yiyundao.compensation.enums.PayrollType;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.provider.SettlementCallbackResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementProvider;
import com.yiyundao.compensation.modules.payment.provider.SettlementRequest;
import com.yiyundao.compensation.modules.payment.provider.SettlementResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementStatus;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.support.PaymentCallbackLogSanitizer;
import com.yiyundao.compensation.service.AlipayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlipaySettlementProvider implements SettlementProvider {

    private final AlipayService alipayService;
    private final PaymentRecordService paymentRecordService;

    @Override
    public String getProviderCode() {
        return "alipay";
    }

    @Override
    public String getProviderName() {
        return "支付宝";
    }

    @Override
    public SettlementResult singleTransfer(SettlementRequest request) {
        if (request == null || request.getPaymentRecordId() == null) {
            return fail("INVALID_REQUEST", "支付记录ID不能为空");
        }

        try {
            String tradeNo = alipayService.singleTransfer(request.getPaymentRecordId());
            PaymentRecord record = paymentRecordService.getById(request.getPaymentRecordId());
            String providerOrderNo = record != null && StringUtils.hasText(record.getProviderOrderNo())
                    ? record.getProviderOrderNo()
                    : (record != null ? record.getAlipayOrderNo() : null);
            String providerTradeNo = StringUtils.hasText(tradeNo)
                    ? tradeNo
                    : (record != null ? record.getProviderTradeNo() : null);

            if (record != null && AlipayService.RESULT_UNKNOWN_ERROR_CODE.equals(record.getErrorCode())) {
                return SettlementResult.builder()
                        .success(true)
                        .status(SettlementStatus.PROCESSING)
                        .providerOrderNo(providerOrderNo)
                        .providerTradeNo(providerTradeNo)
                        .errorCode(record.getErrorCode())
                        .errorMsg(record.getErrorMsg())
                        .responseTime(LocalDateTime.now())
                        .build();
            }

            return SettlementResult.builder()
                    .success(true)
                    .providerOrderNo(providerOrderNo)
                    .providerTradeNo(providerTradeNo)
                    .status(SettlementStatus.SUCCESS)
                    .responseTime(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("支付宝单笔转账失败: paymentRecordId={}", request.getPaymentRecordId(), e);
            PaymentRecord record = paymentRecordService.getById(request.getPaymentRecordId());
            String providerOrderNo = record != null && StringUtils.hasText(record.getProviderOrderNo())
                    ? record.getProviderOrderNo()
                    : (record != null ? record.getAlipayOrderNo() : null);
            String providerTradeNo = record != null && StringUtils.hasText(record.getProviderTradeNo())
                    ? record.getProviderTradeNo()
                    : (record != null ? record.getAlipayTradeNo() : null);
            if (record != null && AlipayService.RESULT_UNKNOWN_ERROR_CODE.equals(record.getErrorCode())) {
                return SettlementResult.builder()
                        .success(true)
                        .status(SettlementStatus.PROCESSING)
                        .providerOrderNo(providerOrderNo)
                        .providerTradeNo(providerTradeNo)
                        .errorCode(record.getErrorCode())
                        .errorMsg(record.getErrorMsg())
                        .responseTime(LocalDateTime.now())
                        .build();
            }
            return SettlementResult.builder()
                    .success(false)
                    .status(SettlementStatus.FAILED)
                    .providerOrderNo(providerOrderNo)
                    .providerTradeNo(providerTradeNo)
                    .errorCode("ALIPAY_TRANSFER_FAILED")
                    .errorMsg(normalizeErrorMessage(e == null ? null : e.getMessage()))
                    .responseTime(LocalDateTime.now())
                    .build();
        }
    }

    @Override
    public SettlementResult batchTransfer(List<SettlementRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return fail("INVALID_REQUEST", "批量请求不能为空");
        }
        int successCount = 0;
        int failedCount = 0;
        String lastErrorCode = null;
        String lastErrorMsg = null;
        for (SettlementRequest request : requests) {
            SettlementResult result = singleTransfer(request);
            if (result != null && result.isSuccess()) {
                successCount++;
            } else {
                failedCount++;
                if (result != null) {
                    lastErrorCode = result.getErrorCode();
                    lastErrorMsg = result.getErrorMsg();
                }
            }
        }

        SettlementStatus status = failedCount == 0
                ? SettlementStatus.SUCCESS
                : successCount > 0 ? SettlementStatus.PROCESSING : SettlementStatus.FAILED;
        return SettlementResult.builder()
                .success(failedCount == 0)
                .status(status)
                .errorCode(lastErrorCode)
                .errorMsg(lastErrorMsg)
                .responseTime(LocalDateTime.now())
                .metadata(Map.of(
                        "count", requests.size(),
                        "successCount", successCount,
                        "failedCount", failedCount
                ))
                .build();
    }

    @Override
    public SettlementStatus queryStatus(String providerOrderNo) {
        try {
            PaymentStatus paymentStatus = alipayService.queryTransferStatus(providerOrderNo);
            return mapPaymentStatus(paymentStatus);
        } catch (Exception e) {
            if (isLocalConfigurationError(e)) {
                log.warn("支付宝状态查询失败，检测到本地配置错误: providerOrderNo={}, msg={}",
                        PaymentCallbackLogSanitizer.sanitizeField("provider_order_no", providerOrderNo),
                        normalizeErrorMessage(e.getMessage()));
                return SettlementStatus.FAILED;
            }
            log.error("支付宝状态查询失败: providerOrderNo={}",
                    PaymentCallbackLogSanitizer.sanitizeField("provider_order_no", providerOrderNo), e);
            return SettlementStatus.PROCESSING;
        }
    }

    @Override
    public SettlementCallbackResult handleCallback(Map<String, String> params) {
        String outBizNo = params.get("out_biz_no");
        String tradeNo = params.get("trade_no");
        String tradeStatus = params.get("trade_status");
        if (!StringUtils.hasText(outBizNo) || !StringUtils.hasText(tradeStatus)) {
            return SettlementCallbackResult.builder()
                    .success(false)
                    .errorMsg("回调参数不完整")
                    .build();
        }

        SettlementStatus settlementStatus = mapKnownAlipayTradeStatus(tradeStatus);
        if (settlementStatus == null) {
            log.warn("忽略未知支付宝回调状态: outBizNo={}, tradeStatus={}",
                    PaymentCallbackLogSanitizer.sanitizeField("out_biz_no", outBizNo), tradeStatus);
            return SettlementCallbackResult.builder()
                    .success(false)
                    .bizNo(outBizNo)
                    .errorMsg("未知支付宝回调状态: " + tradeStatus)
                    .status(SettlementStatus.PROCESSING)
                    .build();
        }

        try {
            alipayService.handleNotification(outBizNo, tradeNo, tradeStatus);
            return SettlementCallbackResult.builder()
                    .success(true)
                    .bizNo(outBizNo)
                    .status(settlementStatus)
                    .build();
        } catch (Exception e) {
            log.error("处理支付宝回调失败: outBizNo={}",
                    PaymentCallbackLogSanitizer.sanitizeField("out_biz_no", outBizNo), e);
            return SettlementCallbackResult.builder()
                    .success(false)
                    .bizNo(outBizNo)
                    .errorMsg(e.getMessage())
                    .status(SettlementStatus.FAILED)
                    .build();
        }
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        return alipayService.verifyNotification(params);
    }

    @Override
    public boolean healthCheck() {
        return alipayService.checkAlipayConnection();
    }

    @Override
    public boolean supports(PayrollType payrollType) {
        return true;
    }

    private SettlementResult fail(String code, String msg) {
        return SettlementResult.builder()
                .success(false)
                .status(SettlementStatus.FAILED)
                .errorCode(code)
                .errorMsg(normalizeErrorMessage(msg))
                .responseTime(LocalDateTime.now())
                .build();
    }

    private String normalizeErrorMessage(String rawMessage) {
        if (!StringUtils.hasText(rawMessage)) {
            return "支付宝转账失败";
        }
        String message = rawMessage.trim();
        if (message.contains("RSA2签名遭遇异常")
                || message.contains("InvalidKeyException")
                || message.contains("privateKeySize")
                || message.contains("支付宝应用私钥格式错误")) {
            return "支付宝签名失败，请检查应用私钥格式（PKCS8）";
        }
        int contentIndex = message.indexOf("content=");
        if (contentIndex > 0) {
            message = message.substring(0, contentIndex).trim();
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }

    private boolean isLocalConfigurationError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (StringUtils.hasText(message)
                    && (message.contains("RSA2签名遭遇异常")
                    || message.contains("InvalidKeyException")
                    || message.contains("privateKeySize")
                    || message.contains("支付宝配置不完整")
                    || message.contains("支付宝应用私钥格式错误")
                    || message.contains("支付宝集成未启用")
                    || message.contains("公钥模式需要配置")
                    || message.contains("证书模式需要配置"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private SettlementStatus mapPaymentStatus(PaymentStatus status) {
        if (status == null) {
            return SettlementStatus.PROCESSING;
        }
        return switch (status) {
            case PENDING -> SettlementStatus.PENDING;
            case PROCESSING -> SettlementStatus.PROCESSING;
            case SUCCESS -> SettlementStatus.SUCCESS;
            case FAILED -> SettlementStatus.FAILED;
            case CANCELLED -> SettlementStatus.CANCELLED;
        };
    }

    private SettlementStatus mapAlipayTradeStatus(String tradeStatus) {
        SettlementStatus status = mapKnownAlipayTradeStatus(tradeStatus);
        return status != null ? status : SettlementStatus.PROCESSING;
    }

    private SettlementStatus mapKnownAlipayTradeStatus(String tradeStatus) {
        return switch (tradeStatus) {
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> SettlementStatus.SUCCESS;
            case "TRADE_CLOSED" -> SettlementStatus.FAILED;
            default -> null;
        };
    }
}
