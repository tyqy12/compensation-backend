package com.yiyundao.compensation.modules.payroll.service;

import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.service.NotificationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayrollApprovalHandlerTest {

    private final PayrollPaymentService payrollPaymentService = mock(PayrollPaymentService.class);
    private final PayrollBatchService payrollBatchService = mock(PayrollBatchService.class);
    private final SysUserService sysUserService = mock(SysUserService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final PayrollProcessManager payrollProcessManager = mock(PayrollProcessManager.class);
    private final PayrollPaymentFailureService payrollPaymentFailureService = mock(PayrollPaymentFailureService.class);

    private final PayrollApprovalHandler handler = new PayrollApprovalHandler(
            payrollPaymentService,
            payrollBatchService,
            sysUserService,
            notificationService,
            payrollProcessManager,
            payrollPaymentFailureService
    );

    @Test
    void onApprovalCompletedShouldParseRetryDistributionBusinessKey() {
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(7003L);
        workflow.setWorkflowType(WorkflowType.PAYROLL_DISTRIBUTION);
        workflow.setBusinessType("payroll_distribution");
        workflow.setBusinessKey("payroll_distribution:55:retry:9001");

        ApprovalCompletedEvent event = new ApprovalCompletedEvent(this, workflow, ApprovalStatus.APPROVED, 8003L);

        handler.onApprovalCompleted(event);

        verify(payrollProcessManager).onApprovalApproved(55L, 7003L, 8003L);
        verify(payrollPaymentService, never()).createPaymentBatch(any(), any(), eq(true));
    }

    @Test
    void onApprovalCompletedShouldRecordFailureWhenPaymentBatchNotCreated() {
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(7001L);
        workflow.setBusinessType("payroll");
        workflow.setBusinessKey("payroll_batch:9001");

        PayrollBatch payrollBatch = new PayrollBatch();
        payrollBatch.setId(9001L);
        payrollBatch.setStatus(PayrollBatchStatus.APPROVED);

        SysUser approver = new SysUser();
        approver.setId(8001L);
        approver.setUsername("finance");

        when(payrollBatchService.getById(9001L)).thenReturn(payrollBatch);
        when(sysUserService.getById(8001L)).thenReturn(approver);
        when(payrollPaymentService.createPaymentBatch(payrollBatch, approver, true)).thenReturn(null);

        ApprovalCompletedEvent event = new ApprovalCompletedEvent(this, workflow, ApprovalStatus.APPROVED, 8001L);

        assertThatThrownBy(() -> handler.onApprovalCompleted(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("薪资批次审批回调处理失败");

        verify(payrollPaymentFailureService).recordFailure(eq(7001L), eq(9001L), eq("payroll_batch:9001"),
                contains("未创建支付批次"));
        verify(notificationService).sendSystemAlert(
                eq("薪资批次支付处理失败"),
                contains("未创建支付批次"),
                eq("PAYROLL_FAILURE:7001"));
        verify(payrollPaymentFailureService, never()).markRetrying(eq(7001L), eq((String) null));
    }

    @Test
    void onApprovalCompletedShouldKeepFailureRetryingWhenPaymentBatchCreated() {
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(7002L);
        workflow.setBusinessType("payroll");
        workflow.setBusinessKey("payroll_batch:9002");

        PayrollBatch payrollBatch = new PayrollBatch();
        payrollBatch.setId(9002L);
        payrollBatch.setStatus(PayrollBatchStatus.APPROVED);

        SysUser approver = new SysUser();
        approver.setId(8002L);
        approver.setUsername("finance2");

        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-9002");

        when(payrollBatchService.getById(9002L)).thenReturn(payrollBatch);
        when(sysUserService.getById(8002L)).thenReturn(approver);
        when(payrollPaymentService.createPaymentBatch(payrollBatch, approver, true)).thenReturn(paymentBatch);

        ApprovalCompletedEvent event = new ApprovalCompletedEvent(this, workflow, ApprovalStatus.APPROVED, 8002L);

        handler.onApprovalCompleted(event);

        verify(payrollPaymentFailureService).markRetrying(7002L, "PB-9002");
        verify(payrollPaymentFailureService, never()).recordFailure(eq(7002L), eq(9002L), eq("payroll_batch:9002"), contains("失败"));
    }
}
