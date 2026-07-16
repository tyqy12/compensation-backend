package com.yiyundao.compensation.modules.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollApprovalProjection;
import com.yiyundao.compensation.modules.payroll.service.PayrollApprovalProjectionService;
import com.yiyundao.compensation.modules.payroll.service.PayrollProcessManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付主动对账任务：周期性轮询处理中记录状态，收敛批次终态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private final SettlementService settlementService;
    private final PaymentBatchService paymentBatchService;
    private final PayrollApprovalProjectionService approvalProjectionService;
    private final PayrollProcessManager payrollProcessManager;

    @Value("${payment.reconciliation.enabled:true}")
    private boolean enabled;

    @Value("${payment.reconciliation.batch-limit:20}")
    private int batchLimit;

    @Value("${payment.reconciliation.record-limit-per-batch:200}")
    private int recordLimitPerBatch;

    @Value("${payment.reconciliation.recover-stale-submitted-enabled:true}")
    private boolean recoverStaleSubmittedEnabled;

    @Value("${payment.reconciliation.stale-submitted-minutes:10}")
    private int staleSubmittedMinutes;

    @Value("${payment.reconciliation.recover-stale-approved-payroll-enabled:true}")
    private boolean recoverStaleApprovedPayrollEnabled;

    @Value("${payment.reconciliation.stale-approved-payroll-minutes:10}")
    private int staleApprovedPayrollMinutes;

    @Scheduled(
            fixedDelayString = "${payment.reconciliation.fixed-delay-ms:300000}",
            initialDelayString = "${payment.reconciliation.initial-delay-ms:120000}"
    )
    public void reconcileInFlightPayments() {
        if (!enabled) {
            return;
        }
        try {
            int scannedBatches = settlementService.reconcileProcessingBatches(batchLimit, recordLimitPerBatch);
            int recoveredBatches = recoverStaleSubmittedPayrollBatches();
            int recoveredApprovalEvents = recoverStaleApprovedPayrollDistributions();
            if (scannedBatches > 0 || recoveredBatches > 0 || recoveredApprovalEvents > 0) {
                log.info("支付主动对账完成: scannedBatches={}, recoveredBatches={}, recoveredApprovalEvents={}, "
                                + "batchLimit={}, recordLimitPerBatch={}",
                        scannedBatches, recoveredBatches, recoveredApprovalEvents, batchLimit, recordLimitPerBatch);
            }
        } catch (Exception ex) {
            log.error("支付主动对账任务异常", ex);
        }
    }

    private int recoverStaleSubmittedPayrollBatches() {
        if (!recoverStaleSubmittedEnabled) {
            return 0;
        }
        int safeLimit = Math.max(1, Math.min(batchLimit, 100));
        int safeStaleMinutes = Math.max(1, Math.min(staleSubmittedMinutes, 24 * 60));
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(safeStaleMinutes);
        List<PaymentBatch> staleBatches = paymentBatchService.list(new LambdaQueryWrapper<PaymentBatch>()
                .eq(PaymentBatch::getStatus, BatchStatus.SUBMITTED)
                .isNotNull(PaymentBatch::getDistributionId)
                .le(PaymentBatch::getUpdateTime, cutoff)
                .orderByAsc(PaymentBatch::getUpdateTime)
                .last("limit " + safeLimit));
        if (staleBatches == null || staleBatches.isEmpty()) {
            return 0;
        }

        int recovered = 0;
        for (PaymentBatch batch : staleBatches) {
            if (batch == null || batch.getBatchNo() == null || batch.getBatchNo().isBlank()) {
                continue;
            }
            settlementService.batchTransfer(batch.getBatchNo());
            recovered++;
        }
        return recovered;
    }

    private int recoverStaleApprovedPayrollDistributions() {
        if (!recoverStaleApprovedPayrollEnabled) {
            return 0;
        }
        int safeLimit = Math.max(1, Math.min(batchLimit, 100));
        int safeStaleMinutes = Math.max(1, Math.min(staleApprovedPayrollMinutes, 24 * 60));
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(safeStaleMinutes);
        List<PayrollApprovalProjection> projections = approvalProjectionService
                .listStalePendingDistributionApprovals(cutoff, safeLimit);
        if (projections == null || projections.isEmpty()) {
            return 0;
        }

        int recovered = 0;
        for (PayrollApprovalProjection projection : projections) {
            if (projection == null || projection.getDistributionId() == null) {
                continue;
            }
            try {
                // 复用审批回调的幂等状态机；提交后的支付动作仍由发放单流程负责。
                payrollProcessManager.onApprovalApproved(
                        projection.getDistributionId(), projection.getWorkflowId(), null);
                recovered++;
            } catch (Exception ex) {
                log.error("恢复已完成薪资审批失败: workflowId={}, distributionId={}",
                        projection.getWorkflowId(), projection.getDistributionId(), ex);
            }
        }
        return recovered;
    }
}
