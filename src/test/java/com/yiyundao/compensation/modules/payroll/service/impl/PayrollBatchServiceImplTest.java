package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.modules.payroll.entity.PayrollApprovalProjection;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class PayrollBatchServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, PayrollBatch.class.getName());
        assistant.setCurrentNamespace(PayrollBatch.class.getName());
        TableInfoHelper.initTableInfo(assistant, PayrollBatch.class);
    }

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
    void lockBatch_shouldUseStatusConditionToAvoidStaleOverwrite() {
        PayrollBatchServiceImpl service = newService();
        PayrollBatch batch = new PayrollBatch();
        batch.setId(10L);
        batch.setStatus(PayrollBatchStatus.DRAFT);
        doReturn(batch).when(service).getById(10L);
        doAnswer(invocation -> {
            LambdaUpdateWrapper<PayrollBatch> wrapper = invocation.getArgument(0);
            String sqlSegment = wrapper.getSqlSegment();
            assertThat(sqlSegment)
                    .contains("id =")
                    .contains("status =");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .contains(PayrollBatchStatus.DRAFT, PayrollBatchStatus.LOCKED);
            return true;
        }).when(service).update(org.mockito.ArgumentMatchers.any(LambdaUpdateWrapper.class));

        assertThat(service.lockBatch(10L)).isTrue();
    }

    @Test
    void updateStatus_shouldRejectIllegalTransition() {
        PayrollBatchServiceImpl service = newService();
        PayrollBatch batch = new PayrollBatch();
        batch.setId(11L);
        batch.setStatus(PayrollBatchStatus.DRAFT);
        doReturn(batch).when(service).getById(11L);

        assertThatThrownBy(() -> service.updateStatus(11L, PayrollBatchStatus.PAID.getCode()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许的薪资批次状态转移");

        verify(service, never()).update(org.mockito.ArgumentMatchers.any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateStatus_shouldGuardExpectedStatusAndVersion() {
        PayrollBatchServiceImpl service = newService();
        PayrollBatch batch = new PayrollBatch();
        batch.setId(12L);
        batch.setStatus(PayrollBatchStatus.DRAFT);
        batch.setVersion(3);
        doReturn(batch).when(service).getById(12L);
        doAnswer(invocation -> {
            LambdaUpdateWrapper<PayrollBatch> wrapper = invocation.getArgument(0);
            assertThat(wrapper.getSqlSegment()).contains("id =").contains("status =").contains("version =");
            assertThat(wrapper.getParamNameValuePairs().values())
                    .contains(PayrollBatchStatus.DRAFT, PayrollBatchStatus.LOCKED, 3, 4);
            return true;
        }).when(service).update(org.mockito.ArgumentMatchers.any(LambdaUpdateWrapper.class));

        assertThat(service.updateStatus(12L, PayrollBatchStatus.LOCKED.getCode())).isTrue();
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

    @Test
    void submitForApproval_shouldRejectWhenCurrentUserMissing() {
        PayrollBatchServiceImpl service = newService();

        PayrollBatch batch = new PayrollBatch();
        batch.setId(3L);
        batch.setStatus(PayrollBatchStatus.CONFIRMED);
        batch.setConfirmationRequired(Boolean.TRUE);
        batch.setBatchRevision(1);
        doReturn(batch).when(service).getById(3L);
        when(confirmationAggregateService.isCompletedForApproval(3L, 1)).thenReturn(true);

        PayrollLine line = new PayrollLine();
        line.setId(3001L);
        line.setBatchId(3L);
        line.setWarning("[]");
        when(payrollLineService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<PayrollLine>>any()))
                .thenReturn(List.of(line));
        when(validationIssueSupport.deserialize(any())).thenReturn(List.of());

        PayrollDistribution distribution = new PayrollDistribution();
        distribution.setId(301L);
        when(distributionService.createOrReuseForBatch(batch)).thenReturn(distribution);
        when(approvalProjectionService.getByDistributionId(301L)).thenReturn(null);

        assertThatThrownBy(() -> service.submitForApproval(3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录或用户不存在");

        verify(approvalEngine, never()).startWorkflow(any(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    void submitForApproval_shouldSyncBatchStatusWhenExistingApprovalProjectionIsInProgress() {
        PayrollBatchServiceImpl service = newService();
        PayrollBatch batch = new PayrollBatch();
        batch.setId(5L);
        batch.setStatus(PayrollBatchStatus.CONFIRMED);
        batch.setConfirmationRequired(Boolean.TRUE);
        batch.setBatchRevision(1);
        doReturn(batch).when(service).getById(5L);
        doAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<PayrollBatch> wrapper =
                    invocation.getArgument(0);
            assertThat(wrapper.getParamNameValuePairs().values())
                    .contains(9005L, PayrollBatchStatus.SUBMITTED.getCode());
            return true;
        }).when(service).update(org.mockito.ArgumentMatchers.any());
        when(confirmationAggregateService.isCompletedForApproval(5L, 1)).thenReturn(true);

        PayrollLine line = new PayrollLine();
        line.setId(5001L);
        line.setBatchId(5L);
        line.setWarning("[]");
        when(payrollLineService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<PayrollLine>>any()))
                .thenReturn(List.of(line));
        when(validationIssueSupport.deserialize(any())).thenReturn(List.of());

        PayrollDistribution distribution = new PayrollDistribution();
        distribution.setId(305L);
        distribution.setApprovalWorkflowId(9005L);
        when(distributionService.createOrReuseForBatch(batch)).thenReturn(distribution);
        PayrollApprovalProjection projection = new PayrollApprovalProjection();
        projection.setBusinessStatus("IN_PROGRESS");
        when(approvalProjectionService.getByDistributionId(305L)).thenReturn(projection);

        boolean submitted = service.submitForApproval(5L);

        org.assertj.core.api.Assertions.assertThat(submitted).isTrue();
        verify(approvalEngine, never()).startWorkflow(any(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    void submitForApproval_shouldUseRetryBusinessKeyWhenPreviousProjectionWasCancelled() {
        PayrollBatchServiceImpl service = newService();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance_user", "n/a")
        );

        PayrollBatch batch = new PayrollBatch();
        batch.setId(8L);
        batch.setStatus(PayrollBatchStatus.CONFIRMED);
        batch.setConfirmationRequired(Boolean.TRUE);
        batch.setBatchRevision(1);
        doReturn(batch).when(service).getById(8L);
        doReturn(true).when(service).update(org.mockito.ArgumentMatchers.any(LambdaUpdateWrapper.class));
        when(confirmationAggregateService.isCompletedForApproval(8L, 1)).thenReturn(true);

        PayrollLine line = new PayrollLine();
        line.setId(8001L);
        line.setBatchId(8L);
        line.setWarning("[]");
        when(payrollLineService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<PayrollLine>>any()))
                .thenReturn(List.of(line));
        when(validationIssueSupport.deserialize(any())).thenReturn(List.of());

        PayrollDistribution distribution = new PayrollDistribution();
        distribution.setId(308L);
        when(distributionService.createOrReuseForBatch(batch)).thenReturn(distribution);
        PayrollApprovalProjection projection = new PayrollApprovalProjection();
        projection.setWorkflowId(9001L);
        projection.setBusinessStatus("CANCELLED");
        when(approvalProjectionService.getByDistributionId(308L)).thenReturn(projection);

        SysUser currentUser = new SysUser();
        currentUser.setId(100L);
        currentUser.setUsername("finance_user");
        when(sysUserService.findByUsername("finance_user")).thenReturn(currentUser);
        when(userRoleService.hasRole(100L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);

        when(approvalEngine.startWorkflow(
                eq(WorkflowType.PAYROLL_DISTRIBUTION),
                eq("payroll_distribution:308:retry:9001"),
                eq("payroll_distribution"),
                eq(100L),
                eq(Map.of("batchId", 8L, "batchRevision", 1, "distributionId", 308L))
        )).thenReturn(9008L);

        assertThat(service.submitForApproval(8L)).isTrue();

        verify(approvalEngine).startWorkflow(
                eq(WorkflowType.PAYROLL_DISTRIBUTION),
                eq("payroll_distribution:308:retry:9001"),
                eq("payroll_distribution"),
                eq(100L),
                eq(Map.of("batchId", 8L, "batchRevision", 1, "distributionId", 308L))
        );
        verify(distributionService).bindApprovalWorkflow(308L, 9008L);
        verify(approvalProjectionService).createOrUpdatePending(batch, distribution, 9008L, 100L);
    }

    @Test
    void submitForApproval_shouldUseCasAndRollBackWhenBatchStateChangedAfterWorkflowStarted() {
        PayrollBatchServiceImpl service = newService();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance_user", "n/a")
        );

        PayrollBatch batch = new PayrollBatch();
        batch.setId(6L);
        batch.setStatus(PayrollBatchStatus.CONFIRMED);
        batch.setConfirmationRequired(Boolean.TRUE);
        batch.setBatchRevision(null);
        doReturn(batch).when(service).getById(6L);
        doReturn(false).when(service).update(org.mockito.ArgumentMatchers.any(LambdaUpdateWrapper.class));
        when(confirmationAggregateService.isCompletedForApproval(6L, 1)).thenReturn(true);

        PayrollLine line = new PayrollLine();
        line.setId(6001L);
        line.setBatchId(6L);
        line.setWarning("[]");
        when(payrollLineService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<PayrollLine>>any()))
                .thenReturn(List.of(line));
        when(validationIssueSupport.deserialize(any())).thenReturn(List.of());

        PayrollDistribution distribution = new PayrollDistribution();
        distribution.setId(306L);
        when(distributionService.createOrReuseForBatch(batch)).thenReturn(distribution);
        when(approvalProjectionService.getByDistributionId(306L)).thenReturn(null);

        SysUser currentUser = new SysUser();
        currentUser.setId(100L);
        currentUser.setUsername("finance_user");
        when(sysUserService.findByUsername("finance_user")).thenReturn(currentUser);
        when(userRoleService.hasRole(100L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(approvalEngine.startWorkflow(
                eq(WorkflowType.PAYROLL_DISTRIBUTION),
                eq("payroll_distribution:306"),
                eq("payroll_distribution"),
                eq(100L),
                eq(Map.of("batchId", 6L, "batchRevision", 1, "distributionId", 306L))
        )).thenReturn(9006L);

        assertThatThrownBy(() -> service.submitForApproval(6L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REQUEST_CONFLICT);
                    assertThat(ex.getMessage()).contains("批次状态已变更");
                });

        verify(service, never()).updateById(any(PayrollBatch.class));
        verify(distributionService).bindApprovalWorkflow(306L, 9006L);
        verify(approvalProjectionService).createOrUpdatePending(batch, distribution, 9006L, 100L);
        verify(payrollProcessManager, never()).onApprovalApproved(anyLong(), anyLong(), anyLong());
    }

    @Test
    void submitForApproval_shouldProtectAdminBypassWithCas() {
        PayrollBatchServiceImpl service = newService();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin_user", "n/a")
        );

        PayrollBatch batch = new PayrollBatch();
        batch.setId(7L);
        batch.setStatus(PayrollBatchStatus.CONFIRMED);
        batch.setConfirmationRequired(Boolean.TRUE);
        batch.setBatchRevision(1);
        doReturn(batch).when(service).getById(7L);
        doReturn(true).when(service).update(org.mockito.ArgumentMatchers.any(LambdaUpdateWrapper.class));
        when(confirmationAggregateService.isCompletedForApproval(7L, 1)).thenReturn(true);

        PayrollLine line = new PayrollLine();
        line.setId(7001L);
        line.setBatchId(7L);
        line.setWarning("[]");
        when(payrollLineService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<PayrollLine>>any()))
                .thenReturn(List.of(line));
        when(validationIssueSupport.deserialize(any())).thenReturn(List.of());

        PayrollDistribution distribution = new PayrollDistribution();
        distribution.setId(307L);
        when(distributionService.createOrReuseForBatch(batch)).thenReturn(distribution);
        when(approvalProjectionService.getByDistributionId(307L)).thenReturn(null);

        SysUser currentUser = new SysUser();
        currentUser.setId(1L);
        currentUser.setUsername("admin_user");
        when(sysUserService.findByUsername("admin_user")).thenReturn(currentUser);
        when(userRoleService.hasRole(1L, SecurityConstants.ROLE_ADMIN)).thenReturn(true);

        assertThat(service.submitForApproval(7L)).isTrue();

        verify(approvalProjectionService).markApproved(-307L, 1L);
        verify(payrollProcessManager).onApprovalApproved(307L, -307L, 1L);
        verify(service, times(1)).update(org.mockito.ArgumentMatchers.any(LambdaUpdateWrapper.class));
    }

    @Test
    void retryCreatePaymentBatch_shouldRejectWhenCurrentUserMissing() {
        PayrollBatchServiceImpl service = newService();

        PayrollBatch batch = new PayrollBatch();
        batch.setId(4L);
        batch.setStatus(PayrollBatchStatus.APPROVED);
        doReturn(batch).when(service).getById(4L);

        assertThatThrownBy(() -> service.retryCreatePaymentBatch(4L, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录或用户不存在");

        verifyNoInteractions(payrollPaymentService);
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
