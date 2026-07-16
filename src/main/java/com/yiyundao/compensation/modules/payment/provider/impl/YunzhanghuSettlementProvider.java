package com.yiyundao.compensation.modules.payment.provider.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
import com.yiyundao.compensation.modules.payment.support.PaymentCallbackLogSanitizer;
import com.yiyundao.compensation.modules.payment.support.PaymentRecordStatusTransitions;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import com.yiyundao.compensation.service.EncryptionService;
import com.yiyundao.compensation.service.YunzhanghuClient;
import com.yunzhanghu.sdk.YzhException;
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
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class YunzhanghuSettlementProvider implements SettlementProvider {

    private static final String PROVIDER_CODE = "yunzhanghu";
    private static final String UNKNOWN_ORDER_ERROR_CODE = "YZH_ORDER_UNKNOWN";
    private static final Set<String> UNKNOWN_SUBMIT_CODES = Set.of("2002", "6000", "9021", "60001");
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
        String existingProviderOrderNo = StringUtils.hasText(record.getProviderOrderNo())
                ? record.getProviderOrderNo()
                : null;
        String requestError = validateRequest(request, providerOrderNo, record);
        if (StringUtils.hasText(requestError)) {
            String errorCode = requestErrorCode(requestError);
            String errorMsg = requestErrorMessage(requestError);
            persistRecord(record.getId(), existingProviderOrderNo, null, Map.of(), PaymentStatus.FAILED,
                    errorCode, errorMsg);
            return fail(errorCode, errorMsg);
        }

        Employee employee = record.getEmployeeId() == null ? null : getEmployeeService().getById(record.getEmployeeId());
        String idCardNo = resolveIdCardNo(request, employee);
        if (!StringUtils.hasText(idCardNo)) {
            persistRecord(record.getId(), existingProviderOrderNo, null, Map.of(), PaymentStatus.FAILED,
                    "YZH_ID_CARD_MISSING", "云账户支付缺少身份证信息");
            return fail("YZH_ID_CARD_MISSING", "云账户支付缺少身份证信息");
        }

        String phoneNo = resolvePhoneNo(request, employee);
        if (!StringUtils.hasText(phoneNo)) {
            persistRecord(record.getId(), existingProviderOrderNo, null, Map.of(), PaymentStatus.FAILED,
                    "YZH_PHONE_MISSING", "云账户支付缺少手机号");
            return fail("YZH_PHONE_MISSING", "云账户支付缺少手机号");
        }

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
                if (isUnknownSubmitResponse(response)) {
                    return resolveUnknownOrder(record, providerOrderNo, response, null);
                }
                String errorCode = response == null ? "YZH_RESPONSE_EMPTY" : response.getCode();
                String errorMsg = response == null ? "云账户响应为空" : response.getMessage();
                persistRecord(record.getId(), providerOrderNo, null, buildResponseMetadata(response),
                        PaymentStatus.FAILED, errorCode, errorMsg);
                return fail(errorCode, errorMsg);
            }

            CreateAlipayOrderResponse data = response.getData();
            Map<String, Object> metadata = buildResponseMetadata(response);
            metadata.put("pay", data.getPay());
            String actualOrderNo = StringUtils.hasText(data.getOrderId()) ? data.getOrderId() : providerOrderNo;

            persistRecord(record.getId(), actualOrderNo, data.getRef(), metadata,
                    PaymentStatus.PROCESSING, null, null);

            return SettlementResult.builder()
                    .success(true)
                    .providerOrderNo(actualOrderNo)
                    .providerTradeNo(data.getRef())
                    .status(SettlementStatus.PROCESSING)
                    .responseTime(LocalDateTime.now())
                    .metadata(metadata)
                    .build();
        } catch (IllegalStateException | IllegalArgumentException ex) {
            log.error("云账户配置或请求参数异常: paymentRecordId={}", request.getPaymentRecordId(), ex);
            persistRecord(record.getId(), existingProviderOrderNo, null, Map.of(), PaymentStatus.FAILED,
                    "YZH_REQUEST_INVALID", ex.getMessage());
            return fail("YZH_REQUEST_INVALID", ex.getMessage());
        } catch (YzhException ex) {
            log.error("云账户单笔转账结果未知: paymentRecordId={}", request.getPaymentRecordId(), ex);
            return resolveUnknownOrder(record, providerOrderNo, null, ex.getMessage());
        } catch (Exception ex) {
            log.error("云账户单笔转账异常: paymentRecordId={}", request.getPaymentRecordId(), ex);
            return resolveUnknownOrder(record, providerOrderNo, null, ex.getMessage());
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
                        PaymentCallbackLogSanitizer.sanitizeField("order_id", providerOrderNo),
                        response.getCode(), response.getMessage());
                return SettlementStatus.PROCESSING;
            }
            GetOrderResponse data = response.getData();
            return mapYunzhanghuStatus(data.getStatus(), data.getStatusDetail());
        } catch (Exception ex) {
            log.error("云账户查单异常: orderNo={}",
                    PaymentCallbackLogSanitizer.sanitizeField("order_id", providerOrderNo), ex);
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
            log.warn("云账户回调未匹配到支付记录: orderNo={}",
                    PaymentCallbackLogSanitizer.sanitizeField("order_id", providerOrderNo));
            return SettlementCallbackResult.builder()
                    .success(false)
                    .bizNo(providerOrderNo)
                    .status(status)
                    .errorMsg("云账户回调未匹配到支付记录")
                    .metadata(metadata)
                    .build();
        }

        PaymentStatus paymentStatus = mapPaymentStatus(status);
        boolean persisted = persistRecord(record, providerOrderNo, data.getRef(), metadata,
                paymentStatus, null, null);
        if (!persisted) {
            return SettlementCallbackResult.builder()
                    .success(false)
                    .bizNo(providerOrderNo)
                    .status(status)
                    .errorMsg("云账户回调状态未持久化")
                    .metadata(metadata)
                    .build();
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

    private boolean persistRecord(PaymentRecord currentRecord,
                                  String providerOrderNo,
                                  String providerTradeNo,
                                  Map<String, Object> metadata,
                                  PaymentStatus status,
                                  String errorCode,
                                  String errorMsg) {
        if (currentRecord == null || currentRecord.getId() == null) {
            return false;
        }
        if (!PaymentRecordStatusTransitions.isAllowed(currentRecord.getStatus(), status)) {
            log.warn("云账户状态更新被忽略: recordId={}, currentStatus={}, targetStatus={}, orderNo={}",
                    currentRecord.getId(), currentRecord.getStatus(), status,
                    PaymentCallbackLogSanitizer.sanitizeField("order_id", providerOrderNo));
            return isTerminalPaymentStatus(currentRecord.getStatus());
        }
        boolean setPaymentTime = status != PaymentStatus.SUCCESS
                || currentRecord.getStatus() != PaymentStatus.SUCCESS
                || currentRecord.getPaymentTime() == null;
        return persistRecord(currentRecord.getId(), providerOrderNo, providerTradeNo, metadata,
                status, errorCode, errorMsg, setPaymentTime);
    }

    private boolean persistRecord(Long recordId,
                                  String providerOrderNo,
                                  String providerTradeNo,
                                  Map<String, Object> metadata,
                                  PaymentStatus status,
                                  String errorCode,
                                  String errorMsg) {
        return persistRecord(recordId, providerOrderNo, providerTradeNo, metadata, status, errorCode, errorMsg, true);
    }

    private boolean persistRecord(Long recordId,
                                  String providerOrderNo,
                                  String providerTradeNo,
                                  Map<String, Object> metadata,
                                  PaymentStatus status,
                                  String errorCode,
                                  String errorMsg,
                                  boolean setPaymentTime) {
        UpdateWrapper<PaymentRecord> wrapper = new UpdateWrapper<PaymentRecord>()
                .eq("id", recordId)
                .set("provider_code", PROVIDER_CODE)
                .set("provider_order_no", providerOrderNo)
                .set("status", status.getCode())
                .set("provider_metadata", serializeMetadata(metadata));
        if (StringUtils.hasText(providerTradeNo)) {
            wrapper.set("provider_trade_no", providerTradeNo);
        }
        if (StringUtils.hasText(errorCode)) {
            wrapper.set("error_code", errorCode);
        }
        if (StringUtils.hasText(errorMsg)) {
            wrapper.set("error_msg", errorMsg);
        }
        if (status == PaymentStatus.SUCCESS && setPaymentTime) {
            wrapper.set("payment_time", LocalDateTime.now())
                    .set("error_code", null)
                    .set("error_msg", null);
        }
        PaymentRecordStatusTransitions.applyAllowedStatusGuard(wrapper, status);
        return paymentRecordService.update(wrapper);
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
            return normalizeIdCard(request.getRecipientIdNo());
        }
        if (employee == null || !StringUtils.hasText(employee.getEncryptedIdCard())) {
            return null;
        }
        try {
            return normalizeIdCard(encryptionService.decryptIdCard(employee.getEncryptedIdCard()));
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

        // 云账户文档规定以 status 主字段判断最终结果，status_detail 只用于解释原因。
        if (equalsAny(normalizedStatus, "1", "SUCCESS", "PAY_SUCCESS", "FINISHED", "DONE")) {
            return SettlementStatus.SUCCESS;
        }
        if (equalsAny(normalizedStatus, "2", "9", "FAILED", "FAIL", "REJECT", "ERROR")) {
            return SettlementStatus.FAILED;
        }
        if (equalsAny(normalizedStatus, "15", "-1", "CANCEL", "CANCELLED", "CLOSED")) {
            return SettlementStatus.CANCELLED;
        }
        if (equalsAny(normalizedStatus, "0", "4", "5", "8", "PENDING", "INIT", "PROCESS", "DEALING", "HANDLING")) {
            return SettlementStatus.PROCESSING;
        }
        if (containsAny(normalizedStatus, "AUDIT")) {
            return SettlementStatus.AUDITING;
        }
        if (containsAny(normalizedStatus, "SIGN")) {
            return SettlementStatus.SIGNING;
        }
        if (containsAny(normalizedStatus, "TAX")) {
            return SettlementStatus.TAXING;
        }
        if (containsAny(normalizedStatus, "WITHDRAW")) {
            return SettlementStatus.WITHDRAWING;
        }
        if (normalizedStatus.matches("-?\\d+")) {
            return SettlementStatus.PROCESSING;
        }
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

    private String validateRequest(SettlementRequest request, String providerOrderNo, PaymentRecord record) {
        if (!isValidOrderId(providerOrderNo)) {
            return "YZH_ORDER_ID_INVALID:云账户订单号必须是1至64位英文字符";
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || request.getAmount().stripTrailingZeros().scale() > 2) {
            return "YZH_AMOUNT_INVALID:云账户支付金额必须大于0且最多保留两位小数";
        }
        if (!StringUtils.hasText(request.getRecipientName())) {
            return "YZH_REAL_NAME_MISSING:云账户支付缺少收款人姓名";
        }
        if (!StringUtils.hasText(request.getRecipientAccount())) {
            return "YZH_ACCOUNT_MISSING:云账户支付缺少支付宝账号";
        }
        if (!isValidRemark(request.getRemark())) {
            return "YZH_REMARK_INVALID:云账户订单备注长度或字符不符合要求";
        }
        if (record == null || record.getEmployeeId() == null) {
            return "YZH_USER_ID_MISSING:云账户支付缺少劳动者唯一标识";
        }
        return null;
    }

    private String requestErrorCode(String validationError) {
        int separator = validationError == null ? -1 : validationError.indexOf(':');
        return separator > 0 ? validationError.substring(0, separator) : "YZH_REQUEST_INVALID";
    }

    private String requestErrorMessage(String validationError) {
        int separator = validationError == null ? -1 : validationError.indexOf(':');
        return separator > 0 ? validationError.substring(separator + 1) : validationError;
    }

    private boolean isValidOrderId(String orderId) {
        if (!StringUtils.hasText(orderId) || orderId.length() > 64) {
            return false;
        }
        return orderId.chars().allMatch(ch -> ch >= 0x21 && ch <= 0x7e);
    }

    private boolean isValidRemark(String remark) {
        if (!StringUtils.hasText(remark)) {
            return true;
        }
        if (remark.length() > 40) {
            return false;
        }
        return remark.chars().noneMatch(ch -> "'\"&|@%()-:#+/<>,\\".indexOf(ch) >= 0 || ch == 0x00a5);
    }

    private String normalizeIdCard(String idCard) {
        return StringUtils.hasText(idCard) ? idCard.trim().toUpperCase(Locale.ROOT) : null;
    }

    private boolean isUnknownSubmitResponse(YzhResponse<?> response) {
        if (response == null) {
            return true;
        }
        String code = normalize(response.getCode());
        return !StringUtils.hasText(code)
                || response.getHttpCode() >= 500
                || UNKNOWN_SUBMIT_CODES.contains(code);
    }

    private SettlementResult resolveUnknownOrder(PaymentRecord record,
                                                 String providerOrderNo,
                                                 YzhResponse<?> submitResponse,
                                                 String submitError) {
        Map<String, Object> metadata = buildResponseMetadata(submitResponse);
        metadata.put("orderUnknown", true);
        try {
            YzhResponse<GetOrderResponse> queryResponse = yunzhanghuClient.queryOrder(providerOrderNo);
            if (queryResponse != null && queryResponse.isSuccess() && queryResponse.getData() != null) {
                GetOrderResponse data = queryResponse.getData();
                SettlementStatus status = mapYunzhanghuStatus(data.getStatus(), data.getStatusDetail());
                metadata.put("queryCode", queryResponse.getCode());
                metadata.put("queryMessage", queryResponse.getMessage());
                metadata.put("queryStatus", data.getStatus());
                metadata.put("queryStatusDetail", data.getStatusDetail());
                metadata.put("queryStatusMessage", data.getStatusMessage());
                metadata.put("queryStatusDetailMessage", data.getStatusDetailMessage());
                if (isTerminal(status)) {
                    String errorCode = status == SettlementStatus.FAILED ? "YZH_PAYMENT_FAILED" : null;
                    String errorMsg = status == SettlementStatus.FAILED
                            ? firstText(data.getStatusDetailMessage(), data.getStatusMessage(), submitError)
                            : null;
                    persistRecord(record.getId(), providerOrderNo, data.getRef(), metadata,
                            mapPaymentStatus(status), errorCode, errorMsg);
                    return resultForStatus(providerOrderNo, data.getRef(), status, metadata, errorCode, errorMsg);
                }
                submitError = "云账户订单仍在处理中，已保留原订单号";
            }
        } catch (Exception queryException) {
            log.warn("云账户未知订单查单失败: orderNo={}, msg={}",
                    PaymentCallbackLogSanitizer.sanitizeField("order_id", providerOrderNo),
                    queryException.getMessage());
            metadata.put("queryError", queryException.getClass().getSimpleName());
        }

        String errorMsg = StringUtils.hasText(submitError) ? submitError : "云账户下单结果未知，已保留原订单号等待查单";
        persistRecord(record.getId(), providerOrderNo, null, metadata,
                PaymentStatus.PROCESSING, UNKNOWN_ORDER_ERROR_CODE, errorMsg);
        return resultForStatus(providerOrderNo, null, SettlementStatus.PROCESSING, metadata,
                UNKNOWN_ORDER_ERROR_CODE, errorMsg);
    }

    private SettlementResult resultForStatus(String providerOrderNo,
                                             String providerTradeNo,
                                             SettlementStatus status,
                                             Map<String, Object> metadata,
                                             String errorCode,
                                             String errorMsg) {
        boolean terminalFailure = status == SettlementStatus.FAILED || status == SettlementStatus.CANCELLED;
        return SettlementResult.builder()
                .success(!terminalFailure)
                .providerOrderNo(providerOrderNo)
                .providerTradeNo(providerTradeNo)
                .status(status)
                .errorCode(errorCode)
                .errorMsg(errorMsg)
                .responseTime(LocalDateTime.now())
                .metadata(metadata)
                .build();
    }

    private boolean isTerminal(SettlementStatus status) {
        return status == SettlementStatus.SUCCESS
                || status == SettlementStatus.FAILED
                || status == SettlementStatus.CANCELLED;
    }

    private boolean isTerminalPaymentStatus(PaymentStatus status) {
        return status == PaymentStatus.SUCCESS
                || status == PaymentStatus.FAILED
                || status == PaymentStatus.CANCELLED;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
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

    private boolean equalsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
