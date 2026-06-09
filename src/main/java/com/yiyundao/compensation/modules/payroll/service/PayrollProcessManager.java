package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.util.TransactionAfterCommitExecutor;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.utils.SettlementAccountPlaintextGuard;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.PaymentType;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollDistributionItemStatus;
import com.yiyundao.compensation.enums.PayrollDistributionStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollDistributionItemMapper;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollApprovalProjection;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollConfirmation;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistributionItem;
import com.yiyundao.compensation.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollProcessManager {

    private static final DateTimeFormatter BATCH_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PayrollBatchMapper payrollBatchMapper;
    private final PayrollDistributionItemMapper distributionItemMapper;
    private final PayrollCalculationService payrollCalculationService;
    private final PayrollConfirmationAggregateService confirmationAggregateService;
    private final PayrollDistributionService distributionService;
    private final PayrollApprovalProjectionService approvalProjectionService;
    private final PayrollReconciliationTaskService reconciliationTaskService;
    private final PaymentBatchService paymentBatchService;
    private final PaymentRecordService paymentRecordService;
    private final SettlementService settlementService;
    private final TransactionAfterCommitExecutor afterCommitExecutor;
    private final EncryptionService encryptionService;
    private final TransactionOperations transactionOperations;

    @Transactional
    public boolean computeAndInitialize(Long batchId) {
        boolean computed = payrollCalculationService.computeAndSave(batchId);
        if (!computed) {
            return false;
        }
        PayrollBatch batch = payrollBatchMapper.selectById(batchId);
        if (batch == null) {
            return false;
        }
        distributionService.supersedeObsolete(batch.getId(), normalizeRevision(batch.getBatchRevision()));
        PayrollConfirmation confirmation = confirmationAggregateService.createOrRefreshForBatch(batch);
        if (confirmation != null && (Boolean.FALSE.equals(batch.getConfirmationRequired())
                || confirmation.getConfirmationStatus() == com.yiyundao.compensation.enums.PayrollConfirmationSheetStatus.SKIPPED)) {
            markSkippedConfirmationBatchConfirmed(batch);
            onConfirmationCompleted(batch.getId(), batch.getBatchRevision());
        }
        return true;
    }

    @Transactional
    public PayrollDistribution onConfirmationCompleted(Long batchId, Integer batchRevision) {
        PayrollBatch batch = payrollBatchMapper.selectById(batchId);
        if (batch == null) {
            return null;
        }
        if (batchRevision != null && batch.getBatchRevision() != null && !batchRevision.equals(batch.getBatchRevision())) {
            log.info("忽略过期确认完成编排: batchId={}, activeRevision={}, callbackRevision={}",
                    batchId, batch.getBatchRevision(), batchRevision);
            return null;
        }
        PayrollConfirmation confirmation = confirmationAggregateService.getByBatchIdAndRevision(batchId, normalizeRevision(batch.getBatchRevision()));
        if (Boolean.TRUE.equals(batch.getConfirmationRequired())) {
            if (confirmation == null || !confirmationAggregateService.isCompletedForApproval(batchId, normalizeRevision(batch.getBatchRevision()))) {
                return null;
            }
        }
        return distributionService.createOrReuseForBatch(batch);
    }

    @Transactional
    public void onApprovalApproved(Long distributionId, Long workflowId, Long approverId) {
        PayrollDistribution distribution = distributionService.getById(distributionId);
        if (distribution == null) {
            return;
        }
        if (workflowId != null) {
            approvalProjectionService.markApproved(workflowId, approverId);
        }
        if (!isCurrentApprovalWorkflow(distribution, workflowId)) {
            return;
        }
        PayrollBatch batch = payrollBatchMapper.selectById(distribution.getBatchId());
        if (!isActiveDistributionRevision(distribution, batch)) {
            return;
        }
        if (distribution.getDistributionStatus() != PayrollDistributionStatus.PLANNED) {
            log.info("忽略已流转发放单的重复审批通过事件: distributionId={}, status={}",
                    distributionId, distribution.getDistributionStatus());
            return;
        }
        markBatchApproved(batch.getId(), distribution.getBatchRevision(), workflowId);
        if (distribution.getScheduledDate() != null && distribution.getScheduledDate().isAfter(LocalDate.now())) {
            distribution.setDistributionStatus(PayrollDistributionStatus.PLANNED);
            distributionService.updateById(distribution);
            return;
        }
        scheduleImmediateDistributionSubmission(distributionId);
    }

    @Transactional
    public void onApprovalRejected(Long distributionId, Long workflowId, Long approverId, String result) {
        if (workflowId != null) {
            approvalProjectionService.markRejected(workflowId, approverId, result);
        }
        PayrollDistribution distribution = distributionService.getById(distributionId);
        if (distribution != null) {
            if (!isCurrentApprovalWorkflow(distribution, workflowId)) {
                return;
            }
            cancelPendingDistribution(distribution);
            PayrollBatch batch = payrollBatchMapper.selectById(distribution.getBatchId());
            if (isActiveDistributionRevision(distribution, batch)) {
                rejectPendingBatch(batch.getId(), distribution.getBatchRevision());
            }
        }
    }

    @Transactional
    public void onApprovalCancelled(Long distributionId, Long workflowId, Long approverId, String result) {
        if (workflowId != null) {
            approvalProjectionService.markCancelled(workflowId, approverId, result);
        }
        PayrollDistribution distribution = distributionService.getById(distributionId);
        if (distribution != null) {
            if (!isCurrentApprovalWorkflow(distribution, workflowId)) {
                return;
            }
            cancelPendingDistribution(distribution);
            PayrollBatch batch = payrollBatchMapper.selectById(distribution.getBatchId());
            if (isActiveDistributionRevision(distribution, batch)) {
                restoreSubmittedBatchToConfirmed(batch.getId(), distribution.getBatchRevision());
            }
        }
    }

    @Transactional
    public void submitDistribution(Long distributionId) {
        PaymentBatch paymentBatch = prepareDistributionSubmission(distributionId);
        if (paymentBatch != null && StringUtils.hasText(paymentBatch.getBatchNo())) {
            afterCommitExecutor.execute(() -> settlementService.batchTransfer(paymentBatch.getBatchNo()));
        }
    }

    protected PaymentBatch prepareDistributionSubmission(Long distributionId) {
        PayrollDistribution distribution = distributionService.getById(distributionId);
        if (distribution == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "发放单不存在");
        }
        if (!canSubmitDistribution(distribution.getDistributionStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前发放单状态不可提交: " + distribution.getDistributionStatus());
        }

        List<PayrollDistributionItem> activeItems = distributionService.listActiveItems(distributionId);
        if (activeItems.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "发放单无可处理明细");
        }

        int nextAttempt = distribution.getCurrentAttempt() == null ? 1 : distribution.getCurrentAttempt() + 1;
        List<PayrollDistributionItem> candidateItems = selectCandidateItems(distribution, activeItems, nextAttempt);
        if (candidateItems.isEmpty()) {
            distribution.setDistributionStatus(PayrollDistributionStatus.FAILED);
            distributionService.updateById(distribution);
            reconciliationTaskService.createOrRefresh(distribution);
            return null;
        }
        ensureRetryCandidatesHaveNoSubmittedProviderOrder(candidateItems, nextAttempt);

        PayrollBatch batch = payrollBatchMapper.selectById(distribution.getBatchId());
        ensureDistributionMatchesActiveApprovedBatch(distribution, batch);
        if (!canReplacePaymentBatchNo(batch)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前支付批次状态不可创建新的发放尝试");
        }
        Map<Long, String> recipientAccountsByItemId = decryptCandidateAccounts(candidateItems);
        String batchNo = buildPaymentBatchNo(distribution, nextAttempt);

        if (!reserveDistributionForSubmission(distribution, batch, batchNo)) {
            return resolveConcurrentDistributionPaymentBatch(distribution);
        }

        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo(batchNo);
        paymentBatch.setBatchName(buildPaymentBatchName(batch));
        paymentBatch.setPaymentType(PaymentType.SALARY);
        paymentBatch.setDistributionId(distribution.getId());
        paymentBatch.setTotalCount(candidateItems.size());
        paymentBatch.setTotalAmount(candidateItems.stream()
                .map(PayrollDistributionItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        paymentBatch.setSuccessCount(0);
        paymentBatch.setFailedCount(0);
        paymentBatch.setStatus(BatchStatus.SUBMITTED);
        paymentBatch.setPaymentStatus(PaymentBatchProcessStatus.SUBMITTED);
        paymentBatch.setSubmitTime(LocalDateTime.now());
        paymentBatch.setApproveTime(LocalDateTime.now());
        paymentBatch.setRemark("Auto-generated from payroll distribution " + distribution.getId());
        paymentBatchService.save(paymentBatch);

        for (PayrollDistributionItem item : candidateItems) {
            PaymentRecord record = new PaymentRecord();
            record.setBatchNo(batchNo);
            record.setEmployeeId(item.getEmployeeId());
            record.setPaymentType(PaymentType.SALARY);
            record.setAmount(item.getAmount());
            record.setCurrency(batch != null && StringUtils.hasText(batch.getCurrency()) ? batch.getCurrency() : "CNY");
            record.setPaymentMethod(item.getPaymentMethod());
            record.setRecipientAccount(recipientAccountsByItemId.get(item.getId()));
            record.setRecipientName(item.getRecipientName());
            record.setPaymentDesc(buildPaymentDesc(batch));
            record.setProviderCode(item.getProviderCode());
            record.setStatus(PaymentStatus.PENDING);
            paymentRecordService.save(record);

            item.setPaymentRecordId(record.getId());
            item.setRetryCount(nextAttempt - 1);
            item.setItemStatus(nextAttempt > 1 ? PayrollDistributionItemStatus.RETRYING : PayrollDistributionItemStatus.PENDING);
            item.setFailureReason(null);
            distributionItemMapper.updateById(item);
        }

        distribution.setCurrentAttempt(nextAttempt);
        distribution.setDistributionStatus(PayrollDistributionStatus.PROCESSING);
        distributionService.updateById(distribution);

        return paymentBatch;
    }

    private boolean reserveDistributionForSubmission(PayrollDistribution distribution, PayrollBatch batch, String batchNo) {
        boolean distributionReserved = distributionService.update(new UpdateWrapper<PayrollDistribution>()
                .eq("id", distribution.getId())
                .in("distribution_status",
                        PayrollDistributionStatus.PLANNED.getCode(),
                        PayrollDistributionStatus.FAILED.getCode(),
                        PayrollDistributionStatus.PARTIALLY_COMPLETED.getCode())
                .set("distribution_status", PayrollDistributionStatus.SUBMITTING.getCode()));
        if (!distributionReserved) {
            return false;
        }

        if (!canReplacePaymentBatchNo(batch)) {
            rollbackDistributionReservation(distribution);
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前支付批次状态不可创建新的发放尝试");
        }

        UpdateWrapper<PayrollBatch> batchUpdate = new UpdateWrapper<PayrollBatch>()
                .eq("id", distribution.getBatchId())
                .eq("batch_revision", normalizeRevision(batch.getBatchRevision()));
        if (batch != null && StringUtils.hasText(batch.getPaymentBatchNo())) {
            batchUpdate.eq("payment_batch_no", batch.getPaymentBatchNo());
        } else {
            batchUpdate.and(w -> w.isNull("payment_batch_no")
                    .or()
                    .eq("payment_batch_no", ""));
        }
        batchUpdate.set("payment_batch_no", batchNo)
                .set("status", PayrollBatchStatus.PAY_PROCESSING.getCode());

        int updatedBatch = payrollBatchMapper.update(null, batchUpdate);
        if (updatedBatch > 0) {
            return true;
        }

        rollbackDistributionReservation(distribution);
        return false;
    }

    private void rollbackDistributionReservation(PayrollDistribution distribution) {
        if (distribution == null || distribution.getId() == null || distribution.getDistributionStatus() == null) {
            return;
        }
        distributionService.update(new UpdateWrapper<PayrollDistribution>()
                .eq("id", distribution.getId())
                .eq("distribution_status", PayrollDistributionStatus.SUBMITTING.getCode())
                .set("distribution_status", distribution.getDistributionStatus().getCode()));
    }

    private boolean canReplacePaymentBatchNo(PayrollBatch batch) {
        if (batch == null || !StringUtils.hasText(batch.getPaymentBatchNo())) {
            return true;
        }

        PaymentBatch existing = paymentBatchService.getByBatchNo(batch.getPaymentBatchNo());
        if (existing == null) {
            log.warn("薪资批次关联的支付批次不存在，允许重建发放尝试: batchId={}, paymentBatchNo={}",
                    batch.getId(), batch.getPaymentBatchNo());
            return true;
        }
        if (existing.getStatus() == BatchStatus.FAILED) {
            return true;
        }
        return existing.getStatus() == BatchStatus.COMPLETED
                && (existing.getPaymentStatus() == PaymentBatchProcessStatus.PARTIAL_SUCCESS
                || (existing.getFailedCount() != null && existing.getFailedCount() > 0));
    }

    private PaymentBatch resolveConcurrentDistributionPaymentBatch(PayrollDistribution distribution) {
        PayrollBatch latestBatch = payrollBatchMapper.selectById(distribution.getBatchId());
        if (latestBatch != null && StringUtils.hasText(latestBatch.getPaymentBatchNo())) {
            PaymentBatch existing = paymentBatchService.getByBatchNo(latestBatch.getPaymentBatchNo());
            if (existing != null) {
                log.info("Payroll distribution {} was linked to payment batch {} by another transaction",
                        distribution.getId(), latestBatch.getPaymentBatchNo());
                return existing;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_STATUS, "发放单正在提交中，请稍后重试");
    }

    @Scheduled(
            fixedDelayString = "${payroll.distribution.fixed-delay-ms:300000}",
            initialDelayString = "${payroll.distribution.initial-delay-ms:120000}"
    )
    public void submitDueDistributions() {
        List<PayrollDistribution> dueDistributions = distributionService.list(new LambdaQueryWrapper<PayrollDistribution>()
                .eq(PayrollDistribution::getDistributionStatus, PayrollDistributionStatus.PLANNED)
                .le(PayrollDistribution::getScheduledDate, LocalDate.now())
                .orderByAsc(PayrollDistribution::getUpdateTime)
                .last("limit 20"));
        for (PayrollDistribution distribution : dueDistributions) {
            try {
                PayrollApprovalProjection projection = approvalProjectionService.getByDistributionId(distribution.getId());
                if (projection == null || !"APPROVED".equalsIgnoreCase(projection.getBusinessStatus())) {
                    continue;
                }
                submitDistributionInTransaction(distribution.getId());
            } catch (Exception ex) {
                log.error("自动提交发放失败: distributionId={}", distribution.getId(), ex);
            }
        }
    }

    private void submitDistributionInTransaction(Long distributionId) {
        transactionOperations.execute(status -> {
            submitDistribution(distributionId);
            return null;
        });
    }

    private void scheduleImmediateDistributionSubmission(Long distributionId) {
        afterCommitExecutor.execute(() -> {
            try {
                submitDistributionInTransaction(distributionId);
            } catch (Exception ex) {
                log.error("审批通过后提交发放失败，等待计划任务或人工重试: distributionId={}", distributionId, ex);
            }
        });
    }

    private List<PayrollDistributionItem> selectCandidateItems(PayrollDistribution distribution,
                                                               List<PayrollDistributionItem> items,
                                                               int nextAttempt) {
        boolean retryMode = nextAttempt > 1;
        List<PayrollDistributionItem> candidates = new ArrayList<>();
        for (PayrollDistributionItem item : items) {
            if (item.getItemStatus() == PayrollDistributionItemStatus.SUCCESS) {
                continue;
            }
            if (retryMode) {
                if (item.getItemStatus() == PayrollDistributionItemStatus.FAILED
                        && (item.getRetryCount() == null || item.getRetryCount() < safeRetryLimit(distribution))) {
                    candidates.add(item);
                }
                continue;
            }
            if (item.getItemStatus() == PayrollDistributionItemStatus.PENDING
                    || item.getItemStatus() == PayrollDistributionItemStatus.RETRYING) {
                candidates.add(item);
            }
        }
        return candidates;
    }

    private void ensureRetryCandidatesHaveNoSubmittedProviderOrder(List<PayrollDistributionItem> candidateItems,
                                                                    int nextAttempt) {
        if (nextAttempt <= 1 || candidateItems == null || candidateItems.isEmpty()) {
            return;
        }
        for (PayrollDistributionItem item : candidateItems) {
            if (item == null || item.getPaymentRecordId() == null) {
                continue;
            }
            PaymentRecord previousRecord = paymentRecordService.getById(item.getPaymentRecordId());
            if (hasProviderOrder(previousRecord)) {
                throw new BusinessException(
                        ErrorCode.INVALID_STATUS,
                        "存在已提交渠道的失败记录，请等待对账确认后再重试，避免重复打款"
                );
            }
        }
    }

    private boolean hasProviderOrder(PaymentRecord record) {
        return record != null
                && (StringUtils.hasText(record.getProviderOrderNo())
                || StringUtils.hasText(record.getProviderTradeNo())
                || StringUtils.hasText(record.getAlipayOrderNo())
                || StringUtils.hasText(record.getAlipayTradeNo()));
    }

    private boolean canSubmitDistribution(PayrollDistributionStatus status) {
        return status == PayrollDistributionStatus.PLANNED
                || status == PayrollDistributionStatus.FAILED
                || status == PayrollDistributionStatus.PARTIALLY_COMPLETED;
    }

    private void ensureDistributionMatchesActiveApprovedBatch(PayrollDistribution distribution, PayrollBatch batch) {
        if (batch == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "薪资批次不存在");
        }
        if (!normalizeRevision(distribution.getBatchRevision()).equals(normalizeRevision(batch.getBatchRevision()))) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "发放单已过期，请重新完成确认并提交审批");
        }
        if (batch.getStatus() != PayrollBatchStatus.APPROVED && batch.getStatus() != PayrollBatchStatus.PAY_FAILED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前薪资批次状态不可发放: " + batch.getStatus());
        }
    }

    private boolean isActiveDistributionRevision(PayrollDistribution distribution, PayrollBatch batch) {
        if (distribution == null || batch == null) {
            return false;
        }
        boolean active = normalizeRevision(distribution.getBatchRevision()).equals(normalizeRevision(batch.getBatchRevision()));
        if (!active) {
            log.info("忽略过期发放单审批回调: distributionId={}, distributionRevision={}, activeRevision={}",
                    distribution.getId(), distribution.getBatchRevision(), batch.getBatchRevision());
        }
        return active;
    }

    private boolean isCurrentApprovalWorkflow(PayrollDistribution distribution, Long workflowId) {
        if (distribution == null || workflowId == null || distribution.getApprovalWorkflowId() == null) {
            return true;
        }
        boolean current = workflowId.equals(distribution.getApprovalWorkflowId());
        if (!current) {
            log.info("忽略过期审批回调: distributionId={}, callbackWorkflowId={}, currentWorkflowId={}",
                    distribution.getId(), workflowId, distribution.getApprovalWorkflowId());
        }
        return current;
    }

    private void markBatchApproved(Long batchId, Integer batchRevision, Long workflowId) {
        UpdateWrapper<PayrollBatch> wrapper = new UpdateWrapper<PayrollBatch>()
                .eq("id", batchId)
                .eq("batch_revision", normalizeRevision(batchRevision))
                .in("status", PayrollBatchStatus.SUBMITTED.getCode(), PayrollBatchStatus.APPROVED.getCode())
                .set("status", PayrollBatchStatus.APPROVED.getCode());
        if (workflowId != null && workflowId > 0) {
            wrapper.set("approval_workflow_id", workflowId);
        }
        payrollBatchMapper.update(null, wrapper);
    }

    private void rejectPendingBatch(Long batchId, Integer batchRevision) {
        payrollBatchMapper.update(null, new UpdateWrapper<PayrollBatch>()
                .eq("id", batchId)
                .eq("batch_revision", normalizeRevision(batchRevision))
                .eq("status", PayrollBatchStatus.SUBMITTED.getCode())
                .set("status", PayrollBatchStatus.REJECTED.getCode()));
    }

    private void restoreSubmittedBatchToConfirmed(Long batchId, Integer batchRevision) {
        payrollBatchMapper.update(null, new UpdateWrapper<PayrollBatch>()
                .eq("id", batchId)
                .eq("batch_revision", normalizeRevision(batchRevision))
                .eq("status", PayrollBatchStatus.SUBMITTED.getCode())
                .set("status", PayrollBatchStatus.CONFIRMED.getCode()));
    }

    private void cancelPendingDistribution(PayrollDistribution distribution) {
        distributionService.update(new UpdateWrapper<PayrollDistribution>()
                .eq("id", distribution.getId())
                .eq("distribution_status", PayrollDistributionStatus.PLANNED.getCode())
                .set("distribution_status", PayrollDistributionStatus.CANCELLED.getCode()));
    }

    private void markSkippedConfirmationBatchConfirmed(PayrollBatch batch) {
        if (batch == null || batch.getId() == null || !canMarkConfirmationSkipped(batch.getStatus())) {
            return;
        }
        batch.setStatus(PayrollBatchStatus.CONFIRMED);
        if (batch.getConfirmationCompletedTime() == null) {
            batch.setConfirmationCompletedTime(LocalDateTime.now());
        }
        payrollBatchMapper.updateById(batch);
    }

    private boolean canMarkConfirmationSkipped(PayrollBatchStatus status) {
        return status == PayrollBatchStatus.LOCKED
                || status == PayrollBatchStatus.CONFIRMING
                || status == PayrollBatchStatus.DISPUTE_PROCESSING
                || status == PayrollBatchStatus.CONFIRMED;
    }

    private int safeRetryLimit(PayrollDistribution distribution) {
        return distribution.getRetryLimit() == null || distribution.getRetryLimit() < 1 ? 3 : distribution.getRetryLimit();
    }

    private Integer normalizeRevision(Integer revision) {
        return revision == null || revision < 1 ? 1 : revision;
    }

    private String buildPaymentBatchNo(PayrollDistribution distribution, int attempt) {
        return "PDS-" + distribution.getId() + "-A" + attempt + "-" + BATCH_NO_FORMATTER.format(LocalDateTime.now());
    }

    private String buildPaymentBatchName(PayrollBatch batch) {
        String period = batch != null && StringUtils.hasText(batch.getPeriodLabel()) ? batch.getPeriodLabel() : "周期";
        return "薪资发放-" + period;
    }

    private String buildPaymentDesc(PayrollBatch batch) {
        String period = batch != null && StringUtils.hasText(batch.getPeriodLabel()) ? batch.getPeriodLabel() : "当期";
        return "薪资发放-" + period;
    }

    private Map<Long, String> decryptCandidateAccounts(List<PayrollDistributionItem> candidateItems) {
        Map<Long, String> result = new HashMap<>();
        for (PayrollDistributionItem item : candidateItems) {
            if (item == null || item.getId() == null) {
                throw new BusinessException(ErrorCode.INVALID_STATUS, "发放明细数据不完整，请重新生成发放单");
            }
            result.put(item.getId(), decryptDistributionAccount(item));
        }
        return result;
    }

    private String decryptDistributionAccount(PayrollDistributionItem item) {
        if (item == null || !StringUtils.hasText(item.getAccountNoEncrypted())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "发放快照缺少收款账号，请重新生成发放单");
        }
        try {
            String plainAccount = encryptionService.decrypt(item.getAccountNoEncrypted());
            if (StringUtils.hasText(plainAccount)) {
                return plainAccount;
            }
            throw new IllegalStateException("账号解密结果为空");
        } catch (Exception ex) {
            if (SettlementAccountPlaintextGuard.isRecognizedPlainAccount(item.getAccountNoEncrypted())) {
                log.warn("解密发放快照账户失败，按历史明文账号兼容处理: distributionItemId={}, msg={}",
                        item.getId(), ex.getMessage());
                return item.getAccountNoEncrypted().trim();
            }
            log.warn("解密发放快照账户失败，且原始值不像合法明文账号: distributionItemId={}, msg={}",
                    item.getId(), ex.getMessage());
            throw new BusinessException(ErrorCode.INVALID_STATUS, "发放快照收款账号解密失败，请重新生成发放单");
        }
    }
}
