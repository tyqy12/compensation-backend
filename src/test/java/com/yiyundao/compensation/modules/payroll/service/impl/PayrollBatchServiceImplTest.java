package com.yiyundao.compensation.modules.payroll.service.impl;

import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollApprovalProjectionService;
import com.yiyundao.compensation.modules.payroll.service.PayrollConfirmationAggregateService;
import com.yiyundao.compensation.modules.payroll.service.PayrollDistributionService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentService;
import com.yiyundao.compensation.modules.payroll.service.PayrollProcessManager;
import com.yiyundao.compensation.modules.payroll.support.PayrollValidationIssueSupport;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class PayrollBatchServiceImplTest {

    @Mock
    private ApprovalEngine approvalEngine;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private PayrollLineService payrollLineService;
    @Mock
    private PayrollPaymentService payrollPaymentService;
    @Mock
    private UserRoleService userRoleService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PayrollValidationIssueSupport validationIssueSupport;
    @Mock
    private PayrollConfirmationAggregateService confirmationAggregateService;
    @Mock
    private PayrollDistributionService distributionService;
    @Mock
    private PayrollApprovalProjectionService approvalProjectionService;
    @Mock
    private PayrollProcessManager payrollProcessManager;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitForApproval_shouldThrowWhenConfirmationsUnresolved() {
        PayrollBatchServiceImpl service = newService();

        PayrollBatch batch = new PayrollBatch();
        batch.setId(1L);
        batch.setStatus(PayrollBatchStatus.CONFIRMED);
        batch.setConfirmationRequired(Boolean.TRUE);
        batch.setBatchRevision(1);
        doReturn(batch).when(service).getById(1L);
        when(confirmationAggregateService.isCompletedForApproval(1L, 1)).thenReturn(false);

        assertThatThrownBy(() -> service.submitForApproval(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("还有员工待确认或异议未处理");

        verify(confirmationAggregateService).syncFromLegacyBatch(1L, 1);
        verify(confirmationAggregateService).isCompletedForApproval(1L, 1);
        verifyNoInteractions(approvalEngine, distributionService, approvalProjectionService, payrollProcessManager);
    }

    @Test
    void submitForApproval_shouldThrowWhenWorkflowStartFailsAfterConfirmationsClosed() {
        PayrollBatchServiceImpl service = newService();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance_user", "n/a")
        );

        PayrollBatch batch = new PayrollBatch();
        batch.setId(2L);
        batch.setStatus(PayrollBatchStatus.CONFIRMED);
        batch.setConfirmationRequired(Boolean.TRUE);
        batch.setBatchRevision(2);
        doReturn(batch).when(service).getById(2L);
        when(confirmationAggregateService.isCompletedForApproval(2L, 2)).thenReturn(true);

        PayrollLine line = new PayrollLine();
        line.setId(2001L);
        line.setBatchId(2L);
        line.setWarning("[]");
        when(payrollLineService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<PayrollLine>>any()))
                .thenReturn(List.of(line));
        when(validationIssueSupport.deserialize(any())).thenReturn(List.of());

        PayrollDistribution distribution = new PayrollDistribution();
        distribution.setId(300L);
        when(distributionService.createOrReuseForBatch(batch)).thenReturn(distribution);
        when(approvalProjectionService.getByDistributionId(300L)).thenReturn(null);

        SysUser currentUser = new SysUser();
        currentUser.setId(100L);
        currentUser.setUsername("finance_user");
        when(sysUserService.findByUsername("finance_user")).thenReturn(currentUser);
        when(userRoleService.hasRole(100L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(approvalEngine.startWorkflow(
                eq(WorkflowType.PAYROLL_DISTRIBUTION),
                eq("payroll_distribution:300"),
                eq("payroll_distribution"),
                eq(100L),
                eq(Map.of("batchId", 2L, "batchRevision", 2, "distributionId", 300L))
        )).thenThrow(new RuntimeException("workflow unavailable"));

        assertThatThrownBy(() -> service.submitForApproval(2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("启动审批流程失败");

        verify(confirmationAggregateService).syncFromLegacyBatch(2L, 2);
        verify(confirmationAggregateService).isCompletedForApproval(2L, 2);
        verify(distributionService).createOrReuseForBatch(batch);
        verify(approvalEngine).startWorkflow(
                eq(WorkflowType.PAYROLL_DISTRIBUTION),
                eq("payroll_distribution:300"),
                eq("payroll_distribution"),
                eq(100L),
                eq(Map.of("batchId", 2L, "batchRevision", 2, "distributionId", 300L))
        );
        verify(distributionService, never()).bindApprovalWorkflow(anyLong(), anyLong());
        verify(approvalProjectionService, never()).createOrUpdatePending(any(), any(), anyLong(), anyLong());
    }

    private PayrollBatchServiceImpl newService() {
        return spy(new PayrollBatchServiceImpl(
                approvalEngine,
                sysUserService,
                payrollLineService,
                payrollPaymentService,
                userRoleService,
                auditLogService,
                validationIssueSupport,
                confirmationAggregateService,
                distributionService,
                approvalProjectionService,
                payrollProcessManager
        ));
    }
}
