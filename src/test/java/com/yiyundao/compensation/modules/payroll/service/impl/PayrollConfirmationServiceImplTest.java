package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollConfirmationStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchConfirmRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollConfirmationAssignRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPendingConfirmationDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayslipConfirmRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayslipObjectionRequest;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollConfirmationAggregateService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.PayrollProcessManager;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityConstants;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

class PayrollConfirmationServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, PayrollLine.class.getName());
        assistant.setCurrentNamespace(PayrollLine.class.getName());
        TableInfoHelper.initTableInfo(assistant, PayrollLine.class);
    }

    @Test
    void refreshBatchConfirmationStatus_shouldMarkBatchConfirmed_whenAllLinesFinal() {
        PayrollLineService lineService = Mockito.mock(PayrollLineService.class);
        PayrollBatchService batchService = Mockito.mock(PayrollBatchService.class);
        ApprovalEngine approvalEngine = Mockito.mock(ApprovalEngine.class);
        SysUserService sysUserService = Mockito.mock(SysUserService.class);
        EmployeeService employeeService = Mockito.mock(EmployeeService.class);
        UserRoleService userRoleService = Mockito.mock(UserRoleService.class);
        PayrollConfirmationAggregateService confirmationAggregateService = Mockito.mock(PayrollConfirmationAggregateService.class);
        PayrollProcessManager payrollProcessManager = Mockito.mock(PayrollProcessManager.class);

        PayrollConfirmationServiceImpl service = new PayrollConfirmationServiceImpl(
                lineService,
                batchService,
                approvalEngine,
                sysUserService,
                employeeService,
                userRoleService,
                new ObjectMapper(),
                confirmationAggregateService,
                payrollProcessManager
        );

        PayrollBatch batch = new PayrollBatch();
        batch.setId(100L);
        batch.setBatchRevision(1);
        batch.setConfirmationRequired(Boolean.TRUE);
        batch.setStatus(PayrollBatchStatus.CONFIRMING);

        PayrollLine line1 = new PayrollLine();
        line1.setBatchId(100L);
        line1.setConfirmationStatus(PayrollConfirmationStatus.CONFIRMED.getCode());
        PayrollLine line2 = new PayrollLine();
        line2.setBatchId(100L);
        line2.setConfirmationStatus(PayrollConfirmationStatus.OBJECTED_APPROVED.getCode());

        Mockito.when(batchService.getById(100L)).thenReturn(batch);
        Mockito.when(lineService.list(Mockito.any(LambdaQueryWrapper.class))).thenReturn(List.of(line1, line2));

        service.refreshBatchConfirmationStatus(100L);

        Assertions.assertEquals(PayrollBatchStatus.CONFIRMED, batch.getStatus());
        Assertions.assertNotNull(batch.getConfirmationCompletedTime());
        Mockito.verify(batchService).updateById(batch);
        Mockito.verify(confirmationAggregateService).syncFromLegacyBatch(100L, 1);
        Mockito.verify(payrollProcessManager).onConfirmationCompleted(100L, 1);
    }

    @Test
    void refreshBatchConfirmationStatus_shouldMarkSkippedConfirmationBatchConfirmed() {
        TestContext ctx = newTestContext();
        PayrollBatch batch = new PayrollBatch();
        batch.setId(110L);
        batch.setBatchRevision(1);
        batch.setConfirmationRequired(Boolean.FALSE);
        batch.setStatus(PayrollBatchStatus.LOCKED);

        Mockito.when(ctx.batchService.getById(110L)).thenReturn(batch);

        ctx.service.refreshBatchConfirmationStatus(110L);

        Assertions.assertEquals(PayrollBatchStatus.CONFIRMED, batch.getStatus());
        Assertions.assertNotNull(batch.getConfirmationCompletedTime());
        Mockito.verify(ctx.batchService).updateById(batch);
        Mockito.verify(ctx.confirmationAggregateService).syncFromLegacyBatch(110L, 1);
        Mockito.verify(ctx.payrollProcessManager).onConfirmationCompleted(110L, 1);
    }

    @Test
    void refreshBatchConfirmationStatus_shouldNotMoveSkippedConfirmationBatchBackAfterApprovalSubmitted() {
        TestContext ctx = newTestContext();
        PayrollBatch batch = new PayrollBatch();
        batch.setId(111L);
        batch.setBatchRevision(1);
        batch.setConfirmationRequired(Boolean.FALSE);
        batch.setStatus(PayrollBatchStatus.SUBMITTED);

        Mockito.when(ctx.batchService.getById(111L)).thenReturn(batch);

        ctx.service.refreshBatchConfirmationStatus(111L);

        Assertions.assertEquals(PayrollBatchStatus.SUBMITTED, batch.getStatus());
        Mockito.verify(ctx.batchService, Mockito.never()).updateById(Mockito.any(PayrollBatch.class));
        Mockito.verify(ctx.confirmationAggregateService).syncFromLegacyBatch(111L, 1);
        Mockito.verify(ctx.payrollProcessManager, Mockito.never()).onConfirmationCompleted(111L, 1);
    }

    @Test
    void refreshBatchConfirmationStatus_shouldNotMoveSubmittedBatchBackToConfirmed() {
        TestContext ctx = newTestContext();
        PayrollBatch batch = new PayrollBatch();
        batch.setId(112L);
        batch.setBatchRevision(2);
        batch.setConfirmationRequired(Boolean.TRUE);
        batch.setStatus(PayrollBatchStatus.SUBMITTED);

        PayrollLine line = new PayrollLine();
        line.setBatchId(112L);
        line.setConfirmationStatus(PayrollConfirmationStatus.CONFIRMED.getCode());

        Mockito.when(ctx.batchService.getById(112L)).thenReturn(batch);
        Mockito.when(ctx.lineService.list(Mockito.any(LambdaQueryWrapper.class))).thenReturn(List.of(line));

        ctx.service.refreshBatchConfirmationStatus(112L);

        Assertions.assertEquals(PayrollBatchStatus.SUBMITTED, batch.getStatus());
        Mockito.verify(ctx.batchService, Mockito.never()).updateById(Mockito.any(PayrollBatch.class));
        Mockito.verify(ctx.lineService, Mockito.never()).list(Mockito.any(LambdaQueryWrapper.class));
        Mockito.verify(ctx.confirmationAggregateService).syncFromLegacyBatch(112L, 2);
        Mockito.verify(ctx.payrollProcessManager, Mockito.never()).onConfirmationCompleted(112L, 2);
    }

    @Test
    void objectPayslip_shouldStartWorkflowAndUpdateLine() {
        PayrollLineService lineService = Mockito.mock(PayrollLineService.class);
        PayrollBatchService batchService = Mockito.mock(PayrollBatchService.class);
        ApprovalEngine approvalEngine = Mockito.mock(ApprovalEngine.class);
        SysUserService sysUserService = Mockito.mock(SysUserService.class);
        EmployeeService employeeService = Mockito.mock(EmployeeService.class);
        UserRoleService userRoleService = Mockito.mock(UserRoleService.class);
        PayrollConfirmationAggregateService confirmationAggregateService = Mockito.mock(PayrollConfirmationAggregateService.class);
        PayrollProcessManager payrollProcessManager = Mockito.mock(PayrollProcessManager.class);

        PayrollConfirmationServiceImpl service = Mockito.spy(new PayrollConfirmationServiceImpl(
                lineService,
                batchService,
                approvalEngine,
                sysUserService,
                employeeService,
                userRoleService,
                new ObjectMapper(),
                confirmationAggregateService,
                payrollProcessManager
        ));
        Mockito.doNothing().when(service).refreshBatchConfirmationStatus(200L);

        SysUser operator = new SysUser();
        operator.setId(11L);
        operator.setEmployeeId(21L);
        operator.setUsername("u1");

        PayrollBatch batch = new PayrollBatch();
        batch.setId(200L);
        batch.setBatchRevision(3);
        batch.setConfirmationRequired(Boolean.TRUE);
        batch.setStatus(PayrollBatchStatus.CONFIRMING);

        PayrollLine line = new PayrollLine();
        line.setId(300L);
        line.setBatchId(200L);
        line.setBatchRevision(3);
        line.setEmployeeId(21L);
        line.setConfirmationAssigneeEmployeeId(21L);
        line.setConfirmationStatus(PayrollConfirmationStatus.PENDING.getCode());

        PayslipObjectionRequest request = new PayslipObjectionRequest();
        request.setReason("金额有误");
        request.setComment("请核对加班费");

        Mockito.when(lineService.getById(300L)).thenReturn(line);
        Mockito.when(batchService.getById(200L)).thenReturn(batch);
        Mockito.when(approvalEngine.startWorkflow(Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyMap()))
                .thenReturn(999L);
        Mockito.when(lineService.update(Mockito.any(LambdaUpdateWrapper.class))).thenReturn(true);

        Long workflowId = service.objectPayslip(300L, operator, request);

        Assertions.assertEquals(999L, workflowId);
        Assertions.assertEquals(PayrollConfirmationStatus.OBJECTED.getCode(), line.getConfirmationStatus());
        Assertions.assertEquals("金额有误", line.getObjectionReason());
        Assertions.assertEquals(999L, line.getDisputeWorkflowId());
        Mockito.verify(lineService).update(Mockito.argThat((LambdaUpdateWrapper<PayrollLine> wrapper) -> {
            wrapper.getSqlSegment();
            return wrapper.getParamNameValuePairs().values().contains(999L)
                    && wrapper.getParamNameValuePairs().values()
                    .contains(PayrollConfirmationStatus.OBJECTED.getCode());
        }));
        Mockito.verify(approvalEngine).startWorkflow(
                Mockito.eq(WorkflowType.PAYROLL_DISPUTE),
                Mockito.argThat(key -> key != null && key.matches("payroll_dispute:line:300-\\d+")),
                Mockito.eq("payroll_dispute"),
                Mockito.eq(11L),
                Mockito.argThat(data -> Integer.valueOf(3).equals(data.get("batchRevision")))
        );
    }

    @Test
    void confirmPayslip_shouldRejectHrWhenNotAssignee() {
        TestContext ctx = newTestContext();
        SysUser hr = user(31L, 301L, "hr1");
        PayrollBatch batch = confirmableBatch(200L);
        PayrollLine line = pendingLine(300L, 200L, 401L, 401L);
        PayslipConfirmRequest request = new PayslipConfirmRequest();
        request.setSignature("HR");

        Mockito.when(ctx.lineService.getById(300L)).thenReturn(line);
        Mockito.when(ctx.batchService.getById(200L)).thenReturn(batch);
        Mockito.when(ctx.userRoleService.hasRole(31L, SecurityConstants.ROLE_HR)).thenReturn(true);

        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> ctx.service.confirmPayslip(300L, hr, request));

        Assertions.assertEquals("仅确认负责人可操作该工资条", exception.getMessage());
        Mockito.verify(ctx.lineService, Mockito.never()).updateById(Mockito.any(PayrollLine.class));
    }

    @Test
    void confirmPayslip_shouldRejectBatchOutsideConfirmationWindow() {
        TestContext ctx = newTestContext();
        SysUser operator = user(36L, 401L, "employee4");
        PayrollBatch batch = confirmableBatch(205L);
        batch.setStatus(PayrollBatchStatus.LOCKED);
        PayrollLine line = pendingLine(305L, 205L, 401L, 401L);

        Mockito.when(ctx.lineService.getById(305L)).thenReturn(line);
        Mockito.when(ctx.batchService.getById(205L)).thenReturn(batch);

        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> ctx.service.confirmPayslip(305L, operator, new PayslipConfirmRequest()));

        Assertions.assertEquals(ErrorCode.INVALID_STATUS, exception.getErrorCode());
        Assertions.assertEquals("当前批次不在员工确认阶段，不能确认或提异议", exception.getMessage());
        Mockito.verify(ctx.lineService, Mockito.never()).update(Mockito.any(LambdaUpdateWrapper.class));
        Mockito.verify(ctx.confirmationAggregateService, Mockito.never())
                .syncFromLegacyBatch(Mockito.anyLong(), Mockito.anyInt());
    }

    @Test
    void confirmPayslip_shouldUseConditionalUpdateForPendingLine() {
        TestContext ctx = newTestContext();
        SysUser operator = user(33L, 401L, "employee1");
        PayrollBatch batch = confirmableBatch(202L);
        PayrollLine line = pendingLine(302L, 202L, 401L, 401L);
        PayslipConfirmRequest request = new PayslipConfirmRequest();
        request.setSignature("员工签名");
        Mockito.when(ctx.lineService.getById(302L)).thenReturn(line);
        Mockito.when(ctx.batchService.getById(202L)).thenReturn(batch);
        Mockito.when(ctx.lineService.update(Mockito.any(LambdaUpdateWrapper.class))).thenReturn(true);

        ctx.service.confirmPayslip(302L, operator, request);

        Assertions.assertEquals(PayrollConfirmationStatus.CONFIRMED.getCode(), line.getConfirmationStatus());
        Mockito.verify(ctx.lineService).update(Mockito.argThat((LambdaUpdateWrapper<PayrollLine> wrapper) -> {
            wrapper.getSqlSegment();
            return wrapper.getParamNameValuePairs().values()
                    .contains(PayrollConfirmationStatus.CONFIRMED.getCode());
        }));
        Mockito.verify(ctx.lineService, Mockito.never()).updateById(Mockito.any(PayrollLine.class));
    }

    @Test
    void confirmPayslip_shouldNotRefreshBatchWhenConditionalUpdateLost() {
        TestContext ctx = newTestContext();
        SysUser operator = user(34L, 401L, "employee2");
        PayrollBatch batch = confirmableBatch(203L);
        PayrollLine line = pendingLine(303L, 203L, 401L, 401L);
        Mockito.when(ctx.lineService.getById(303L)).thenReturn(line);
        Mockito.when(ctx.batchService.getById(203L)).thenReturn(batch);
        Mockito.when(ctx.lineService.update(Mockito.any(LambdaUpdateWrapper.class))).thenReturn(false);

        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> ctx.service.confirmPayslip(303L, operator, new PayslipConfirmRequest()));

        Assertions.assertEquals(ErrorCode.REQUEST_CONFLICT, exception.getErrorCode());
        Assertions.assertEquals(PayrollConfirmationStatus.PENDING.getCode(), line.getConfirmationStatus());
        Mockito.verify(ctx.confirmationAggregateService, Mockito.never())
                .syncFromLegacyBatch(Mockito.anyLong(), Mockito.anyInt());
        Mockito.verify(ctx.payrollProcessManager, Mockito.never())
                .onConfirmationCompleted(Mockito.anyLong(), Mockito.anyInt());
    }

    @Test
    void objectPayslip_shouldThrowConflictWhenLineStateChangedBeforeObjectedUpdate() {
        TestContext ctx = newTestContext();
        SysUser operator = user(35L, 401L, "employee3");
        PayrollBatch batch = confirmableBatch(204L);
        batch.setBatchRevision(1);
        PayrollLine line = pendingLine(304L, 204L, 401L, 401L);
        PayslipObjectionRequest request = new PayslipObjectionRequest();
        request.setReason("金额不对");
        Mockito.when(ctx.lineService.getById(304L)).thenReturn(line);
        Mockito.when(ctx.batchService.getById(204L)).thenReturn(batch);
        Mockito.when(ctx.approvalEngine.startWorkflow(Mockito.any(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyLong(), Mockito.anyMap())).thenReturn(1004L);
        Mockito.when(ctx.lineService.update(Mockito.any(LambdaUpdateWrapper.class))).thenReturn(false);

        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> ctx.service.objectPayslip(304L, operator, request));

        Assertions.assertEquals(ErrorCode.REQUEST_CONFLICT, exception.getErrorCode());
        Assertions.assertEquals(PayrollConfirmationStatus.PENDING.getCode(), line.getConfirmationStatus());
        Mockito.verify(ctx.approvalEngine).startWorkflow(Mockito.any(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyLong(), Mockito.anyMap());
        Mockito.verify(ctx.confirmationAggregateService, Mockito.never())
                .syncFromLegacyBatch(Mockito.anyLong(), Mockito.anyInt());
    }

    @Test
    void batchConfirm_shouldNotLetHrConfirmUnassignedLines() {
        TestContext ctx = newTestContext();
        SysUser hr = user(32L, 302L, "hr2");
        PayrollBatch batch = confirmableBatch(201L);
        PayrollLine line = pendingLine(301L, 201L, 402L, 402L);
        PayrollBatchConfirmRequest request = new PayrollBatchConfirmRequest();
        request.setLineIds(List.of(301L));
        request.setSignature("HR");

        Mockito.when(ctx.batchService.getById(201L)).thenReturn(batch);
        Mockito.when(ctx.lineService.list(Mockito.any(LambdaQueryWrapper.class))).thenReturn(List.of(line));
        Mockito.when(ctx.userRoleService.hasRole(32L, SecurityConstants.ROLE_HR)).thenReturn(true);

        int affected = ctx.service.batchConfirm(201L, hr, request);

        Assertions.assertEquals(0, affected);
        Mockito.verify(ctx.lineService, Mockito.never()).updateById(Mockito.any(PayrollLine.class));
        Mockito.verify(ctx.batchService, Mockito.never()).updateById(Mockito.any(PayrollBatch.class));
    }

    @Test
    void assignConfirmationAssignee_shouldUseConditionalPartialUpdateForOpenLine() {
        TestContext ctx = newTestContext();
        SysUser finance = user(37L, 501L, "finance1");
        SysUser assignee = user(38L, 901L, "assignee1");
        PayrollBatch batch = confirmableBatch(206L);
        PayrollLine line = pendingLine(306L, 206L, 401L, 401L);
        PayrollConfirmationAssignRequest request = new PayrollConfirmationAssignRequest();
        request.setAssigneeEmployeeId(901L);
        request.setLineIds(List.of(306L));

        Mockito.when(ctx.userRoleService.hasRole(37L, SecurityConstants.ROLE_FINANCE)).thenReturn(true);
        Mockito.when(ctx.sysUserService.findByEmployeeId(901L)).thenReturn(assignee);
        Mockito.when(ctx.batchService.getById(206L)).thenReturn(batch);
        Mockito.when(ctx.lineService.list(Mockito.any(LambdaQueryWrapper.class))).thenReturn(List.of(line));
        Mockito.when(ctx.lineService.update(Mockito.any(LambdaUpdateWrapper.class))).thenReturn(true);

        int affected = ctx.service.assignConfirmationAssignee(206L, finance, request);

        Assertions.assertEquals(1, affected);
        Mockito.verify(ctx.lineService).update(Mockito.argThat((LambdaUpdateWrapper<PayrollLine> wrapper) -> {
            wrapper.getSqlSegment();
            return wrapper.getParamNameValuePairs().values().contains(901L)
                    && wrapper.getParamNameValuePairs().values()
                    .contains(PayrollConfirmationStatus.PENDING.getCode())
                    && wrapper.getParamNameValuePairs().values()
                    .contains(PayrollConfirmationStatus.OBJECTED.getCode())
                    && wrapper.getParamNameValuePairs().values()
                    .contains(PayrollConfirmationStatus.OBJECTED_REJECTED.getCode());
        }));
        Mockito.verify(ctx.lineService, Mockito.never()).updateById(Mockito.any(PayrollLine.class));
        Mockito.verify(ctx.batchService).updateById(batch);
    }

    @Test
    void assignConfirmationAssignee_shouldNotReportAffectedWhenConditionalUpdateLost() {
        TestContext ctx = newTestContext();
        SysUser finance = user(39L, 502L, "finance2");
        SysUser assignee = user(40L, 902L, "assignee2");
        PayrollBatch batch = confirmableBatch(207L);
        PayrollLine line = pendingLine(307L, 207L, 402L, 402L);
        PayrollConfirmationAssignRequest request = new PayrollConfirmationAssignRequest();
        request.setAssigneeEmployeeId(902L);
        request.setLineIds(List.of(307L));

        Mockito.when(ctx.userRoleService.hasRole(39L, SecurityConstants.ROLE_FINANCE)).thenReturn(true);
        Mockito.when(ctx.sysUserService.findByEmployeeId(902L)).thenReturn(assignee);
        Mockito.when(ctx.batchService.getById(207L)).thenReturn(batch);
        Mockito.when(ctx.lineService.list(Mockito.any(LambdaQueryWrapper.class))).thenReturn(List.of(line));
        Mockito.when(ctx.lineService.update(Mockito.any(LambdaUpdateWrapper.class))).thenReturn(false);

        int affected = ctx.service.assignConfirmationAssignee(207L, finance, request);

        Assertions.assertEquals(0, affected);
        Mockito.verify(ctx.lineService, Mockito.never()).updateById(Mockito.any(PayrollLine.class));
        Mockito.verify(ctx.batchService, Mockito.never()).updateById(Mockito.any(PayrollBatch.class));
    }

    @Test
    void handleDisputeWorkflowCompleted_shouldMarkApprovedAndRefreshBatch() {
        PayrollLineService lineService = Mockito.mock(PayrollLineService.class);
        PayrollBatchService batchService = Mockito.mock(PayrollBatchService.class);
        ApprovalEngine approvalEngine = Mockito.mock(ApprovalEngine.class);
        SysUserService sysUserService = Mockito.mock(SysUserService.class);
        EmployeeService employeeService = Mockito.mock(EmployeeService.class);
        UserRoleService userRoleService = Mockito.mock(UserRoleService.class);
        PayrollConfirmationAggregateService confirmationAggregateService = Mockito.mock(PayrollConfirmationAggregateService.class);
        PayrollProcessManager payrollProcessManager = Mockito.mock(PayrollProcessManager.class);

        PayrollConfirmationServiceImpl service = Mockito.spy(new PayrollConfirmationServiceImpl(
                lineService,
                batchService,
                approvalEngine,
                sysUserService,
                employeeService,
                userRoleService,
                new ObjectMapper(),
                confirmationAggregateService,
                payrollProcessManager
        ));

        PayrollLine line = new PayrollLine();
        line.setId(300L);
        line.setBatchId(200L);
        line.setDisputeWorkflowId(999L);
        line.setConfirmationStatus(PayrollConfirmationStatus.OBJECTED.getCode());

        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(999L);
        workflow.setWorkflowType(WorkflowType.PAYROLL_DISPUTE);
        workflow.setBusinessKey("payroll_dispute:line:300");
        workflow.setWorkflowData("{\"lineId\":300,\"batchRevision\":1}");

        PayrollBatch batch = new PayrollBatch();
        batch.setId(200L);
        batch.setBatchRevision(1);

        Mockito.when(lineService.getById(300L)).thenReturn(line);
        Mockito.when(batchService.getById(200L)).thenReturn(batch);
        Mockito.doNothing().when(service).refreshBatchConfirmationStatus(200L);
        Mockito.when(lineService.update(Mockito.any(LambdaUpdateWrapper.class))).thenReturn(true);

        service.handleDisputeWorkflowCompleted(workflow, ApprovalStatus.APPROVED);

        Assertions.assertEquals(PayrollConfirmationStatus.OBJECTED_APPROVED.getCode(), line.getConfirmationStatus());
        Assertions.assertEquals("异议审批通过", line.getConfirmationComment());
        Mockito.verify(lineService).update(Mockito.argThat((LambdaUpdateWrapper<PayrollLine> wrapper) -> {
            wrapper.getSqlSegment();
            return wrapper.getParamNameValuePairs().values().contains(999L)
                    && wrapper.getParamNameValuePairs().values()
                    .contains(PayrollConfirmationStatus.OBJECTED_APPROVED.getCode());
        }));
        Mockito.verify(service).refreshBatchConfirmationStatus(200L);
    }

    @Test
    void handleDisputeWorkflowCompleted_shouldMarkRejectedAndRefreshBatch() {
        PayrollLineService lineService = Mockito.mock(PayrollLineService.class);
        PayrollBatchService batchService = Mockito.mock(PayrollBatchService.class);
        ApprovalEngine approvalEngine = Mockito.mock(ApprovalEngine.class);
        SysUserService sysUserService = Mockito.mock(SysUserService.class);
        EmployeeService employeeService = Mockito.mock(EmployeeService.class);
        UserRoleService userRoleService = Mockito.mock(UserRoleService.class);
        PayrollConfirmationAggregateService confirmationAggregateService = Mockito.mock(PayrollConfirmationAggregateService.class);
        PayrollProcessManager payrollProcessManager = Mockito.mock(PayrollProcessManager.class);

        PayrollConfirmationServiceImpl service = Mockito.spy(new PayrollConfirmationServiceImpl(
                lineService,
                batchService,
                approvalEngine,
                sysUserService,
                employeeService,
                userRoleService,
                new ObjectMapper(),
                confirmationAggregateService,
                payrollProcessManager
        ));

        PayrollLine line = new PayrollLine();
        line.setId(301L);
        line.setBatchId(201L);
        line.setDisputeWorkflowId(1000L);
        line.setConfirmationStatus(PayrollConfirmationStatus.OBJECTED.getCode());

        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(1000L);
        workflow.setWorkflowType(WorkflowType.PAYROLL_DISPUTE);
        workflow.setBusinessKey("payroll_dispute:line:301");
        workflow.setWorkflowData("{\"lineId\":301,\"batchRevision\":1}");

        PayrollBatch batch = new PayrollBatch();
        batch.setId(201L);
        batch.setBatchRevision(1);

        Mockito.when(lineService.getById(301L)).thenReturn(line);
        Mockito.when(batchService.getById(201L)).thenReturn(batch);
        Mockito.doNothing().when(service).refreshBatchConfirmationStatus(201L);
        Mockito.when(lineService.update(Mockito.any(LambdaUpdateWrapper.class))).thenReturn(true);

        service.handleDisputeWorkflowCompleted(workflow, ApprovalStatus.REJECTED);

        Assertions.assertEquals(PayrollConfirmationStatus.OBJECTED_REJECTED.getCode(), line.getConfirmationStatus());
        Assertions.assertEquals("异议审批未通过，请重新确认", line.getConfirmationComment());
        Mockito.verify(lineService).update(Mockito.argThat((LambdaUpdateWrapper<PayrollLine> wrapper) -> {
            wrapper.getSqlSegment();
            return wrapper.getParamNameValuePairs().values().contains(1000L)
                    && wrapper.getParamNameValuePairs().values()
                    .contains(PayrollConfirmationStatus.OBJECTED_REJECTED.getCode());
        }));
        Mockito.verify(service).refreshBatchConfirmationStatus(201L);
    }

    @Test
    void handleDisputeWorkflowCompleted_shouldParseLineIdFromVersionedBusinessKeyWhenWorkflowDataMissing() {
        TestContext ctx = newTestContext();
        PayrollConfirmationServiceImpl service = Mockito.spy(ctx.service);
        PayrollLine line = new PayrollLine();
        line.setId(304L);
        line.setBatchId(204L);
        line.setDisputeWorkflowId(1004L);
        line.setConfirmationStatus(PayrollConfirmationStatus.OBJECTED.getCode());

        PayrollBatch batch = new PayrollBatch();
        batch.setId(204L);
        batch.setBatchRevision(1);

        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(1004L);
        workflow.setWorkflowType(WorkflowType.PAYROLL_DISPUTE);
        workflow.setBusinessKey("payroll_dispute:line:304-1780600000000");

        Mockito.when(ctx.lineService.getById(304L)).thenReturn(line);
        Mockito.when(ctx.batchService.getById(204L)).thenReturn(batch);
        Mockito.when(ctx.lineService.update(Mockito.any(LambdaUpdateWrapper.class))).thenReturn(true);
        Mockito.doNothing().when(service).refreshBatchConfirmationStatus(204L);

        service.handleDisputeWorkflowCompleted(workflow, ApprovalStatus.REJECTED);

        Assertions.assertEquals(PayrollConfirmationStatus.OBJECTED_REJECTED.getCode(), line.getConfirmationStatus());
        Mockito.verify(ctx.lineService).update(Mockito.any(LambdaUpdateWrapper.class));
        Mockito.verify(service).refreshBatchConfirmationStatus(204L);
    }

    @Test
    void handleDisputeWorkflowCompleted_shouldIgnoreWorkflowThatNoLongerMatchesLine() {
        TestContext ctx = newTestContext();
        PayrollLine line = new PayrollLine();
        line.setId(302L);
        line.setBatchId(202L);
        line.setDisputeWorkflowId(null);
        line.setConfirmationStatus(PayrollConfirmationStatus.PENDING.getCode());

        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(1001L);
        workflow.setWorkflowType(WorkflowType.PAYROLL_DISPUTE);
        workflow.setBusinessKey("payroll_dispute:line:302");
        workflow.setWorkflowData("{\"lineId\":302,\"batchRevision\":1}");

        Mockito.when(ctx.lineService.getById(302L)).thenReturn(line);

        ctx.service.handleDisputeWorkflowCompleted(workflow, ApprovalStatus.APPROVED);

        Assertions.assertEquals(PayrollConfirmationStatus.PENDING.getCode(), line.getConfirmationStatus());
        Mockito.verify(ctx.lineService, Mockito.never()).updateById(Mockito.any(PayrollLine.class));
        Mockito.verify(ctx.batchService, Mockito.never()).getById(Mockito.anyLong());
        Mockito.verify(ctx.payrollProcessManager, Mockito.never()).onConfirmationCompleted(Mockito.anyLong(), Mockito.anyInt());
    }

    @Test
    void handleDisputeWorkflowCompleted_shouldIgnoreObsoleteBatchRevision() {
        TestContext ctx = newTestContext();
        PayrollLine line = new PayrollLine();
        line.setId(303L);
        line.setBatchId(203L);
        line.setDisputeWorkflowId(1002L);
        line.setConfirmationStatus(PayrollConfirmationStatus.OBJECTED.getCode());

        PayrollBatch batch = new PayrollBatch();
        batch.setId(203L);
        batch.setBatchRevision(2);

        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(1002L);
        workflow.setWorkflowType(WorkflowType.PAYROLL_DISPUTE);
        workflow.setBusinessKey("payroll_dispute:line:303");
        workflow.setWorkflowData("{\"lineId\":303,\"batchRevision\":1}");

        Mockito.when(ctx.lineService.getById(303L)).thenReturn(line);
        Mockito.when(ctx.batchService.getById(203L)).thenReturn(batch);

        ctx.service.handleDisputeWorkflowCompleted(workflow, ApprovalStatus.APPROVED);

        Assertions.assertEquals(PayrollConfirmationStatus.OBJECTED.getCode(), line.getConfirmationStatus());
        Mockito.verify(ctx.lineService, Mockito.never()).updateById(Mockito.any(PayrollLine.class));
        Mockito.verify(ctx.confirmationAggregateService, Mockito.never()).syncFromLegacyBatch(Mockito.anyLong(), Mockito.anyInt());
        Mockito.verify(ctx.payrollProcessManager, Mockito.never()).onConfirmationCompleted(Mockito.anyLong(), Mockito.anyInt());
    }

    @Test
    void pagePendingConfirmationsShouldClampPageAndSizeBeforeQuerying() {
        TestContext ctx = newTestContext();
        SysUser admin = user(1L, null, "admin");
        Mockito.when(ctx.userRoleService.hasRole(1L, SecurityConstants.ROLE_FINANCE)).thenReturn(true);
        Page<PayrollLine> linePage = new Page<>(1, 200, 0);
        linePage.setRecords(List.of());
        Mockito.when(ctx.lineService.page(Mockito.any(Page.class), Mockito.any(Wrapper.class))).thenReturn(linePage);

        Page<PayrollPendingConfirmationDto> result = ctx.service.pagePendingConfirmations(admin, null, -1, 1000);

        Mockito.verify(ctx.lineService).page(
                Mockito.argThat(page -> page.getCurrent() == 1 && page.getSize() == 200),
                Mockito.any(Wrapper.class)
        );
        Assertions.assertEquals(1, result.getCurrent());
        Assertions.assertEquals(200, result.getSize());
    }

    @Test
    void pagePendingConfirmationsShouldFilterBatchesToConfirmationWindow() {
        TestContext ctx = newTestContext();
        SysUser admin = user(2L, null, "admin2");
        Mockito.when(ctx.userRoleService.hasRole(2L, SecurityConstants.ROLE_FINANCE)).thenReturn(true);
        Page<PayrollLine> linePage = new Page<>(1, 10, 0);
        linePage.setRecords(List.of());
        Mockito.when(ctx.lineService.page(Mockito.any(Page.class), Mockito.any(Wrapper.class))).thenReturn(linePage);

        ctx.service.pagePendingConfirmations(admin, null, 1, 10);

        Mockito.verify(ctx.lineService).page(
                Mockito.any(Page.class),
                Mockito.argThat(wrapper -> {
                    String sqlSegment = wrapper.getSqlSegment();
                    return sqlSegment.contains("EXISTS (SELECT 1 FROM payroll_batch pb")
                            && sqlSegment.contains("pb.status IN")
                            && sqlSegment.contains("confirming")
                            && sqlSegment.contains("dispute_processing")
                            && sqlSegment.contains("confirmed");
                })
        );
    }

    private TestContext newTestContext() {
        PayrollLineService lineService = Mockito.mock(PayrollLineService.class);
        PayrollBatchService batchService = Mockito.mock(PayrollBatchService.class);
        ApprovalEngine approvalEngine = Mockito.mock(ApprovalEngine.class);
        SysUserService sysUserService = Mockito.mock(SysUserService.class);
        EmployeeService employeeService = Mockito.mock(EmployeeService.class);
        UserRoleService userRoleService = Mockito.mock(UserRoleService.class);
        PayrollConfirmationAggregateService confirmationAggregateService = Mockito.mock(PayrollConfirmationAggregateService.class);
        PayrollProcessManager payrollProcessManager = Mockito.mock(PayrollProcessManager.class);
        PayrollConfirmationServiceImpl service = new PayrollConfirmationServiceImpl(
                lineService,
                batchService,
                approvalEngine,
                sysUserService,
                employeeService,
                userRoleService,
                new ObjectMapper(),
                confirmationAggregateService,
                payrollProcessManager
        );
        return new TestContext(lineService, batchService, approvalEngine, sysUserService, userRoleService,
                confirmationAggregateService, payrollProcessManager, service);
    }

    private PayrollBatch confirmableBatch(Long batchId) {
        PayrollBatch batch = new PayrollBatch();
        batch.setId(batchId);
        batch.setConfirmationRequired(Boolean.TRUE);
        batch.setStatus(PayrollBatchStatus.CONFIRMING);
        return batch;
    }

    private PayrollLine pendingLine(Long lineId, Long batchId, Long employeeId, Long assigneeEmployeeId) {
        PayrollLine line = new PayrollLine();
        line.setId(lineId);
        line.setBatchId(batchId);
        line.setEmployeeId(employeeId);
        line.setConfirmationAssigneeEmployeeId(assigneeEmployeeId);
        line.setConfirmationStatus(PayrollConfirmationStatus.PENDING.getCode());
        return line;
    }

    private SysUser user(Long userId, Long employeeId, String username) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setEmployeeId(employeeId);
        user.setUsername(username);
        return user;
    }

    private static class TestContext {
        private final PayrollLineService lineService;
        private final PayrollBatchService batchService;
        private final ApprovalEngine approvalEngine;
        private final SysUserService sysUserService;
        private final UserRoleService userRoleService;
        private final PayrollConfirmationAggregateService confirmationAggregateService;
        private final PayrollProcessManager payrollProcessManager;
        private final PayrollConfirmationServiceImpl service;

        private TestContext(PayrollLineService lineService,
                            PayrollBatchService batchService,
                            ApprovalEngine approvalEngine,
                            SysUserService sysUserService,
                            UserRoleService userRoleService,
                            PayrollConfirmationAggregateService confirmationAggregateService,
                            PayrollProcessManager payrollProcessManager,
                            PayrollConfirmationServiceImpl service) {
            this.lineService = lineService;
            this.batchService = batchService;
            this.approvalEngine = approvalEngine;
            this.sysUserService = sysUserService;
            this.userRoleService = userRoleService;
            this.confirmationAggregateService = confirmationAggregateService;
            this.payrollProcessManager = payrollProcessManager;
            this.service = service;
        }
    }
}
