package com.yiyundao.compensation.modules.payroll.service;

import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollDisputeReconciliationSchedulerTest {

    @Mock
    private ApprovalWorkflowMapper approvalWorkflowMapper;

    @Mock
    private PayrollConfirmationService payrollConfirmationService;

    private PayrollDisputeReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PayrollDisputeReconciliationScheduler(approvalWorkflowMapper, payrollConfirmationService);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "batchLimit", 20);
        ReflectionTestUtils.setField(scheduler, "staleMinutes", 10);
    }

    @Test
    void reconcileCompletedDisputesShouldReplayPendingWorkflow() {
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(9001L);
        workflow.setStatus(ApprovalStatus.APPROVED);
        when(approvalWorkflowMapper.selectPendingPayrollDisputeWorkflows(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(workflow));

        scheduler.reconcileCompletedDisputes();

        verify(payrollConfirmationService).handleDisputeWorkflowCompleted(workflow, ApprovalStatus.APPROVED);
    }

    @Test
    void reconcileCompletedDisputesShouldContinueWhenOneReplayFails() {
        ApprovalWorkflow failed = new ApprovalWorkflow();
        failed.setId(9001L);
        failed.setStatus(ApprovalStatus.REJECTED);
        ApprovalWorkflow succeeding = new ApprovalWorkflow();
        succeeding.setId(9002L);
        succeeding.setStatus(ApprovalStatus.CANCELLED);
        when(approvalWorkflowMapper.selectPendingPayrollDisputeWorkflows(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(failed, succeeding));
        org.mockito.Mockito.doThrow(new IllegalStateException("temporary failure"))
                .when(payrollConfirmationService)
                .handleDisputeWorkflowCompleted(failed, ApprovalStatus.REJECTED);

        scheduler.reconcileCompletedDisputes();

        verify(payrollConfirmationService).handleDisputeWorkflowCompleted(succeeding, ApprovalStatus.CANCELLED);
    }

    @Test
    void reconcileCompletedDisputesShouldSkipWhenDisabled() {
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.reconcileCompletedDisputes();

        verify(approvalWorkflowMapper, never()).selectPendingPayrollDisputeWorkflows(any(LocalDateTime.class), anyInt());
    }
}
