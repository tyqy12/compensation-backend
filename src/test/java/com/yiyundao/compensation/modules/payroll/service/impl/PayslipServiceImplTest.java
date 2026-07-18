package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayCycleService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.support.PayrollValidationIssueSupport;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.security.DatabasePermissionService;
import com.yiyundao.compensation.service.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Mock
    private PayrollValidationIssueSupport validationIssueSupport;
    @Mock
    private UserRoleService userRoleService;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private DatabasePermissionService databasePermissionService;

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
        ReflectionTestUtils.setField(payslipService, "databasePermissionService", databasePermissionService);
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
        batch.setStatus(PayrollBatchStatus.CONFIRMING);

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
    void pagePayslipsShouldClampPageAndSizeBeforeQuerying() {
        Page<PayrollLine> entityPage = new Page<>(1, 200, 0);
        entityPage.setRecords(List.of());
        Mockito.when(payrollLineService.page(ArgumentMatchers.any(Page.class), ArgumentMatchers.any()))
                .thenReturn(entityPage);

        var page = payslipService.pagePayslips(employeeUser, null, -1, 1000);

        ArgumentCaptor<Page<PayrollLine>> captor = ArgumentCaptor.forClass(Page.class);
        Mockito.verify(payrollLineService).page(captor.capture(), ArgumentMatchers.any(Wrapper.class));
        assertThat(captor.getValue().getCurrent()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(200);
        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(200);
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
        batch.setStatus(PayrollBatchStatus.CONFIRMING);

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
        employee.setBankAccount("encrypted-bank-account");

        Mockito.when(payrollLineService.getById(11L)).thenReturn(line);
        Mockito.when(payrollBatchService.getById(22L)).thenReturn(batch);
        Mockito.when(payCycleService.getById(33L)).thenReturn(cycle);
        Mockito.when(employeeService.getById(99L)).thenReturn(employee);
        Mockito.when(objectMapper.readValue(Mockito.eq("[]"), ArgumentMatchers.<TypeReference<List<PayrollPreviewDto.PayrollPreviewItemDto>>>any()))
                .thenReturn(List.of());
        Mockito.when(validationIssueSupport.deserialize(ArgumentMatchers.isNull())).thenReturn(List.of());
        Mockito.when(validationIssueSupport.toMessages(List.of())).thenReturn(List.of());
        Mockito.when(validationIssueSupport.countBlocking(List.of())).thenReturn(0);
        Mockito.when(validationIssueSupport.countReview(List.of())).thenReturn(0);
        Mockito.when(validationIssueSupport.hasBlocking(List.of())).thenReturn(false);
        Mockito.when(encryptionService.decrypt("encrypted-bank-account")).thenReturn("6228123456789999");
        Mockito.when(encryptionService.maskBankAccount("6228123456789999")).thenReturn("6228****9999");

        var detail = payslipService.getPayslipDetail(employeeUser, 11L);

        assertThat(detail).isNotNull();
        assertThat(detail.getBankAccountMasked()).isEqualTo("6228****9999");

        byte[] exported = payslipService.exportPayslip(employeeUser, 11L);
        assertThat(new String(exported)).contains("Payslip for,Alice");
        assertThat(new String(exported)).contains("Account,6228****9999");
    }

    @Test
    void detailShouldNotExposeEncryptedBankAccountWhenDecryptFails() throws Exception {
        PayrollLine line = new PayrollLine();
        line.setId(16L);
        line.setBatchId(27L);
        line.setEmployeeId(99L);
        line.setItemsSnapshotJson("[]");

        PayrollBatch batch = new PayrollBatch();
        batch.setId(27L);
        batch.setStatus(PayrollBatchStatus.CONFIRMING);

        Employee employee = new Employee();
        employee.setId(99L);
        employee.setName("Alice");
        employee.setBankName("ICBC");
        employee.setBankAccount("AbCdEfGhIjKlMnOpQrStUvWxYz==");

        Mockito.when(payrollLineService.getById(16L)).thenReturn(line);
        Mockito.when(payrollBatchService.getById(27L)).thenReturn(batch);
        Mockito.when(employeeService.getById(99L)).thenReturn(employee);
        Mockito.when(objectMapper.readValue(Mockito.eq("[]"), ArgumentMatchers.<TypeReference<List<PayrollPreviewDto.PayrollPreviewItemDto>>>any()))
                .thenReturn(List.of());
        Mockito.when(validationIssueSupport.deserialize(ArgumentMatchers.isNull())).thenReturn(List.of());
        Mockito.when(validationIssueSupport.toMessages(List.of())).thenReturn(List.of());
        Mockito.when(validationIssueSupport.countBlocking(List.of())).thenReturn(0);
        Mockito.when(validationIssueSupport.countReview(List.of())).thenReturn(0);
        Mockito.when(validationIssueSupport.hasBlocking(List.of())).thenReturn(false);
        Mockito.when(encryptionService.decrypt("AbCdEfGhIjKlMnOpQrStUvWxYz=="))
                .thenThrow(new RuntimeException("decrypt failed"));

        var detail = payslipService.getPayslipDetail(employeeUser, 16L);

        assertThat(detail).isNotNull();
        assertThat(detail.getBankAccountMasked()).isNull();
        Mockito.verify(encryptionService, Mockito.never()).maskBankAccount("AbCdEfGhIjKlMnOpQrStUvWxYz==");
    }

    @Test
    void detailShouldMaskLegacyPlainBankAccountWhenDecryptFails() throws Exception {
        PayrollLine line = new PayrollLine();
        line.setId(17L);
        line.setBatchId(28L);
        line.setEmployeeId(99L);
        line.setItemsSnapshotJson("[]");

        PayrollBatch batch = new PayrollBatch();
        batch.setId(28L);
        batch.setStatus(PayrollBatchStatus.CONFIRMING);

        Employee employee = new Employee();
        employee.setId(99L);
        employee.setName("Alice");
        employee.setBankAccount("6228123456789999");

        Mockito.when(payrollLineService.getById(17L)).thenReturn(line);
        Mockito.when(payrollBatchService.getById(28L)).thenReturn(batch);
        Mockito.when(employeeService.getById(99L)).thenReturn(employee);
        Mockito.when(objectMapper.readValue(Mockito.eq("[]"), ArgumentMatchers.<TypeReference<List<PayrollPreviewDto.PayrollPreviewItemDto>>>any()))
                .thenReturn(List.of());
        Mockito.when(validationIssueSupport.deserialize(ArgumentMatchers.isNull())).thenReturn(List.of());
        Mockito.when(validationIssueSupport.toMessages(List.of())).thenReturn(List.of());
        Mockito.when(validationIssueSupport.countBlocking(List.of())).thenReturn(0);
        Mockito.when(validationIssueSupport.countReview(List.of())).thenReturn(0);
        Mockito.when(validationIssueSupport.hasBlocking(List.of())).thenReturn(false);
        Mockito.when(encryptionService.decrypt("6228123456789999"))
                .thenThrow(new RuntimeException("legacy plaintext"));
        Mockito.when(encryptionService.maskBankAccount("6228123456789999")).thenReturn("6228****9999");

        var detail = payslipService.getPayslipDetail(employeeUser, 17L);

        assertThat(detail).isNotNull();
        assertThat(detail.getBankAccountMasked()).isEqualTo("6228****9999");
    }

    @Test
    void exportPayslipShouldEscapeCsvCellsAndProtectFormulas() throws Exception {
        PayrollLine line = new PayrollLine();
        line.setId(14L);
        line.setBatchId(25L);
        line.setEmployeeId(99L);
        line.setCurrency("CNY");
        line.setGrossAmount(new BigDecimal("10000"));
        line.setTaxAmount(new BigDecimal("1000"));
        line.setSocialAmount(new BigDecimal("500"));
        line.setNetAmount(new BigDecimal("8500"));
        line.setItemsSnapshotJson("formula-items");

        PayrollBatch batch = new PayrollBatch();
        batch.setId(25L);
        batch.setPayCycleId(35L);
        batch.setPeriodLabel("2024-09");
        batch.setCurrency("CNY");
        batch.setStatus(PayrollBatchStatus.CONFIRMING);

        PayCycle cycle = new PayCycle();
        cycle.setId(35L);
        cycle.setStartDate(LocalDate.of(2024, 9, 1));
        cycle.setEndDate(LocalDate.of(2024, 9, 30));

        Employee employee = new Employee();
        employee.setId(99L);
        employee.setEmployeeId("E-100");
        employee.setName("=HYPERLINK(\"http://evil\")");
        employee.setDepartment("Finance");
        employee.setEmploymentType("full_time");
        employee.setBankName("ICBC, Main");
        employee.setBankAccount("6228123456789999");

        PayrollPreviewDto.PayrollPreviewItemDto item = new PayrollPreviewDto.PayrollPreviewItemDto();
        item.setCode("BONUS");
        item.setName("+Bonus, \"Special\"");
        item.setType("earning");
        item.setTaxable(true);
        item.setAmount(new BigDecimal("1000"));

        Mockito.when(payrollLineService.getById(14L)).thenReturn(line);
        Mockito.when(payrollBatchService.getById(25L)).thenReturn(batch);
        Mockito.when(payCycleService.getById(35L)).thenReturn(cycle);
        Mockito.when(employeeService.getById(99L)).thenReturn(employee);
        Mockito.when(objectMapper.readValue(Mockito.eq("formula-items"),
                        ArgumentMatchers.<TypeReference<List<PayrollPreviewDto.PayrollPreviewItemDto>>>any()))
                .thenReturn(List.of(item));
        Mockito.when(validationIssueSupport.deserialize(ArgumentMatchers.isNull())).thenReturn(List.of());
        Mockito.when(validationIssueSupport.toMessages(List.of())).thenReturn(List.of());
        Mockito.when(validationIssueSupport.countBlocking(List.of())).thenReturn(0);
        Mockito.when(validationIssueSupport.countReview(List.of())).thenReturn(0);
        Mockito.when(validationIssueSupport.hasBlocking(List.of())).thenReturn(false);

        String csv = new String(payslipService.exportPayslip(employeeUser, 14L));

        assertThat(csv).contains("Payslip for,\"'=HYPERLINK(\"\"http://evil\"\")\"");
        assertThat(csv).contains("Bank,\"ICBC, Main\"");
        assertThat(csv).contains("BONUS,\"'+Bonus, \"\"Special\"\"\",earning,Y,1000.00");
        assertThat(csv).doesNotContain("\n=HYPERLINK");
        assertThat(csv).doesNotContain(",+Bonus");
    }

    @Test
    void detailShouldRejectEmployeeWhenBatchIsNotVisibleYet() {
        PayrollLine line = new PayrollLine();
        line.setId(12L);
        line.setBatchId(23L);
        line.setEmployeeId(99L);

        PayrollBatch batch = new PayrollBatch();
        batch.setId(23L);
        batch.setStatus(PayrollBatchStatus.LOCKED);
        batch.setConfirmationRequired(Boolean.FALSE);

        Mockito.when(payrollLineService.getById(12L)).thenReturn(line);
        Mockito.when(payrollBatchService.getById(23L)).thenReturn(batch);

        assertThatThrownBy(() -> payslipService.getPayslipDetail(employeeUser, 12L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("工资条暂不可查看");
    }

    @Test
    void financeCanViewNotYetVisibleBatchForAudit() throws Exception {
        SysUser finance = new SysUser();
        finance.setId(2L);
        finance.setUsername("fin");
        finance.setEmployeeId(100L);

        PayrollLine line = new PayrollLine();
        line.setId(13L);
        line.setBatchId(24L);
        line.setEmployeeId(99L);
        line.setItemsSnapshotJson("[]");

        PayrollBatch batch = new PayrollBatch();
        batch.setId(24L);
        batch.setStatus(PayrollBatchStatus.LOCKED);
        batch.setConfirmationRequired(Boolean.FALSE);

        Mockito.when(databasePermissionService.hasCurrentRequestScope(2L, "ALL")).thenReturn(true);
        Mockito.when(payrollLineService.getById(13L)).thenReturn(line);
        Mockito.when(payrollBatchService.getById(24L)).thenReturn(batch);
        Mockito.when(objectMapper.readValue(Mockito.eq("[]"), ArgumentMatchers.<TypeReference<List<PayrollPreviewDto.PayrollPreviewItemDto>>>any()))
                .thenReturn(List.of());
        Mockito.when(validationIssueSupport.deserialize(ArgumentMatchers.isNull())).thenReturn(List.of());
        Mockito.when(validationIssueSupport.toMessages(List.of())).thenReturn(List.of());
        Mockito.when(validationIssueSupport.countBlocking(List.of())).thenReturn(0);
        Mockito.when(validationIssueSupport.countReview(List.of())).thenReturn(0);
        Mockito.when(validationIssueSupport.hasBlocking(List.of())).thenReturn(false);

        var detail = payslipService.getPayslipDetail(finance, 13L);

        assertThat(detail).isNotNull();
        assertThat(detail.getLineId()).isEqualTo(13L);
    }

    @Test
    void financeRoleShouldBeRecognizedWhenRoleCodesAreNormalizedWithoutRolePrefix() throws Exception {
        SysUser finance = new SysUser();
        finance.setId(3L);
        finance.setUsername("fin2");
        finance.setEmployeeId(100L);

        PayrollLine line = new PayrollLine();
        line.setId(15L);
        line.setBatchId(26L);
        line.setEmployeeId(99L);
        line.setItemsSnapshotJson("[]");

        PayrollBatch batch = new PayrollBatch();
        batch.setId(26L);
        batch.setStatus(PayrollBatchStatus.LOCKED);
        batch.setConfirmationRequired(Boolean.FALSE);

        Mockito.when(databasePermissionService.hasCurrentRequestScope(3L, "ALL")).thenReturn(true);
        Mockito.when(payrollLineService.getById(15L)).thenReturn(line);
        Mockito.when(payrollBatchService.getById(26L)).thenReturn(batch);
        Mockito.when(objectMapper.readValue(Mockito.eq("[]"), ArgumentMatchers.<TypeReference<List<PayrollPreviewDto.PayrollPreviewItemDto>>>any()))
                .thenReturn(List.of());
        Mockito.when(validationIssueSupport.deserialize(ArgumentMatchers.isNull())).thenReturn(List.of());
        Mockito.when(validationIssueSupport.toMessages(List.of())).thenReturn(List.of());
        Mockito.when(validationIssueSupport.countBlocking(List.of())).thenReturn(0);
        Mockito.when(validationIssueSupport.countReview(List.of())).thenReturn(0);
        Mockito.when(validationIssueSupport.hasBlocking(List.of())).thenReturn(false);

        var detail = payslipService.getPayslipDetail(finance, 15L);

        assertThat(detail).isNotNull();
        assertThat(detail.getLineId()).isEqualTo(15L);
        Mockito.verify(databasePermissionService, Mockito.atLeastOnce()).hasCurrentRequestScope(3L, "ALL");
    }
}
