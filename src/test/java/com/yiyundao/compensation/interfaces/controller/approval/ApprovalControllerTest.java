package com.yiyundao.compensation.interfaces.controller.approval;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.interfaces.vo.approval.ApprovalStepVO;
import com.yiyundao.compensation.interfaces.vo.approval.ApprovalWorkflowDetailVO;
import com.yiyundao.compensation.interfaces.vo.approval.ApprovalWorkflowVO;
import com.yiyundao.compensation.modules.approval.entity.ApprovalStep;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.approval.service.ApprovalStepService;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.DatabasePermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalControllerTest {

    @Mock
    private ApprovalEngine approvalEngine;
    @Mock
    private ApprovalStepService approvalStepService;
    @Mock
    private ApprovalWorkflowMapper approvalWorkflowMapper;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private DatabasePermissionService databasePermissionService;

    private ApprovalController controller;
    private SysUser manager;

    @BeforeEach
    void setUp() {
        controller = new ApprovalController(
                approvalEngine,
                approvalStepService,
                approvalWorkflowMapper,
                sysUserService,
                auditLogService,
                databasePermissionService
        );
        manager = user(20L, "manager");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", "n/a")
        );
        lenient().when(sysUserService.findByUsername("manager")).thenReturn(manager);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getDetailShouldAllowCurrentApproverToReadWorkflow() {
        ApprovalWorkflow workflow = pendingWorkflow(1001L, 10L, 20L);
        when(approvalEngine.getById(1001L)).thenReturn(workflow);
        when(approvalStepService.listByWorkflow(1001L)).thenReturn(List.of(step(1001L, 1, 20L)));

        ApiResponse<ApprovalWorkflowDetailVO> response = controller.getDetail(1001L);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getId()).isEqualTo(1001L);
        assertThat(response.getData().getSteps()).hasSize(1);
    }

    @Test
    void getDetailShouldExposePayrollDistributionBusinessContext() {
        ApprovalWorkflow workflow = pendingWorkflow(1008L, 10L, 20L);
        workflow.setBusinessType("payroll_distribution");
        workflow.setBusinessKey("payroll_distribution:55:retry:1001");
        when(approvalEngine.getById(1008L)).thenReturn(workflow);
        when(approvalStepService.listByWorkflow(1008L)).thenReturn(List.of(step(1008L, 1, 20L)));

        ApiResponse<ApprovalWorkflowDetailVO> response = controller.getDetail(1008L);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getBusinessInfo())
                .containsEntry("businessType", "payroll_distribution")
                .containsEntry("distributionId", "55");
    }

    @Test
    void getPendingShouldPopulateWorkflowUserNames() {
        ApprovalWorkflow workflow = pendingWorkflow(1007L, 10L, 20L);
        SysUser initiator = user(10L, "initiator");
        initiator.setRealName("发起人");
        manager.setRealName("审批人");
        when(approvalEngine.getPendingWorkflows(20L)).thenReturn(List.of(workflow));
        when(sysUserService.getById(10L)).thenReturn(initiator);
        when(sysUserService.getById(20L)).thenReturn(manager);

        ApiResponse<List<ApprovalWorkflowVO>> response = controller.getPending();

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getInitiatorName()).isEqualTo("发起人");
        assertThat(response.getData().get(0).getCurrentApproverName()).isEqualTo("审批人");
    }

    @Test
    void getStepsShouldAllowHistoricalStepApproverToReadWorkflow() {
        ApprovalWorkflow workflow = pendingWorkflow(1002L, 10L, 30L);
        when(approvalEngine.getById(1002L)).thenReturn(workflow);
        when(approvalStepService.listByWorkflow(1002L))
                .thenReturn(List.of(step(1002L, 1, 20L), step(1002L, 2, 30L)));

        ApiResponse<List<ApprovalStepVO>> response = controller.getSteps(1002L);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).hasSize(2);
    }

    @Test
    void getDetailShouldRejectUnrelatedManager() {
        ApprovalWorkflow workflow = pendingWorkflow(1003L, 10L, 30L);
        when(approvalEngine.getById(1003L)).thenReturn(workflow);
        when(approvalStepService.listByWorkflow(1003L)).thenReturn(List.of(step(1003L, 1, 30L)));

        ApiResponse<ApprovalWorkflowDetailVO> response = controller.getDetail(1003L);

        assertThat(response.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
        assertThat(response.getData()).isNull();
    }

    @Test
    void getDetailShouldAllowFinanceToReadAnyWorkflow() {
        SysUser finance = user(40L, "finance");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance", "n/a")
        );
        when(sysUserService.findByUsername("finance")).thenReturn(finance);
        when(databasePermissionService.hasCurrentRequestScope(40L, "ALL")).thenReturn(true);
        ApprovalWorkflow workflow = pendingWorkflow(1004L, 10L, 30L);
        when(approvalEngine.getById(1004L)).thenReturn(workflow);
        when(approvalStepService.listByWorkflow(1004L)).thenReturn(List.of(step(1004L, 1, 30L)));

        ApiResponse<ApprovalWorkflowDetailVO> response = controller.getDetail(1004L);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getId()).isEqualTo(1004L);
        verify(databasePermissionService).hasCurrentRequestScope(40L, "ALL");
    }

    @Test
    void getStepsShouldReturnNotFoundWithoutLoadingStepsWhenWorkflowMissing() {
        when(approvalEngine.getById(9999L)).thenReturn(null);

        ApiResponse<List<ApprovalStepVO>> response = controller.getSteps(9999L);

        assertThat(response.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
        verify(approvalStepService, never()).listByWorkflow(9999L);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listShouldSearchWorkflowIdWhenKeywordIsNumeric() {
        SysUser finance = user(40L, "finance");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance", "n/a")
        );
        when(sysUserService.findByUsername("finance")).thenReturn(finance);
        when(databasePermissionService.hasCurrentRequestScope(40L, "ALL")).thenReturn(true);
        ApprovalWorkflow workflow = pendingWorkflow(7001L, 10L, 30L);
        Page<ApprovalWorkflow> result = new Page<>(1, 10);
        result.setRecords(List.of(workflow));
        result.setTotal(1);
        when(approvalWorkflowMapper.selectPage(any(IPage.class), any(QueryWrapper.class))).thenReturn(result);

        ApiResponse<PageResponse<?>> response = (ApiResponse) controller.list(
                1,
                10,
                null,
                null,
                "7001",
                null,
                null,
                "submitTime",
                "desc"
        );

        ArgumentCaptor<QueryWrapper<ApprovalWorkflow>> queryCaptor =
                ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(approvalWorkflowMapper).selectPage(any(IPage.class), queryCaptor.capture());
        assertThat(response.getCode()).isZero();
        assertThat(queryCaptor.getValue().getSqlSegment())
                .contains("workflow_name LIKE")
                .contains("business_key LIKE")
                .contains("id =");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values()).contains(7001L);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listShouldRestrictManagerToReadableWorkflows() {
        Page<ApprovalWorkflow> result = new Page<>(1, 10);
        result.setRecords(List.of());
        when(approvalWorkflowMapper.selectPage(any(IPage.class), any(QueryWrapper.class))).thenReturn(result);

        ApiResponse<PageResponse<?>> response = (ApiResponse) controller.list(
                1,
                10,
                null,
                null,
                null,
                null,
                null,
                "submitTime",
                "desc"
        );

        ArgumentCaptor<QueryWrapper<ApprovalWorkflow>> queryCaptor =
                ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(approvalWorkflowMapper).selectPage(any(IPage.class), queryCaptor.capture());
        assertThat(response.getCode()).isZero();
        assertThat(queryCaptor.getValue().getSqlSegment())
                .contains("initiator_id =")
                .contains("current_approver_id =")
                .contains("SELECT workflow_id FROM approval_step WHERE approver_id = 20 AND deleted = 0");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listShouldClampPageAndSize() {
        Page<ApprovalWorkflow> result = new Page<>(1, 200);
        result.setRecords(List.of());
        when(approvalWorkflowMapper.selectPage(any(IPage.class), any(QueryWrapper.class))).thenReturn(result);

        ApiResponse<PageResponse<?>> response = (ApiResponse) controller.list(
                -1,
                1000,
                null,
                null,
                null,
                null,
                null,
                "submitTime",
                "desc"
        );

        ArgumentCaptor<IPage<ApprovalWorkflow>> pageCaptor = ArgumentCaptor.forClass((Class) IPage.class);
        verify(approvalWorkflowMapper).selectPage(pageCaptor.capture(), any(QueryWrapper.class));
        assertThat(response.getCode()).isZero();
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getMyShouldClampPageAndSize() {
        Page<ApprovalWorkflow> result = new Page<>(1, 200);
        result.setRecords(List.of());
        when(approvalWorkflowMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class))).thenReturn(result);

        ApiResponse<PageResponse<ApprovalWorkflowVO>> response = controller.getMy(-1, 1000, null);

        ArgumentCaptor<IPage<ApprovalWorkflow>> pageCaptor = ArgumentCaptor.forClass((Class) IPage.class);
        verify(approvalWorkflowMapper).selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class));
        assertThat(response.getCode()).isZero();
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200);
    }

    @Test
    void listShouldRejectInvalidFilters() {
        assertThatThrownBy(() -> controller.list(
                1,
                10,
                "unknown",
                null,
                null,
                null,
                null,
                "submitTime",
                "desc"
        )).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
            assertThat(ex.getMessage()).contains("无效的审批状态");
        });

        assertThatThrownBy(() -> controller.list(
                1,
                10,
                null,
                "unknown",
                null,
                null,
                null,
                "submitTime",
                "desc"
        )).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
            assertThat(ex.getMessage()).contains("无效的流程类型");
        });

        assertThatThrownBy(() -> controller.list(
                1,
                10,
                null,
                null,
                null,
                "2026-99-01",
                null,
                "submitTime",
                "desc"
        )).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
            assertThat(ex.getMessage()).contains("无效的开始日期");
        });

        verify(approvalWorkflowMapper, never()).selectPage(any(), any());
    }

    @Test
    void getMyShouldRejectInvalidStatusFilter() {
        assertThatThrownBy(() -> controller.getMy(1, 10, "unknown"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(ex.getMessage()).contains("无效的审批状态");
                });

        verify(approvalWorkflowMapper, never()).selectPage(any(), any());
    }

    @Test
    void cancelShouldUseCurrentUserAsOperator() {
        ApprovalWorkflow workflow = pendingWorkflow(1005L, 20L, 30L);
        workflow.setStatus(ApprovalStatus.CANCELLED);
        workflow.setCurrentApproverId(null);
        when(approvalEngine.getById(1005L)).thenReturn(workflow);

        ApprovalController.DecisionRequest request = new ApprovalController.DecisionRequest();
        request.setComment("不再需要");
        ApiResponse<Boolean> response = controller.cancel(1005L, request);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).isTrue();
        verify(approvalEngine).cancelWorkflow(1005L, 20L, "不再需要");
        verify(auditLogService).record(
                eq("审批撤销"),
                eq("POST"),
                eq("/approval/workflows/1005/decide"),
                eq(null),
                eq(null),
                eq("APPROVAL"),
                eq("1005"),
                eq("manager"),
                contains("status=CANCELLED"),
                eq("OK"),
                eq(null),
                any(Long.class)
        );
    }

    @Test
    void cancelShouldReturnForbiddenWhenEngineRejectsOperator() {
        ApprovalController.DecisionRequest request = new ApprovalController.DecisionRequest();
        request.setComment("不再需要");
        org.mockito.Mockito.doThrow(new IllegalArgumentException("只有发起人可以撤销流程"))
                .when(approvalEngine)
                .cancelWorkflow(1006L, 20L, "不再需要");

        ApiResponse<Boolean> response = controller.cancel(1006L, request);

        assertThat(response.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
        assertThat(response.getMessage()).isEqualTo("只有发起人可以撤销流程");
        verify(approvalEngine).cancelWorkflow(1006L, 20L, "不再需要");
        verify(approvalEngine, never()).getById(1006L);
        verify(auditLogService).record(
                eq("审批撤销"),
                eq("POST"),
                eq("/approval/workflows/1006/decide"),
                eq(null),
                eq(null),
                eq("APPROVAL"),
                eq("1006"),
                eq("manager"),
                eq("只有发起人可以撤销流程"),
                eq("FAILED"),
                eq("只有发起人可以撤销流程"),
                any(Long.class)
        );
        verifyNoMoreInteractions(approvalStepService);
    }

    @Test
    void approveShouldReturnNotFoundWhenWorkflowMissing() {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("审批流程不存在: 404"))
                .when(approvalEngine)
                .processApproval(404L, 20L, ApprovalStatus.APPROVED, null);

        ApiResponse<Boolean> response = controller.approve(404L, null);

        assertThat(response.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
        assertThat(response.getMessage()).isEqualTo("审批流程不存在: 404");
        verify(approvalEngine).processApproval(404L, 20L, ApprovalStatus.APPROVED, null);
        verify(approvalEngine, never()).getById(404L);
    }

    @Test
    void approveShouldReturnConflictWhenWorkflowStateChangedConcurrently() {
        ApprovalController.DecisionRequest request = new ApprovalController.DecisionRequest();
        request.setComment("同意");
        org.mockito.Mockito.doThrow(new IllegalStateException("审批流程状态已变更，请刷新后重试"))
                .when(approvalEngine)
                .processApproval(1007L, 20L, ApprovalStatus.APPROVED, "同意");

        ApiResponse<Boolean> response = controller.approve(1007L, request);

        assertThat(response.getCode()).isEqualTo(ErrorCode.REQUEST_CONFLICT.getCode());
        assertThat(response.getMessage()).isEqualTo("审批流程状态已变更，请刷新后重试");
        verify(approvalEngine).processApproval(1007L, 20L, ApprovalStatus.APPROVED, "同意");
        verify(approvalEngine, never()).getById(1007L);
    }

    private static ApprovalWorkflow pendingWorkflow(Long id, Long initiatorId, Long currentApproverId) {
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(id);
        workflow.setWorkflowName("审批-" + id);
        workflow.setWorkflowType(WorkflowType.BATCH);
        workflow.setBusinessKey("batch:" + id);
        workflow.setBusinessType("payroll");
        workflow.setCurrentStep(1);
        workflow.setTotalSteps(2);
        workflow.setStatus(ApprovalStatus.PENDING);
        workflow.setInitiatorId(initiatorId);
        workflow.setCurrentApproverId(currentApproverId);
        workflow.setSubmitTime(LocalDateTime.now());
        return workflow;
    }

    private static ApprovalStep step(Long workflowId, Integer stepNo, Long approverId) {
        ApprovalStep step = new ApprovalStep();
        step.setId(workflowId * 10 + stepNo);
        step.setWorkflowId(workflowId);
        step.setStepNo(stepNo);
        step.setStepName("步骤" + stepNo);
        step.setApproverId(approverId);
        step.setApproverName("approver-" + approverId);
        step.setStatus(ApprovalStatus.PENDING);
        return step;
    }

    private static SysUser user(Long id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setRealName(username);
        return user;
    }
}
