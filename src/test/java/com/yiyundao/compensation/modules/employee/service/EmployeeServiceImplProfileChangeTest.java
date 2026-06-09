package com.yiyundao.compensation.modules.employee.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.utils.VOConverter;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.employee.dto.EmployeeProfileChangePayload;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.impl.EmployeeServiceImpl;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payroll.service.PayCycleService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.service.EncryptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class EmployeeServiceImplProfileChangeTest {

    @Test
    void submitProfileChangeShouldRejectWhenEmployeeHasPendingProfileChange() {
        SysUserService sysUserService = mock(SysUserService.class);
        ApprovalWorkflowMapper approvalWorkflowMapper = mock(ApprovalWorkflowMapper.class);
        EncryptionService encryptionService = mock(EncryptionService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ApprovalEngine> approvalEngineProvider = mock(ObjectProvider.class);
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(
                encryptionService,
                approvalEngineProvider,
                sysUserService,
                approvalWorkflowMapper
        );
        Employee employee = new Employee();
        employee.setId(2001L);
        employee.setEmployeeId("E2001");
        employee.setName("张三");
        service.employees.put(2001L, employee);
        SysUser user = new SysUser();
        user.setId(1001L);
        user.setEmployeeId(2001L);
        when(sysUserService.getById(1001L)).thenReturn(user);
        when(approvalWorkflowMapper.selectCount(any())).thenReturn(1L);

        EmployeeProfileChangePayload payload = new EmployeeProfileChangePayload();
        payload.setName("张三新");

        assertThatThrownBy(() -> service.submitCurrentEmployeeProfileChange(1001L, payload, "更新姓名"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.INVALID_STATUS.getCode()))
                .hasMessageContaining("已有待审批的资料变更申请");

        verify(encryptionService, never()).encrypt(any());
        verify(approvalEngineProvider, never()).getObject();
        verify(approvalWorkflowMapper).selectCount(org.mockito.ArgumentMatchers.<Wrapper<ApprovalWorkflow>>any());
    }

    @Test
    void submitProfileChangeShouldStartWorkflowWhenNoPendingProfileChangeExists() {
        SysUserService sysUserService = mock(SysUserService.class);
        ApprovalWorkflowMapper approvalWorkflowMapper = mock(ApprovalWorkflowMapper.class);
        EncryptionService encryptionService = mock(EncryptionService.class);
        ApprovalEngine approvalEngine = mock(ApprovalEngine.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ApprovalEngine> approvalEngineProvider = mock(ObjectProvider.class);
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(
                encryptionService,
                approvalEngineProvider,
                sysUserService,
                approvalWorkflowMapper
        );
        Employee employee = new Employee();
        employee.setId(2002L);
        employee.setEmployeeId("E2002");
        employee.setName("李四");
        employee.setVersion(7);
        employee.setUpdateTime(LocalDateTime.parse("2026-06-04T10:00:00"));
        service.employees.put(2002L, employee);
        SysUser user = new SysUser();
        user.setId(1002L);
        user.setEmployeeId(2002L);
        when(sysUserService.getById(1002L)).thenReturn(user);
        when(approvalWorkflowMapper.selectCount(any())).thenReturn(0L);
        when(encryptionService.encrypt(any())).thenReturn("cipher_payload");
        when(approvalEngineProvider.getObject()).thenReturn(approvalEngine);
        when(approvalEngine.startWorkflow(any(), any(), any(), any(), any())).thenReturn(9002L);

        EmployeeProfileChangePayload payload = new EmployeeProfileChangePayload();
        payload.setName("李四新");

        Long workflowId = service.submitCurrentEmployeeProfileChange(1002L, payload, "更新姓名");

        assertThat(workflowId).isEqualTo(9002L);
        verify(approvalWorkflowMapper).selectCount(org.mockito.ArgumentMatchers.<Wrapper<ApprovalWorkflow>>any());
        verify(encryptionService).encrypt(any());
        verify(approvalEngine).startWorkflow(
                any(),
                any(),
                org.mockito.ArgumentMatchers.eq(EmployeeService.BUSINESS_TYPE_EMPLOYEE_PROFILE_CHANGE),
                org.mockito.ArgumentMatchers.eq(1002L),
                org.mockito.ArgumentMatchers.argThat(data ->
                        Integer.valueOf(7).equals(data.get("snapshotVersion"))
                                && "2026-06-04T10:00".equals(data.get("snapshotUpdateTime")))
        );
    }

    @Test
    void applyApprovedProfileChangeShouldRejectWhenEmployeeChangedAfterWorkflowStarted() {
        SysUserService sysUserService = mock(SysUserService.class);
        ApprovalWorkflowMapper approvalWorkflowMapper = mock(ApprovalWorkflowMapper.class);
        EncryptionService encryptionService = mock(EncryptionService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ApprovalEngine> approvalEngineProvider = mock(ObjectProvider.class);
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(
                encryptionService,
                approvalEngineProvider,
                sysUserService,
                approvalWorkflowMapper
        );
        Employee current = new Employee();
        current.setId(2003L);
        current.setEmployeeId("E2003");
        current.setName("王五");
        current.setVersion(8);
        current.setUpdateTime(LocalDateTime.parse("2026-06-04T11:00:00"));
        service.employees.put(2003L, current);
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(9003L);
        workflow.setWorkflowData("""
                {
                  "snapshotVersion": 7,
                  "snapshotUpdateTime": "2026-06-04T10:00:00"
                }
                """);
        when(approvalWorkflowMapper.selectById(9003L)).thenReturn(workflow);

        EmployeeProfileChangePayload payload = new EmployeeProfileChangePayload();
        payload.setName("王五新");

        assertThatThrownBy(() -> service.applyApprovedProfileChange(9003L, 2003L, payload))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.REQUEST_CONFLICT.getCode()))
                .hasMessageContaining("员工资料已变更");

        assertThat(service.updateEmployeeCalls).isZero();
    }

    @Test
    void applyApprovedProfileChangeShouldAllowLegacyWorkflowWithoutSnapshot() {
        SysUserService sysUserService = mock(SysUserService.class);
        ApprovalWorkflowMapper approvalWorkflowMapper = mock(ApprovalWorkflowMapper.class);
        EncryptionService encryptionService = mock(EncryptionService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ApprovalEngine> approvalEngineProvider = mock(ObjectProvider.class);
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(
                encryptionService,
                approvalEngineProvider,
                sysUserService,
                approvalWorkflowMapper
        );
        Employee current = new Employee();
        current.setId(2004L);
        current.setEmployeeId("E2004");
        current.setName("赵六");
        service.employees.put(2004L, current);
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(9004L);
        workflow.setWorkflowData("{}");
        when(approvalWorkflowMapper.selectById(9004L)).thenReturn(workflow);

        EmployeeProfileChangePayload payload = new EmployeeProfileChangePayload();
        payload.setName("赵六新");

        service.applyApprovedProfileChange(9004L, 2004L, payload);

        assertThat(service.updateEmployeeCalls).isEqualTo(1);
        assertThat(service.lastUpdateInfo.getName()).isEqualTo("赵六新");
    }

    private static class TestableEmployeeServiceImpl extends EmployeeServiceImpl {
        private final Map<Long, Employee> employees = new HashMap<>();
        private int updateEmployeeCalls;
        private Employee lastUpdateInfo;

        private TestableEmployeeServiceImpl(EncryptionService encryptionService,
                                            ObjectProvider<ApprovalEngine> approvalEngineProvider,
                                            SysUserService sysUserService,
                                            ApprovalWorkflowMapper approvalWorkflowMapper) {
            super(
                    encryptionService,
                    mock(ObjectProvider.class),
                    approvalEngineProvider,
                    sysUserService,
                    mock(ExternalIdentityService.class),
                    approvalWorkflowMapper,
                    mock(PayrollLineService.class),
                    mock(PayrollBatchService.class),
                    mock(PayCycleService.class),
                    mock(PaymentRecordService.class),
                    mock(VOConverter.class),
                    new ObjectMapper()
            );
        }

        @Override
        public Employee getById(java.io.Serializable id) {
            if (!(id instanceof Long longId)) {
                return null;
            }
            return employees.get(longId);
        }

        @Override
        public com.yiyundao.compensation.interfaces.vo.employee.EmployeeVO updateEmployee(Long id, Employee updateInfo) {
            updateEmployeeCalls++;
            lastUpdateInfo = updateInfo;
            Employee employee = employees.get(id);
            if (employee != null && updateInfo.getName() != null) {
                employee.setName(updateInfo.getName());
            }
            return new com.yiyundao.compensation.interfaces.vo.employee.EmployeeVO();
        }
    }
}
