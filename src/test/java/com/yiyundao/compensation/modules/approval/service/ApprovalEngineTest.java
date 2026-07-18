package com.yiyundao.compensation.modules.approval.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.util.TransactionAfterCommitExecutor;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.EmployeeStatus;
import com.yiyundao.compensation.enums.UserStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollLineMapper;
import com.yiyundao.compensation.modules.approval.config.ApprovalFlowConfigManager;
import com.yiyundao.compensation.modules.approval.entity.ApprovalStep;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.service.OrganizationSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalEngineTest {

    @Mock
    private ApprovalStepService approvalStepService;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private PayrollLineMapper payrollLineMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private SysConfigService sysConfigService;
    @Mock
    private ApprovalFlowConfigManager approvalFlowConfigManager;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ExternalIdentityService externalIdentityService;
    @Mock
    private TransactionAfterCommitExecutor afterCommitExecutor;

    @Test
    void processApprovalShouldRejectSelfApprovalEvenWhenInitiatorIsCurrentApprover() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalWorkflow workflow = pendingWorkflow(100L, 100L);
        doReturn(workflow).when(engine).getById(1L);

        assertThatThrownBy(() -> engine.processApproval(1L, 100L, ApprovalStatus.APPROVED, "ok"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("发起人不能审批自己发起的流程");

        verify(approvalStepService, never()).getCurrentStep(any(), any());
        verify(engine, never()).updateById(any(ApprovalWorkflow.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void processApprovalShouldAllowDifferentCurrentApprover() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalWorkflow workflow = pendingWorkflow(100L, 200L);
        workflow.setTotalSteps(2);
        ApprovalStep currentStep = approvalStep(1, 200L);
        ApprovalStep nextStep = approvalStep(2, 300L);
        doReturn(workflow).when(engine).getById(1L);
        when(approvalStepService.getCurrentStep(1L, 1)).thenReturn(currentStep);
        when(approvalStepService.getStepByNo(1L, 2)).thenReturn(nextStep);
        when(approvalStepService.update(any(UpdateWrapper.class))).thenReturn(true);
        doReturn(true).when(engine).update(any(UpdateWrapper.class));

        engine.processApproval(1L, 200L, ApprovalStatus.APPROVED, "ok");

        verify(approvalStepService).update(any(UpdateWrapper.class));
        verify(engine).update(any(UpdateWrapper.class));
        verify(engine, never()).updateById(any(ApprovalWorkflow.class));
    }

    @Test
    void processApprovalShouldRejectInvalidDecision() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalWorkflow workflow = pendingWorkflow(100L, 200L);
        doReturn(workflow).when(engine).getById(1L);

        assertThatThrownBy(() -> engine.processApproval(1L, 200L, ApprovalStatus.CANCELLED, "cancel"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("无效的审批决策: CANCELLED");

        verify(approvalStepService, never()).getCurrentStep(any(), any());
        verify(approvalStepService, never()).update(any(UpdateWrapper.class));
        verify(engine, never()).update(any(UpdateWrapper.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void processApprovalShouldRejectWhenCurrentStepAlreadyHandled() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalWorkflow workflow = pendingWorkflow(100L, 200L);
        ApprovalStep currentStep = approvalStep(1, 200L);
        doReturn(workflow).when(engine).getById(1L);
        when(approvalStepService.getCurrentStep(1L, 1)).thenReturn(currentStep);
        when(approvalStepService.update(any(UpdateWrapper.class))).thenReturn(false);

        assertThatThrownBy(() -> engine.processApproval(1L, 200L, ApprovalStatus.APPROVED, "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("审批步骤状态已变更，请刷新后重试");

        verify(engine, never()).update(any(UpdateWrapper.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void processApprovalShouldRejectIncompleteWorkflowRoutingWithoutNullPointer() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalWorkflow workflow = pendingWorkflow(100L, null);
        doReturn(workflow).when(engine).getById(1L);

        assertThatThrownBy(() -> engine.processApproval(1L, 200L, ApprovalStatus.APPROVED, "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("审批流程数据不完整，请联系管理员处理");

        verify(approvalStepService, never()).getCurrentStep(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void cancelWorkflowShouldRejectMissingInitiatorWithoutNullPointer() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalWorkflow workflow = pendingWorkflow(null, 200L);
        doReturn(workflow).when(engine).getById(1L);

        assertThatThrownBy(() -> engine.cancelWorkflow(1L, 100L, "withdraw"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("审批流程数据不完整，请联系管理员处理");

        verify(approvalStepService, never()).update(any(UpdateWrapper.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void processApprovalShouldRejectWhenWorkflowAlreadyAdvancedBeforeCompletion() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalWorkflow workflow = pendingWorkflow(100L, 200L);
        ApprovalStep currentStep = approvalStep(1, 200L);
        doReturn(workflow).when(engine).getById(1L);
        when(approvalStepService.getCurrentStep(1L, 1)).thenReturn(currentStep);
        when(approvalStepService.update(any(UpdateWrapper.class))).thenReturn(true);
        doReturn(false).when(engine).update(any(UpdateWrapper.class));

        assertThatThrownBy(() -> engine.processApproval(1L, 200L, ApprovalStatus.APPROVED, "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("审批流程状态已变更，请刷新后重试");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void processApprovalShouldScheduleCompletionNotificationAfterCommit() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalWorkflow workflow = pendingWorkflow(100L, 200L);
        ApprovalStep currentStep = approvalStep(1, 200L);
        doReturn(workflow).when(engine).getById(1L);
        when(approvalStepService.getCurrentStep(1L, 1)).thenReturn(currentStep);
        when(approvalStepService.update(any(UpdateWrapper.class))).thenReturn(true);
        doReturn(true).when(engine).update(any(UpdateWrapper.class));

        engine.processApproval(1L, 200L, ApprovalStatus.APPROVED, "ok");

        verify(afterCommitExecutor).execute(any(Runnable.class));
        verify(sysUserService, never()).getById(100L);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void cancelWorkflowShouldPublishOperatorAsFinalApproverId() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalWorkflow workflow = pendingWorkflow(100L, 200L);
        doReturn(workflow).when(engine).getById(1L);
        when(approvalStepService.update(any(UpdateWrapper.class))).thenReturn(true);
        doReturn(true).when(engine).update(any(UpdateWrapper.class));

        engine.cancelWorkflow(1L, 100L, "withdraw");

        assertThat(workflow.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        assertThat(workflow.getCurrentApproverId()).isNull();
        verify(approvalStepService).update(any(UpdateWrapper.class));
        verify(eventPublisher).publishEvent(argThat(event ->
                event instanceof ApprovalCompletedEvent completed
                        && completed.getWorkflowId().equals(1L)
                        && completed.getFinalStatus() == ApprovalStatus.CANCELLED
                        && completed.getFinalApproverId().equals(100L)));
    }

    @Test
    void cancelWorkflowShouldRejectWhenCurrentStepAlreadyHandled() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalWorkflow workflow = pendingWorkflow(100L, 200L);
        doReturn(workflow).when(engine).getById(1L);
        when(approvalStepService.update(any(UpdateWrapper.class))).thenReturn(false);

        assertThatThrownBy(() -> engine.cancelWorkflow(1L, 100L, "withdraw"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("审批步骤状态已变更，请刷新后重试");

        verify(engine, never()).update(any(UpdateWrapper.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void startWorkflowShouldFailWhenRequiredApproverMissing() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalFlowConfigManager.ApprovalStepConfig requiredStep = ApprovalFlowConfigManager.ApprovalStepConfig.builder()
                .stepNo(1)
                .stepName("财务审批")
                .role("ROLE_FINANCE")
                .optional(false)
                .build();
        when(sysConfigService.getString(any(), any())).thenReturn(null);
        when(approvalFlowConfigManager.getDefaultSteps(WorkflowType.PAYROLL_DISTRIBUTION))
                .thenReturn(List.of(requiredStep));
        when(sysUserService.findFirstByRoleExcluding("ROLE_FINANCE", 100L)).thenReturn(null);

        assertThatThrownBy(() -> engine.startWorkflow(
                WorkflowType.PAYROLL_DISTRIBUTION,
                "payroll_distribution:10",
                "payroll_distribution",
                100L,
                Map.of("distributionId", 10L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("启动审批流程失败")
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("未配置有效审批人");

        verify(engine, never()).save(any(ApprovalWorkflow.class));
        verify(approvalStepService, never()).save(any(ApprovalStep.class));
    }

    @Test
    void startWorkflowShouldFailWhenNoApprovalStepsConfigured() {
        ApprovalEngine engine = spy(newEngine());
        when(sysConfigService.getString(any(), any())).thenReturn("[]");

        assertThatThrownBy(() -> engine.startWorkflow(
                WorkflowType.PAYROLL_DISTRIBUTION,
                "payroll_distribution:10",
                "payroll_distribution",
                100L,
                Map.of("distributionId", 10L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("启动审批流程失败")
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("未生成任何有效审批步骤");

        verify(engine, never()).save(any(ApprovalWorkflow.class));
        verify(approvalStepService, never()).save(any(ApprovalStep.class));
    }

    @Test
    void startWorkflowShouldFailWhenAllOptionalStepsAreSkipped() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalFlowConfigManager.ApprovalStepConfig optionalStep = ApprovalFlowConfigManager.ApprovalStepConfig.builder()
                .stepNo(1)
                .stepName("可选审批")
                .role("ROLE_ADMIN")
                .optional(true)
                .build();
        when(sysConfigService.getString(any(), any())).thenReturn(null);
        when(approvalFlowConfigManager.getDefaultSteps(WorkflowType.PERMISSION))
                .thenReturn(List.of(optionalStep));
        when(sysUserService.findFirstByRoleExcluding("ROLE_ADMIN", 100L)).thenReturn(null);

        assertThatThrownBy(() -> engine.startWorkflow(
                WorkflowType.PERMISSION,
                "permission:10",
                "permission",
                100L,
                Map.of("targetUserId", 10L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("启动审批流程失败")
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("未生成任何有效审批步骤");

        verify(engine, never()).save(any(ApprovalWorkflow.class));
        verify(approvalStepService, never()).save(any(ApprovalStep.class));
    }

    @Test
    void startWorkflowShouldFailBeforePersistingWhenOnlyRoleApproverIsInitiator() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalFlowConfigManager.ApprovalStepConfig requiredStep = ApprovalFlowConfigManager.ApprovalStepConfig.builder()
                .stepNo(1)
                .stepName("财务审批")
                .role("ROLE_FINANCE")
                .optional(false)
                .build();
        when(sysConfigService.getString(any(), any())).thenReturn(null);
        when(approvalFlowConfigManager.getDefaultSteps(WorkflowType.PAYROLL_DISTRIBUTION))
                .thenReturn(List.of(requiredStep));
        when(sysUserService.findFirstByRoleExcluding("ROLE_FINANCE", 100L)).thenReturn(null);

        assertThatThrownBy(() -> engine.startWorkflow(
                WorkflowType.PAYROLL_DISTRIBUTION,
                "payroll_distribution:10",
                "payroll_distribution",
                100L,
                Map.of("distributionId", 10L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("启动审批流程失败")
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("未配置有效审批人");

        verify(sysUserService).findFirstByRoleExcluding("ROLE_FINANCE", 100L);
        verify(engine, never()).save(any(ApprovalWorkflow.class));
        verify(approvalStepService, never()).save(any(ApprovalStep.class));
    }

    @Test
    void startWorkflowShouldFailBeforePersistingWhenConfiguredApproverIsInactive() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalFlowConfigManager.ApprovalStepConfig requiredStep = ApprovalFlowConfigManager.ApprovalStepConfig.builder()
                .stepNo(1)
                .stepName("指定审批")
                .approverId(200L)
                .optional(false)
                .build();
        SysUser inactiveApprover = user(200L, "disabled-finance", UserStatus.INACTIVE);
        when(sysConfigService.getString(any(), any())).thenReturn(null);
        when(approvalFlowConfigManager.getDefaultSteps(WorkflowType.PERMISSION))
                .thenReturn(List.of(requiredStep));
        when(sysUserService.getById(200L)).thenReturn(inactiveApprover);

        assertThatThrownBy(() -> engine.startWorkflow(
                WorkflowType.PERMISSION,
                "permission:10",
                "permission",
                100L,
                Map.of("targetUserId", 10L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("启动审批流程失败")
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("未配置有效审批人");

        verify(engine, never()).save(any(ApprovalWorkflow.class));
        verify(approvalStepService, never()).save(any(ApprovalStep.class));
    }

    @Test
    void startWorkflowShouldRenumberStepsAfterOptionalStepSkipped() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalFlowConfigManager.ApprovalStepConfig optionalStep = ApprovalFlowConfigManager.ApprovalStepConfig.builder()
                .stepNo(1)
                .stepName("可选审批")
                .role("ROLE_MANAGER")
                .optional(true)
                .build();
        ApprovalFlowConfigManager.ApprovalStepConfig requiredStep = ApprovalFlowConfigManager.ApprovalStepConfig.builder()
                .stepNo(2)
                .stepName("财务审批")
                .role("ROLE_FINANCE")
                .optional(false)
                .build();
        SysUser finance = user(200L, "finance", UserStatus.ACTIVE);
        when(sysConfigService.getString(any(), any())).thenReturn(null);
        when(approvalFlowConfigManager.getDefaultSteps(WorkflowType.PAYROLL_DISTRIBUTION))
                .thenReturn(List.of(optionalStep, requiredStep));
        when(sysUserService.findFirstByRoleExcluding("ROLE_MANAGER", 100L)).thenReturn(null);
        when(sysUserService.findFirstByRoleExcluding("ROLE_FINANCE", 100L)).thenReturn(finance);
        doAnswer(invocation -> {
            ApprovalWorkflow workflow = invocation.getArgument(0);
            workflow.setId(9001L);
            return true;
        }).when(engine).save(any(ApprovalWorkflow.class));
        when(approvalStepService.save(any(ApprovalStep.class))).thenReturn(true);

        engine.startWorkflow(
                WorkflowType.PAYROLL_DISTRIBUTION,
                "payroll_distribution:10",
                "payroll_distribution",
                100L,
                Map.of("distributionId", 10L));

        verify(approvalStepService).save(argThat(step ->
                step.getStepNo().equals(1)
                        && step.getApproverId().equals(200L)
                        && step.getWorkflowId() != null));
    }

    @Test
    void startWorkflowShouldRenumberNonContiguousConfiguredSteps() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalFlowConfigManager.ApprovalStepConfig requiredStep = ApprovalFlowConfigManager.ApprovalStepConfig.builder()
                .stepNo(3)
                .stepName("终审")
                .role("ROLE_FINANCE")
                .optional(false)
                .build();
        SysUser finance = user(200L, "finance", UserStatus.ACTIVE);
        when(sysConfigService.getString(any(), any())).thenReturn(null);
        when(approvalFlowConfigManager.getDefaultSteps(WorkflowType.PAYROLL_DISTRIBUTION))
                .thenReturn(List.of(requiredStep));
        when(sysUserService.findFirstByRoleExcluding("ROLE_FINANCE", 100L)).thenReturn(finance);
        doAnswer(invocation -> {
            ApprovalWorkflow workflow = invocation.getArgument(0);
            workflow.setId(9001L);
            return true;
        }).when(engine).save(any(ApprovalWorkflow.class));
        when(approvalStepService.save(any(ApprovalStep.class))).thenReturn(true);

        engine.startWorkflow(
                WorkflowType.PAYROLL_DISTRIBUTION,
                "payroll_distribution:10",
                "payroll_distribution",
                100L,
                Map.of("distributionId", 10L));

        verify(approvalStepService).save(argThat(step ->
                step.getStepNo().equals(1)
                        && step.getStepName().equals("终审")
                        && step.getApproverId().equals(200L)));
    }

    @Test
    void startWorkflowShouldExtractEmployeeIdFromProfileChangeBusinessKey() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalFlowConfigManager.ApprovalStepConfig requiredStep = ApprovalFlowConfigManager.ApprovalStepConfig.builder()
                .stepNo(1)
                .stepName("资料审批")
                .role("ROLE_MANAGER")
                .optional(false)
                .build();
        SysUser manager = user(200L, "manager", UserStatus.ACTIVE);
        when(sysConfigService.getString(any(), any())).thenReturn(null);
        when(approvalFlowConfigManager.getDefaultSteps(WorkflowType.OFFLINE))
                .thenReturn(List.of(requiredStep));
        when(sysUserService.findFirstByRoleExcluding("ROLE_MANAGER", 100L)).thenReturn(manager);
        doAnswer(invocation -> {
            ApprovalWorkflow workflow = invocation.getArgument(0);
            workflow.setId(9001L);
            return true;
        }).when(engine).save(any(ApprovalWorkflow.class));
        when(approvalStepService.save(any(ApprovalStep.class))).thenReturn(true);

        engine.startWorkflow(
                WorkflowType.OFFLINE,
                "EMPLOYEE-PROFILE-2002-1780600000000",
                "EMPLOYEE_PROFILE_CHANGE",
                100L,
                Map.of());

        verify(engine).save(argThat(workflow ->
                workflow.getEmployeeId() != null && workflow.getEmployeeId().equals(2002L)));
    }

    @Test
    void payrollDistributionShouldResolveAllBatchManagersFromEmployeeRelations() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalFlowConfigManager.ApprovalStepConfig managerStep = ApprovalFlowConfigManager.ApprovalStepConfig.builder()
                .stepNo(1)
                .stepName("部门负责人审批")
                .role("ROLE_MANAGER")
                .approverType("EMPLOYEE_MANAGER")
                .optional(false)
                .build();
        when(sysConfigService.getString(any(), any())).thenReturn(null);
        when(approvalFlowConfigManager.getDefaultSteps(WorkflowType.PAYROLL_DISTRIBUTION))
                .thenReturn(List.of(managerStep));
        when(payrollLineMapper.selectList(any())).thenReturn(List.of(
                payrollLine(11L), payrollLine(12L), payrollLine(13L)));
        when(employeeMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(
                        employee(11L, 101L), employee(12L, 102L), employee(13L, 101L)))
                .thenReturn(List.of(employee(101L, null), employee(102L, null)));
        when(sysUserService.findByEmployeeId(101L)).thenReturn(user(201L, "manager-a", UserStatus.ACTIVE));
        when(sysUserService.findByEmployeeId(102L)).thenReturn(user(202L, "manager-b", UserStatus.ACTIVE));
        doAnswer(invocation -> {
            ApprovalWorkflow workflow = invocation.getArgument(0);
            workflow.setId(9002L);
            return true;
        }).when(engine).save(any(ApprovalWorkflow.class));
        when(approvalStepService.save(any(ApprovalStep.class))).thenReturn(true);

        engine.startWorkflow(
                WorkflowType.PAYROLL_DISTRIBUTION,
                "payroll_distribution:10",
                "payroll_distribution",
                100L,
                Map.of("batchId", 42L, "distributionId", 10L));

        var captor = org.mockito.ArgumentCaptor.forClass(ApprovalStep.class);
        verify(approvalStepService, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ApprovalStep::getApproverId)
                .containsExactly(201L, 202L);
        verify(sysUserService, never()).findFirstByRoleExcluding(any(), any());
    }

    @Test
    void payrollDistributionShouldFailWhenEmployeeHasNoManager() {
        ApprovalEngine engine = spy(newEngine());
        ApprovalFlowConfigManager.ApprovalStepConfig managerStep = ApprovalFlowConfigManager.ApprovalStepConfig.builder()
                .stepNo(1)
                .stepName("部门负责人审批")
                .role("ROLE_MANAGER")
                .approverType("EMPLOYEE_MANAGER")
                .optional(false)
                .build();
        when(sysConfigService.getString(any(), any())).thenReturn(null);
        when(approvalFlowConfigManager.getDefaultSteps(WorkflowType.PAYROLL_DISTRIBUTION))
                .thenReturn(List.of(managerStep));
        when(payrollLineMapper.selectList(any())).thenReturn(List.of(payrollLine(11L)));
        when(employeeMapper.selectBatchIds(anyCollection())).thenReturn(List.of(employee(11L, null)));

        assertThatThrownBy(() -> engine.startWorkflow(
                WorkflowType.PAYROLL_DISTRIBUTION,
                "payroll_distribution:10",
                "payroll_distribution",
                100L,
                Map.of("batchId", 42L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("启动审批流程失败")
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("未配置直属负责人");

        verify(engine, never()).save(any(ApprovalWorkflow.class));
        verify(approvalStepService, never()).save(any(ApprovalStep.class));
    }

    private ApprovalEngine newEngine() {
        return new ApprovalEngine(
                approvalStepService,
                sysUserService,
                payrollLineMapper,
                employeeMapper,
                emptyProvider(),
                new ObjectMapper(),
                sysConfigService,
                approvalFlowConfigManager,
                eventPublisher,
                externalIdentityService,
                afterCommitExecutor
        );
    }

    private ApprovalWorkflow pendingWorkflow(Long initiatorId, Long approverId) {
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(1L);
        workflow.setWorkflowName("测试审批");
        workflow.setWorkflowType(WorkflowType.PAYROLL_DISTRIBUTION);
        workflow.setBusinessKey("payroll_distribution:1");
        workflow.setBusinessType("payroll_distribution");
        workflow.setCurrentStep(1);
        workflow.setTotalSteps(1);
        workflow.setStatus(ApprovalStatus.PENDING);
        workflow.setInitiatorId(initiatorId);
        workflow.setCurrentApproverId(approverId);
        workflow.setSubmitTime(LocalDateTime.now());
        return workflow;
    }

    private ApprovalStep approvalStep(Integer stepNo, Long approverId) {
        ApprovalStep step = new ApprovalStep();
        step.setWorkflowId(1L);
        step.setStepNo(stepNo);
        step.setApproverId(approverId);
        step.setApproverName("approver-" + approverId);
        step.setStatus(ApprovalStatus.PENDING);
        return step;
    }

    private SysUser user(Long id, String username, UserStatus status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setRealName(username);
        user.setStatus(status);
        return user;
    }

    private PayrollLine payrollLine(Long employeeId) {
        PayrollLine line = new PayrollLine();
        line.setEmployeeId(employeeId);
        line.setBatchId(42L);
        return line;
    }

    private Employee employee(Long id, Long managerId) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setManagerId(managerId);
        employee.setStatus(EmployeeStatus.ACTIVE.getCode());
        return employee;
    }

    private ObjectProvider<OrganizationSyncService> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public OrganizationSyncService getObject(Object... args) {
                return null;
            }

            @Override
            public OrganizationSyncService getIfAvailable() {
                return null;
            }

            @Override
            public OrganizationSyncService getIfUnique() {
                return null;
            }

            @Override
            public OrganizationSyncService getObject() {
                return null;
            }
        };
    }
}
