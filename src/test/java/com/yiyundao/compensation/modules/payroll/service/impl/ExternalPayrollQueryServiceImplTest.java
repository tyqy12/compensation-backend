package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.interfaces.dto.openapi.OpenApiPayrollBatchDto;
import com.yiyundao.compensation.interfaces.dto.openapi.OpenApiPayslipDto;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeDepartmentService;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.SalaryItemService;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.service.EncryptionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalPayrollQueryServiceImplTest {

    @Mock
    private PayrollBatchService payrollBatchService;

    @Mock
    private PayrollLineService payrollLineService;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private PaymentBatchService paymentBatchService;

    @Mock
    private SalaryItemService salaryItemService;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private ExternalIdentityService externalIdentityService;
    @Mock
    private EmployeeDepartmentService employeeDepartmentService;

    private ExternalPayrollQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        initTableInfo(ExternalIdentity.class);
        service = new ExternalPayrollQueryServiceImpl(
                payrollBatchService,
                payrollLineService,
                employeeService,
                paymentBatchService,
                salaryItemService,
                new ObjectMapper(),
                encryptionService,
                externalIdentityService,
                employeeDepartmentService
        );
    }

    @Test
    void findBatchShouldHidePartTimeBatchBeforeApprovalOrPaymentVisibility() {
        PayrollBatch batch = batch(101L, PayrollBatchStatus.DRAFT);
        when(payrollBatchService.getById(101L)).thenReturn(batch);

        OpenApiPayrollBatchDto result = service.findBatch(101L);

        assertThat(result).isNull();
        verify(payrollLineService, never()).listMaps(org.mockito.ArgumentMatchers.<Wrapper<PayrollLine>>any());
        verify(paymentBatchService, never()).getByBatchNo(any());
    }

    @Test
    void pageBatchLinesShouldHidePartTimeBatchBeforeApprovalOrPaymentVisibility() {
        PayrollBatch batch = batch(102L, PayrollBatchStatus.CONFIRMING);
        when(payrollBatchService.getById(102L)).thenReturn(batch);

        var result = service.pageBatchLines(102L, null, 1, 20);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
        verify(payrollLineService, never()).page(any(), any());
    }

    @Test
    void pageBatchLinesShouldReturnEmptyWhenEmployeeRefDoesNotResolve() {
        PayrollBatch batch = batch(106L, PayrollBatchStatus.APPROVED);
        when(payrollBatchService.getById(106L)).thenReturn(batch);
        when(employeeService.getByEmployeeId("MISSING")).thenReturn(null);

        var result = service.pageBatchLines(106L, "emp:MISSING", 1, 20);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
        verify(payrollLineService, never()).page(any(), any());
    }

    @Test
    void pageBatchLinesShouldRejectInactiveEmployeeNoRef() {
        PayrollBatch batch = batch(109L, PayrollBatchStatus.APPROVED);
        Employee inactiveEmployee = employee(301L, "E01");
        inactiveEmployee.setStatus("inactive");
        when(payrollBatchService.getById(109L)).thenReturn(batch);
        when(employeeService.getByEmployeeId("E01")).thenReturn(inactiveEmployee);

        var result = service.pageBatchLines(109L, "emp:E01", 1, 20);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
        verify(payrollLineService, never()).page(any(), any());
    }

    @Test
    void findPayslipShouldHideLineWhenBatchIsNotExternallyVisible() {
        PayrollLine line = line(201L, 103L);
        when(payrollLineService.getById(201L)).thenReturn(line);
        when(employeeService.getByEmployeeId("E01")).thenReturn(employee(301L, "E01"));
        when(payrollBatchService.getById(103L)).thenReturn(batch(103L, PayrollBatchStatus.REJECTED));

        OpenApiPayslipDto result = service.findPayslip(201L, "emp:E01");

        assertThat(result).isNull();
        verify(salaryItemService, never()).list(org.mockito.ArgumentMatchers.<Wrapper<com.yiyundao.compensation.modules.payroll.entity.SalaryItem>>any());
    }

    @Test
    void findPayslipShouldRequireEmployeeRefToOwnPayslip() {
        PayrollLine line = line(202L, 104L);
        PayrollBatch batch = batch(104L, PayrollBatchStatus.APPROVED);
        batch.setPeriodLabel("2026-05");
        when(employeeService.getByEmployeeId("E02")).thenReturn(employee(302L, "E02"));
        when(payrollLineService.getById(202L)).thenReturn(line);

        OpenApiPayslipDto result = service.findPayslip(202L, "emp:E02");

        assertThat(result).isNull();
        verify(payrollBatchService, never()).getById(any());
        verify(salaryItemService, never()).list(org.mockito.ArgumentMatchers.<Wrapper<com.yiyundao.compensation.modules.payroll.entity.SalaryItem>>any());
    }

    @Test
    void findPayslipShouldResolveEmpPrefixAsEmployeeNo() {
        PayrollLine line = line(203L, 105L);
        PayrollBatch batch = batch(105L, PayrollBatchStatus.APPROVED);
        batch.setPeriodLabel("2026-05");
        Employee employee = employee(301L, "E01");
        when(employeeService.getByEmployeeId("E01")).thenReturn(employee);
        when(payrollLineService.getById(203L)).thenReturn(line);
        when(payrollBatchService.getById(105L)).thenReturn(batch);
        when(employeeService.getById(301L)).thenReturn(employee);

        OpenApiPayslipDto result = service.findPayslip(203L, "emp:E01");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(203L);
        assertThat(result.getEmployeeRef()).isEqualTo("emp:E01");
    }

    @Test
    void findPayslipShouldResolveExplicitTenantPlatformRef() {
        PayrollLine line = line(204L, 106L);
        PayrollBatch batch = batch(106L, PayrollBatchStatus.APPROVED);
        batch.setPeriodLabel("2026-05");
        Employee employee = activeEmployee(301L, "E01");
        ExternalIdentity identity = identity("wechat", "corp-a", "wx-employee", 301L);
        when(externalIdentityService.list(org.mockito.ArgumentMatchers.<Wrapper<ExternalIdentity>>any()))
                .thenReturn(List.of(identity));
        when(employeeService.getById(301L)).thenReturn(employee);
        when(payrollLineService.getById(204L)).thenReturn(line);
        when(payrollBatchService.getById(106L)).thenReturn(batch);
        when(externalIdentityService.findPrimaryByEmployeeId(301L)).thenReturn(identity);

        OpenApiPayslipDto result = service.findPayslip(204L, "wechat:corp-a:wx-employee");

        assertThat(result).isNotNull();
        assertThat(result.getEmployeeRef()).isEqualTo("wechat:corp-a:wx-employee");
    }

    @Test
    void pageBatchLinesShouldRejectAmbiguousPlatformRefWithoutTenant() {
        PayrollBatch batch = batch(107L, PayrollBatchStatus.APPROVED);
        when(payrollBatchService.getById(107L)).thenReturn(batch);
        when(externalIdentityService.list(org.mockito.ArgumentMatchers.<Wrapper<ExternalIdentity>>any()))
                .thenReturn(List.of(
                        identity("wechat", "corp-a", "wx-shared", 301L),
                        identity("wechat", "corp-b", "wx-shared", 302L)
                ));

        var result = service.pageBatchLines(107L, "wechat:wx-shared", 1, 20);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
        verify(payrollLineService, never()).page(any(), any());
    }

    @Test
    void findPayslipShouldExposeTenantInEmployeeRefForNonDefaultPrimaryIdentity() {
        PayrollLine line = line(205L, 108L);
        PayrollBatch batch = batch(108L, PayrollBatchStatus.APPROVED);
        batch.setPeriodLabel("2026-05");
        Employee employee = employee(301L, "E01");
        ExternalIdentity identity = identity("wechat", "corp-a", "wx-primary", 301L);
        when(employeeService.getByEmployeeId("E01")).thenReturn(employee);
        when(payrollLineService.getById(205L)).thenReturn(line);
        when(payrollBatchService.getById(108L)).thenReturn(batch);
        when(employeeService.getById(301L)).thenReturn(employee);
        when(externalIdentityService.findPrimaryByEmployeeId(301L)).thenReturn(identity);

        OpenApiPayslipDto result = service.findPayslip(205L, "emp:E01");

        assertThat(result).isNotNull();
        assertThat(result.getEmployeeRef()).isEqualTo("wechat:corp-a:wx-primary");
    }

    @Test
    void findBatchShouldExposeApprovedPartTimeBatch() {
        PayrollBatch batch = batch(104L, PayrollBatchStatus.APPROVED);
        batch.setPeriodLabel("2026-05");
        batch.setCurrency("CNY");
        when(payrollBatchService.getById(104L)).thenReturn(batch);
        when(payrollLineService.listMaps(org.mockito.ArgumentMatchers.<Wrapper<PayrollLine>>any()))
                .thenReturn(java.util.List.of());

        OpenApiPayrollBatchDto result = service.findBatch(104L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(104L);
        assertThat(result.getStatus()).isEqualTo(PayrollBatchStatus.APPROVED.getCode());
    }

    @Test
    void findBatchShouldNotExposePaidAtBeforePaymentSuccess() {
        PayrollBatch batch = batch(110L, PayrollBatchStatus.APPROVED);
        batch.setPaymentBatchNo("PB-APPROVED-110");
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-APPROVED-110");
        paymentBatch.setStatus(BatchStatus.APPROVED);
        paymentBatch.setPaymentStatus(PaymentBatchProcessStatus.SUBMITTED);
        paymentBatch.setApproveTime(LocalDateTime.of(2026, 6, 1, 10, 0));

        when(payrollBatchService.getById(110L)).thenReturn(batch);
        when(payrollLineService.listMaps(org.mockito.ArgumentMatchers.<Wrapper<PayrollLine>>any()))
                .thenReturn(java.util.List.of());
        when(paymentBatchService.getByBatchNo("PB-APPROVED-110")).thenReturn(paymentBatch);

        OpenApiPayrollBatchDto result = service.findBatch(110L);

        assertThat(result).isNotNull();
        assertThat(result.getPaidAt()).isNull();
    }

    @Test
    void findBatchShouldExposePaidAtOnlyWhenPaymentBatchSucceeded() {
        PayrollBatch batch = batch(111L, PayrollBatchStatus.PAID);
        batch.setPaymentBatchNo("PB-PAID-111");
        LocalDateTime processEndTime = LocalDateTime.of(2026, 6, 2, 11, 30);
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-PAID-111");
        paymentBatch.setStatus(BatchStatus.COMPLETED);
        paymentBatch.setPaymentStatus(PaymentBatchProcessStatus.SUCCESS);
        paymentBatch.setProcessEndTime(processEndTime);

        when(payrollBatchService.getById(111L)).thenReturn(batch);
        when(payrollLineService.listMaps(org.mockito.ArgumentMatchers.<Wrapper<PayrollLine>>any()))
                .thenReturn(java.util.List.of());
        when(paymentBatchService.getByBatchNo("PB-PAID-111")).thenReturn(paymentBatch);

        OpenApiPayrollBatchDto result = service.findBatch(111L);

        assertThat(result).isNotNull();
        assertThat(result.getPaidAt()).isEqualTo(processEndTime);
    }

    private PayrollBatch batch(Long id, PayrollBatchStatus status) {
        PayrollBatch batch = new PayrollBatch();
        batch.setId(id);
        batch.setType("part_time");
        batch.setStatus(status);
        return batch;
    }

    private PayrollLine line(Long id, Long batchId) {
        PayrollLine line = new PayrollLine();
        line.setId(id);
        line.setBatchId(batchId);
        line.setEmployeeId(301L);
        line.setEmploymentType("part_time");
        line.setGrossAmount(BigDecimal.TEN);
        line.setTaxAmount(BigDecimal.ZERO);
        line.setSocialAmount(BigDecimal.ZERO);
        line.setNetAmount(BigDecimal.TEN);
        line.setCurrency("CNY");
        return line;
    }

    private Employee employee(Long id, String employeeNo) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmployeeId(employeeNo);
        employee.setName("张三");
        employee.setEmploymentType("part_time");
        employee.setStatus("active");
        return employee;
    }

    private static void initTableInfo(Class<?> entityType) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, entityType.getName());
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    private Employee activeEmployee(Long id, String employeeNo) {
        Employee employee = employee(id, employeeNo);
        employee.setStatus("active");
        return employee;
    }

    private ExternalIdentity identity(String provider, String tenantKey, String subjectId, Long employeeId) {
        ExternalIdentity identity = new ExternalIdentity();
        identity.setProvider(provider);
        identity.setTenantKey(tenantKey);
        identity.setSubjectType(ExternalIdentityService.DEFAULT_SUBJECT_TYPE);
        identity.setSubjectId(subjectId);
        identity.setEmployeeId(employeeId);
        identity.setStatus(ExternalIdentityService.STATUS_ACTIVE);
        identity.setPrimaryFlag(true);
        return identity;
    }
}
