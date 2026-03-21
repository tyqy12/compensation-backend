package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
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
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
    private final EncryptionService encryptionService;

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
        PayrollConfirmation confirmation = confirmationAggregateService.createOrRefreshForBatch(batch);
        if (confirmation != null && (Boolean.FALSE.equals(batch.getConfirmationRequired())
                || confirmation.getConfirmationStatus() == com.yiyundao.compensation.enums.PayrollConfirmationSheetStatus.SKIPPED)) {
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
        PayrollBatch batch = payrollBatchMapper.selectById(distribution.getBatchId());
        if (batch != null) {
            batch.setStatus(PayrollBatchStatus.APPROVED);
            if (workflowId != null && workflowId > 0) {
                batch.setApprovalWorkflowId(workflowId);
            }
            payrollBatchMapper.updateById(batch);
        }
        if (distribution.getScheduledDate() != null && distribution.getScheduledDate().isAfter(LocalDate.now())) {
            distribution.setDistributionStatus(PayrollDistributionStatus.PLANNED);
            distributionService.updateById(distribution);
            return;
        }
        submitDistribution(distributionId);
    }

    @Transactional
    public void onApprovalRejected(Long distributionId, Long workflowId, Long approverId, String result) {
        if (workflowId != null) {
            approvalProjectionService.markRejected(workflowId, approverId, result);
        }
        PayrollDistribution distribution = distributionService.getById(distributionId);
        if (distribution != null) {
            distribution.setDistributionStatus(PayrollDistributionStatus.CANCELLED);
            distributionService.updateById(distribution);
            PayrollBatch batch = payrollBatchMapper.selectById(distribution.getBatchId());
            if (batch != null) {
                batch.setStatus(PayrollBatchStatus.REJECTED);
                payrollBatchMapper.updateById(batch);
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
            distribution.setDistributionStatus(PayrollDistributionStatus.CANCELLED);
            distributionService.updateById(distribution);
        }
    }

    public void submitDistribution(Long distributionId) {
        PaymentBatch paymentBatch = prepareDistributionSubmission(distributionId);
        if (paymentBatch != null && StringUtils.hasText(paymentBatch.getBatchNo())) {
            settlementService.batchTransfer(paymentBatch.getBatchNo());
        }
    }

    @Transactional
    protected PaymentBatch prepareDistributionSubmission(Long distributionId) {
        PayrollDistribution distribution = distributionService.getById(distributionId);
        if (distribution == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "发放单不存在");
        }
        if (distribution.getDistributionStatus() != PayrollDistributionStatus.PLANNED
                && distribution.getDistributionStatus() != PayrollDistributionStatus.FAILED) {
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
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前无可提交的有效发放明细");
        }

        PayrollBatch batch = payrollBatchMapper.selectById(distribution.getBatchId());
        String batchNo = buildPaymentBatchNo(distribution, nextAttempt);

        distribution.setDistributionStatus(PayrollDistributionStatus.SUBMITTING);
        distributionService.updateById(distribution);

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
            record.setRecipientAccount(decryptDistributionAccount(item));
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

        if (batch != null) {
            batch.setPaymentBatchNo(batchNo);
            batch.setStatus(PayrollBatchStatus.PAY_PROCESSING);
            payrollBatchMapper.updateById(batch);
        }
        return paymentBatch;
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
                submitDistribution(distribution.getId());
            } catch (Exception ex) {
                log.error("自动提交发放失败: distributionId={}", distribution.getId(), ex);
            }
        }
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

    private int safeRetryLimit(PayrollDistribution distribution) {
        return distribution.getRetryLimit() == null || distribution.getRetryLimit() < 1 ? 3 : distribution.getRetryLimit();
    }

    private int normalizeRevision(Integer revision) {
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

    private String decryptDistributionAccount(PayrollDistributionItem item) {
        if (item == null || !StringUtils.hasText(item.getAccountNoEncrypted())) {
            return null;
        }
        try {
            return encryptionService.decrypt(item.getAccountNoEncrypted());
        } catch (Exception ex) {
            log.warn("解密发放快照账户失败，按密文兜底: distributionItemId={}, msg={}", item.getId(), ex.getMessage());
            return item.getAccountNoEncrypted();
        }
    }
}
