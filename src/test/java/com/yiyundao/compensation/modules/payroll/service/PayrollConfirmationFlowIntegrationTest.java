package com.yiyundao.compensation.modules.payroll.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollConfirmationStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.interfaces.dto.payroll.PayslipConfirmRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayslipObjectionRequest;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.impl.PayrollBatchServiceImpl;
import com.yiyundao.compensation.modules.payroll.service.impl.PayrollConfirmationServiceImpl;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 薪酬确认流程集成测试（跨服务编排）
 * 覆盖链路：员工确认 -> 员工异议 -> 审批回写 -> 发薪提交闸门变化
 */
class PayrollConfirmationFlowIntegrationTest {

    @AfterEach
    void cleanupSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void flow_shouldOpenSubmitGateAfterDisputeApproved() {
        FlowContext ctx = buildContext();

        when(ctx.payrollLineService.count(any())).thenReturn(2L, 0L);
        when(ctx.approvalEngine.startWorkflow(any(), anyString(), anyString(), anyLong(), anyMap()))
                .thenReturn(7001L)
                .thenThrow(new RuntimeException("workflow unavailable"));

        PayslipConfirmRequest confirmRequest = new PayslipConfirmRequest();
        confirmRequest.setSignature("员工签字");
        confirmRequest.setComment("确认无误");
        ctx.confirmationService.confirmPayslip(ctx.line1.getId(), ctx.operator, confirmRequest);
        assertThat(ctx.line1.getConfirmationStatus()).isEqualTo(PayrollConfirmationStatus.CONFIRMED.getCode());
        assertThat(ctx.batch.getStatus()).isEqualTo(PayrollBatchStatus.CONFIRMING);

        PayslipObjectionRequest objectionRequest = new PayslipObjectionRequest();
        objectionRequest.setReason("绩效金额有偏差");
        objectionRequest.setComment("请核对规则");
        Long disputeWorkflowId = ctx.confirmationService.objectPayslip(ctx.line2.getId(), ctx.operator, objectionRequest);
        assertThat(disputeWorkflowId).isEqualTo(7001L);
        assertThat(ctx.line2.getConfirmationStatus()).isEqualTo(PayrollConfirmationStatus.OBJECTED.getCode());
        assertThat(ctx.batch.getStatus()).isEqualTo(PayrollBatchStatus.DISPUTE_PROCESSING);

        boolean submitBlocked = ctx.batchService.submitForApproval(ctx.batch.getId());
        assertThat(submitBlocked).isFalse();

        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(7001L);
        workflow.setWorkflowType(WorkflowType.PAYROLL_DISPUTE);
        workflow.setBusinessType("payroll_dispute");
        workflow.setBusinessKey("payroll_dispute:line:" + ctx.line2.getId());
        ctx.disputeApprovalHandler.onApprovalCompleted(
                new ApprovalCompletedEvent(this, workflow, ApprovalStatus.APPROVED, 90001L)
        );

        assertThat(ctx.line2.getConfirmationStatus()).isEqualTo(PayrollConfirmationStatus.OBJECTED_APPROVED.getCode());
        assertThat(ctx.batch.getStatus()).isEqualTo(PayrollBatchStatus.CONFIRMED);

        assertThatThrownBy(() -> ctx.batchService.submitForApproval(ctx.batch.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("启动审批流程失败");

        verify(ctx.approvalEngine).startWorkflow(
                eq(WorkflowType.PAYROLL_DISPUTE),
                eq("payroll_dispute:line:" + ctx.line2.getId()),
                eq("payroll_dispute"),
                eq(ctx.operator.getId()),
                anyMap()
        );
        verify(ctx.approvalEngine).startWorkflow(
                eq(WorkflowType.BATCH),
                eq("payroll_batch:" + ctx.batch.getId()),
                eq("payroll"),
                eq(ctx.operator.getId()),
                eq(Map.of("batchId", ctx.batch.getId()))
        );
        verify(ctx.payrollLineService, times(2)).count(any());
    }

    @Test
    void flow_shouldKeepSubmitBlockedAfterDisputeRejected() {
        FlowContext ctx = buildContext();

        when(ctx.payrollLineService.count(any())).thenReturn(2L, 1L, 2L, 1L);
        when(ctx.approvalEngine.startWorkflow(any(), anyString(), anyString(), anyLong(), anyMap()))
                .thenReturn(8001L);

        PayslipConfirmRequest confirmRequest = new PayslipConfirmRequest();
        confirmRequest.setSignature("员工签字");
        ctx.confirmationService.confirmPayslip(ctx.line1.getId(), ctx.operator, confirmRequest);

        PayslipObjectionRequest objectionRequest = new PayslipObjectionRequest();
        objectionRequest.setReason("个税口径不一致");
        Long disputeWorkflowId = ctx.confirmationService.objectPayslip(ctx.line2.getId(), ctx.operator, objectionRequest);
        assertThat(disputeWorkflowId).isEqualTo(8001L);
        assertThat(ctx.batch.getStatus()).isEqualTo(PayrollBatchStatus.DISPUTE_PROCESSING);

        boolean firstSubmitBlocked = ctx.batchService.submitForApproval(ctx.batch.getId());
        assertThat(firstSubmitBlocked).isFalse();

        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(8001L);
        workflow.setWorkflowType(WorkflowType.PAYROLL_DISPUTE);
        workflow.setBusinessType("payroll_dispute");
        workflow.setBusinessKey("payroll_dispute:line:" + ctx.line2.getId());
        ctx.disputeApprovalHandler.onApprovalCompleted(
                new ApprovalCompletedEvent(this, workflow, ApprovalStatus.REJECTED, 90002L)
        );

        assertThat(ctx.line2.getConfirmationStatus()).isEqualTo(PayrollConfirmationStatus.OBJECTED_REJECTED.getCode());
        assertThat(ctx.batch.getStatus()).isEqualTo(PayrollBatchStatus.CONFIRMING);

        boolean secondSubmitBlocked = ctx.batchService.submitForApproval(ctx.batch.getId());
        assertThat(secondSubmitBlocked).isFalse();

        verify(ctx.approvalEngine, times(1)).startWorkflow(
                eq(WorkflowType.PAYROLL_DISPUTE),
                eq("payroll_dispute:line:" + ctx.line2.getId()),
                eq("payroll_dispute"),
                eq(ctx.operator.getId()),
                anyMap()
        );
        verify(ctx.approvalEngine, times(1)).startWorkflow(any(), anyString(), anyString(), anyLong(), anyMap());
    }

    private FlowContext buildContext() {
        FlowContext ctx = new FlowContext();
        ctx.payrollLineService = mock(PayrollLineService.class);
        ctx.payrollBatchService = mock(PayrollBatchService.class);
        ctx.approvalEngine = mock(ApprovalEngine.class);
        ctx.sysUserService = mock(SysUserService.class);
        ctx.employeeService = mock(EmployeeService.class);
        ctx.userRoleService = mock(UserRoleService.class);
        ctx.payrollPaymentService = mock(PayrollPaymentService.class);
        ctx.auditLogService = mock(AuditLogService.class);

        ctx.operator = new SysUser();
        ctx.operator.setId(10001L);
        ctx.operator.setUsername("flow_user");
        ctx.operator.setRealName("流程员工");
        ctx.operator.setEmployeeId(20001L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ctx.operator.getUsername(), "n/a")
        );
        when(ctx.sysUserService.findByUsername(ctx.operator.getUsername())).thenReturn(ctx.operator);
        when(ctx.sysUserService.findByUsername("admin")).thenReturn(ctx.operator);
        when(ctx.userRoleService.hasRole(ctx.operator.getId(), SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(ctx.userRoleService.hasRole(eq(ctx.operator.getId()), anyString())).thenReturn(false);

        ctx.batch = new PayrollBatch();
        ctx.batch.setId(5001L);
        ctx.batch.setStatus(PayrollBatchStatus.CONFIRMING);
        ctx.batch.setConfirmationRequired(Boolean.TRUE);
        when(ctx.payrollBatchService.getById(ctx.batch.getId())).thenReturn(ctx.batch);
        when(ctx.payrollBatchService.updateById(any(PayrollBatch.class))).thenReturn(true);

        ctx.line1 = new PayrollLine();
        ctx.line1.setId(6001L);
        ctx.line1.setBatchId(ctx.batch.getId());
        ctx.line1.setEmployeeId(ctx.operator.getEmployeeId());
        ctx.line1.setConfirmationAssigneeEmployeeId(ctx.operator.getEmployeeId());
        ctx.line1.setConfirmationStatus(PayrollConfirmationStatus.PENDING.getCode());

        ctx.line2 = new PayrollLine();
        ctx.line2.setId(6002L);
        ctx.line2.setBatchId(ctx.batch.getId());
        ctx.line2.setEmployeeId(ctx.operator.getEmployeeId());
        ctx.line2.setConfirmationAssigneeEmployeeId(ctx.operator.getEmployeeId());
        ctx.line2.setConfirmationStatus(PayrollConfirmationStatus.PENDING.getCode());

        Map<Long, PayrollLine> lineStore = new LinkedHashMap<>();
        lineStore.put(ctx.line1.getId(), ctx.line1);
        lineStore.put(ctx.line2.getId(), ctx.line2);

        when(ctx.payrollLineService.getById(anyLong())).thenAnswer(invocation -> lineStore.get(invocation.getArgument(0)));
        when(ctx.payrollLineService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<PayrollLine>>any()))
                .thenAnswer(invocation -> new ArrayList<>(lineStore.values()));
        when(ctx.payrollLineService.updateById(any(PayrollLine.class))).thenAnswer(invocation -> {
            PayrollLine updated = invocation.getArgument(0);
            lineStore.put(updated.getId(), updated);
            return true;
        });

        ctx.confirmationService = new PayrollConfirmationServiceImpl(
                ctx.payrollLineService,
                ctx.payrollBatchService,
                ctx.approvalEngine,
                ctx.sysUserService,
                ctx.employeeService,
                ctx.userRoleService,
                new ObjectMapper()
        );
        ctx.disputeApprovalHandler = new PayrollDisputeApprovalHandler(ctx.confirmationService);

        ctx.batchService = spy(new PayrollBatchServiceImpl(
                ctx.approvalEngine,
                ctx.sysUserService,
                ctx.payrollLineService,
                ctx.payrollPaymentService,
                ctx.userRoleService,
                ctx.auditLogService
        ));
        doReturn(ctx.batch).when(ctx.batchService).getById(ctx.batch.getId());

        return ctx;
    }

    private static class FlowContext {
        private PayrollLineService payrollLineService;
        private PayrollBatchService payrollBatchService;
        private ApprovalEngine approvalEngine;
        private SysUserService sysUserService;
        private EmployeeService employeeService;
        private UserRoleService userRoleService;
        private PayrollPaymentService payrollPaymentService;
        private AuditLogService auditLogService;
        private PayrollConfirmationServiceImpl confirmationService;
        private PayrollDisputeApprovalHandler disputeApprovalHandler;
        private PayrollBatchServiceImpl batchService;
        private SysUser operator;
        private PayrollBatch batch;
        private PayrollLine line1;
        private PayrollLine line2;
    }
}
