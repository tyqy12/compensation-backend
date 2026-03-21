package com.yiyundao.compensation.modules.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.EmploymentType;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.PaymentType;
import com.yiyundao.compensation.enums.SettlementAccountType;
import com.yiyundao.compensation.common.utils.ValidationUtils;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payment.dto.PaymentBatchTransferValidationDto;
import com.yiyundao.compensation.modules.payment.dto.TransferValidationIssueDto;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.provider.SettlementCallbackResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementProvider;
import com.yiyundao.compensation.modules.payment.provider.SettlementRequest;
import com.yiyundao.compensation.modules.payment.provider.SettlementResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementStatus;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.service.SettlementProviderRoutingService;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.service.PayrollDistributionService;
import com.yiyundao.compensation.service.EncryptionService;
import com.yiyundao.compensation.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private static final int ERROR_CODE_MAX_LENGTH = 50;
    private static final int ERROR_MSG_MAX_LENGTH = 500;

    private final List<SettlementProvider> providers;
    private final PaymentRecordService paymentRecordService;
    private final PaymentBatchService paymentBatchService;
    private final NotificationService notificationService;
    private final EmployeeMapper employeeMapper;
    private final PayrollBatchMapper payrollBatchMapper;
    private final SettlementProviderRoutingService routingService;
    private final EncryptionService encryptionService;
    private final ObjectProvider<PayrollDistributionService> payrollDistributionServiceProvider;

    private Map<String, SettlementProvider> providerMap;

    @PostConstruct
    public void initProviderMap() {
        Map<String, SettlementProvider> mapped = new LinkedHashMap<>();
        for (SettlementProvider provider : providers) {
            String code = normalizeProviderCode(provider.getProviderCode());
            if (!StringUtils.hasText(code)) {
                continue;
            }
            SettlementProvider previous = mapped.putIfAbsent(code, provider);
            if (previous != null) {
                throw new IllegalStateException("Duplicate settlement provider code: " + code);
            }
        }
        this.providerMap = mapped;
        log.info("Settlement provider initialized: {}", providerMap.keySet());
    }

    @Override
    public SettlementResult singleTransfer(Long paymentRecordId) {
        PaymentRecord record = paymentRecordService.getById(paymentRecordId);
        if (record == null) {
            throw new IllegalArgumentException("支付记录不存在: " + paymentRecordId);
        }

        TransferValidationIssueDto issue = validateRecordBeforeSubmit(record, null, true, true);
        if (issue != null) {
            return SettlementResult.builder()
                    .success(false)
                    .status(SettlementStatus.FAILED)
                    .errorCode(issue.getErrorCode())
                    .errorMsg(issue.getErrorMsg())
                    .responseTime(LocalDateTime.now())
                    .build();
        }

        record = paymentRecordService.getById(paymentRecordId);
        if (record == null) {
            throw new IllegalArgumentException("支付记录不存在: " + paymentRecordId);
        }

        String providerCode = resolveProviderCode(record);
        SettlementProvider provider = getProvider(providerCode);
        SettlementRequest request = buildRequest(record);
        SettlementResult result = provider.singleTransfer(request);
        persistProviderResult(record.getId(), providerCode, result);
        return result;
    }

    @Override
    public PaymentBatchTransferValidationDto validateBatchForTransfer(String batchNo, boolean persistFailure) {
        if (!StringUtils.hasText(batchNo)) {
            throw new IllegalArgumentException("批次号不能为空");
        }
        PaymentBatch batch = paymentBatchService.getByBatchNo(batchNo);
        if (batch == null) {
            throw new IllegalArgumentException("支付批次不存在: " + batchNo);
        }

        List<PaymentRecord> pendingRecords = paymentRecordService.getByBatchNo(batchNo, PaymentStatus.PENDING);
        if (pendingRecords.isEmpty()) {
            return PaymentBatchTransferValidationDto.builder()
                    .batchNo(batchNo)
                    .pendingCount(0)
                    .passCount(0)
                    .blockedCount(0)
                    .pass(false)
                    .warnings(List.of("当前批次无待处理记录，无需启动转账"))
                    .blockedRecords(List.of())
                    .build();
        }

        Map<Long, Employee> employeeMap = buildEmployeeMap(pendingRecords);
        List<TransferValidationIssueDto> blockedRecords = new ArrayList<>();
        int passCount = 0;

        for (PaymentRecord record : pendingRecords) {
            Employee employee = employeeMap.get(record.getEmployeeId());
            TransferValidationIssueDto issue = validateRecordBeforeSubmit(record, employee, persistFailure, false);
            if (issue == null) {
                passCount++;
            } else {
                blockedRecords.add(issue);
            }
        }

        boolean pass = blockedRecords.isEmpty();
        List<String> warnings = pass
                ? List.of()
                : List.of("存在 " + blockedRecords.size() + " 条高风险记录，已拦截发放，请先修复收款信息");

        return PaymentBatchTransferValidationDto.builder()
                .batchNo(batchNo)
                .pendingCount(pendingRecords.size())
                .passCount(passCount)
                .blockedCount(blockedRecords.size())
                .pass(pass)
                .warnings(warnings)
                .blockedRecords(blockedRecords)
                .build();
    }

    @Override
    @Async
    public void batchTransfer(String batchNo) {
        if (!StringUtils.hasText(batchNo)) {
            throw new IllegalArgumentException("批次号不能为空");
        }
        PaymentBatch batch = paymentBatchService.getByBatchNo(batchNo);
        if (batch == null) {
            throw new IllegalArgumentException("支付批次不存在: " + batchNo);
        }

        BatchStatus previousStatus = batch.getStatus();
        LocalDateTime processStartTime = LocalDateTime.now();
        paymentBatchService.update(new UpdateWrapper<PaymentBatch>()
                .eq("id", batch.getId())
                .set("status", BatchStatus.PROCESSING.getCode())
                .set("payment_status", PaymentBatchProcessStatus.PROCESSING.getCode())
                .set("process_start_time", processStartTime)
                .set("process_end_time", null));
        batch.setStatus(BatchStatus.PROCESSING);
        batch.setPaymentStatus(PaymentBatchProcessStatus.PROCESSING);
        batch.setProcessStartTime(processStartTime);
        batch.setProcessEndTime(null);

        List<PaymentRecord> pendingRecords = paymentRecordService.getByBatchNo(batchNo, PaymentStatus.PENDING);
        if (!pendingRecords.isEmpty()) {
            for (PaymentRecord record : pendingRecords) {
                if (record == null || record.getId() == null) {
                    continue;
                }
                try {
                    SettlementResult result = singleTransfer(record.getId());
                    if (result == null || !result.isSuccess()) {
                        log.warn("批次转账提交失败: batchNo={}, recordId={}, provider={}, error={}",
                                batchNo,
                                record.getId(),
                                record.getProviderCode(),
                                result == null ? "empty_result" : result.getErrorMsg());
                        markRecordFailedOnSubmitFailure(record, result);
                    }
                } catch (Exception ex) {
                    log.error("批次转账执行异常: batchNo={}, recordId={}", batchNo, record.getId(), ex);
                    markRecordFailedOnException(record, ex);
                }
            }
        }

        settleBatchStatus(batch, previousStatus);
    }

    @Override
    public SettlementStatus queryStatus(String providerCode, String providerOrderNo) {
        SettlementProvider provider = getProvider(providerCode);
        return provider.queryStatus(providerOrderNo);
    }

    @Override
    public SettlementCallbackResult handleCallback(String providerCode, Map<String, String> params) {
        SettlementProvider provider = getProvider(providerCode);
        if (!provider.verifyCallback(params)) {
            return SettlementCallbackResult.builder()
                    .success(false)
                    .errorMsg("回调验签失败")
                    .build();
        }
        SettlementCallbackResult callbackResult = provider.handleCallback(params);
        refreshBatchStatusFromCallback(providerCode, callbackResult);
        return callbackResult;
    }

    @Override
    public int reconcileProcessingBatches(int batchLimit, int recordLimitPerBatch) {
        int normalizedBatchLimit = Math.max(1, batchLimit);
        int normalizedRecordLimit = Math.max(1, recordLimitPerBatch);

        List<PaymentBatch> processingBatches = paymentBatchService.list(
                new LambdaQueryWrapper<PaymentBatch>()
                        .eq(PaymentBatch::getStatus, BatchStatus.PROCESSING)
                        .orderByAsc(PaymentBatch::getUpdateTime)
                        .last("limit " + normalizedBatchLimit)
        );
        if (processingBatches.isEmpty()) {
            return 0;
        }

        int scanned = 0;
        for (PaymentBatch batch : processingBatches) {
            if (batch == null || !StringUtils.hasText(batch.getBatchNo())) {
                continue;
            }
            scanned++;
            try {
                reconcileSingleBatch(batch, normalizedRecordLimit);
            } catch (Exception ex) {
                log.warn("轮询对账失败: batchNo={}, msg={}", batch.getBatchNo(), ex.getMessage());
            }
        }
        return scanned;
    }

    private SettlementProvider getProvider(String providerCode) {
        String normalized = normalizeProviderCode(providerCode);
        SettlementProvider provider = providerMap.get(normalized);
        if (provider == null) {
            throw new IllegalArgumentException("不支持的结算渠道: " + providerCode);
        }
        return provider;
    }

    private void reconcileSingleBatch(PaymentBatch batch, int recordLimitPerBatch) {
        List<PaymentRecord> processingRecords = paymentRecordService.list(
                new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getBatchNo, batch.getBatchNo())
                        .eq(PaymentRecord::getStatus, PaymentStatus.PROCESSING)
                        .orderByAsc(PaymentRecord::getId)
                        .last("limit " + recordLimitPerBatch)
        );

        for (PaymentRecord record : processingRecords) {
            if (record == null || record.getId() == null) {
                continue;
            }
            refreshRecordStatusFromProvider(record);
        }

        settleBatchStatus(batch, batch.getStatus());
    }

    private void refreshRecordStatusFromProvider(PaymentRecord record) {
        String providerCode = resolveProviderCode(record);
        String providerOrderNo = StringUtils.hasText(record.getProviderOrderNo())
                ? record.getProviderOrderNo()
                : record.getAlipayOrderNo();
        if (!StringUtils.hasText(providerOrderNo)) {
            log.warn("跳过轮询: recordId={} 缺少渠道订单号", record.getId());
            return;
        }

        SettlementStatus settlementStatus = queryStatus(providerCode, providerOrderNo);
        PaymentStatus targetStatus = mapSettlementStatus(settlementStatus);
        if (targetStatus == null || targetStatus == PaymentStatus.PENDING || targetStatus == PaymentStatus.PROCESSING) {
            return;
        }

        PaymentRecord update = new PaymentRecord();
        update.setId(record.getId());
        update.setProviderCode(providerCode);
        update.setProviderOrderNo(providerOrderNo);
        update.setStatus(targetStatus);
        if (targetStatus == PaymentStatus.SUCCESS) {
            update.setPaymentTime(LocalDateTime.now());
        } else {
            update.setErrorCode(StringUtils.hasText(record.getErrorCode()) ? record.getErrorCode() : "RECONCILED");
            update.setErrorMsg(StringUtils.hasText(record.getErrorMsg())
                    ? record.getErrorMsg()
                    : "settlement status reconciled by polling");
        }
        paymentRecordService.updateById(update);
    }

    private Map<Long, Employee> buildEmployeeMap(List<PaymentRecord> records) {
        if (records == null || records.isEmpty()) {
            return Map.of();
        }
        Set<Long> employeeIds = records.stream()
                .map(PaymentRecord::getEmployeeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        return employeeMapper.selectBatchIds(employeeIds).stream()
                .filter(employee -> employee != null && employee.getId() != null)
                .collect(Collectors.toMap(Employee::getId, employee -> employee));
    }

    private TransferValidationIssueDto validateRecordBeforeSubmit(PaymentRecord record,
                                                                  Employee employee,
                                                                  boolean persistFailure) {
        return validateRecordBeforeSubmit(record, employee, persistFailure, false);
    }

    private TransferValidationIssueDto validateRecordBeforeSubmit(PaymentRecord record,
                                                                  Employee employee,
                                                                  boolean persistFailure,
                                                                  boolean allowUnsupportedProviderPassThrough) {
        if (record == null || record.getId() == null) {
            return TransferValidationIssueDto.builder()
                    .recordId(record != null ? record.getId() : null)
                    .errorCode("RECORD_INVALID")
                    .errorMsg("支付记录不存在或ID为空")
                    .build();
        }

        PaymentRecord latest = paymentRecordService.getById(record.getId());
        if (latest == null) {
            return TransferValidationIssueDto.builder()
                    .recordId(record.getId())
                    .employeeId(record.getEmployeeId())
                    .errorCode("RECORD_NOT_FOUND")
                    .errorMsg("支付记录不存在")
                    .build();
        }

        if (employee == null && latest.getEmployeeId() != null) {
            employee = employeeMapper.selectById(latest.getEmployeeId());
        }

        try {
            refreshRecipientRouteBeforeTransfer(latest);
        } catch (Exception ex) {
            PaymentRecord refreshed = paymentRecordService.getById(latest.getId());
            String errorCode = refreshed != null && StringUtils.hasText(refreshed.getErrorCode())
                    ? refreshed.getErrorCode()
                    : "ACCOUNT_ROUTE_FAILED";
            String errorMsg = refreshed != null && StringUtils.hasText(refreshed.getErrorMsg())
                    ? refreshed.getErrorMsg()
                    : "收款路由校验失败";
            if (persistFailure && refreshed != null) {
                markRecordFailed(refreshed.getId(), refreshed.getProviderCode(), errorCode, errorMsg);
            }
            return buildValidationIssue(refreshed != null ? refreshed : latest, employee, errorCode, errorMsg);
        }

        latest = paymentRecordService.getById(latest.getId());
        if (latest == null) {
            return TransferValidationIssueDto.builder()
                    .recordId(record.getId())
                    .employeeId(record.getEmployeeId())
                    .errorCode("RECORD_NOT_FOUND")
                    .errorMsg("支付记录不存在")
                    .build();
        }

        String errorCode = null;
        String errorMsg = null;
        String providerCode = normalizeProviderCode(resolveProviderCode(latest));
        String paymentMethod = normalizeValidationPaymentMethod(latest.getPaymentMethod());
        String recipientAccount = latest.getRecipientAccount();
        boolean strictFieldValidation = latest.getPaymentType() == PaymentType.SALARY;

        if (latest.getStatus() != null && latest.getStatus() != PaymentStatus.PENDING) {
            errorCode = StringUtils.hasText(latest.getErrorCode()) ? latest.getErrorCode() : "STATUS_INVALID";
            errorMsg = StringUtils.hasText(latest.getErrorMsg())
                    ? latest.getErrorMsg()
                    : "记录状态不是待处理，禁止发起转账";
        } else if (!allowUnsupportedProviderPassThrough
                && (!StringUtils.hasText(providerCode) || !providerMap.containsKey(providerCode))) {
            errorCode = "PROVIDER_UNSUPPORTED";
            errorMsg = "结算渠道不可用: " + providerCode;
        } else if (strictFieldValidation && (latest.getAmount() == null || latest.getAmount().compareTo(BigDecimal.ZERO) <= 0)) {
            errorCode = "INVALID_AMOUNT";
            errorMsg = "支付金额必须大于0";
        } else if (strictFieldValidation && !StringUtils.hasText(latest.getRecipientName())) {
            errorCode = "RECIPIENT_NAME_MISSING";
            errorMsg = "收款人姓名不能为空";
        } else if (strictFieldValidation && !StringUtils.hasText(paymentMethod)) {
            errorCode = "PAYMENT_METHOD_MISSING";
            errorMsg = "支付方式不能为空";
        } else if (strictFieldValidation && !StringUtils.hasText(recipientAccount)) {
            errorCode = "ACCOUNT_MISSING";
            errorMsg = "收款账号不能为空";
        } else if (strictFieldValidation && "ALIPAY".equals(paymentMethod) && !isValidAlipayRecipientAccount(recipientAccount)) {
            errorCode = "ALIPAY_ACCOUNT_FORMAT_INVALID";
            errorMsg = "支付宝收款账号必须为手机号或邮箱";
        } else if (strictFieldValidation && "BANK_CARD".equals(paymentMethod) && !isValidBankCard(recipientAccount)) {
            errorCode = "BANK_CARD_FORMAT_INVALID";
            errorMsg = "银行卡账号格式不正确";
        } else if (strictFieldValidation && !"ALIPAY".equals(paymentMethod) && !"BANK_CARD".equals(paymentMethod)) {
            errorCode = "PAYMENT_METHOD_UNSUPPORTED";
            errorMsg = "当前支付方式暂不支持发放: " + paymentMethod;
        }

        if (!StringUtils.hasText(errorCode)) {
            return null;
        }
        if (persistFailure) {
            markRecordFailed(latest.getId(), providerCode, errorCode, errorMsg);
        }
        return buildValidationIssue(latest, employee, errorCode, errorMsg);
    }

    private void markRecordFailed(Long recordId, String providerCode, String errorCode, String errorMsg) {
        if (recordId == null) {
            return;
        }
        UpdateWrapper<PaymentRecord> wrapper = new UpdateWrapper<PaymentRecord>()
                .eq("id", recordId)
                .in("status", PaymentStatus.PENDING.getCode(), PaymentStatus.PROCESSING.getCode())
                .set("status", PaymentStatus.FAILED.getCode())
                .set("error_code", safeErrorCode(errorCode, "TRANSFER_VALIDATION_FAILED"))
                .set("error_msg", safeErrorMessage(errorMsg, "transfer validation failed"));
        if (StringUtils.hasText(providerCode)) {
            wrapper.set("provider_code", normalizeProviderCode(providerCode));
        }
        paymentRecordService.update(wrapper);
    }

    private TransferValidationIssueDto buildValidationIssue(PaymentRecord record,
                                                            Employee employee,
                                                            String errorCode,
                                                            String errorMsg) {
        String employeeName = employee != null && StringUtils.hasText(employee.getName())
                ? employee.getName()
                : (StringUtils.hasText(record.getRecipientName()) ? record.getRecipientName() : "-");
        return TransferValidationIssueDto.builder()
                .recordId(record.getId())
                .employeeId(record.getEmployeeId())
                .employeeName(employeeName)
                .providerCode(record.getProviderCode())
                .paymentMethod(record.getPaymentMethod())
                .recipientAccountMasked(maskRecipientAccount(record.getRecipientAccount(), record.getPaymentMethod()))
                .errorCode(errorCode)
                .errorMsg(errorMsg)
                .build();
    }

    private String normalizeValidationPaymentMethod(String paymentMethod) {
        if (!StringUtils.hasText(paymentMethod)) {
            return "";
        }
        return paymentMethod.trim().toUpperCase();
    }

    private boolean isValidAlipayRecipientAccount(String account) {
        if (!StringUtils.hasText(account)) {
            return false;
        }
        String normalized = account.trim();
        return ValidationUtils.isValidPhone(normalized) || ValidationUtils.isValidEmail(normalized);
    }

    private boolean isValidBankCard(String account) {
        if (!StringUtils.hasText(account)) {
            return false;
        }
        String compact = account.replaceAll("\\s+", "");
        return compact.matches("\\d{10,32}");
    }

    private String maskRecipientAccount(String account, String paymentMethod) {
        if (!StringUtils.hasText(account)) {
            return "-";
        }
        String normalizedMethod = normalizeValidationPaymentMethod(paymentMethod);
        String normalizedAccount = account.trim();
        if ("BANK_CARD".equals(normalizedMethod)) {
            String compact = normalizedAccount.replaceAll("\\s+", "");
            if (compact.length() <= 8) {
                return "****";
            }
            return compact.substring(0, 4) + "****" + compact.substring(compact.length() - 4);
        }
        if (normalizedAccount.contains("@")) {
            int atIndex = normalizedAccount.indexOf('@');
            if (atIndex <= 1) {
                return "****";
            }
            String localPart = normalizedAccount.substring(0, atIndex);
            String domainPart = normalizedAccount.substring(atIndex);
            if (localPart.length() <= 2) {
                return localPart.substring(0, 1) + "***" + domainPart;
            }
            return localPart.substring(0, 2) + "***" + domainPart;
        }
        if (normalizedAccount.length() <= 4) {
            return "****";
        }
        return normalizedAccount.substring(0, 2) + "****" + normalizedAccount.substring(normalizedAccount.length() - 2);
    }

    /**
     * 在真正发起打款前，强制按员工的结算字段重算收款路由，避免历史脏数据（如平台 subject_id）继续被用于打款。
     */
    private void refreshRecipientRouteBeforeTransfer(PaymentRecord record) {
        if (record == null || record.getEmployeeId() == null || record.getPaymentType() != PaymentType.SALARY) {
            return;
        }

        Employee employee = employeeMapper.selectById(record.getEmployeeId());
        if (employee == null) {
            return;
        }

        PayrollBatch payrollBatch = resolvePayrollBatchByPaymentBatchNo(record.getBatchNo());
        RecipientRouteResult routeResult = resolveRecipientRoute(payrollBatch, employee);

        if (!routeResult.supported()) {
            paymentRecordService.update(new LambdaUpdateWrapper<PaymentRecord>()
                    .eq(PaymentRecord::getId, record.getId())
                    .set(PaymentRecord::getStatus, PaymentStatus.FAILED)
                    .set(PaymentRecord::getProviderCode, routeResult.providerCode())
                    .set(PaymentRecord::getErrorCode, routeResult.errorCode())
                    .set(PaymentRecord::getErrorMsg, routeResult.errorMsg()));
            throw new IllegalStateException("支付路由失败: " + routeResult.errorMsg());
        }

        String recipientName = StringUtils.hasText(employee.getSettlementAccountName())
                ? employee.getSettlementAccountName().trim()
                : employee.getName();

        boolean routeChanged = !Objects.equals(record.getRecipientAccount(), routeResult.recipientAccount())
                || !Objects.equals(record.getPaymentMethod(), routeResult.paymentMethod())
                || !Objects.equals(normalizeProviderCode(record.getProviderCode()), normalizeProviderCode(routeResult.providerCode()))
                || !Objects.equals(record.getRecipientName(), recipientName);
        if (!routeChanged) {
            return;
        }

        paymentRecordService.update(new LambdaUpdateWrapper<PaymentRecord>()
                .eq(PaymentRecord::getId, record.getId())
                .set(PaymentRecord::getRecipientAccount, routeResult.recipientAccount())
                .set(PaymentRecord::getPaymentMethod, routeResult.paymentMethod())
                .set(PaymentRecord::getProviderCode, routeResult.providerCode())
                .set(PaymentRecord::getRecipientName, recipientName)
                .set(PaymentRecord::getErrorCode, null)
                .set(PaymentRecord::getErrorMsg, null));
    }

    private RecipientRouteResult resolveRecipientRoute(PayrollBatch payrollBatch, Employee employee) {
        if (employee == null) {
            return RecipientRouteResult.failed("ACCOUNT_MISSING", "员工信息不存在");
        }

        String employmentType = resolveEmploymentType(payrollBatch, employee);
        if (!StringUtils.hasText(employmentType)) {
            return RecipientRouteResult.failed("EMPLOYMENT_TYPE_MISSING", "缺少用工类型，无法路由结算渠道");
        }

        String settlementType = normalizeSettlementType(employee.getSettlementAccountType());
        String settlementAccount = decryptAccount(employee.getSettlementAccount());
        String bankAccount = decryptAccount(employee.getBankAccount());

        if (!StringUtils.hasText(settlementAccount) && StringUtils.hasText(bankAccount)) {
            settlementAccount = bankAccount;
            if (!StringUtils.hasText(settlementType)) {
                settlementType = SettlementAccountType.BANK_CARD.getCode();
            }
        }
        if (!StringUtils.hasText(settlementType) && StringUtils.hasText(settlementAccount)) {
            settlementType = inferSettlementType(settlementAccount);
        }

        if (!StringUtils.hasText(settlementAccount)) {
            String fallbackAlipayAccount = resolveFallbackAlipayAccount(employee);
            if (StringUtils.hasText(fallbackAlipayAccount)) {
                settlementAccount = fallbackAlipayAccount;
                settlementType = SettlementAccountType.ALIPAY.getCode();
            }
        }

        if (!StringUtils.hasText(settlementAccount)) {
            return RecipientRouteResult.failed("ACCOUNT_MISSING", "缺少收款账号（结算账户/银行卡/手机号/邮箱）");
        }

        String accountType = StringUtils.hasText(settlementType) ? settlementType : SettlementAccountType.BANK_CARD.getCode();
        String paymentMethod = resolvePaymentMethod(accountType);

        if (EmploymentType.PART_TIME.getCode().equals(employmentType)) {
            if (!SettlementAccountType.ALIPAY.getCode().equals(accountType)) {
                return RecipientRouteResult.failed(
                        accountType,
                        settlementAccount,
                        paymentMethod,
                        "yunzhanghu",
                        "ACCOUNT_TYPE_UNSUPPORTED",
                        "灵活用工仅支持支付宝收款账户"
                );
            }
            return RecipientRouteResult.supported(accountType, settlementAccount, "ALIPAY", "yunzhanghu");
        }

        if (EmploymentType.FULL_TIME.getCode().equals(employmentType)) {
            if (SettlementAccountType.ALIPAY.getCode().equals(accountType)) {
                return RecipientRouteResult.supported(accountType, settlementAccount, "ALIPAY", "alipay");
            }
            if (SettlementAccountType.BANK_CARD.getCode().equals(accountType)) {
                return RecipientRouteResult.supported(accountType, settlementAccount, "BANK_CARD", "alipay");
            }
            return RecipientRouteResult.failed(
                    accountType,
                    settlementAccount,
                    paymentMethod,
                    "alipay",
                    "ACCOUNT_TYPE_UNSUPPORTED",
                    "全职用工仅支持支付宝或银行卡收款账户"
            );
        }

        return RecipientRouteResult.failed(
                accountType,
                settlementAccount,
                paymentMethod,
                "unknown",
                "EMPLOYMENT_TYPE_UNSUPPORTED",
                "不支持的用工类型: " + employmentType
        );
    }

    private String resolveEmploymentType(PayrollBatch payrollBatch, Employee employee) {
        String rawEmploymentType = employee != null ? employee.getEmploymentType() : null;
        if (!StringUtils.hasText(rawEmploymentType) && payrollBatch != null) {
            rawEmploymentType = payrollBatch.getType();
        }
        return normalizeEmploymentType(rawEmploymentType);
    }

    private String normalizeEmploymentType(String employmentType) {
        if (!StringUtils.hasText(employmentType)) {
            return null;
        }
        String normalized = employmentType.trim().toLowerCase();
        return switch (normalized) {
            case "fulltime", "full-time", "full_time" -> EmploymentType.FULL_TIME.getCode();
            case "parttime", "part-time", "part_time" -> EmploymentType.PART_TIME.getCode();
            default -> normalized;
        };
    }

    private String resolveFallbackAlipayAccount(Employee employee) {
        if (employee == null) {
            return null;
        }
        if (StringUtils.hasText(employee.getPhone())) {
            return employee.getPhone().trim();
        }
        if (StringUtils.hasText(employee.getEmail())) {
            return employee.getEmail().trim();
        }
        return null;
    }

    private String inferSettlementType(String account) {
        if (!StringUtils.hasText(account)) {
            return SettlementAccountType.BANK_CARD.getCode();
        }
        if (ValidationUtils.isValidPhone(account) || ValidationUtils.isValidEmail(account)) {
            return SettlementAccountType.ALIPAY.getCode();
        }
        return SettlementAccountType.BANK_CARD.getCode();
    }

    private String normalizeSettlementType(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        String normalized = type.trim().toLowerCase();
        return switch (normalized) {
            case "bank", "bankcard", "bank_card" -> SettlementAccountType.BANK_CARD.getCode();
            case "alipay" -> SettlementAccountType.ALIPAY.getCode();
            case "wechat", "weixin", "wx" -> SettlementAccountType.WECHAT.getCode();
            case "other" -> SettlementAccountType.OTHER.getCode();
            default -> normalized;
        };
    }

    private String resolvePaymentMethod(String accountType) {
        return switch (accountType) {
            case "alipay" -> "ALIPAY";
            case "bank_card" -> "BANK_CARD";
            case "wechat" -> "WECHAT";
            case "other" -> "OTHER";
            default -> "UNKNOWN";
        };
    }

    private String decryptAccount(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue)) {
            return null;
        }
        try {
            return encryptionService.decrypt(encryptedValue);
        } catch (Exception ex) {
            log.warn("解密收款账户失败，按明文兜底处理: {}", ex.getMessage());
            return encryptedValue;
        }
    }

    private String resolveProviderCode(PaymentRecord record) {
        // 如果记录已有渠道代码，直接使用
        if (StringUtils.hasText(record.getProviderCode())) {
            return normalizeProviderCode(record.getProviderCode());
        }

        // 使用新的路由逻辑
        try {
            // 获取员工信息
            Employee employee = employeeMapper.selectById(record.getEmployeeId());
            if (employee == null) {
                log.warn("员工不存在，使用默认渠道: employeeId={}", record.getEmployeeId());
                return "alipay";
            }

            // 获取薪酬批次信息（如果有）
            PayrollBatch payrollBatch = resolvePayrollBatchByPaymentBatchNo(record.getBatchNo());

            // 使用路由服务确定渠道
            String providerCode = routingService.determineProvider(employee, payrollBatch);
            log.debug("路由确定渠道: employeeId={}, provider={}", employee.getId(), providerCode);
            return normalizeProviderCode(providerCode);

        } catch (Exception e) {
            log.error("路由确定渠道失败，使用默认渠道: recordId={}", record.getId(), e);
            // 兜底逻辑：使用 paymentMethod 或默认 alipay
            if (StringUtils.hasText(record.getPaymentMethod())) {
                return normalizeProviderCode(record.getPaymentMethod());
            }
            return "alipay";
        }
    }

    private PayrollBatch resolvePayrollBatchByPaymentBatchNo(String paymentBatchNo) {
        if (!StringUtils.hasText(paymentBatchNo)) {
            return null;
        }
        return payrollBatchMapper.selectOne(new LambdaQueryWrapper<PayrollBatch>()
                .eq(PayrollBatch::getPaymentBatchNo, paymentBatchNo)
                .last("limit 1"));
    }

    private String normalizeProviderCode(String providerCode) {
        if (!StringUtils.hasText(providerCode)) {
            return "";
        }
        return providerCode.trim().toLowerCase();
    }

    private PaymentStatus mapSettlementStatus(SettlementStatus settlementStatus) {
        if (settlementStatus == null) {
            return PaymentStatus.PROCESSING;
        }
        return switch (settlementStatus) {
            case SUCCESS -> PaymentStatus.SUCCESS;
            case FAILED -> PaymentStatus.FAILED;
            case CANCELLED -> PaymentStatus.CANCELLED;
            case PENDING -> PaymentStatus.PENDING;
            case PROCESSING, AUDITING, SIGNING, TAXING, WITHDRAWING -> PaymentStatus.PROCESSING;
        };
    }

    private SettlementRequest buildRequest(PaymentRecord record) {
        return SettlementRequest.builder()
                .paymentRecordId(record.getId())
                .bizNo(StringUtils.hasText(record.getProviderOrderNo()) ? record.getProviderOrderNo() : record.getAlipayOrderNo())
                .amount(record.getAmount())
                .currency(record.getCurrency())
                .recipientName(record.getRecipientName())
                .recipientAccount(record.getRecipientAccount())
                .remark(record.getPaymentDesc())
                .build();
    }

    private void refreshBatchStatusFromCallback(String providerCode, SettlementCallbackResult callbackResult) {
        if (callbackResult == null || !StringUtils.hasText(callbackResult.getBizNo())) {
            return;
        }
        String normalizedProviderCode = normalizeProviderCode(providerCode);
        PaymentRecord record = paymentRecordService.getByProviderOrderNo(normalizedProviderCode, callbackResult.getBizNo());
        if (record == null || !StringUtils.hasText(record.getBatchNo())) {
            return;
        }
        PaymentBatch batch = paymentBatchService.getByBatchNo(record.getBatchNo());
        if (batch == null) {
            return;
        }
        settleBatchStatus(batch, batch.getStatus());
    }

    private void settleBatchStatus(PaymentBatch batch, BatchStatus previousStatus) {
        List<PaymentRecord> allRecords = paymentRecordService.getByBatchNo(batch.getBatchNo(), null);
        if (allRecords.isEmpty()) {
            batch.setSuccessCount(0);
            batch.setFailedCount(0);
            batch.setStatus(BatchStatus.FAILED);
            batch.setPaymentStatus(PaymentBatchProcessStatus.FAILED);
            batch.setProcessEndTime(LocalDateTime.now());
            paymentBatchService.update(new UpdateWrapper<PaymentBatch>()
                    .eq("id", batch.getId())
                    .set("success_count", 0)
                    .set("failed_count", 0)
                    .set("status", BatchStatus.FAILED.getCode())
                    .set("payment_status", PaymentBatchProcessStatus.FAILED.getCode())
                    .set("process_end_time", batch.getProcessEndTime()));
            syncPayrollBatchStatus(batch);
            syncDistributionStatus(batch);
            if (previousStatus != BatchStatus.FAILED) {
                safeNotifyBatchCompleted(batch);
            }
            return;
        }

        int successCount = 0;
        int failedCount = 0;
        int processingCount = 0;
        for (PaymentRecord record : allRecords) {
            PaymentStatus status = record.getStatus();
            if (status == PaymentStatus.SUCCESS) {
                successCount++;
            } else if (status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED) {
                failedCount++;
            } else {
                processingCount++;
            }
        }

        BatchStatus targetStatus;
        if (processingCount > 0) {
            targetStatus = BatchStatus.PROCESSING;
            batch.setProcessEndTime(null);
        } else if (failedCount == 0) {
            targetStatus = BatchStatus.COMPLETED;
            batch.setProcessEndTime(LocalDateTime.now());
        } else if (successCount == 0) {
            targetStatus = BatchStatus.FAILED;
            batch.setProcessEndTime(LocalDateTime.now());
        } else {
            targetStatus = BatchStatus.COMPLETED;
            batch.setProcessEndTime(LocalDateTime.now());
        }

        batch.setSuccessCount(successCount);
        batch.setFailedCount(failedCount);
        batch.setStatus(targetStatus);
        batch.setPaymentStatus(mapPaymentBatchProcessStatus(targetStatus, successCount > 0 && failedCount > 0));
        paymentBatchService.update(new UpdateWrapper<PaymentBatch>()
                .eq("id", batch.getId())
                .set("success_count", successCount)
                .set("failed_count", failedCount)
                .set("status", targetStatus.getCode())
                .set("payment_status", mapPaymentBatchProcessStatus(targetStatus, successCount > 0 && failedCount > 0).getCode())
                .set("process_end_time", batch.getProcessEndTime()));

        if (isTerminalStatus(targetStatus)) {
            syncPayrollBatchStatus(batch);
            syncDistributionStatus(batch);
            if (previousStatus != targetStatus) {
                safeNotifyBatchCompleted(batch);
            }
        } else if (batch.getDistributionId() != null) {
            syncDistributionStatus(batch);
        }
    }

    private boolean isTerminalStatus(BatchStatus status) {
        return status == BatchStatus.COMPLETED || status == BatchStatus.FAILED;
    }

    private void safeNotifyBatchCompleted(PaymentBatch batch) {
        try {
            notificationService.sendBatchCompleteNotification(batch);
        } catch (Exception ex) {
            log.warn("批次完成通知发送失败: batchNo={}, msg={}", batch.getBatchNo(), ex.getMessage());
        }
    }

    private void syncDistributionStatus(PaymentBatch paymentBatch) {
        if (paymentBatch == null || paymentBatch.getDistributionId() == null) {
            return;
        }
        PayrollDistributionService payrollDistributionService = payrollDistributionServiceProvider.getIfAvailable();
        if (payrollDistributionService == null) {
            return;
        }
        payrollDistributionService.syncFromPaymentBatch(paymentBatch);
    }

    private PaymentBatchProcessStatus mapPaymentBatchProcessStatus(BatchStatus status, boolean partialSuccess) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case DRAFT -> PaymentBatchProcessStatus.CREATED;
            case SUBMITTED, APPROVED -> PaymentBatchProcessStatus.SUBMITTED;
            case PROCESSING -> PaymentBatchProcessStatus.PROCESSING;
            case COMPLETED -> partialSuccess ? PaymentBatchProcessStatus.PARTIAL_SUCCESS : PaymentBatchProcessStatus.SUCCESS;
            case FAILED -> PaymentBatchProcessStatus.FAILED;
        };
    }

    private void syncPayrollBatchStatus(PaymentBatch paymentBatch) {
        if (paymentBatch == null || !StringUtils.hasText(paymentBatch.getBatchNo())) {
            return;
        }
        BatchStatus status = paymentBatch.getStatus();
        if (status != BatchStatus.COMPLETED && status != BatchStatus.FAILED) {
            return;
        }
        boolean partialSuccess = paymentBatch.getPaymentStatus() == PaymentBatchProcessStatus.PARTIAL_SUCCESS
                || (paymentBatch.getSuccessCount() != null && paymentBatch.getSuccessCount() > 0
                && paymentBatch.getFailedCount() != null && paymentBatch.getFailedCount() > 0);
        UpdateWrapper<PayrollBatch> wrapper = new UpdateWrapper<PayrollBatch>()
                .eq("payment_batch_no", paymentBatch.getBatchNo())
                .set("status", status == BatchStatus.COMPLETED && !partialSuccess ? "paid" : "pay_failed");
        payrollBatchMapper.update(null, wrapper);
    }

    private void persistProviderResult(Long recordId, String providerCode, SettlementResult result) {
        if (recordId == null || result == null) {
            return;
        }
        PaymentRecord update = new PaymentRecord();
        update.setId(recordId);
        update.setProviderCode(providerCode);
        if (StringUtils.hasText(result.getProviderOrderNo())) {
            update.setProviderOrderNo(result.getProviderOrderNo());
        }
        if (StringUtils.hasText(result.getProviderTradeNo())) {
            update.setProviderTradeNo(result.getProviderTradeNo());
        }
        paymentRecordService.updateById(update);
    }

    private void markRecordFailedOnSubmitFailure(PaymentRecord record, SettlementResult result) {
        if (record == null || record.getId() == null) {
            return;
        }
        String errorCode = safeErrorCode(result == null ? null : result.getErrorCode(), "SETTLEMENT_SUBMIT_FAILED");
        String errorMsg = safeErrorMessage(result == null ? null : result.getErrorMsg(), "settlement submit failed");
        paymentRecordService.update(new LambdaUpdateWrapper<PaymentRecord>()
                .eq(PaymentRecord::getId, record.getId())
                .in(PaymentRecord::getStatus, PaymentStatus.PENDING, PaymentStatus.PROCESSING)
                .set(PaymentRecord::getStatus, PaymentStatus.FAILED)
                .set(PaymentRecord::getErrorCode, errorCode)
                .set(PaymentRecord::getErrorMsg, errorMsg));
    }

    private void markRecordFailedOnException(PaymentRecord record, Exception ex) {
        if (record == null || record.getId() == null) {
            return;
        }
        String errorMsg = safeErrorMessage(ex == null ? null : ex.getMessage(), "batch transfer exception");
        paymentRecordService.update(new LambdaUpdateWrapper<PaymentRecord>()
                .eq(PaymentRecord::getId, record.getId())
                .in(PaymentRecord::getStatus, PaymentStatus.PENDING, PaymentStatus.PROCESSING)
                .set(PaymentRecord::getStatus, PaymentStatus.FAILED)
                .set(PaymentRecord::getErrorCode, "BATCH_TRANSFER_EXCEPTION")
                .set(PaymentRecord::getErrorMsg, errorMsg));
    }

    private String safeErrorCode(String raw, String fallback) {
        return truncate(raw, ERROR_CODE_MAX_LENGTH, fallback);
    }

    private String safeErrorMessage(String raw, String fallback) {
        return truncate(raw, ERROR_MSG_MAX_LENGTH, fallback);
    }

    private String truncate(String raw, int maxLength, String fallback) {
        String value = StringUtils.hasText(raw) ? raw.trim() : fallback;
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record RecipientRouteResult(
            String accountType,
            String recipientAccount,
            String paymentMethod,
            String providerCode,
            String errorCode,
            String errorMsg
    ) {
        private static RecipientRouteResult supported(String accountType,
                                                      String recipientAccount,
                                                      String paymentMethod,
                                                      String providerCode) {
            return new RecipientRouteResult(accountType, recipientAccount, paymentMethod, providerCode, null, null);
        }

        private static RecipientRouteResult failed(String errorCode, String errorMsg) {
            return new RecipientRouteResult(null, null, "UNKNOWN", "unknown", errorCode, errorMsg);
        }

        private static RecipientRouteResult failed(String accountType,
                                                   String recipientAccount,
                                                   String paymentMethod,
                                                   String providerCode,
                                                   String errorCode,
                                                   String errorMsg) {
            return new RecipientRouteResult(accountType, recipientAccount, paymentMethod, providerCode, errorCode, errorMsg);
        }

        private boolean supported() {
            return StringUtils.hasText(recipientAccount) && !StringUtils.hasText(errorCode);
        }
    }
}
