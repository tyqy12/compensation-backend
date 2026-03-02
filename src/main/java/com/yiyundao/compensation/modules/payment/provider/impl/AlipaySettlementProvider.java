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

            return SettlementResult.builder()
                    .success(true)
                    .providerOrderNo(providerOrderNo)
                    .providerTradeNo(providerTradeNo)
                    .status(SettlementStatus.SUCCESS)
                    .responseTime(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("支付宝单笔转账失败: paymentRecordId={}", request.getPaymentRecordId(), e);
            return fail("ALIPAY_TRANSFER_FAILED", e.getMessage());
        }
    }

    @Override
    public SettlementResult batchTransfer(List<SettlementRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return fail("INVALID_REQUEST", "批量请求不能为空");
        }
        if (requests.size() == 1) {
            return singleTransfer(requests.get(0));
        }

        SettlementRequest first = requests.get(0);
        String batchNo = null;
        if (first.getExtra() != null && first.getExtra().get("batchNo") != null) {
            batchNo = String.valueOf(first.getExtra().get("batchNo"));
        }
        if (!StringUtils.hasText(batchNo) && first.getPaymentRecordId() != null) {
            PaymentRecord record = paymentRecordService.getById(first.getPaymentRecordId());
            batchNo = record != null ? record.getBatchNo() : null;
        }
        if (!StringUtils.hasText(batchNo)) {
            return fail("INVALID_REQUEST", "批量转账缺少批次号");
        }

        try {
            alipayService.batchTransfer(batchNo);
            return SettlementResult.builder()
                    .success(true)
                    .status(SettlementStatus.PROCESSING)
                    .responseTime(LocalDateTime.now())
                    .metadata(Map.of("batchNo", batchNo, "count", requests.size()))
                    .build();
        } catch (Exception e) {
            log.error("支付宝批量转账失败: batchNo={}", batchNo, e);
            return fail("ALIPAY_BATCH_FAILED", e.getMessage());
        }
    }

    @Override
    public SettlementStatus queryStatus(String providerOrderNo) {
        try {
            PaymentStatus paymentStatus = alipayService.queryTransferStatus(providerOrderNo);
            return mapPaymentStatus(paymentStatus);
        } catch (Exception e) {
            log.error("支付宝状态查询失败: providerOrderNo={}", providerOrderNo, e);
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

        try {
            alipayService.handleNotification(outBizNo, tradeNo, tradeStatus);
            return SettlementCallbackResult.builder()
                    .success(true)
                    .bizNo(outBizNo)
                    .status(mapAlipayTradeStatus(tradeStatus))
                    .build();
        } catch (Exception e) {
            log.error("处理支付宝回调失败: outBizNo={}", outBizNo, e);
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
                .errorMsg(msg)
                .responseTime(LocalDateTime.now())
                .build();
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
        return switch (tradeStatus) {
            case "TRADE_SUCCESS" -> SettlementStatus.SUCCESS;
            case "TRADE_CLOSED", "TRADE_FINISHED" -> SettlementStatus.FAILED;
            default -> SettlementStatus.PROCESSING;
        };
    }
}

