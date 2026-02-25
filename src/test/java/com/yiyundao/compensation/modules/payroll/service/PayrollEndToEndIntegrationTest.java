package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.interfaces.dto.payroll.EmployeePayslipDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollImportItem;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.entity.SalaryItem;
import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplate;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.enums.UserStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollImportItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Disabled("Manual end-to-end scenario used during UAT")
class PayrollEndToEndIntegrationTest {

    private static final BigDecimal BASE_AMOUNT = new BigDecimal("10000");
    private static final BigDecimal BONUS_AMOUNT = new BigDecimal("1500");
    private static final BigDecimal DEDUCT_AMOUNT = new BigDecimal("800");

    @Autowired
    private PayrollCalculationService payrollCalculationService;
    @Autowired
    private PayrollBatchService payrollBatchService;
    @Autowired
    private PayrollLineService payrollLineService;
    @Autowired
    private PayrollReportService payrollReportService;
    @Autowired
    private PayslipService payslipService;
    @Autowired
    private PayrollImportItemMapper payrollImportItemMapper;
    @Autowired
    private SalaryItemService salaryItemService;
    @Autowired
    private SalaryTemplateService salaryTemplateService;
    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private PayCycleService payCycleService;
    @Autowired
    private SysUserService sysUserService;

    private Employee employee;
    private PayCycle payCycle;
    private SalaryTemplate template;
    private PayrollBatch batch;
    private SysUser employeeUser;

    @BeforeEach
    void setUp() {
        setUpEmployee();
        setUpSalaryItems();
        setUpTemplate();
        setUpPayCycle();
        setUpBatch();
        setUpImportItems();
        setUpEmployeeUser();
    }

    @Test
    void fullPayrollLifecycleProducesConsistentResults() {
        PayrollPreviewDto preview = payrollCalculationService.dryRunPreview(batch.getId());
        assertThat(preview).isNotNull();
        assertThat(preview.getTotalEmployees()).isEqualTo(1);
        assertThat(preview.getGrossTotal()).isEqualByComparingTo(BASE_AMOUNT.add(BONUS_AMOUNT));
        assertThat(preview.getDeductionsTotal()).isEqualByComparingTo(DEDUCT_AMOUNT);
        assertThat(preview.getTaxTotal()).isEqualByComparingTo("1150.00");
        assertThat(preview.getSocialTotal()).isEqualByComparingTo("575.00");
        assertThat(preview.getNetTotal()).isEqualByComparingTo("8975.00");

        boolean persisted = payrollCalculationService.computeAndSave(batch.getId());
        assertThat(persisted).isTrue();

        List<PayrollLine> stored = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batch.getId()));
        assertThat(stored).hasSize(1);
        PayrollLine line = stored.get(0);
        assertThat(line.getGrossAmount()).isEqualByComparingTo("11500.00");
        assertThat(line.getNetAmount()).isEqualByComparingTo("8975.00");

        var report = payrollReportService.basicReport(batch.getId(), null, null);
        assertThat(report.getEmployeeCount()).isEqualTo(1);
        assertThat(report.getGrossTotal()).isEqualByComparingTo("11500.00");
        assertThat(report.getNetTotal()).isEqualByComparingTo("8975.00");

        EmployeePayslipDto.PayslipDetail detail = payslipService.getPayslipDetail(employeeUser, line.getId());
        assertThat(detail.getItems()).hasSize(3);
        assertThat(detail.getNetAmount()).isEqualByComparingTo("8975.00");
        assertThat(detail.getBankAccountMasked()).contains("****");

        var summaries = payslipService.pagePayslips(employeeUser, null, 1, 10);
        assertThat(summaries.getRecords()).hasSize(1);
        assertThat(summaries.getRecords().get(0).getNetAmount()).isEqualByComparingTo("8975.00");
    }

    private void setUpEmployee() {
        employee = new Employee();
        employee.setEmployeeId("EMP-1001");
        employee.setName("测试员工");
        employee.setDepartment("Finance");
        employee.setEmploymentType("full_time");
        employee.setBankName("ICBC");
        employee.setBankAccount("6228123412341234");
        employeeService.save(employee);
    }

    private void setUpSalaryItems() {
        SalaryItem base = new SalaryItem();
        base.setCode("BASE");
        base.setName("基本工资");
        base.setType("earning");
        base.setTaxable(true);
        base.setShowOnPayslip(true);
        base.setOrderNum(1);
        base.setStatus("enabled");
        salaryItemService.save(base);

        SalaryItem bonus = new SalaryItem();
        bonus.setCode("BONUS");
        bonus.setName("绩效奖金");
        bonus.setType("earning");
        bonus.setTaxable(true);
        bonus.setShowOnPayslip(true);
        bonus.setOrderNum(2);
        bonus.setStatus("enabled");
        salaryItemService.save(bonus);

        SalaryItem deduct = new SalaryItem();
        deduct.setCode("DEDUCT");
        deduct.setName("个人扣款");
        deduct.setType("deduction");
        deduct.setTaxable(false);
        deduct.setShowOnPayslip(true);
        deduct.setOrderNum(3);
        deduct.setStatus("enabled");
        salaryItemService.save(deduct);
    }

    private void setUpTemplate() {
        template = new SalaryTemplate();
        template.setName("FT 模板");
        template.setType("full_time");
        template.setItemsJson("""
                [
                  {"code":"BASE","required":true,"min":5000,"max":20000},
                  {"code":"BONUS","required":false,"min":0,"max":5000},
                  {"code":"DEDUCT","required":false,"min":0,"max":5000}
                ]
                """);
        template.setTaxRuleJson("""
                {
                  "tax": {"rate": 0.1, "applyOn": "taxableEarnings"},
                  "social": {"rate": 0.05, "applyOn": "gross"},
                  "rounding": {"scale": 2, "mode": "HALF_UP"}
                }
                """);
        template.setStatus("enabled");
        salaryTemplateService.save(template);
    }

    private void setUpPayCycle() {
        payCycle = new PayCycle();
        payCycle.setType("monthly");
        payCycle.setPeriodLabel("2024-08");
        payCycle.setStatus("open");
        payCycleService.save(payCycle);
    }

    private void setUpBatch() {
        batch = new PayrollBatch();
        batch.setPayCycleId(payCycle.getId());
        batch.setPeriodLabel("2024-08");
        batch.setType("full_time");
        batch.setCurrency("CNY");
        batch.setStatus(PayrollBatchStatus.LOCKED);
        payrollBatchService.save(batch);
    }

    private void setUpImportItems() {
        insertImportItem("BASE", BASE_AMOUNT, 1);
        insertImportItem("BONUS", BONUS_AMOUNT, 2);
        insertImportItem("DEDUCT", DEDUCT_AMOUNT, 3);
    }

    private void insertImportItem(String code, BigDecimal amount, int rowNo) {
        PayrollImportItem item = new PayrollImportItem();
        item.setBatchId(batch.getId());
        item.setEmployeeId(employee.getId());
        item.setItemCode(code);
        item.setAmount(amount);
        item.setStatus("valid");
        item.setSourceName("import");
        item.setRowNo(rowNo);
        payrollImportItemMapper.insert(item);
    }

    private void setUpEmployeeUser() {
        employeeUser = new SysUser();
        employeeUser.setUsername("emp-user");
        employeeUser.setPassword("test");
        employeeUser.setRealName("测试员工");
        employeeUser.setRoles("ROLE_EMPLOYEE");
        employeeUser.setEmployeeId(employee.getId());
        employeeUser.setStatus(UserStatus.ACTIVE);
        sysUserService.save(employeeUser);
    }
}
