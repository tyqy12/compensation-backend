package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollConfirmationStatus;
import com.yiyundao.compensation.enums.WorkflowType;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

class PayrollConfirmationServiceImplTest {

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
        batch.setConfirmationRequired(Boolean.TRUE);
        batch.setStatus(PayrollBatchStatus.CONFIRMING);

        PayrollLine line = new PayrollLine();
        line.setId(300L);
        line.setBatchId(200L);
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

        Long workflowId = service.objectPayslip(300L, operator, request);

        Assertions.assertEquals(999L, workflowId);
        Assertions.assertEquals(PayrollConfirmationStatus.OBJECTED.getCode(), line.getConfirmationStatus());
        Assertions.assertEquals("金额有误", line.getObjectionReason());
        Assertions.assertEquals(999L, line.getDisputeWorkflowId());
        Mockito.verify(lineService).updateById(line);
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

        Mockito.when(lineService.getById(300L)).thenReturn(line);
        Mockito.doNothing().when(service).refreshBatchConfirmationStatus(200L);

        service.handleDisputeWorkflowCompleted(workflow, ApprovalStatus.APPROVED);

        Assertions.assertEquals(PayrollConfirmationStatus.OBJECTED_APPROVED.getCode(), line.getConfirmationStatus());
        Assertions.assertEquals("异议审批通过", line.getConfirmationComment());
        Mockito.verify(lineService).updateById(line);
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

        Mockito.when(lineService.getById(301L)).thenReturn(line);
        Mockito.doNothing().when(service).refreshBatchConfirmationStatus(201L);

        service.handleDisputeWorkflowCompleted(workflow, ApprovalStatus.REJECTED);

        Assertions.assertEquals(PayrollConfirmationStatus.OBJECTED_REJECTED.getCode(), line.getConfirmationStatus());
        Assertions.assertEquals("异议审批未通过，请重新确认", line.getConfirmationComment());
        Mockito.verify(lineService).updateById(line);
        Mockito.verify(service).refreshBatchConfirmationStatus(201L);
    }
}
