package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitForApproval_shouldRejectWhenConfirmationsUnresolved() {
        PayrollBatchServiceImpl service = spy(new PayrollBatchServiceImpl(
                approvalEngine,
                sysUserService,
                payrollLineService,
                payrollPaymentService,
                userRoleService,
                auditLogService
        ));

        PayrollBatch batch = new PayrollBatch();
        batch.setId(1L);
        batch.setStatus(PayrollBatchStatus.CONFIRMED);
        batch.setConfirmationRequired(Boolean.TRUE);
        doReturn(batch).when(service).getById(1L);
        when(payrollLineService.count(any())).thenReturn(5L, 1L);

        boolean result = service.submitForApproval(1L);

        assertThat(result).isFalse();
        verifyNoInteractions(approvalEngine);
        verify(service, never()).update(ArgumentMatchers.<LambdaUpdateWrapper<PayrollBatch>>any());
    }

    @Test
    void submitForApproval_shouldThrowWhenWorkflowStartFailsAfterConfirmationsClosed() {
        PayrollBatchServiceImpl service = spy(new PayrollBatchServiceImpl(
                approvalEngine,
                sysUserService,
                payrollLineService,
                payrollPaymentService,
                userRoleService,
                auditLogService
        ));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance_user", "n/a")
        );

        PayrollBatch batch = new PayrollBatch();
        batch.setId(2L);
        batch.setStatus(PayrollBatchStatus.CONFIRMED);
        batch.setConfirmationRequired(Boolean.TRUE);
        doReturn(batch).when(service).getById(2L);
        when(payrollLineService.count(any())).thenReturn(5L, 0L);

        SysUser currentUser = new SysUser();
        currentUser.setId(100L);
        currentUser.setUsername("finance_user");
        when(sysUserService.findByUsername("finance_user")).thenReturn(currentUser);
        when(userRoleService.hasRole(100L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(approvalEngine.startWorkflow(
                WorkflowType.BATCH,
                "payroll_batch:2",
                "payroll",
                100L,
                java.util.Map.of("batchId", 2L)
        )).thenThrow(new RuntimeException("workflow unavailable"));

        assertThatThrownBy(() -> service.submitForApproval(2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("启动审批流程失败");
        verify(approvalEngine).startWorkflow(
                WorkflowType.BATCH,
                "payroll_batch:2",
                "payroll",
                100L,
                java.util.Map.of("batchId", 2L)
        );
        verify(service, never()).update(ArgumentMatchers.<LambdaUpdateWrapper<PayrollBatch>>any());
        verify(payrollLineService, times(2)).count(any());
    }
}
