package com.yiyundao.compensation.modules.employee.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.utils.VOConverter;
import com.yiyundao.compensation.enums.EmployeeStatus;
import com.yiyundao.compensation.enums.UserStatus;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.impl.EmployeeServiceImpl;
import com.yiyundao.compensation.modules.payroll.service.PayCycleService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.service.EncryptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeServiceImplOfflineManagerTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setOfflineManagerShouldThrowWhenEmployeeMissing() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl();

        assertThatThrownBy(() -> service.setOfflineManager(10L, 20L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    void setOfflineManagerShouldThrowWhenManagerMissing() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl();
        Employee employee = new Employee();
        employee.setId(10L);
        service.employees.put(10L, employee);

        assertThatThrownBy(() -> service.setOfflineManager(10L, 20L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    void setOfflineManagerShouldPersistWhenManagerExists() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl();
        Employee employee = new Employee();
        employee.setId(10L);
        Employee manager = new Employee();
        manager.setId(20L);
        manager.setStatus("active");
        service.employees.put(10L, employee);
        service.employees.put(20L, manager);

        service.setOfflineManager(10L, 20L);

        assertThat(employee.getManagerId()).isEqualTo(20L);
        assertThat(service.lastUpdatedEmployee).isSameAs(employee);
    }

    @Test
    void pageEmployeesShouldRejectInvalidStatusFilter() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl();

        assertThatThrownBy(() -> service.pageEmployees(
                1,
                10,
                null,
                null,
                "unknown",
                null,
                null,
                null,
                "createTime",
                "desc"
        )).isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(businessException.getMessage()).contains("无效的员工状态");
                });
    }

    @Test
    void pageEmployeesShouldClampPageAndSize() {
        TestableEmployeeServiceImpl service = authenticatedService();

        service.pageEmployees(-1, 1000, null, null, null, null, null, null, "createTime", "desc");

        assertThat(service.lastPage.getCurrent()).isEqualTo(1);
        assertThat(service.lastPage.getSize()).isEqualTo(200);
    }

    private TestableEmployeeServiceImpl authenticatedService() {
        SysUserService sysUserService = mock(SysUserService.class);
        SysUser tester = user(1L);
        tester.setEmployeeId(100L);
        when(sysUserService.findByUsername("tester")).thenReturn(tester);
        when(sysUserService.getById(1L)).thenReturn(tester);
        com.yiyundao.compensation.security.DatabasePermissionService permissionService =
                mock(com.yiyundao.compensation.security.DatabasePermissionService.class);
        when(permissionService.hasCurrentRequestScope(1L, "ASSIGNED")).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tester", "n/a"));
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(
                mock(EncryptionService.class), sysUserService, permissionService);
        Employee currentEmployee = new Employee();
        currentEmployee.setId(100L);
        service.employees.put(100L, currentEmployee);
        return service;
    }

    private SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("tester");
        return user;
    }

    @Test
    void updateEmployeeShouldClearDepartmentWhenBlankDepartmentIsExplicitlyProvided() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl();
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setDepartment("研发部");
        service.employees.put(10L, employee);

        Employee update = new Employee();
        update.setDepartment(" ");

        service.updateEmployee(10L, update);

        assertThat(employee.getDepartment()).isNull();
        assertThat(service.lastUpdatedEmployee).isSameAs(employee);
    }

    @Test
    void updateEmployeeShouldReportOptimisticLockConflict() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl();
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setName("张三");
        service.employees.put(10L, employee);
        service.updateSuccessful = false;

        Employee update = new Employee();
        update.setName("李四");

        assertThatThrownBy(() -> service.updateEmployee(10L, update))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.REQUEST_CONFLICT));
    }

    @Test
    void updateEmployeeShouldRejectTypeChangeWithoutNewAccount() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl();
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setSettlementAccountType("bank_card");
        employee.setSettlementAccount("cipher-bank-card");
        service.employees.put(10L, employee);

        Employee update = new Employee();
        update.setSettlementAccountType("alipay");

        assertThatThrownBy(() -> service.updateEmployee(10L, update))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.PARAM_MISSING.getCode()))
                .hasMessageContaining("同时提交新的收款账号");

        assertThat(service.lastUpdatedEmployee).isNull();
    }

    @Test
    void updateEmployeeShouldRejectLooseTypeChangeWithoutNewAccount() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl();
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setSettlementAccountType("alipay");
        employee.setSettlementAccount("cipher-alipay");
        service.employees.put(10L, employee);

        Employee update = new Employee();
        update.setSettlementAccountType("wechat");

        assertThatThrownBy(() -> service.updateEmployee(10L, update))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.PARAM_MISSING.getCode()))
                .hasMessageContaining("同时提交新的收款账号");

        assertThat(service.lastUpdatedEmployee).isNull();
    }

    @Test
    void updateEmployeeShouldValidateNewAccountAgainstExistingSettlementType() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl();
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setSettlementAccountType("alipay");
        service.employees.put(10L, employee);
        when(service.encryptionService.encrypt("user@example.com")).thenReturn("cipher-alipay");

        Employee update = new Employee();
        update.setSettlementAccount("user@example.com");

        service.updateEmployee(10L, update);

        assertThat(employee.getSettlementAccountType()).isEqualTo("alipay");
        assertThat(employee.getSettlementAccount()).isEqualTo("cipher-alipay");
        assertThat(employee.getBankAccount()).isNull();
    }

    @Test
    void updateEmployeeShouldClearLegacyBankFieldsWhenSwitchingToNonBankSettlementAccount() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl();
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setSettlementAccountType("bank_card");
        employee.setSettlementAccount("cipher-old-bank-card");
        employee.setBankAccount("cipher-old-bank-card");
        employee.setBankName("旧银行");
        employee.setBankBranchName("旧支行");
        service.employees.put(10L, employee);
        when(service.encryptionService.encrypt("user@example.com")).thenReturn("cipher-alipay");

        Employee update = new Employee();
        update.setSettlementAccountType("alipay");
        update.setSettlementAccount("user@example.com");

        service.updateEmployee(10L, update);

        assertThat(employee.getSettlementAccountType()).isEqualTo("alipay");
        assertThat(employee.getSettlementAccount()).isEqualTo("cipher-alipay");
        assertThat(employee.getBankAccount()).isNull();
        assertThat(employee.getBankName()).isNull();
        assertThat(employee.getBankBranchName()).isNull();
        verify(service.encryptionService, never()).decrypt("cipher-old-bank-card");
    }

    @Test
    void updateEmployeeShouldAllowUnchangedTypeWithoutDecryptingExistingAccount() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl();
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setSettlementAccountType("alipay");
        employee.setSettlementAccount("cipher-broken-alipay");
        employee.setDepartment("研发部");
        service.employees.put(10L, employee);

        Employee update = new Employee();
        update.setSettlementAccountType("alipay");
        update.setDepartment("运营部");

        service.updateEmployee(10L, update);

        assertThat(employee.getSettlementAccountType()).isEqualTo("alipay");
        assertThat(employee.getSettlementAccount()).isEqualTo("cipher-broken-alipay");
        assertThat(employee.getDepartment()).isEqualTo("运营部");
        verify(service.encryptionService, never()).decrypt("cipher-broken-alipay");
    }

    @Test
    void updateEmployeeShouldRejectNonBankTypeWithOnlyBankAccount() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl();
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setSettlementAccountType("alipay");
        employee.setSettlementAccount("cipher-alipay");
        service.employees.put(10L, employee);

        Employee update = new Employee();
        update.setSettlementAccountType("alipay");
        update.setBankAccount("6222020202020202020");

        assertThatThrownBy(() -> service.updateEmployee(10L, update))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.PARAM_MISSING.getCode()))
                .hasMessageContaining("非银行卡收款类型请提交收款账号");

        assertThat(service.lastUpdatedEmployee).isNull();
    }

    @Test
    void updateStatusShouldDisableLinkedActiveUsersWhenEmployeeLeaves() {
        SysUserService sysUserService = mock(SysUserService.class);
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(
                mock(EncryptionService.class),
                sysUserService
        );
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setStatus(EmployeeStatus.ACTIVE.getCode());
        service.employees.put(10L, employee);
        SysUser user = new SysUser();
        user.setId(99L);
        user.setStatus(UserStatus.ACTIVE);
        when(sysUserService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(user));
        when(sysUserService.update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(true);

        service.updateStatus(10L, EmployeeStatus.INACTIVE);

        verify(sysUserService).update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(sysUserService).batchIncrementPermissionVersion(Set.of(99L));
    }

    @Test
    void updateStatusShouldNotReactivateLinkedUsersWhenEmployeeBecomesActive() {
        SysUserService sysUserService = mock(SysUserService.class);
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(
                mock(EncryptionService.class),
                sysUserService
        );
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setStatus(EmployeeStatus.INACTIVE.getCode());
        service.employees.put(10L, employee);

        service.updateStatus(10L, EmployeeStatus.ACTIVE);

        verify(sysUserService, never()).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(sysUserService, never()).update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(sysUserService, never()).batchIncrementPermissionVersion(any());
    }

    @Test
    void updateEmployeeShouldRejectInvalidStatusValue() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl();
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setStatus(EmployeeStatus.ACTIVE.getCode());
        service.employees.put(10L, employee);
        Employee update = new Employee();
        update.setStatus("terminated");

        assertThatThrownBy(() -> service.updateEmployee(10L, update))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_INVALID));
        assertThat(service.lastUpdatedEmployee).isNull();
    }

    @Test
    void updateEmployeeShouldNormalizeEmploymentType() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl();
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setEmploymentType("full_time");
        service.employees.put(10L, employee);
        Employee update = new Employee();
        update.setEmploymentType("PART_TIME");

        service.updateEmployee(10L, update);

        assertThat(employee.getEmploymentType()).isEqualTo("part_time");
        assertThat(service.lastUpdatedEmployee).isSameAs(employee);
    }

    @Test
    void updateEmployeeShouldRejectInvalidEmploymentType() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl();
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setEmploymentType("full_time");
        service.employees.put(10L, employee);
        Employee update = new Employee();
        update.setEmploymentType("contractor_plus");

        assertThatThrownBy(() -> service.updateEmployee(10L, update))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_INVALID));
        assertThat(service.lastUpdatedEmployee).isNull();
    }

    private static class TestableEmployeeServiceImpl extends EmployeeServiceImpl {
        private final Map<Long, Employee> employees = new HashMap<>();
        private final EncryptionService encryptionService;
        private Employee lastUpdatedEmployee;
        private Page<Employee> lastPage;
        private boolean updateSuccessful = true;

        private TestableEmployeeServiceImpl() {
            this(mock(EncryptionService.class));
        }

        private TestableEmployeeServiceImpl(EncryptionService encryptionService) {
            this(encryptionService, mock(SysUserService.class));
        }

        private TestableEmployeeServiceImpl(EncryptionService encryptionService,
                                            SysUserService sysUserService) {
            this(encryptionService, sysUserService,
                    mock(com.yiyundao.compensation.security.DatabasePermissionService.class));
        }

        private TestableEmployeeServiceImpl(EncryptionService encryptionService,
                                            SysUserService sysUserService,
                                            com.yiyundao.compensation.security.DatabasePermissionService permissionService) {
            super(
                    encryptionService,
                    mock(ObjectProvider.class),
                    mock(ObjectProvider.class),
                    sysUserService,
                    mock(ExternalIdentityService.class),
                    mock(ApprovalWorkflowMapper.class),
                    mock(PayrollLineService.class),
                    mock(PayrollBatchService.class),
                    mock(PayCycleService.class),
                    mock(PaymentRecordService.class),
                    mock(VOConverter.class),
                    new ObjectMapper(),
                    mock(EmployeeDepartmentService.class),
                    permissionService
            );
            this.encryptionService = encryptionService;
        }

        @Override
        public Employee getById(java.io.Serializable id) {
            if (!(id instanceof Long longId)) {
                return null;
            }
            return employees.get(longId);
        }

        @Override
        public boolean updateById(Employee entity) {
            if (!updateSuccessful) {
                return false;
            }
            lastUpdatedEmployee = entity;
            employees.put(entity.getId(), entity);
            return true;
        }

        @Override
        public boolean update(Wrapper<Employee> updateWrapper) {
            return true;
        }

        @Override
        public <E extends IPage<Employee>> E page(E page, Wrapper<Employee> queryWrapper) {
            lastPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        }
    }
}
