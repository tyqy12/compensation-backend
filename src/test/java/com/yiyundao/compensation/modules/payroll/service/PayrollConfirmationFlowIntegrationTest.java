package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
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
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.impl.PayrollBatchServiceImpl;
import com.yiyundao.compensation.modules.payroll.service.impl.PayrollConfirmationServiceImpl;
import com.yiyundao.compensation.modules.payroll.support.PayrollValidationIssueSupport;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityConstants;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 薪酬确认流程集成测试（跨服务编排）
 * 覆盖链路：员工确认 -> 员工异议 -> 审批回写 -> 发薪提交流转
 */
class PayrollConfirmationFlowIntegrationTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, PayrollLine.class.getName());
        assistant.setCurrentNamespace(PayrollLine.class.getName());
        TableInfoHelper.initTableInfo(assistant, PayrollLine.class);
    }

    @AfterEach
    void cleanupSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void flow_shouldOpenSubmitGateAfterDisputeApproved() {
        FlowContext ctx = buildContext();
        when(ctx.approvalEngine.startWorkflow(
                eq(WorkflowType.PAYROLL_DISPUTE),
                argThat(key -> key != null && key.startsWith("payroll_dispute:line:" + ctx.line2.getId() + "-")),
                eq("payroll_dispute"),
                eq(ctx.operator.getId()),
                anyMap()
        )).thenReturn(7001L);
        when(ctx.approvalEngine.startWorkflow(
                eq(WorkflowType.PAYROLL_DISTRIBUTION),
                eq("payroll_distribution:" + ctx.distribution.getId()),
                eq("payroll_distribution"),
                eq(ctx.operator.getId()),
                eq(Map.of(
                        "batchId", ctx.batch.getId(),
                        "batchRevision", ctx.batch.getBatchRevision(),
                        "distributionId", ctx.distribution.getId()
                ))
        )).thenThrow(new RuntimeException("workflow unavailable"));

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

        assertThatThrownBy(() -> ctx.batchService.submitForApproval(ctx.batch.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不可提交审批：dispute_processing");

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
        verify(ctx.payrollProcessManager).onConfirmationCompleted(ctx.batch.getId(), ctx.batch.getBatchRevision());

        assertThatThrownBy(() -> ctx.batchService.submitForApproval(ctx.batch.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("启动审批流程失败");

        verify(ctx.approvalEngine).startWorkflow(
                eq(WorkflowType.PAYROLL_DISPUTE),
                argThat(key -> key != null && key.startsWith("payroll_dispute:line:" + ctx.line2.getId() + "-")),
                eq("payroll_dispute"),
                eq(ctx.operator.getId()),
                anyMap()
        );
        verify(ctx.approvalEngine).startWorkflow(
                eq(WorkflowType.PAYROLL_DISTRIBUTION),
                eq("payroll_distribution:" + ctx.distribution.getId()),
                eq("payroll_distribution"),
                eq(ctx.operator.getId()),
                eq(Map.of(
                        "batchId", ctx.batch.getId(),
                        "batchRevision", ctx.batch.getBatchRevision(),
                        "distributionId", ctx.distribution.getId()
                ))
        );
    }

    @Test
    void flow_shouldKeepSubmitBlockedAfterDisputeRejected() {
        FlowContext ctx = buildContext();
        when(ctx.approvalEngine.startWorkflow(
                eq(WorkflowType.PAYROLL_DISPUTE),
                argThat(key -> key != null && key.startsWith("payroll_dispute:line:" + ctx.line2.getId() + "-")),
                eq("payroll_dispute"),
                eq(ctx.operator.getId()),
                anyMap()
        )).thenReturn(8001L);

        PayslipConfirmRequest confirmRequest = new PayslipConfirmRequest();
        confirmRequest.setSignature("员工签字");
        ctx.confirmationService.confirmPayslip(ctx.line1.getId(), ctx.operator, confirmRequest);

        PayslipObjectionRequest objectionRequest = new PayslipObjectionRequest();
        objectionRequest.setReason("个税口径不一致");
        Long disputeWorkflowId = ctx.confirmationService.objectPayslip(ctx.line2.getId(), ctx.operator, objectionRequest);
        assertThat(disputeWorkflowId).isEqualTo(8001L);
        assertThat(ctx.batch.getStatus()).isEqualTo(PayrollBatchStatus.DISPUTE_PROCESSING);

        assertThatThrownBy(() -> ctx.batchService.submitForApproval(ctx.batch.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不可提交审批：dispute_processing");

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

        assertThatThrownBy(() -> ctx.batchService.submitForApproval(ctx.batch.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不可提交审批：confirming");

        verify(ctx.payrollProcessManager, never()).onConfirmationCompleted(ctx.batch.getId(), ctx.batch.getBatchRevision());
        verify(ctx.approvalEngine, never()).startWorkflow(
                eq(WorkflowType.PAYROLL_DISTRIBUTION),
                anyString(),
                anyString(),
                anyLong(),
                anyMap()
        );
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
        ctx.validationIssueSupport = mock(PayrollValidationIssueSupport.class);
        ctx.confirmationAggregateService = mock(PayrollConfirmationAggregateService.class);
        ctx.distributionService = mock(PayrollDistributionService.class);
        ctx.approvalProjectionService = mock(PayrollApprovalProjectionService.class);
        ctx.payrollProcessManager = mock(PayrollProcessManager.class);

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
        ctx.batch.setBatchRevision(1);
        ctx.batch.setStatus(PayrollBatchStatus.CONFIRMING);
        ctx.batch.setConfirmationRequired(Boolean.TRUE);
        when(ctx.payrollBatchService.getById(ctx.batch.getId())).thenReturn(ctx.batch);
        when(ctx.payrollBatchService.updateById(any(PayrollBatch.class))).thenReturn(true);
        when(ctx.confirmationAggregateService.isCompletedForApproval(ctx.batch.getId(), ctx.batch.getBatchRevision()))
                .thenReturn(true);

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
        when(ctx.payrollLineService.update(org.mockito.ArgumentMatchers.any(LambdaUpdateWrapper.class)))
                .thenReturn(true);
        when(ctx.validationIssueSupport.deserialize(any())).thenReturn(List.of());

        ctx.distribution = new PayrollDistribution();
        ctx.distribution.setId(7101L);
        when(ctx.distributionService.createOrReuseForBatch(any(PayrollBatch.class))).thenReturn(ctx.distribution);
        when(ctx.approvalProjectionService.getByDistributionId(ctx.distribution.getId())).thenReturn(null);

        ctx.confirmationService = new PayrollConfirmationServiceImpl(
                ctx.payrollLineService,
                ctx.payrollBatchService,
                ctx.approvalEngine,
                ctx.sysUserService,
                ctx.employeeService,
                ctx.userRoleService,
                new ObjectMapper(),
                ctx.confirmationAggregateService,
                ctx.payrollProcessManager
        );
        ctx.disputeApprovalHandler = new PayrollDisputeApprovalHandler(ctx.confirmationService);

        ctx.batchService = spy(new PayrollBatchServiceImpl(
                ctx.approvalEngine,
                ctx.sysUserService,
                ctx.payrollLineService,
                ctx.payrollPaymentService,
                ctx.userRoleService,
                ctx.auditLogService,
                ctx.validationIssueSupport,
                ctx.confirmationAggregateService,
                ctx.distributionService,
                ctx.approvalProjectionService,
                ctx.payrollProcessManager
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
        private PayrollValidationIssueSupport validationIssueSupport;
        private PayrollConfirmationAggregateService confirmationAggregateService;
        private PayrollDistributionService distributionService;
        private PayrollApprovalProjectionService approvalProjectionService;
        private PayrollProcessManager payrollProcessManager;
        private PayrollConfirmationServiceImpl confirmationService;
        private PayrollDisputeApprovalHandler disputeApprovalHandler;
        private PayrollBatchServiceImpl batchService;
        private SysUser operator;
        private PayrollBatch batch;
        private PayrollDistribution distribution;
        private PayrollLine line1;
        private PayrollLine line2;
    }
}
