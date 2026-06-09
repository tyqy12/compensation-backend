package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.interfaces.dto.payroll.EmployeePayslipDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollManualImportItemRequest;
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
import com.yiyundao.compensation.enums.PayrollConfirmationStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollImportItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PayrollEndToEndIntegrationTest {

    private static final BigDecimal BASE_AMOUNT = new BigDecimal("10000");
    private static final BigDecimal BONUS_AMOUNT = new BigDecimal("1500");
    private static final BigDecimal DEDUCT_AMOUNT = new BigDecimal("800");
    private static final String SCENARIO_ID = "E2E-20240509";

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
    private PayrollImportService payrollImportService;
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

    @Test
    void persistedPayrollLedgerShouldUseComputedTemplateAndItemSnapshots() {
        assertThat(payrollCalculationService.computeAndSave(batch.getId())).isTrue();
        PayrollLine storedLine = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                        .eq(PayrollLine::getBatchId, batch.getId()))
                .stream()
                .findFirst()
                .orElseThrow();
        assertThat(storedLine.getTemplateId()).isEqualTo(template.getId());

        template.setItemsJson("""
                [
                  {"code":"%s-BASE","required":true,"min":5000,"max":20000},
                  {"code":"%s-NEW","required":true,"min":1,"max":9999}
                ]
                """.formatted(SCENARIO_ID, SCENARIO_ID));
        template.setTaxRuleJson("""
                {
                  "tax": {"rate": 0.9, "applyOn": "gross"},
                  "social": {"rate": 0.4, "applyOn": "gross"},
                  "rounding": {"scale": 2, "mode": "HALF_UP"}
                }
                """);
        salaryTemplateService.updateById(template);

        SalaryItem baseItem = salaryItemService.lambdaQuery()
                .eq(SalaryItem::getCode, SCENARIO_ID + "-BASE")
                .one();
        baseItem.setType("deduction");
        baseItem.setTaxable(false);
        baseItem.setName("改名后的基本工资");
        salaryItemService.updateById(baseItem);

        var ledger = payrollCalculationService.ledger(batch.getId());

        assertThat(ledger.getTaxTotal()).isEqualByComparingTo("1150.00");
        assertThat(ledger.getSocialTotal()).isEqualByComparingTo("575.00");
        assertThat(ledger.getNetTotal()).isEqualByComparingTo("8975.00");
        assertThat(ledger.getHasBlockingIssues()).isFalse();
        assertThat(ledger.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getItems())
                    .filteredOn(item -> (SCENARIO_ID + "-BASE").equals(item.getCode()))
                    .singleElement()
                    .satisfies(item -> {
                        assertThat(item.getName()).isEqualTo("基本工资");
                        assertThat(item.getType()).isEqualTo("earning");
                        assertThat(item.getTaxable()).isTrue();
                    });
            assertThat(line.getMissingItems()).doesNotContain(SCENARIO_ID + "-NEW");
        });
    }

    @Test
    void employeePayslipListShouldHideUnapprovedNoConfirmationBatch() {
        PayrollBatch hiddenBatch = new PayrollBatch();
        hiddenBatch.setPayCycleId(payCycle.getId());
        hiddenBatch.setPeriodLabel("2099-09");
        hiddenBatch.setType("full_time");
        hiddenBatch.setCurrency("CNY");
        hiddenBatch.setStatus(PayrollBatchStatus.LOCKED);
        hiddenBatch.setConfirmationRequired(Boolean.FALSE);
        hiddenBatch.setBatchRevision(1);
        payrollBatchService.save(hiddenBatch);

        PayrollLine hiddenLine = new PayrollLine();
        hiddenLine.setBatchId(hiddenBatch.getId());
        hiddenLine.setEmployeeId(employee.getId());
        hiddenLine.setEmploymentType("full_time");
        hiddenLine.setCurrency("CNY");
        hiddenLine.setGrossAmount(new BigDecimal("12000.00"));
        hiddenLine.setTaxAmount(BigDecimal.ZERO);
        hiddenLine.setSocialAmount(BigDecimal.ZERO);
        hiddenLine.setNetAmount(new BigDecimal("12000.00"));
        hiddenLine.setStatus("calculated");
        hiddenLine.setItemsSnapshotJson("[]");
        payrollLineService.save(hiddenLine);

        var summaries = payslipService.pagePayslips(employeeUser, null, 1, 10);

        assertThat(summaries.getRecords())
                .extracting(EmployeePayslipDto.PayslipSummary::getLineId)
                .doesNotContain(hiddenLine.getId());
    }

    @Test
    void approvedBatchShouldNotBeRecomputedAndOverwriteApprovedPayrollLines() {
        assertThat(payrollCalculationService.computeAndSave(batch.getId())).isTrue();
        PayrollLine approvedLine = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batch.getId()))
                .stream()
                .findFirst()
                .orElseThrow();
        assertThat(approvedLine.getNetAmount()).isEqualByComparingTo("8975.00");

        batch.setStatus(PayrollBatchStatus.APPROVED);
        payrollBatchService.updateById(batch);

        PayrollImportItem adjustment = new PayrollImportItem();
        adjustment.setBatchId(batch.getId());
        adjustment.setEmployeeId(employee.getId());
        adjustment.setItemCode(SCENARIO_ID + "-BONUS");
        adjustment.setAmount(new BigDecimal("9999.00"));
        adjustment.setStatus("valid");
        adjustment.setSourceName("late-import");
        adjustment.setRowNo(99);
        payrollImportItemMapper.insert(adjustment);

        boolean recomputed = payrollCalculationService.computeAndSave(batch.getId());

        PayrollBatch refreshedBatch = payrollBatchService.getById(batch.getId());
        PayrollLine refreshedLine = payrollLineService.getById(approvedLine.getId());
        assertThat(recomputed).isFalse();
        assertThat(refreshedBatch.getStatus()).isEqualTo(PayrollBatchStatus.APPROVED);
        assertThat(refreshedLine.getNetAmount()).isEqualByComparingTo("8975.00");
    }

    @Test
    void confirmedBatchShouldNotBeRecomputedAndResetConfirmedPayslips() {
        assertThat(payrollCalculationService.computeAndSave(batch.getId())).isTrue();
        PayrollLine confirmedLine = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batch.getId()))
                .stream()
                .findFirst()
                .orElseThrow();
        confirmedLine.setConfirmationStatus(PayrollConfirmationStatus.CONFIRMED.getCode());
        payrollLineService.updateById(confirmedLine);
        batch.setStatus(PayrollBatchStatus.CONFIRMED);
        payrollBatchService.updateById(batch);

        PayrollImportItem adjustment = new PayrollImportItem();
        adjustment.setBatchId(batch.getId());
        adjustment.setEmployeeId(employee.getId());
        adjustment.setItemCode(SCENARIO_ID + "-BONUS");
        adjustment.setAmount(new BigDecimal("9999.00"));
        adjustment.setStatus("valid");
        adjustment.setSourceName("late-import");
        adjustment.setRowNo(100);
        payrollImportItemMapper.insert(adjustment);

        boolean recomputed = payrollCalculationService.computeAndSave(batch.getId());

        PayrollBatch refreshedBatch = payrollBatchService.getById(batch.getId());
        PayrollLine refreshedLine = payrollLineService.getById(confirmedLine.getId());
        assertThat(recomputed).isFalse();
        assertThat(refreshedBatch.getStatus()).isEqualTo(PayrollBatchStatus.CONFIRMED);
        assertThat(refreshedLine.getNetAmount()).isEqualByComparingTo("8975.00");
        assertThat(refreshedLine.getConfirmationStatus()).isEqualTo(PayrollConfirmationStatus.CONFIRMED.getCode());
    }

    @Test
    void repeatedCsvCommitShouldReplacePreviousRowsFromSameFile() {
        PayrollBatch csvBatch = new PayrollBatch();
        csvBatch.setPayCycleId(payCycle.getId());
        csvBatch.setPeriodLabel("2099-10");
        csvBatch.setType("full_time");
        csvBatch.setCurrency("CNY");
        csvBatch.setStatus(PayrollBatchStatus.LOCKED);
        payrollBatchService.save(csvBatch);

        insertImportItem(csvBatch.getId(), SCENARIO_ID + "-BASE", new BigDecimal("1000.00"), "payroll.csv", 1);
        insertImportItem(csvBatch.getId(), SCENARIO_ID + "-DEDUCT", new BigDecimal("100.00"), "manual_entry", 2);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payroll.csv",
                "text/csv",
                ("employeeId,itemCode,amount,note\n"
                        + employee.getEmployeeId() + "," + SCENARIO_ID + "-BASE,2000.00,reimport\n").getBytes()
        );

        payrollImportService.commitCsv(csvBatch.getId(), file);
        PayrollPreviewDto preview = payrollCalculationService.dryRunPreview(csvBatch.getId());

        List<PayrollImportItem> items = payrollImportItemMapper.selectList(new LambdaQueryWrapper<PayrollImportItem>()
                .eq(PayrollImportItem::getBatchId, csvBatch.getId())
                .orderByAsc(PayrollImportItem::getSourceName)
                .orderByAsc(PayrollImportItem::getId));
        assertThat(items).hasSize(2);
        assertThat(items)
                .filteredOn(item -> "payroll.csv".equals(item.getSourceName()))
                .singleElement()
                .extracting(PayrollImportItem::getAmount)
                .satisfies(amount -> assertThat((BigDecimal) amount).isEqualByComparingTo("2000.00"));
        assertThat(items)
                .filteredOn(item -> "manual_entry".equals(item.getSourceName()))
                .singleElement()
                .extracting(PayrollImportItem::getAmount)
                .satisfies(amount -> assertThat((BigDecimal) amount).isEqualByComparingTo("100.00"));
        assertThat(preview.getGrossTotal()).isEqualByComparingTo("2000.00");
        assertThat(preview.getDeductionsTotal()).isEqualByComparingTo("100.00");
    }

    @Test
    void csvCommitShouldParseQuotedFieldsWithCommas() {
        PayrollBatch csvBatch = new PayrollBatch();
        csvBatch.setPayCycleId(payCycle.getId());
        csvBatch.setPeriodLabel("2099-12");
        csvBatch.setType("full_time");
        csvBatch.setCurrency("CNY");
        csvBatch.setStatus(PayrollBatchStatus.LOCKED);
        payrollBatchService.save(csvBatch);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payroll-quoted.csv",
                "text/csv",
                ("employeeId,itemCode,amount,note\n"
                        + "\"" + employee.getEmployeeId() + "\",\"" + SCENARIO_ID
                        + "-BASE\",\"2000.00\",\"base, reimported\"\n").getBytes()
        );

        payrollImportService.commitCsv(csvBatch.getId(), file);

        List<PayrollImportItem> items = payrollImportItemMapper.selectList(new LambdaQueryWrapper<PayrollImportItem>()
                .eq(PayrollImportItem::getBatchId, csvBatch.getId()));
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getAmount()).isEqualByComparingTo("2000.00");
            assertThat(item.getNote()).isEqualTo("base, reimported");
            assertThat(item.getRowNo()).isEqualTo(2);
        });
    }

    @Test
    void lockedBatchWithComputedLinesShouldRejectImportMutation() {
        PayrollBatch lockedBatch = new PayrollBatch();
        lockedBatch.setPayCycleId(payCycle.getId());
        lockedBatch.setPeriodLabel("2100-01");
        lockedBatch.setType("full_time");
        lockedBatch.setCurrency("CNY");
        lockedBatch.setStatus(PayrollBatchStatus.LOCKED);
        payrollBatchService.save(lockedBatch);
        insertImportItem(lockedBatch.getId(), SCENARIO_ID + "-BASE", new BigDecimal("1000.00"), "payroll.csv", 1);
        insertComputedLine(lockedBatch.getId());

        PayrollManualImportItemRequest request = new PayrollManualImportItemRequest();
        request.setEmployeeNo(employee.getEmployeeId());
        request.setItemCode(SCENARIO_ID + "-BASE");
        request.setAmount(new BigDecimal("1200.00"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payroll.csv",
                "text/csv",
                ("employeeId,itemCode,amount,note\n"
                        + employee.getEmployeeId() + "," + SCENARIO_ID + "-BASE,1200.00,late change\n").getBytes()
        );
        PayrollImportItem existing = payrollImportItemMapper.selectList(new LambdaQueryWrapper<PayrollImportItem>()
                        .eq(PayrollImportItem::getBatchId, lockedBatch.getId()))
                .stream()
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() -> payrollImportService.commitCsv(lockedBatch.getId(), file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已生成工资结果");
        assertThatThrownBy(() -> payrollImportService.addManualItem(lockedBatch.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已生成工资结果");
        assertThatThrownBy(() -> payrollImportService.updateItem(lockedBatch.getId(), existing.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已生成工资结果");
        assertThatThrownBy(() -> payrollImportService.deleteItem(lockedBatch.getId(), existing.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已生成工资结果");
    }

    @Test
    void failedCsvCommitShouldThrowAndKeepPreviousRowsFromSameFile() {
        PayrollBatch csvBatch = new PayrollBatch();
        csvBatch.setPayCycleId(payCycle.getId());
        csvBatch.setPeriodLabel("2099-11");
        csvBatch.setType("full_time");
        csvBatch.setCurrency("CNY");
        csvBatch.setStatus(PayrollBatchStatus.LOCKED);
        payrollBatchService.save(csvBatch);
        insertImportItem(csvBatch.getId(), SCENARIO_ID + "-BASE", new BigDecimal("1000.00"), "payroll.csv", 1);

        assertThatThrownBy(() -> payrollImportService.commitCsv(csvBatch.getId(), brokenMultipartFile("payroll.csv")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CSV导入失败")
                .hasMessageContaining("broken stream");

        List<PayrollImportItem> items = payrollImportItemMapper.selectList(new LambdaQueryWrapper<PayrollImportItem>()
                .eq(PayrollImportItem::getBatchId, csvBatch.getId()));
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getAmount()).isEqualByComparingTo("1000.00");
    }

    private void setUpEmployee() {
        employee = new Employee();
        employee.setEmployeeId(SCENARIO_ID + "-EMP-1001");
        employee.setName("测试员工");
        employee.setDepartment("Finance");
        employee.setEmploymentType("full_time");
        employee.setBankName("ICBC");
        employee.setBankAccount("6228123412341234");
        employeeService.save(employee);
    }

    private void setUpSalaryItems() {
        SalaryItem base = new SalaryItem();
        base.setCode(SCENARIO_ID + "-BASE");
        base.setName("基本工资");
        base.setType("earning");
        base.setTaxable(true);
        base.setShowOnPayslip(true);
        base.setOrderNum(1);
        base.setStatus("enabled");
        salaryItemService.save(base);

        SalaryItem bonus = new SalaryItem();
        bonus.setCode(SCENARIO_ID + "-BONUS");
        bonus.setName("绩效奖金");
        bonus.setType("earning");
        bonus.setTaxable(true);
        bonus.setShowOnPayslip(true);
        bonus.setOrderNum(2);
        bonus.setStatus("enabled");
        salaryItemService.save(bonus);

        SalaryItem deduct = new SalaryItem();
        deduct.setCode(SCENARIO_ID + "-DEDUCT");
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
        template.setName(SCENARIO_ID + " FT 模板");
        template.setType("full_time");
        template.setItemsJson("""
                [
                  {"code":"%s-BASE","required":true,"min":5000,"max":20000},
                  {"code":"%s-BONUS","required":false,"min":0,"max":5000},
                  {"code":"%s-DEDUCT","required":false,"min":0,"max":5000}
                ]
                """.formatted(SCENARIO_ID, SCENARIO_ID, SCENARIO_ID));
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
        payCycle.setPeriodLabel("2099-08");
        payCycle.setStatus("open");
        payCycleService.save(payCycle);
    }

    private void setUpBatch() {
        batch = new PayrollBatch();
        batch.setPayCycleId(payCycle.getId());
        batch.setPeriodLabel("2099-08");
        batch.setType("full_time");
        batch.setCurrency("CNY");
        batch.setStatus(PayrollBatchStatus.LOCKED);
        payrollBatchService.save(batch);
    }

    private void setUpImportItems() {
        insertImportItem(SCENARIO_ID + "-BASE", BASE_AMOUNT, 1);
        insertImportItem(SCENARIO_ID + "-BONUS", BONUS_AMOUNT, 2);
        insertImportItem(SCENARIO_ID + "-DEDUCT", DEDUCT_AMOUNT, 3);
    }

    private void insertImportItem(String code, BigDecimal amount, int rowNo) {
        insertImportItem(batch.getId(), code, amount, "import", rowNo);
    }

    private void insertImportItem(Long batchId, String code, BigDecimal amount, String sourceName, int rowNo) {
        PayrollImportItem item = new PayrollImportItem();
        item.setBatchId(batchId);
        item.setEmployeeId(employee.getId());
        item.setItemCode(code);
        item.setAmount(amount);
        item.setStatus("valid");
        item.setSourceName(sourceName);
        item.setRowNo(rowNo);
        payrollImportItemMapper.insert(item);
    }

    private void insertComputedLine(Long batchId) {
        PayrollLine line = new PayrollLine();
        line.setBatchId(batchId);
        line.setEmployeeId(employee.getId());
        line.setEmploymentType("full_time");
        line.setCurrency("CNY");
        line.setGrossAmount(new BigDecimal("1000.00"));
        line.setTaxAmount(BigDecimal.ZERO);
        line.setSocialAmount(BigDecimal.ZERO);
        line.setNetAmount(new BigDecimal("1000.00"));
        line.setStatus("calculated");
        line.setItemsSnapshotJson("[]");
        payrollLineService.save(line);
    }

    private void setUpEmployeeUser() {
        employeeUser = new SysUser();
        employeeUser.setUsername(SCENARIO_ID + "-emp-user");
        employeeUser.setPassword("test");
        employeeUser.setRealName("测试员工");
        employeeUser.setRoles("ROLE_EMPLOYEE");
        employeeUser.setEmployeeId(employee.getId());
        employeeUser.setStatus(UserStatus.ACTIVE);
        sysUserService.save(employeeUser);
    }

    private MultipartFile brokenMultipartFile(String filename) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return filename;
            }

            @Override
            public String getContentType() {
                return "text/csv";
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public long getSize() {
                return 1;
            }

            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("broken stream");
            }

            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("broken stream");
            }

            @Override
            public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
                throw new IOException("broken stream");
            }
        };
    }
}
