package com.yiyundao.compensation.modules.payment.provider.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.PayrollType;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.provider.SettlementCallbackResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementProvider;
import com.yiyundao.compensation.modules.payment.provider.SettlementRequest;
import com.yiyundao.compensation.modules.payment.provider.SettlementResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementStatus;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import com.yiyundao.compensation.service.EncryptionService;
import com.yiyundao.compensation.service.YunzhanghuClient;
import com.yunzhanghu.sdk.base.YzhResponse;
import com.yunzhanghu.sdk.notify.domain.NotifyResponse;
import com.yunzhanghu.sdk.payment.domain.CreateAlipayOrderResponse;
import com.yunzhanghu.sdk.payment.domain.GetOrderResponse;
import com.yunzhanghu.sdk.payment.domain.NotifyOrderData;
import com.yunzhanghu.sdk.payment.domain.NotifyOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class YunzhanghuSettlementProvider implements SettlementProvider {

    private static final String PROVIDER_CODE = "yunzhanghu";
    private static final ThreadLocal<NotifyResponse<NotifyOrderRequest>> CALLBACK_CACHE = new ThreadLocal<>();

    private final IntegrationConfigService integrationConfigService;
    private final YunzhanghuClient yunzhanghuClient;
    private final PaymentRecordService paymentRecordService;
    private final ObjectProvider<EmployeeService> employeeServiceProvider;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public String getProviderName() {
        return "云账户";
    }

    @Override
    public SettlementResult singleTransfer(SettlementRequest request) {
        if (request == null || request.getPaymentRecordId() == null) {
            return fail("INVALID_REQUEST", "支付记录ID不能为空");
        }
        PaymentRecord record = paymentRecordService.getById(request.getPaymentRecordId());
        if (record == null) {
            return fail("PAYMENT_RECORD_NOT_FOUND", "支付记录不存在");
        }

        String providerOrderNo = resolveProviderOrderNo(request, record);
        Employee employee = record.getEmployeeId() == null ? null : getEmployeeService().getById(record.getEmployeeId());
        String idCardNo = resolveIdCardNo(request, employee);
        if (!StringUtils.hasText(idCardNo)) {
            persistRecord(record.getId(), providerOrderNo, null, Map.of(), PaymentStatus.FAILED,
                    "YZH_ID_CARD_MISSING", "云账户支付缺少身份证信息");
            return fail("YZH_ID_CARD_MISSING", "云账户支付缺少身份证信息");
        }

        String phoneNo = resolvePhoneNo(request, employee);
        String dealerUserId = record.getEmployeeId() == null ? null : String.valueOf(record.getEmployeeId());
        String dealerUserNickname = request.getRecipientName();

        try {
            YzhResponse<CreateAlipayOrderResponse> response = yunzhanghuClient.createAlipayOrder(
                    providerOrderNo,
                    request.getAmount(),
                    request.getRecipientName(),
                    request.getRecipientAccount(),
                    idCardNo,
                    phoneNo,
                    request.getRemark(),
                    dealerUserId,
                    dealerUserNickname
            );

            if (response == null || !response.isSuccess() || response.getData() == null) {
                String errorCode = response == null ? "YZH_RESPONSE_EMPTY" : response.getCode();
                String errorMsg = response == null ? "云账户响应为空" : response.getMessage();
                persistRecord(record.getId(), providerOrderNo, null, buildResponseMetadata(response),
                        PaymentStatus.FAILED, errorCode, errorMsg);
                return fail(errorCode, errorMsg);
            }

            CreateAlipayOrderResponse data = response.getData();
            Map<String, Object> metadata = buildResponseMetadata(response);
            metadata.put("pay", data.getPay());

            persistRecord(record.getId(), providerOrderNo, data.getRef(), metadata,
                    PaymentStatus.PROCESSING, null, null);

            return SettlementResult.builder()
                    .success(true)
                    .providerOrderNo(data.getOrderId())
                    .providerTradeNo(data.getRef())
                    .status(SettlementStatus.PROCESSING)
                    .responseTime(LocalDateTime.now())
                    .metadata(metadata)
                    .build();
        } catch (Exception ex) {
            log.error("云账户单笔转账异常: paymentRecordId={}", request.getPaymentRecordId(), ex);
            persistRecord(record.getId(), providerOrderNo, null, Map.of(),
                    PaymentStatus.FAILED, "YZH_TRANSFER_EXCEPTION", ex.getMessage());
            return fail("YZH_TRANSFER_EXCEPTION", ex.getMessage());
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
        return fail("YZH_BATCH_UNSUPPORTED", "当前版本暂不支持云账户批量发放");
    }

    @Override
    public SettlementStatus queryStatus(String providerOrderNo) {
        if (!StringUtils.hasText(providerOrderNo)) {
            return SettlementStatus.PROCESSING;
        }
        try {
            YzhResponse<GetOrderResponse> response = yunzhanghuClient.queryOrder(providerOrderNo);
            if (response == null || response.getData() == null) {
                return SettlementStatus.PROCESSING;
            }
            if (!response.isSuccess()) {
                log.warn("云账户查单失败: orderNo={}, code={}, msg={}",
                        providerOrderNo, response.getCode(), response.getMessage());
                return SettlementStatus.PROCESSING;
            }
            GetOrderResponse data = response.getData();
            return mapYunzhanghuStatus(data.getStatus(), data.getStatusDetail());
        } catch (Exception ex) {
            log.error("云账户查单异常: orderNo={}", providerOrderNo, ex);
            return SettlementStatus.PROCESSING;
        }
    }

    @Override
    public SettlementCallbackResult handleCallback(Map<String, String> params) {
        NotifyResponse<NotifyOrderRequest> decoded = readCallbackFromCache(params);
        if (decoded == null || !Boolean.TRUE.equals(decoded.getSignRes()) || !Boolean.TRUE.equals(decoded.getDescryptRes())) {
            return SettlementCallbackResult.builder()
                    .success(false)
                    .errorMsg("云账户回调验签或解密失败")
                    .status(SettlementStatus.PROCESSING)
                    .build();
        }
        NotifyOrderRequest notifyOrderRequest = decoded.getData();
        if (notifyOrderRequest == null || notifyOrderRequest.getData() == null) {
            return SettlementCallbackResult.builder()
                    .success(false)
                    .errorMsg("云账户回调缺少业务数据")
                    .status(SettlementStatus.PROCESSING)
                    .build();
        }

        NotifyOrderData data = notifyOrderRequest.getData();
        String providerOrderNo = data.getOrderId();
        SettlementStatus status = mapYunzhanghuStatus(data.getStatus(), data.getStatusDetail());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("notifyId", notifyOrderRequest.getNotifyId());
        metadata.put("notifyTime", notifyOrderRequest.getNotifyTime());
        metadata.put("status", data.getStatus());
        metadata.put("statusDetail", data.getStatusDetail());
        metadata.put("statusMessage", data.getStatusMessage());
        metadata.put("statusDetailMessage", data.getStatusDetailMessage());
        metadata.put("supplementalDetailMessage", data.getSupplementalDetailMessage());
        metadata.put("withdrawPlatform", data.getWithdrawPlatform());

        if (!StringUtils.hasText(providerOrderNo)) {
            return SettlementCallbackResult.builder()
                    .success(false)
                    .errorMsg("云账户回调缺少orderId")
                    .status(status)
                    .metadata(metadata)
                    .build();
        }

        PaymentRecord record = paymentRecordService.getByProviderOrderNo(PROVIDER_CODE, providerOrderNo);
        if (record == null) {
            log.warn("云账户回调未匹配到支付记录: orderNo={}", providerOrderNo);
        } else {
            persistRecord(record.getId(), providerOrderNo, data.getRef(), metadata,
                    mapPaymentStatus(status), null, null);
        }

        return SettlementCallbackResult.builder()
                .success(true)
                .bizNo(providerOrderNo)
                .status(status)
                .metadata(metadata)
                .build();
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        try {
            NotifyResponse<NotifyOrderRequest> decoded = yunzhanghuClient.decodeOrderNotify(params);
            boolean verified = decoded != null
                    && Boolean.TRUE.equals(decoded.getSignRes())
                    && Boolean.TRUE.equals(decoded.getDescryptRes());
            if (verified) {
                CALLBACK_CACHE.set(decoded);
            } else {
                CALLBACK_CACHE.remove();
            }
            return verified;
        } catch (Exception ex) {
            CALLBACK_CACHE.remove();
            log.warn("云账户回调验签异常: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean healthCheck() {
        return integrationConfigService.isPlatformEnabled(PROVIDER_CODE) && yunzhanghuClient.healthCheck();
    }

    @Override
    public boolean supports(PayrollType payrollType) {
        return payrollType == null || payrollType == PayrollType.PART_TIME;
    }

    private NotifyResponse<NotifyOrderRequest> readCallbackFromCache(Map<String, String> params) {
        NotifyResponse<NotifyOrderRequest> cached = CALLBACK_CACHE.get();
        CALLBACK_CACHE.remove();
        if (cached != null) {
            return cached;
        }
        return yunzhanghuClient.decodeOrderNotify(params);
    }

    private EmployeeService getEmployeeService() {
        EmployeeService employeeService = employeeServiceProvider.getIfAvailable();
        if (employeeService == null) {
            throw new IllegalStateException("EmployeeService 不可用，无法处理云账户结算");
        }
        return employeeService;
    }

    private void persistRecord(Long recordId,
                               String providerOrderNo,
                               String providerTradeNo,
                               Map<String, Object> metadata,
                               PaymentStatus status,
                               String errorCode,
                               String errorMsg) {
        PaymentRecord update = new PaymentRecord();
        update.setId(recordId);
        update.setProviderCode(PROVIDER_CODE);
        update.setProviderOrderNo(providerOrderNo);
        update.setStatus(status);
        update.setProviderMetadata(serializeMetadata(metadata));
        if (StringUtils.hasText(providerTradeNo)) {
            update.setProviderTradeNo(providerTradeNo);
        }
        if (StringUtils.hasText(errorCode)) {
            update.setErrorCode(errorCode);
        }
        if (StringUtils.hasText(errorMsg)) {
            update.setErrorMsg(errorMsg);
        }
        if (status == PaymentStatus.SUCCESS) {
            update.setPaymentTime(LocalDateTime.now());
        }
        if (status == PaymentStatus.SUCCESS || status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED) {
            update.setNotificationTime(LocalDateTime.now());
        }
        paymentRecordService.updateById(update);
    }

    private String resolveProviderOrderNo(SettlementRequest request, PaymentRecord record) {
        if (StringUtils.hasText(request.getBizNo())) {
            return request.getBizNo().trim();
        }
        if (record != null && StringUtils.hasText(record.getProviderOrderNo())) {
            return record.getProviderOrderNo();
        }
        return "YZH_" + System.currentTimeMillis() + "_" + request.getPaymentRecordId();
    }

    private String resolveIdCardNo(SettlementRequest request, Employee employee) {
        if (request != null && StringUtils.hasText(request.getRecipientIdNo())) {
            return request.getRecipientIdNo().trim();
        }
        if (employee == null || !StringUtils.hasText(employee.getEncryptedIdCard())) {
            return null;
        }
        try {
            return encryptionService.decryptIdCard(employee.getEncryptedIdCard());
        } catch (Exception ex) {
            log.warn("解密员工身份证失败: employeeId={}, msg={}", employee.getId(), ex.getMessage());
            return null;
        }
    }

    private String resolvePhoneNo(SettlementRequest request, Employee employee) {
        if (request != null && request.getExtra() != null) {
            Object phoneNo = request.getExtra().get("phoneNo");
            if (phoneNo != null && StringUtils.hasText(String.valueOf(phoneNo))) {
                return String.valueOf(phoneNo).trim();
            }
        }
        return employee == null ? null : employee.getPhone();
    }

    private Map<String, Object> buildResponseMetadata(YzhResponse<?> response) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (response == null) {
            return metadata;
        }
        metadata.put("requestId", response.getRequestId());
        metadata.put("code", response.getCode());
        metadata.put("message", response.getMessage());
        metadata.put("httpCode", response.getHttpCode());
        return metadata;
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

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            log.warn("序列化云账户扩展信息失败: {}", ex.getMessage());
            return null;
        }
    }

    private SettlementStatus mapYunzhanghuStatus(String status, String statusDetail) {
        String normalizedStatus = normalize(status);
        String normalizedDetail = normalize(statusDetail);
        String merged = normalizedStatus + "|" + normalizedDetail;

        if (containsAny(merged, "SUCCESS", "PAY_SUCCESS", "FINISHED", "DONE")) {
            return SettlementStatus.SUCCESS;
        }
        if (containsAny(merged, "FAILED", "FAIL", "REJECT", "ERROR")) {
            return SettlementStatus.FAILED;
        }
        if (containsAny(merged, "CANCEL", "CLOSED")) {
            return SettlementStatus.CANCELLED;
        }
        if (containsAny(merged, "AUDIT")) {
            return SettlementStatus.AUDITING;
        }
        if (containsAny(merged, "SIGN")) {
            return SettlementStatus.SIGNING;
        }
        if (containsAny(merged, "TAX")) {
            return SettlementStatus.TAXING;
        }
        if (containsAny(merged, "WITHDRAW")) {
            return SettlementStatus.WITHDRAWING;
        }
        if (containsAny(merged, "PENDING", "INIT", "PROCESS", "DEALING", "HANDLING")) {
            return SettlementStatus.PROCESSING;
        }
        return SettlementStatus.PROCESSING;
    }

    private PaymentStatus mapPaymentStatus(SettlementStatus status) {
        if (status == null) {
            return PaymentStatus.PROCESSING;
        }
        return switch (status) {
            case SUCCESS -> PaymentStatus.SUCCESS;
            case FAILED -> PaymentStatus.FAILED;
            case CANCELLED -> PaymentStatus.CANCELLED;
            case PENDING -> PaymentStatus.PENDING;
            default -> PaymentStatus.PROCESSING;
        };
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
