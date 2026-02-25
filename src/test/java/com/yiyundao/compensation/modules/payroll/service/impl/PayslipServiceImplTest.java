package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayCycleService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PayslipServiceImplTest {

    @Mock
    private PayrollLineService payrollLineService;
    @Mock
    private PayrollBatchService payrollBatchService;
    @Mock
    private PayCycleService payCycleService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PayslipServiceImpl payslipService;

    private SysUser employeeUser;

    @BeforeEach
    void setUp() {
        employeeUser = new SysUser();
        employeeUser.setId(1L);
        employeeUser.setUsername("alice");
        employeeUser.setRoles("ROLE_EMPLOYEE");
        employeeUser.setEmployeeId(99L);
    }

    @Test
    void pagePayslipsReturnsSummaries() {
        PayrollLine line = new PayrollLine();
        line.setId(11L);
        line.setBatchId(22L);
        line.setEmployeeId(99L);
        line.setCurrency("CNY");
        line.setGrossAmount(new BigDecimal("10000"));
        line.setTaxAmount(new BigDecimal("1000"));
        line.setSocialAmount(new BigDecimal("500"));
        line.setNetAmount(new BigDecimal("8500"));
        line.setStatus("calculated");

        Page<PayrollLine> entityPage = new Page<>(1, 10, 1);
        entityPage.setRecords(List.of(line));

        PayrollBatch batch = new PayrollBatch();
        batch.setId(22L);
        batch.setPayCycleId(33L);
        batch.setPeriodLabel("2024-08");
        batch.setCurrency("CNY");

        PayCycle cycle = new PayCycle();
        cycle.setId(33L);
        cycle.setStartDate(LocalDate.of(2024, 8, 1));
        cycle.setEndDate(LocalDate.of(2024, 8, 31));

        Mockito.when(payrollLineService.page(ArgumentMatchers.any(Page.class), ArgumentMatchers.any()))
                .thenReturn(entityPage);
        Mockito.when(payrollBatchService.listByIds(ArgumentMatchers.anyCollection())).thenReturn(List.of(batch));
        Mockito.when(payCycleService.listByIds(ArgumentMatchers.anyCollection())).thenReturn(List.of(cycle));

        var page = payslipService.pagePayslips(employeeUser, null, 1, 10);

        assertThat(page.getRecords()).hasSize(1);
        var summary = page.getRecords().get(0);
        assertThat(summary.getBatchId()).isEqualTo(22L);
        assertThat(summary.getPeriodLabel()).isEqualTo("2024-08");
        assertThat(summary.getPeriodStart()).isEqualTo(LocalDate.of(2024, 8, 1));
        assertThat(summary.getNetAmount()).isEqualByComparingTo("8500");
    }

    @Test
    void detailIncludesMaskedBankAccount() throws Exception {
        PayrollLine line = new PayrollLine();
        line.setId(11L);
        line.setBatchId(22L);
        line.setEmployeeId(99L);
        line.setCurrency("CNY");
        line.setGrossAmount(new BigDecimal("10000"));
        line.setTaxAmount(new BigDecimal("1000"));
        line.setSocialAmount(new BigDecimal("500"));
        line.setNetAmount(new BigDecimal("8500"));
        line.setItemsSnapshotJson("[]");

        PayrollBatch batch = new PayrollBatch();
        batch.setId(22L);
        batch.setPayCycleId(33L);
        batch.setPeriodLabel("2024-08");
        batch.setCurrency("CNY");

        PayCycle cycle = new PayCycle();
        cycle.setId(33L);
        cycle.setStartDate(LocalDate.of(2024, 8, 1));
        cycle.setEndDate(LocalDate.of(2024, 8, 31));

        Employee employee = new Employee();
        employee.setId(99L);
        employee.setEmployeeId("E-100");
        employee.setName("Alice");
        employee.setDepartment("Finance");
        employee.setEmploymentType("full_time");
        employee.setBankName("ICBC");
        employee.setBankAccount("6228123456789999");

        Mockito.when(payrollLineService.getById(11L)).thenReturn(line);
        Mockito.when(payrollBatchService.getById(22L)).thenReturn(batch);
        Mockito.when(payCycleService.getById(33L)).thenReturn(cycle);
        Mockito.when(employeeService.getById(99L)).thenReturn(employee);
        Mockito.when(objectMapper.readValue(Mockito.eq("[]"), ArgumentMatchers.<TypeReference<List<PayrollPreviewDto.PayrollPreviewItemDto>>>any()))
                .thenReturn(List.of());

        var detail = payslipService.getPayslipDetail(employeeUser, 11L);

        assertThat(detail).isNotNull();
        assertThat(detail.getBankAccountMasked()).contains("****");

        byte[] exported = payslipService.exportPayslip(employeeUser, 11L);
        assertThat(new String(exported)).contains("Payslip for Alice");
    }
}
