package com.yiyundao.compensation.modules.employee.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.utils.VOConverter;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.dto.EmployeeBatchImportResult;
import com.yiyundao.compensation.modules.employee.service.impl.EmployeeServiceImpl;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payroll.service.PayCycleService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.service.EncryptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeServiceImplBatchImportTest {

    @Test
    void batchImportShouldSkipDuplicateEmployeeIdsWithinSameRequest() {
        EncryptionService encryptionService = mock(EncryptionService.class);
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(encryptionService, Set.of());
        when(encryptionService.encryptIdCard("110101199001010011")).thenReturn("encrypted-id-card");

        service.batchImport(List.of(
                employee(" EMP001 ", "张三", "110101199001010011"),
                employee("EMP001", "张三重复", null),
                employee("EMP002", "李四", null)
        ));

        assertThat(service.savedEmployees).extracting(Employee::getEmployeeId)
                .containsExactly("EMP001", "EMP002");
        assertThat(service.savedEmployees).extracting(Employee::getName)
                .containsExactly("张三", "李四");
        assertThat(service.savedEmployees.get(0).getEncryptedIdCard()).isEqualTo("encrypted-id-card");
    }

    @Test
    void batchImportShouldSkipAlreadyExistingEmployeeIdsBeforeSaveBatch() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(mock(EncryptionService.class), Set.of("EMP001"));

        service.batchImport(List.of(
                employee("EMP001", "已存在", null),
                employee("EMP002", "新员工", null)
        ));

        assertThat(service.savedEmployees).extracting(Employee::getEmployeeId)
                .containsExactly("EMP002");
    }

    @Test
    void batchImportShouldReturnWithoutSavingWhenNoValidEmployeeRemains() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(mock(EncryptionService.class), Set.of("EMP001"));

        service.batchImport(List.of(
                employee("EMP001", "已存在", null),
                employee(" ", "无效", null)
        ));

        assertThat(service.saveBatchCalls).isZero();
        assertThat(service.savedEmployees).isEmpty();
    }

    @Test
    void batchImportShouldNormalizeEmploymentTypeBeforeSaving() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(mock(EncryptionService.class), Set.of());
        Employee employee = employee("EMP001", "张三", null);
        employee.setEmploymentType("PART_TIME");

        service.batchImport(List.of(employee));

        assertThat(service.savedEmployees).extracting(Employee::getEmploymentType)
                .containsExactly("part_time");
    }

    @Test
    void batchImportShouldRejectInvalidEmploymentTypeBeforeSaving() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(mock(EncryptionService.class), Set.of());
        Employee employee = employee("EMP001", "张三", null);
        employee.setEmploymentType("contractor_plus");

        assertThatThrownBy(() -> service.batchImport(List.of(employee)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_INVALID));

        assertThat(service.saveBatchCalls).isZero();
        assertThat(service.savedEmployees).isEmpty();
    }

    @Test
    void batchImportShouldNormalizeEmployeeStatusBeforeSaving() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(mock(EncryptionService.class), Set.of());
        Employee employee = employee("EMP001", "张三", null);
        employee.setStatus("INACTIVE");

        service.batchImport(List.of(employee));

        assertThat(service.savedEmployees).extracting(Employee::getStatus)
                .containsExactly("inactive");
    }

    @Test
    void batchImportShouldRejectInvalidEmployeeStatusBeforeSaving() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(mock(EncryptionService.class), Set.of());
        Employee employee = employee("EMP001", "张三", null);
        employee.setStatus("terminated");

        assertThatThrownBy(() -> service.batchImport(List.of(employee)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_INVALID));

        assertThat(service.saveBatchCalls).isZero();
        assertThat(service.savedEmployees).isEmpty();
    }

    @Test
    void batchImportShouldRejectInvalidPhoneBeforeSaving() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(mock(EncryptionService.class), Set.of());
        Employee employee = employee("EMP001", "张三", null);
        employee.setPhone("12345");

        assertThatThrownBy(() -> service.batchImport(List.of(employee)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_FORMAT_ERROR));

        assertThat(service.saveBatchCalls).isZero();
        assertThat(service.savedEmployees).isEmpty();
    }

    @Test
    void batchImportShouldRejectInvalidEmailBeforeSaving() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(mock(EncryptionService.class), Set.of());
        Employee employee = employee("EMP001", "张三", null);
        employee.setEmail("invalid-email");

        assertThatThrownBy(() -> service.batchImport(List.of(employee)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_FORMAT_ERROR));

        assertThat(service.saveBatchCalls).isZero();
        assertThat(service.savedEmployees).isEmpty();
    }

    @Test
    void batchImportShouldRejectInvalidSettlementAccountBeforeSaving() {
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(mock(EncryptionService.class), Set.of());
        Employee employee = employee("EMP001", "张三", null);
        employee.setSettlementAccountType("alipay");
        employee.setSettlementAccount("not-phone-or-email");

        assertThatThrownBy(() -> service.batchImport(List.of(employee)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_FORMAT_ERROR));

        assertThat(service.saveBatchCalls).isZero();
        assertThat(service.savedEmployees).isEmpty();
    }

    @Test
    void batchImportShouldKeepPlatformBindingAfterEmployeeNormalization() {
        ExternalIdentityService externalIdentityService = mock(ExternalIdentityService.class);
        EmployeeDepartmentService employeeDepartmentService = mock(EmployeeDepartmentService.class);
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(
                mock(EncryptionService.class),
                Set.of(),
                externalIdentityService,
                employeeDepartmentService
        );
        Employee employee = employee("EMP010", "张三", null);
        employee.setProvider("wechat");
        employee.setSubjectId("wx-010");
        employee.setDepartment("技术部,平台研发部");

        EmployeeBatchImportResult result = service.batchImport(List.of(employee));

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getBound()).isEqualTo(1);
        verify(externalIdentityService).upsertPlatformIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-010",
                1L,
                null,
                "sync",
                true
        );
        verify(employeeDepartmentService).replaceDepartments(
                1L,
                "wechat",
                List.of("技术部", "平台研发部")
        );
    }

    @Test
    void batchImportShouldPersistManualDepartmentsWithoutPlatformBinding() {
        EmployeeDepartmentService employeeDepartmentService = mock(EmployeeDepartmentService.class);
        TestableEmployeeServiceImpl service = new TestableEmployeeServiceImpl(
                mock(EncryptionService.class), Set.of(), mock(ExternalIdentityService.class), employeeDepartmentService);
        Employee employee = employee("EMP011", "李四", null);
        employee.setDepartments(List.of("财务部", "共享服务部"));

        EmployeeBatchImportResult result = service.batchImport(List.of(employee));

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getBound()).isZero();
        verify(employeeDepartmentService).replaceDepartments(
                1L,
                EmployeeDepartmentService.MANUAL_PLATFORM,
                List.of("财务部", "共享服务部")
        );
    }

    private static Employee employee(String employeeId, String name, String idCard) {
        Employee employee = new Employee();
        employee.setEmployeeId(employeeId);
        employee.setName(name);
        employee.setEncryptedIdCard(idCard);
        return employee;
    }

    private static class TestableEmployeeServiceImpl extends EmployeeServiceImpl {
        private final Set<String> existingEmployeeIds;
        private final List<Employee> savedEmployees = new ArrayList<>();
        private int saveBatchCalls;

        private TestableEmployeeServiceImpl(EncryptionService encryptionService, Set<String> existingEmployeeIds) {
            this(encryptionService, existingEmployeeIds,
                    mock(ExternalIdentityService.class),
                    mock(EmployeeDepartmentService.class));
        }

        private TestableEmployeeServiceImpl(EncryptionService encryptionService,
                                            Set<String> existingEmployeeIds,
                                            ExternalIdentityService externalIdentityService,
                                            EmployeeDepartmentService employeeDepartmentService) {
            super(
                    encryptionService,
                    mock(ObjectProvider.class),
                    mock(ObjectProvider.class),
                    mock(SysUserService.class),
                    externalIdentityService,
                    mock(ApprovalWorkflowMapper.class),
                    mock(PayrollLineService.class),
                    mock(PayrollBatchService.class),
                    mock(PayCycleService.class),
                    mock(PaymentRecordService.class),
                    mock(VOConverter.class),
                    new ObjectMapper(),
                    employeeDepartmentService,
                    mock(com.yiyundao.compensation.security.DatabasePermissionService.class)
            );
            this.existingEmployeeIds = new HashSet<>(existingEmployeeIds);
        }

        @Override
        public boolean existsByEmployeeId(String employeeId) {
            return existingEmployeeIds.contains(employeeId);
        }

        @Override
        public boolean saveBatch(Collection<Employee> entityList) {
            saveBatchCalls++;
            long nextId = 1L;
            for (Employee employee : entityList) {
                employee.setId(nextId++);
                savedEmployees.add(employee);
            }
            return true;
        }

        @Override
        public long count(Wrapper<Employee> queryWrapper) {
            return 0;
        }
    }
}
