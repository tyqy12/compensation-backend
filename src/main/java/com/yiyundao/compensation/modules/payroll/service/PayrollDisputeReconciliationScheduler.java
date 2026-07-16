package com.yiyundao.compensation.modules.payroll.service;

import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 恢复审批完成后未落地的薪资异议结果。
 *
 * <p>审批完成事件仍负责实时处理；本任务只处理事务提交后监听器失败的遗留记录。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollDisputeReconciliationScheduler {

    private final ApprovalWorkflowMapper approvalWorkflowMapper;
    private final PayrollConfirmationService payrollConfirmationService;

    @Value("${payroll.confirmation.dispute-reconciliation.enabled:true}")
    private boolean enabled;

    @Value("${payroll.confirmation.dispute-reconciliation.batch-limit:20}")
    private int batchLimit;

    @Value("${payroll.confirmation.dispute-reconciliation.stale-minutes:10}")
    private int staleMinutes;

    @Scheduled(
            fixedDelayString = "${payroll.confirmation.dispute-reconciliation.fixed-delay-ms:300000}",
            initialDelayString = "${payroll.confirmation.dispute-reconciliation.initial-delay-ms:120000}"
    )
    public void reconcileCompletedDisputes() {
        if (!enabled) {
            return;
        }

        int safeLimit = Math.max(1, Math.min(batchLimit, 100));
        int safeStaleMinutes = Math.max(1, Math.min(staleMinutes, 24 * 60));
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(safeStaleMinutes);

        try {
            List<ApprovalWorkflow> workflows = approvalWorkflowMapper.selectPendingPayrollDisputeWorkflows(
                    cutoff, safeLimit);
            if (workflows == null || workflows.isEmpty()) {
                return;
            }

            int recovered = 0;
            for (ApprovalWorkflow workflow : workflows) {
                if (workflow == null || workflow.getId() == null || !isFinalStatus(workflow.getStatus())) {
                    continue;
                }
                try {
                    payrollConfirmationService.handleDisputeWorkflowCompleted(workflow, workflow.getStatus());
                    recovered++;
                } catch (Exception ex) {
                    log.error("恢复薪资异议审批结果失败: workflowId={}, status={}",
                            workflow.getId(), workflow.getStatus(), ex);
                }
            }
            if (recovered > 0) {
                log.info("薪资异议审批结果恢复完成: recovered={}, limit={}, staleMinutes={}",
                        recovered, safeLimit, safeStaleMinutes);
            }
        } catch (Exception ex) {
            log.error("薪资异议恢复任务异常", ex);
        }
    }

    private boolean isFinalStatus(ApprovalStatus status) {
        return status == ApprovalStatus.APPROVED
                || status == ApprovalStatus.REJECTED
                || status == ApprovalStatus.CANCELLED;
    }
}
