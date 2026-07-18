package com.yiyundao.compensation.modules.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.PaymentType;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.common.utils.ValidationUtils;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollDistributionItemMapper;
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
import com.yiyundao.compensation.modules.payment.support.PaymentRecordStatusTransitions;
import com.yiyundao.compensation.modules.payment.support.SettlementRecipientRouteResolver;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistributionItem;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentFailureService;
import com.yiyundao.compensation.modules.payroll.service.PayrollDistributionService;
import com.yiyundao.compensation.modules.payroll.service.PayrollSettlementIntegrityService;
import com.yiyundao.compensation.enums.PayrollDistributionItemStatus;
import com.yiyundao.compensation.service.EncryptionService;
import com.yiyundao.compensation.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private static final int ERROR_CODE_MAX_LENGTH = 50;
    private static final int ERROR_MSG_MAX_LENGTH = 500;
    private static final int DEFAULT_RECONCILE_BATCH_LIMIT = 20;
    private static final int DEFAULT_RECONCILE_RECORD_LIMIT = 200;
    private static final int MAX_RECONCILE_BATCH_LIMIT = 100;
    private static final int MAX_RECONCILE_RECORD_LIMIT = 500;
    private final List<SettlementProvider> providers;
    private final PaymentRecordService paymentRecordService;
    private final PaymentBatchService paymentBatchService;
    private final NotificationService notificationService;
    private final EmployeeMapper employeeMapper;
    private final PayrollBatchMapper payrollBatchMapper;
    private final PayrollDistributionItemMapper payrollDistributionItemMapper;
    private final SettlementProviderRoutingService routingService;
    private final EncryptionService encryptionService;
    private final ObjectProvider<PayrollDistributionService> payrollDistributionServiceProvider;
    private final ObjectProvider<PayrollPaymentFailureService> payrollPaymentFailureServiceProvider;
    private final ObjectProvider<PlatformTransactionManager> transactionManagerProvider;
    private PayrollSettlementIntegrityService payrollSettlementIntegrityService;

    private Map<String, SettlementProvider> providerMap;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setPayrollSettlementIntegrityService(
            PayrollSettlementIntegrityService payrollSettlementIntegrityService) {
        this.payrollSettlementIntegrityService = payrollSettlementIntegrityService;
    }

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
        PaymentBatch batch = resolveBatchForRecord(record);
        PayrollDistributionTransferCheck distributionCheck = validatePayrollDistributionForTransfer(batch);
        if (!distributionCheck.pass()) {
            return SettlementResult.builder()
                    .success(false)
                    .status(SettlementStatus.FAILED)
                    .errorCode("PAYROLL_DISTRIBUTION_INVALID")
                    .errorMsg(distributionCheck.message())
                    .responseTime(LocalDateTime.now())
                    .build();
        }

        record = paymentRecordService.getById(paymentRecordId);
        if (record == null) {
            throw new IllegalArgumentException("支付记录不存在: " + paymentRecordId);
        }
        String providerCode = resolveProviderCode(record);
        SettlementProvider provider = getProvider(providerCode);
        if (!claimRecordForTransfer(record.getId())) {
            log.info("支付记录已被其他任务领取或状态已变化，跳过重复提交: recordId={}", record.getId());
            return SettlementResult.builder()
                    .success(true)
                    .status(SettlementStatus.PROCESSING)
                    .errorCode("RECORD_ALREADY_PROCESSING")
                    .errorMsg("支付记录已在处理中或已完成")
                    .responseTime(LocalDateTime.now())
                    .build();
        }
        record.setStatus(PaymentStatus.PROCESSING);

        SettlementRequest request = buildRequest(record);
        SettlementResult result;
        try {
            result = provider.singleTransfer(request);
        } catch (Exception ex) {
            log.error("结算渠道调用异常: recordId={}, provider={}", record.getId(), providerCode, ex);
            result = SettlementResult.builder()
                    .success(false)
                    .status(SettlementStatus.FAILED)
                    .errorCode("PROVIDER_EXCEPTION")
                    .errorMsg(safeErrorMessage(ex.getMessage(), "结算渠道调用异常"))
                    .responseTime(LocalDateTime.now())
                    .build();
        }
        boolean updated = persistProviderResult(record, providerCode, result);
        if (updated) {
            notifyTerminalPaymentRecordFromResult(providerCode, record, result);
        }
        return result;
    }

    private boolean claimRecordForTransfer(Long recordId) {
        if (recordId == null) {
            return false;
        }
        return paymentRecordService.update(new UpdateWrapper<PaymentRecord>()
                .eq("id", recordId)
                .eq("status", PaymentStatus.PENDING.getCode())
                .set("status", PaymentStatus.PROCESSING.getCode()));
    }

    @Override
    public SettlementResult retryFailedRecord(Long paymentRecordId) {
        PaymentRecord record = paymentRecordService.getById(paymentRecordId);
        if (record == null) {
            throw new IllegalArgumentException("支付记录不存在: " + paymentRecordId);
        }
        if (record.getStatus() != PaymentStatus.FAILED && record.getStatus() != PaymentStatus.CANCELLED) {
            throw new IllegalStateException("仅失败或已取消的支付记录支持重试");
        }

        PaymentBatch batch = StringUtils.hasText(record.getBatchNo())
                ? paymentBatchService.getByBatchNo(record.getBatchNo())
                : null;
        if (batch != null && batch.getStatus() == BatchStatus.PROCESSING) {
            throw new IllegalStateException("支付批次正在处理中，请稍后查看结果");
        }

        SettlementResult reconciledResult = reconcileExistingProviderOrderBeforeRetry(record, batch);
        if (reconciledResult != null) {
            return reconciledResult;
        }
        AtomicReference<PaymentBatch> claimedBatch = new AtomicReference<>();
        PaymentBatch preparedBatch;
        try {
            preparedBatch = executeRetryPreparation(transactionStatus ->
                    prepareRetrySubmission(record, batch, claimedBatch));
        } catch (Exception ex) {
            PaymentBatch batchToSettle = claimedBatch.get();
            if (batchToSettle != null) {
                PaymentBatch latestBatch = paymentBatchService.getByBatchNo(batchToSettle.getBatchNo());
                settleBatchStatus(latestBatch != null ? latestBatch : batchToSettle, BatchStatus.PROCESSING);
            }
            throw ex;
        }

        SettlementResult result;
        try {
            result = singleTransfer(paymentRecordId);
            if (shouldMarkRecordFailed(result)) {
                markRecordFailedOnSubmitFailure(paymentRecordService.getById(paymentRecordId), result);
            }
        } catch (Exception ex) {
            markRecordFailedOnException(paymentRecordService.getById(paymentRecordId), ex);
            throw ex;
        } finally {
            if (preparedBatch != null) {
                PaymentBatch latestBatch = paymentBatchService.getByBatchNo(preparedBatch.getBatchNo());
                settleBatchStatus(latestBatch != null ? latestBatch : preparedBatch, BatchStatus.PROCESSING);
            }
        }
        return result;
    }

    private PaymentBatch prepareRetrySubmission(PaymentRecord record,
                                                PaymentBatch batch,
                                                AtomicReference<PaymentBatch> claimedBatch) {
        claimDistributionRecordRetry(batch, record);
        PayrollDistributionTransferCheck distributionCheck = validatePayrollDistributionForTransfer(batch);
        if (!distributionCheck.pass()) {
            throw new IllegalStateException(distributionCheck.message());
        }

        if (batch != null) {
            LocalDateTime processStartTime = LocalDateTime.now();
            if (!claimBatchForRecordRetry(batch, processStartTime)) {
                throw new IllegalStateException("支付批次状态已变更，不能重试");
            }
            claimedBatch.set(batch);
            batch.setStatus(BatchStatus.PROCESSING);
            batch.setPaymentStatus(PaymentBatchProcessStatus.PROCESSING);
            batch.setProcessStartTime(processStartTime);
            batch.setProcessEndTime(null);
        }

        boolean reset = paymentRecordService.update(new UpdateWrapper<PaymentRecord>()
                .eq("id", record.getId())
                .in("status", PaymentStatus.FAILED.getCode(), PaymentStatus.CANCELLED.getCode())
                .set("status", PaymentStatus.PENDING.getCode())
                .set("error_code", null)
                .set("error_msg", null)
                .set("payment_time", null)
                .set("notification_time", null)
                .set("provider_order_no", null)
                .set("provider_trade_no", null)
                .set("alipay_order_no", null)
                .set("alipay_trade_no", null));
        if (!reset) {
            throw new IllegalStateException("支付记录状态已变更，不能重试");
        }
        if (claimedBatch.get() != null) {
            syncPayrollBatchProcessing(batch);
        }
        return batch;
    }

    private <T> T executeRetryPreparation(TransactionCallback<T> callback) {
        PlatformTransactionManager transactionManager = transactionManagerProvider.getIfAvailable();
        if (transactionManager == null) {
            return callback.doInTransaction(null);
        }
        return new TransactionTemplate(transactionManager).execute(callback);
    }

    private boolean claimBatchForRecordRetry(PaymentBatch batch, LocalDateTime processStartTime) {
        if (batch == null || batch.getId() == null) {
            return false;
        }
        return paymentBatchService.update(new UpdateWrapper<PaymentBatch>()
                .eq("id", batch.getId())
                .and(status -> status
                        .in("status",
                                BatchStatus.SUBMITTED.getCode(),
                                BatchStatus.APPROVED.getCode(),
                                BatchStatus.FAILED.getCode())
                        .or(partial -> partial
                                .eq("status", BatchStatus.COMPLETED.getCode())
                                .eq("payment_status", PaymentBatchProcessStatus.PARTIAL_SUCCESS.getCode())))
                .set("status", BatchStatus.PROCESSING.getCode())
                .set("payment_status", PaymentBatchProcessStatus.PROCESSING.getCode())
                .set("process_start_time", processStartTime)
                .set("process_end_time", null));
    }

    private void claimDistributionRecordRetry(PaymentBatch batch, PaymentRecord record) {
        if (batch == null || batch.getDistributionId() == null || record == null || record.getId() == null) {
            return;
        }
        PayrollDistributionService distributionService = payrollDistributionServiceProvider.getIfAvailable();
        if (distributionService == null) {
            throw new IllegalStateException("薪资发放服务不可用，无法校验记录重试次数");
        }
        PayrollDistribution distribution = distributionService.getById(batch.getDistributionId());
        if (distribution == null) {
            throw new IllegalStateException("关联薪资发放单不存在，无法重试支付记录");
        }
        List<PayrollDistributionItem> activeItems = distributionService.listActiveItems(distribution.getId());
        if (activeItems == null || activeItems.isEmpty()) {
            return;
        }
        PayrollDistributionItem item = activeItems.stream()
                .filter(candidate -> record.getId().equals(candidate.getPaymentRecordId()))
                .findFirst()
                .orElse(null);
        if (item == null) {
            return;
        }
        int retryCount = item.getRetryCount() == null ? 0 : Math.max(item.getRetryCount(), 0);
        int retryLimit = distribution.getRetryLimit() == null || distribution.getRetryLimit() < 1
                ? 3
                : distribution.getRetryLimit();
        if (retryCount >= retryLimit) {
            throw new IllegalStateException("该工资明细已达到最大重试次数: " + retryLimit);
        }
        if (item.getId() == null || item.getItemStatus() != PayrollDistributionItemStatus.FAILED) {
            throw new IllegalStateException("工资明细状态已变更，不能重复重试");
        }
        boolean claimed = payrollDistributionItemMapper.update(null, new UpdateWrapper<PayrollDistributionItem>()
                .eq("id", item.getId())
                .eq("payment_record_id", record.getId())
                .eq("item_status", PayrollDistributionItemStatus.FAILED.getCode())
                .eq("retry_count", retryCount)
                .set("item_status", PayrollDistributionItemStatus.RETRYING.getCode())
                .set("retry_count", retryCount + 1)
                .set("failure_reason", null)) > 0;
        if (!claimed) {
            throw new IllegalStateException("工资明细状态已变更，不能重试");
        }
    }

    private SettlementResult reconcileExistingProviderOrderBeforeRetry(PaymentRecord record, PaymentBatch batch) {
        String providerOrderNo = resolveProviderOrderNo(record);
        if (!StringUtils.hasText(providerOrderNo)) {
            if (hasProviderTradeNo(record)) {
                throw new IllegalStateException("存在已提交渠道交易号，请等待对账确认后再重试，避免重复打款");
            }
            return null;
        }

        String providerCode = resolveProviderCode(record);
        SettlementStatus settlementStatus;
        try {
            settlementStatus = queryStatus(providerCode, providerOrderNo);
        } catch (Exception ex) {
            throw new IllegalStateException("渠道订单状态查询失败，请稍后重试或等待自动对账", ex);
        }

        PaymentStatus targetStatus = mapSettlementStatus(settlementStatus);
        if (targetStatus == PaymentStatus.SUCCESS) {
            refreshRecordStatusFromProvider(record, settlementStatus, providerCode, providerOrderNo);
            if (batch != null) {
                PaymentBatch latestBatch = paymentBatchService.getByBatchNo(batch.getBatchNo());
                settleBatchStatus(latestBatch != null ? latestBatch : batch, batch.getStatus());
            }
            return SettlementResult.builder()
                    .success(true)
                    .providerOrderNo(providerOrderNo)
                    .status(SettlementStatus.SUCCESS)
                    .responseTime(LocalDateTime.now())
                    .build();
        }
        if (targetStatus == PaymentStatus.FAILED || targetStatus == PaymentStatus.CANCELLED) {
            return null;
        }
        throw new IllegalStateException("渠道订单仍在处理中，请等待对账完成后再重试");
    }

    private PaymentBatch resolveBatchForRecord(PaymentRecord record) {
        if (record == null || !StringUtils.hasText(record.getBatchNo())) {
            return null;
        }
        return paymentBatchService.getByBatchNo(record.getBatchNo());
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
        PayrollDistributionTransferCheck distributionCheck = validatePayrollDistributionForTransfer(batch);
        if (!distributionCheck.pass()) {
            return PaymentBatchTransferValidationDto.builder()
                    .batchNo(batchNo)
                    .pendingCount(0)
                    .passCount(0)
                    .blockedCount(0)
                    .pass(false)
                    .warnings(List.of(distributionCheck.message()))
                    .blockedRecords(List.of())
                    .build();
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
            Long employeeId = record == null ? null : record.getEmployeeId();
            Employee employee = employeeId == null ? null : employeeMap.get(employeeId);
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
        if (persistFailure && !blockedRecords.isEmpty()) {
            refreshBatchAfterPersistedValidationFailures(batch);
        }

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

    private void refreshBatchAfterPersistedValidationFailures(PaymentBatch batch) {
        if (batch == null || !StringUtils.hasText(batch.getBatchNo())) {
            return;
        }
        List<PaymentRecord> allRecords = paymentRecordService.getByBatchNo(batch.getBatchNo(), null);
        int successCount = 0;
        int failedCount = 0;
        int pendingCount = 0;
        int processingCount = 0;
        for (PaymentRecord record : allRecords) {
            PaymentStatus status = record == null ? null : record.getStatus();
            if (status == PaymentStatus.SUCCESS) {
                successCount++;
            } else if (status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED) {
                failedCount++;
            } else if (status == PaymentStatus.PENDING) {
                pendingCount++;
            } else if (status == PaymentStatus.PROCESSING) {
                processingCount++;
            }
        }
        BatchStatus targetStatus = pendingCount > 0 || processingCount > 0
                ? BatchStatus.SUBMITTED
                : successCount > 0 && failedCount > 0
                ? BatchStatus.COMPLETED
                : BatchStatus.FAILED;
        PaymentBatchProcessStatus paymentStatus = pendingCount > 0 || processingCount > 0
                ? PaymentBatchProcessStatus.SUBMITTED
                : successCount > 0 && failedCount > 0
                ? PaymentBatchProcessStatus.PARTIAL_SUCCESS
                : PaymentBatchProcessStatus.FAILED;
        batch.setSuccessCount(successCount);
        batch.setFailedCount(failedCount);
        batch.setStatus(targetStatus);
        batch.setPaymentStatus(paymentStatus);
        batch.setProcessEndTime(LocalDateTime.now());

        paymentBatchService.update(new UpdateWrapper<PaymentBatch>()
                .eq("batch_no", batch.getBatchNo())
                .set("success_count", successCount)
                .set("failed_count", failedCount)
                .set("status", targetStatus.getCode())
                .set("payment_status", paymentStatus.getCode())
                .set("process_end_time", batch.getProcessEndTime()));
        syncPayrollBatchStatus(batch);
        syncDistributionStatus(batch);
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
        if (previousStatus == BatchStatus.PROCESSING) {
            log.info("支付批次已在处理中，跳过重复提交: batchNo={}", batchNo);
            return;
        }
        if (previousStatus != BatchStatus.SUBMITTED && previousStatus != BatchStatus.APPROVED) {
            log.warn("支付批次状态不可启动转账: batchNo={}, status={}", batchNo, previousStatus);
            return;
        }
        PayrollDistributionTransferCheck distributionCheck = validatePayrollDistributionForTransfer(batch);
        if (!distributionCheck.pass()) {
            log.warn("薪资支付批次关联发放单不可启动转账: batchNo={}, reason={}", batchNo, distributionCheck.message());
            markBatchTransferBlocked(batch, distributionCheck.message());
            return;
        }

        LocalDateTime processStartTime = LocalDateTime.now();
        if (!claimBatchForTransfer(batch, processStartTime)) {
            log.info("支付批次已被其他任务领取，跳过重复提交: batchNo={}", batchNo);
            return;
        }
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
                    if (shouldMarkRecordFailed(result)) {
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

    private boolean claimBatchForTransfer(PaymentBatch batch, LocalDateTime processStartTime) {
        return paymentBatchService.update(new UpdateWrapper<PaymentBatch>()
                .eq("id", batch.getId())
                .in("status", BatchStatus.SUBMITTED.getCode(), BatchStatus.APPROVED.getCode())
                .set("status", BatchStatus.PROCESSING.getCode())
                .set("payment_status", PaymentBatchProcessStatus.PROCESSING.getCode())
                .set("process_start_time", processStartTime)
                .set("process_end_time", null));
    }

    private void markBatchTransferBlocked(PaymentBatch batch, String reason) {
        if (batch == null || !StringUtils.hasText(batch.getBatchNo())) {
            return;
        }

        List<PaymentRecord> records = paymentRecordService.getByBatchNo(batch.getBatchNo(), null);
        if (records != null) {
            for (PaymentRecord record : records) {
                if (record == null
                        || record.getId() == null
                        || (record.getStatus() != PaymentStatus.PENDING
                        && record.getStatus() != PaymentStatus.PROCESSING)
                        || hasProviderOrder(record)) {
                    continue;
                }
                markRecordFailed(record.getId(), record.getProviderCode(),
                        "PAYROLL_DISTRIBUTION_INVALID",
                        StringUtils.hasText(reason) ? reason : "薪资发放单校验失败，无法启动转账");
            }
        }
        settleBatchStatus(batch, batch.getStatus());
    }

    private PayrollDistributionTransferCheck validatePayrollDistributionForTransfer(PaymentBatch paymentBatch) {
        if (paymentBatch == null
                || paymentBatch.getPaymentType() != PaymentType.SALARY
                || paymentBatch.getDistributionId() == null) {
            return PayrollDistributionTransferCheck.allowed();
        }

        PayrollDistributionService payrollDistributionService = payrollDistributionServiceProvider.getIfAvailable();
        if (payrollDistributionService == null) {
            return PayrollDistributionTransferCheck.blocked("薪资发放服务不可用，禁止启动工资转账");
        }

        PayrollDistribution distribution = payrollDistributionService.getById(paymentBatch.getDistributionId());
        if (distribution == null || distribution.getBatchId() == null) {
            return PayrollDistributionTransferCheck.blocked("支付批次关联的薪资发放单不存在，禁止启动工资转账");
        }

        PayrollBatch payrollBatch = payrollBatchMapper.selectById(distribution.getBatchId());
        if (payrollBatch == null) {
            return PayrollDistributionTransferCheck.blocked("支付批次关联的薪资批次不存在，禁止启动工资转账");
        }
        if (!Objects.equals(normalizeRevision(distribution.getBatchRevision()),
                normalizeRevision(payrollBatch.getBatchRevision()))) {
            return PayrollDistributionTransferCheck.blocked("支付批次关联的薪资发放单已过期，禁止启动工资转账");
        }
        if (!Objects.equals(paymentBatch.getBatchNo(), payrollBatch.getPaymentBatchNo())) {
            return PayrollDistributionTransferCheck.blocked("支付批次不是当前薪资批次绑定的支付批次，禁止启动工资转账");
        }
        if (payrollBatch.getStatus() != PayrollBatchStatus.PAY_PROCESSING
                && payrollBatch.getStatus() != PayrollBatchStatus.PAY_FAILED) {
            return PayrollDistributionTransferCheck.blocked("薪资批次未进入支付处理状态，禁止启动工资转账");
        }
        return PayrollDistributionTransferCheck.allowed();
    }

    private Integer normalizeRevision(Integer revision) {
        return revision == null ? 1 : revision;
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
        if (callbackResult != null && callbackResult.isSuccess()) {
            refreshBatchStatusFromCallback(providerCode, callbackResult);
        }
        return callbackResult;
    }

    @Override
    public int reconcileProcessingBatches(int batchLimit, int recordLimitPerBatch) {
        int normalizedBatchLimit = normalizeReconcileLimit(
                batchLimit,
                DEFAULT_RECONCILE_BATCH_LIMIT,
                MAX_RECONCILE_BATCH_LIMIT
        );
        int normalizedRecordLimit = normalizeReconcileLimit(
                recordLimitPerBatch,
                DEFAULT_RECONCILE_RECORD_LIMIT,
                MAX_RECONCILE_RECORD_LIMIT
        );

        int scanned = 0;
        List<PaymentBatch> processingBatches = listProcessingBatches(normalizedBatchLimit);
        scanned += reconcileBatches(processingBatches, normalizedRecordLimit);

        int remainingBatchLimit = normalizedBatchLimit - scanned;
        if (remainingBatchLimit > 0) {
            List<PaymentBatch> recoverableTerminalBatches = listRecoverableTerminalBatches(remainingBatchLimit);
            scanned += reconcileBatches(recoverableTerminalBatches, normalizedRecordLimit);
        }
        return scanned;
    }

    private List<PaymentBatch> listProcessingBatches(int batchLimit) {
        return paymentBatchService.list(new LambdaQueryWrapper<PaymentBatch>()
                .eq(PaymentBatch::getStatus, BatchStatus.PROCESSING)
                .orderByAsc(PaymentBatch::getUpdateTime)
                .last("limit " + batchLimit));
    }

    private List<PaymentBatch> listRecoverableTerminalBatches(int batchLimit) {
        return paymentBatchService.list(new LambdaQueryWrapper<PaymentBatch>()
                .in(PaymentBatch::getStatus, BatchStatus.FAILED, BatchStatus.COMPLETED)
                .in(PaymentBatch::getPaymentStatus,
                        PaymentBatchProcessStatus.FAILED,
                        PaymentBatchProcessStatus.PARTIAL_SUCCESS)
                .exists("SELECT 1 FROM payment_record pr"
                        + " WHERE pr.batch_no = payment_batch.batch_no"
                        + " AND pr.status IN ('failed', 'cancelled')"
                        + " AND ("
                        + "   (pr.provider_order_no IS NOT NULL AND pr.provider_order_no <> '')"
                        + "   OR (pr.provider_trade_no IS NOT NULL AND pr.provider_trade_no <> '')"
                        + "   OR (pr.alipay_order_no IS NOT NULL AND pr.alipay_order_no <> '')"
                        + "   OR (pr.alipay_trade_no IS NOT NULL AND pr.alipay_trade_no <> '')"
                        + " )")
                .orderByDesc(PaymentBatch::getUpdateTime)
                .last("limit " + batchLimit));
    }

    private int reconcileBatches(List<PaymentBatch> batches, int recordLimitPerBatch) {
        if (batches == null || batches.isEmpty()) {
            return 0;
        }
        int scanned = 0;
        for (PaymentBatch batch : batches) {
            if (batch == null || !StringUtils.hasText(batch.getBatchNo())) {
                continue;
            }
            scanned++;
            try {
                reconcileSingleBatch(batch, recordLimitPerBatch);
            } catch (Exception ex) {
                log.warn("轮询对账失败: batchNo={}, msg={}", batch.getBatchNo(), ex.getMessage());
            }
        }
        return scanned;
    }

    private int normalizeReconcileLimit(int limit, int defaultLimit, int maxLimit) {
        if (limit < 1) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
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
                        .in(PaymentRecord::getStatus,
                                PaymentStatus.PENDING,
                                PaymentStatus.PROCESSING,
                                PaymentStatus.FAILED,
                                PaymentStatus.CANCELLED)
                        .orderByAsc(PaymentRecord::getId)
                        .last("limit " + recordLimitPerBatch)
        );

        for (PaymentRecord record : processingRecords) {
            if (record == null || record.getId() == null) {
                continue;
            }
            if (isUnsubmittedPendingRecord(record)) {
                resumeUnsubmittedPendingRecord(record);
                continue;
            }
            refreshRecordStatusFromProvider(record);
        }

        settleBatchStatus(batch, batch.getStatus());
    }

    private boolean isUnsubmittedPendingRecord(PaymentRecord record) {
        return record != null
                && record.getStatus() == PaymentStatus.PENDING
                && !StringUtils.hasText(record.getProviderOrderNo())
                && !StringUtils.hasText(record.getProviderTradeNo())
                && !StringUtils.hasText(record.getAlipayOrderNo())
                && !StringUtils.hasText(record.getAlipayTradeNo());
    }

    private void resumeUnsubmittedPendingRecord(PaymentRecord record) {
        log.warn("主动对账发现未提交渠道的待处理支付记录，尝试续提交: recordId={}, batchNo={}",
                record.getId(), record.getBatchNo());
        try {
            SettlementResult result = singleTransfer(record.getId());
            if (shouldMarkRecordFailed(result)) {
                markRecordFailedOnSubmitFailure(paymentRecordService.getById(record.getId()), result);
            }
        } catch (Exception ex) {
            markRecordFailedOnException(paymentRecordService.getById(record.getId()), ex);
        }
    }

    private void refreshRecordStatusFromProvider(PaymentRecord record) {
        String providerCode = resolveProviderCode(record);
        String providerOrderNo = resolveProviderOrderNo(record);
        if (!StringUtils.hasText(providerOrderNo)) {
            log.warn("跳过轮询: recordId={} 缺少渠道订单号", record.getId());
            return;
        }

        SettlementStatus settlementStatus = queryStatus(providerCode, providerOrderNo);
        refreshRecordStatusFromProvider(record, settlementStatus, providerCode, providerOrderNo);
    }

    private boolean refreshRecordStatusFromProvider(PaymentRecord record,
                                                    SettlementStatus settlementStatus,
                                                    String providerCode,
                                                    String providerOrderNo) {
        PaymentStatus targetStatus = mapSettlementStatus(settlementStatus);
        if (targetStatus == null || targetStatus == PaymentStatus.PENDING || targetStatus == PaymentStatus.PROCESSING) {
            return false;
        }
        if ((record.getStatus() == PaymentStatus.FAILED || record.getStatus() == PaymentStatus.CANCELLED)
                && targetStatus != PaymentStatus.SUCCESS) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<PaymentRecord> wrapper = new UpdateWrapper<PaymentRecord>()
                .eq("id", record.getId())
                .set("provider_code", providerCode)
                .set("provider_order_no", providerOrderNo)
                .set("status", targetStatus.getCode());
        if (targetStatus == PaymentStatus.SUCCESS) {
            wrapper.set("payment_time", now)
                    .set("error_code", null)
                    .set("error_msg", null);
        } else {
            wrapper.set("error_code",
                            StringUtils.hasText(record.getErrorCode()) ? record.getErrorCode() : "RECONCILED")
                    .set("error_msg",
                            StringUtils.hasText(record.getErrorMsg())
                                    ? record.getErrorMsg()
                                    : "settlement status reconciled by polling");
        }
        PaymentRecordStatusTransitions.applyAllowedStatusGuard(wrapper, targetStatus);
        boolean updated = paymentRecordService.update(wrapper);
        if (updated) {
            record.setProviderCode(providerCode);
            record.setProviderOrderNo(providerOrderNo);
            record.setStatus(targetStatus);
            if (targetStatus == PaymentStatus.SUCCESS) {
                record.setPaymentTime(now);
                record.setErrorCode(null);
                record.setErrorMsg(null);
            } else {
                record.setErrorCode(StringUtils.hasText(record.getErrorCode()) ? record.getErrorCode() : "RECONCILED");
                record.setErrorMsg(StringUtils.hasText(record.getErrorMsg())
                        ? record.getErrorMsg()
                        : "settlement status reconciled by polling");
            }
            safeNotifyTerminalPaymentRecord(record, targetStatus, "polling");
        }
        return updated;
    }

    private String resolveProviderOrderNo(PaymentRecord record) {
        if (record == null) {
            return null;
        }
        return StringUtils.hasText(record.getProviderOrderNo())
                ? record.getProviderOrderNo()
                : record.getAlipayOrderNo();
    }

    private boolean hasProviderTradeNo(PaymentRecord record) {
        return record != null
                && (StringUtils.hasText(record.getProviderTradeNo())
                || StringUtils.hasText(record.getAlipayTradeNo()));
    }

    private boolean hasProviderOrder(PaymentRecord record) {
        return record != null
                && (StringUtils.hasText(resolveProviderOrderNo(record))
                || hasProviderTradeNo(record));
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

        if (latest.getStatus() != null && latest.getStatus() != PaymentStatus.PENDING) {
            return buildValidationIssue(
                    latest,
                    employee,
                    StringUtils.hasText(latest.getErrorCode()) ? latest.getErrorCode() : "STATUS_INVALID",
                    StringUtils.hasText(latest.getErrorMsg())
                            ? latest.getErrorMsg()
                    : "记录状态不是待处理，禁止发起转账"
            );
        }

        if (hasProviderOrder(latest)) {
            String errorCode = "PROVIDER_ORDER_EXISTS";
            String errorMsg = "支付记录已有渠道订单或交易号，请先完成渠道对账";
            if (persistFailure) {
                markRecordFailed(latest.getId(), resolveProviderCode(latest), errorCode, errorMsg);
            }
            return buildValidationIssue(latest, employee, errorCode, errorMsg);
        }

        try {
            refreshRecipientRouteBeforeTransfer(latest);
        } catch (Exception ex) {
            log.warn("支付记录收款路由刷新失败: recordId={}, employeeId={}, message={}",
                    latest.getId(), latest.getEmployeeId(), ex.getMessage(), ex);
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

        if (!allowUnsupportedProviderPassThrough
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
        PaymentRecord notificationRecord = paymentRecordService.getById(recordId);
        boolean updated = paymentRecordService.update(wrapper);
        if (notificationRecord == null) {
            notificationRecord = new PaymentRecord();
            notificationRecord.setId(recordId);
        }
        notificationRecord.setProviderCode(normalizeProviderCode(providerCode));
        notifyFailedRecordAfterUpdate(notificationRecord, updated,
                safeErrorCode(errorCode, "TRANSFER_VALIDATION_FAILED"),
                safeErrorMessage(errorMsg, "transfer validation failed"),
                "validation");
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
                    .eq(PaymentRecord::getStatus, PaymentStatus.PENDING)
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
                .eq(PaymentRecord::getStatus, PaymentStatus.PENDING)
                .set(PaymentRecord::getRecipientAccount, routeResult.recipientAccount())
                .set(PaymentRecord::getPaymentMethod, routeResult.paymentMethod())
                .set(PaymentRecord::getProviderCode, routeResult.providerCode())
                .set(PaymentRecord::getRecipientName, recipientName)
                .set(PaymentRecord::getErrorCode, null)
                .set(PaymentRecord::getErrorMsg, null));
    }

    private RecipientRouteResult resolveRecipientRoute(PayrollBatch payrollBatch, Employee employee) {
        SettlementRecipientRouteResolver.RouteResult route = SettlementRecipientRouteResolver.resolve(
                payrollBatch, null, employee, encryptionService, routingService);
        return new RecipientRouteResult(
                route.accountType(),
                route.recipientAccount(),
                route.paymentMethod(),
                route.providerCode(),
                route.errorCode(),
                route.errorMsg()
        );
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
        if (record == null) {
            return;
        }
        notifyTerminalPaymentRecordFromCallback(normalizedProviderCode, callbackResult, record);
        if (!StringUtils.hasText(record.getBatchNo())) {
            return;
        }
        PaymentBatch batch = paymentBatchService.getByBatchNo(record.getBatchNo());
        if (batch == null) {
            return;
        }
        settleBatchStatus(batch, batch.getStatus());
    }

    private void settleBatchStatus(PaymentBatch batch, BatchStatus previousStatus) {
        if (batch == null || batch.getId() == null || !StringUtils.hasText(batch.getBatchNo())) {
            return;
        }
        BatchStatus expectedStatus = batch.getStatus();
        Integer expectedVersion = batch.getVersion();
        List<PaymentRecord> allRecords = paymentRecordService.getByBatchNo(batch.getBatchNo(), null);
        if (allRecords == null || allRecords.isEmpty()) {
            batch.setSuccessCount(0);
            batch.setFailedCount(0);
            batch.setStatus(BatchStatus.FAILED);
            batch.setPaymentStatus(PaymentBatchProcessStatus.FAILED);
            batch.setProcessEndTime(LocalDateTime.now());
            UpdateWrapper<PaymentBatch> updateWrapper = new UpdateWrapper<PaymentBatch>()
                    .eq("id", batch.getId())
                    .eq(expectedStatus != null, "status", expectedStatus == null ? null : expectedStatus.getCode())
                    .eq(expectedVersion != null, "version", expectedVersion)
                    .set("success_count", 0)
                    .set("failed_count", 0)
                    .set("status", BatchStatus.FAILED.getCode())
                    .set("payment_status", PaymentBatchProcessStatus.FAILED.getCode())
                    .set("process_end_time", batch.getProcessEndTime());
            if (expectedVersion != null) {
                updateWrapper.setSql("version = COALESCE(version, 0) + 1");
            }
            boolean updated = paymentBatchService.update(updateWrapper);
            if (!updated) {
                log.info("支付批次状态已被其他任务更新，跳过旧快照同步: batchNo={}", batch.getBatchNo());
                return;
            }
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

        if (isTerminalStatus(previousStatus) && !isTerminalStatus(targetStatus)) {
            log.warn("忽略支付批次终态回退: batchNo={}, previousStatus={}, targetStatus={}",
                    batch.getBatchNo(), previousStatus, targetStatus);
            if (batch.getDistributionId() != null) {
                syncDistributionStatus(batch);
            }
            return;
        }

        batch.setSuccessCount(successCount);
        batch.setFailedCount(failedCount);
        batch.setStatus(targetStatus);
        batch.setPaymentStatus(mapPaymentBatchProcessStatus(targetStatus, successCount > 0 && failedCount > 0));
        UpdateWrapper<PaymentBatch> updateWrapper = new UpdateWrapper<PaymentBatch>()
                .eq("id", batch.getId())
                .eq(expectedStatus != null, "status", expectedStatus == null ? null : expectedStatus.getCode())
                .eq(expectedVersion != null, "version", expectedVersion)
                .set("success_count", successCount)
                .set("failed_count", failedCount)
                .set("status", targetStatus.getCode())
                .set("payment_status", mapPaymentBatchProcessStatus(targetStatus, successCount > 0 && failedCount > 0).getCode())
                .set("process_end_time", batch.getProcessEndTime());
        if (expectedVersion != null) {
            updateWrapper.setSql("version = COALESCE(version, 0) + 1");
        }
        boolean updated = paymentBatchService.update(updateWrapper);
        if (!updated) {
            log.info("支付批次状态已被其他任务更新，跳过旧快照同步: batchNo={}, expectedStatus={}",
                    batch.getBatchNo(), expectedStatus);
            return;
        }

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

    private void notifyTerminalPaymentRecordFromResult(String providerCode, PaymentRecord record, SettlementResult result) {
        if (result == null || record == null) {
            return;
        }
        PaymentStatus targetStatus = mapSettlementStatus(result.getStatus());
        if (targetStatus == null || targetStatus != PaymentStatus.SUCCESS) {
            return;
        }
        record.setProviderCode(normalizeProviderCode(providerCode));
        if (StringUtils.hasText(result.getProviderOrderNo())) {
            record.setProviderOrderNo(result.getProviderOrderNo());
        }
        if (StringUtils.hasText(result.getProviderTradeNo())) {
            record.setProviderTradeNo(result.getProviderTradeNo());
        }
        record.setStatus(PaymentStatus.SUCCESS);
        record.setPaymentTime(LocalDateTime.now());
        record.setErrorCode(null);
        record.setErrorMsg(null);
        safeNotifyTerminalPaymentRecord(record, targetStatus, "submit");
    }

    private void notifyTerminalPaymentRecordFromCallback(String providerCode,
                                                         SettlementCallbackResult callbackResult,
                                                         PaymentRecord record) {
        if (callbackResult == null || record == null) {
            return;
        }
        if ("alipay".equals(normalizeProviderCode(providerCode))) {
            return;
        }
        PaymentStatus targetStatus = mapSettlementStatus(callbackResult.getStatus());
        if (targetStatus == null) {
            return;
        }
        record.setProviderCode(normalizeProviderCode(providerCode));
        record.setProviderOrderNo(callbackResult.getBizNo());
        if (targetStatus == PaymentStatus.SUCCESS) {
            record.setStatus(PaymentStatus.SUCCESS);
            if (record.getPaymentTime() == null) {
                record.setPaymentTime(LocalDateTime.now());
            }
            record.setErrorCode(null);
            record.setErrorMsg(null);
        } else if (targetStatus == PaymentStatus.FAILED || targetStatus == PaymentStatus.CANCELLED) {
            record.setStatus(targetStatus);
            record.setErrorCode(safeErrorCode(record.getErrorCode(), "SETTLEMENT_CALLBACK_FAILED"));
            record.setErrorMsg(safeErrorMessage(callbackResult.getErrorMsg(), "settlement callback failed"));
        }
        safeNotifyTerminalPaymentRecord(record, targetStatus, "callback");
    }

    private boolean claimNotification(Long recordId, PaymentStatus status) {
        if (recordId == null || status == null) {
            return false;
        }
        if (status != PaymentStatus.SUCCESS && status != PaymentStatus.FAILED && status != PaymentStatus.CANCELLED) {
            return false;
        }
        return paymentRecordService.update(new UpdateWrapper<PaymentRecord>()
                .eq("id", recordId)
                .eq("status", status.getCode())
                .isNull("notification_time")
                .set("notification_time", LocalDateTime.now()));
    }

    private void safeNotifyTerminalPaymentRecord(PaymentRecord record, PaymentStatus status, String source) {
        if (record == null || status == null) {
            return;
        }
        if (status != PaymentStatus.SUCCESS && status != PaymentStatus.FAILED && status != PaymentStatus.CANCELLED) {
            return;
        }
        if (!claimNotification(record.getId(), status)) {
            return;
        }
        try {
            if (status == PaymentStatus.SUCCESS) {
                notificationService.sendPaymentSuccessNotification(record);
            } else {
                notificationService.sendPaymentFailedNotification(record);
            }
        } catch (Exception ex) {
            log.warn("支付结果通知发送失败: source={}, recordId={}, status={}, msg={}",
                    source, record.getId(), status, ex.getMessage());
        }
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
                .in("status", PayrollBatchStatus.PAY_PROCESSING.getCode(), PayrollBatchStatus.PAY_FAILED.getCode())
                .set("payment_status", mapPaymentBatchProcessStatus(status, partialSuccess).getCode())
                .set("status", status == BatchStatus.COMPLETED && !partialSuccess ? "paid" : "pay_failed");
            PayrollBatchStatus targetStatus = partialSuccess
                    ? PayrollBatchStatus.PAY_FAILED
                    : PayrollBatchStatus.PAID;
            if (targetStatus == PayrollBatchStatus.PAID && payrollSettlementIntegrityService != null) {
                payrollSettlementIntegrityService.finalizeByPaymentBatchNo(
                        paymentBatch.getBatchNo(),
                        targetStatus,
                        PaymentBatchProcessStatus.SUCCESS.getCode());
            } else {
                payrollBatchMapper.update(null, wrapper);
            }
            PayrollPaymentFailureService failureService = payrollPaymentFailureServiceProvider.getIfAvailable();
        if (failureService != null) {
            if (status == BatchStatus.COMPLETED && !partialSuccess) {
                failureService.markResolvedByPaymentBatchNo(paymentBatch.getBatchNo());
            } else {
                failureService.markUnresolvedByPaymentBatchNo(
                        paymentBatch.getBatchNo(),
                        partialSuccess ? "支付批次部分成功，仍有失败记录" : "支付批次支付失败"
                );
            }
        }
    }

    private void syncPayrollBatchProcessing(PaymentBatch paymentBatch) {
        if (paymentBatch == null || !StringUtils.hasText(paymentBatch.getBatchNo())) {
            return;
        }
        payrollBatchMapper.update(null, new UpdateWrapper<PayrollBatch>()
                .eq("payment_batch_no", paymentBatch.getBatchNo())
                .in("status", PayrollBatchStatus.PAY_FAILED.getCode(), PayrollBatchStatus.PAY_PROCESSING.getCode())
                .set("status", PayrollBatchStatus.PAY_PROCESSING.getCode())
                .set("payment_status", PaymentBatchProcessStatus.PROCESSING.getCode()));
    }

    private boolean persistProviderResult(PaymentRecord record, String providerCode, SettlementResult result) {
        if (record == null || record.getId() == null || result == null) {
            return false;
        }
        PaymentStatus paymentStatus = mapSettlementStatus(result.getStatus());
        UpdateWrapper<PaymentRecord> wrapper = new UpdateWrapper<PaymentRecord>()
                .eq("id", record.getId())
                .set("provider_code", providerCode);
        if (StringUtils.hasText(result.getProviderOrderNo())) {
            wrapper.set("provider_order_no", result.getProviderOrderNo());
        }
        if (StringUtils.hasText(result.getProviderTradeNo())) {
            wrapper.set("provider_trade_no", result.getProviderTradeNo());
        }
        if (paymentStatus != null && paymentStatus != PaymentStatus.PENDING) {
            wrapper.set("status", paymentStatus.getCode());
            if (paymentStatus == PaymentStatus.SUCCESS) {
                wrapper.set("payment_time", LocalDateTime.now())
                        .set("error_code", null)
                        .set("error_msg", null);
            } else if (paymentStatus == PaymentStatus.FAILED || paymentStatus == PaymentStatus.CANCELLED) {
                wrapper.set("error_code", safeErrorCode(result.getErrorCode(), "SETTLEMENT_FAILED"))
                        .set("error_msg", safeErrorMessage(result.getErrorMsg(), "settlement failed"));
            }
            PaymentRecordStatusTransitions.applyAllowedStatusGuard(wrapper, paymentStatus);
        }
        return paymentRecordService.update(wrapper);
    }

    private boolean shouldMarkRecordFailed(SettlementResult result) {
        if (result == null) {
            return true;
        }
        PaymentStatus status = mapSettlementStatus(result.getStatus());
        return status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED;
    }

    private void markRecordFailedOnSubmitFailure(PaymentRecord record, SettlementResult result) {
        if (record == null || record.getId() == null) {
            return;
        }
        String errorCode = safeErrorCode(result == null ? null : result.getErrorCode(), "SETTLEMENT_SUBMIT_FAILED");
        String errorMsg = safeErrorMessage(result == null ? null : result.getErrorMsg(), "settlement submit failed");
        boolean updated = paymentRecordService.update(new UpdateWrapper<PaymentRecord>()
                .eq("id", record.getId())
                .in("status", PaymentStatus.PENDING.getCode(), PaymentStatus.PROCESSING.getCode())
                .set("status", PaymentStatus.FAILED.getCode())
                .set("error_code", errorCode)
                .set("error_msg", errorMsg));
        notifyFailedRecordAfterUpdate(record, updated, errorCode, errorMsg, "submit");
    }

    private void markRecordFailedOnException(PaymentRecord record, Exception ex) {
        if (record == null || record.getId() == null) {
            return;
        }
        String errorMsg = safeErrorMessage(ex == null ? null : ex.getMessage(), "batch transfer exception");
        boolean updated = paymentRecordService.update(new UpdateWrapper<PaymentRecord>()
                .eq("id", record.getId())
                .in("status", PaymentStatus.PENDING.getCode(), PaymentStatus.PROCESSING.getCode())
                .set("status", PaymentStatus.FAILED.getCode())
                .set("error_code", "BATCH_TRANSFER_EXCEPTION")
                .set("error_msg", errorMsg));
        notifyFailedRecordAfterUpdate(record, updated, "BATCH_TRANSFER_EXCEPTION", errorMsg, "exception");
    }

    private void notifyFailedRecordAfterUpdate(PaymentRecord record,
                                               boolean updated,
                                               String errorCode,
                                               String errorMsg,
                                               String source) {
        if (record == null || record.getId() == null) {
            return;
        }
        PaymentRecord notificationRecord = record;
        PaymentStatus notificationStatus = PaymentStatus.FAILED;
        if (!updated) {
            PaymentRecord latest = paymentRecordService.getById(record.getId());
            if (latest == null
                    || (latest.getStatus() != PaymentStatus.FAILED && latest.getStatus() != PaymentStatus.CANCELLED)) {
                return;
            }
            notificationRecord = latest;
            notificationStatus = latest.getStatus();
        } else {
            notificationRecord.setStatus(PaymentStatus.FAILED);
        }
        if (!StringUtils.hasText(notificationRecord.getErrorCode())) {
            notificationRecord.setErrorCode(errorCode);
        }
        if (!StringUtils.hasText(notificationRecord.getErrorMsg())) {
            notificationRecord.setErrorMsg(errorMsg);
        }
        safeNotifyTerminalPaymentRecord(notificationRecord, notificationStatus, source);
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
        private boolean supported() {
            return StringUtils.hasText(recipientAccount) && !StringUtils.hasText(errorCode);
        }
    }

    private record PayrollDistributionTransferCheck(boolean pass, String message) {

        private static PayrollDistributionTransferCheck allowed() {
            return new PayrollDistributionTransferCheck(true, null);
        }

        private static PayrollDistributionTransferCheck blocked(String message) {
            return new PayrollDistributionTransferCheck(false, message);
        }
    }
}
