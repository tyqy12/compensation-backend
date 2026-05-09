package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollDistributionItemStatus;
import com.yiyundao.compensation.enums.PayrollDistributionStatus;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistributionItem;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.service.AlipayService;
import com.yiyundao.compensation.service.EncryptionService;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PayrollDistributionPaymentIntegrationTest {

    private static final String SCENARIO_ID = "PDS-20240509";

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private PayCycleService payCycleService;
    @Autowired
    private PayrollBatchService payrollBatchService;
    @Autowired
    private PayrollLineService payrollLineService;
    @Autowired
    private PayrollDistributionService distributionService;
    @Autowired
    private PayrollProcessManager payrollProcessManager;
    @Autowired
    private PaymentBatchService paymentBatchService;
    @Autowired
    private PaymentRecordService paymentRecordService;
    @Autowired
    private EncryptionService encryptionService;

    @MockBean
    private AlipayService alipayService;
    @MockBean
    private IntegrationConfigService integrationConfigService;

    private PayrollDistribution distribution;
    private PayrollBatch batch;

    @BeforeEach
    void setUp() throws Exception {
        when(integrationConfigService.isPlatformEnabled("alipay")).thenReturn(true);
        when(alipayService.singleTransfer(anyLong())).thenReturn("ALI_TEST_TRADE");

        Employee employee = new Employee();
        employee.setEmployeeId(SCENARIO_ID + "-EMP-1001");
        employee.setName("发放员工");
        employee.setDepartment("Finance");
        employee.setEmploymentType("full_time");
        employee.setSettlementAccountType("alipay");
        employee.setSettlementAccount(encryptionService.encrypt("payee@example.com"));
        employee.setSettlementAccountName("发放员工");
        employeeService.save(employee);

        PayCycle payCycle = new PayCycle();
        payCycle.setType("monthly");
        payCycle.setPeriodLabel("2099-09");
        payCycle.setStatus("open");
        payCycleService.save(payCycle);

        batch = new PayrollBatch();
        batch.setPayCycleId(payCycle.getId());
        batch.setPeriodLabel("2099-09");
        batch.setType("full_time");
        batch.setCurrency("CNY");
        batch.setStatus(PayrollBatchStatus.APPROVED);
        batch.setConfirmationRequired(Boolean.FALSE);
        batch.setBatchRevision(1);
        payrollBatchService.save(batch);

        PayrollLine line = new PayrollLine();
        line.setBatchId(batch.getId());
        line.setEmployeeId(employee.getId());
        line.setGrossAmount(new BigDecimal("12000.00"));
        line.setNetAmount(new BigDecimal("9600.00"));
        payrollLineService.save(line);

        distribution = distributionService.createOrReuseForBatch(batch);
        distribution.setScheduledDate(LocalDate.now());
        distributionService.updateById(distribution);
    }

    @Test
    void submitDistribution_shouldCreatePaymentAndSyncDistributionCompleted() {
        payrollProcessManager.submitDistribution(distribution.getId());

        PayrollDistribution refreshedDistribution = distributionService.getById(distribution.getId());
        assertThat(refreshedDistribution.getDistributionStatus()).isEqualTo(PayrollDistributionStatus.COMPLETED);
        assertThat(refreshedDistribution.getSuccessCount()).isEqualTo(1);
        assertThat(refreshedDistribution.getFailedCount()).isZero();
        assertThat(refreshedDistribution.getActualAmount()).isEqualByComparingTo("9600.00");

        PayrollBatch refreshedBatch = payrollBatchService.getById(batch.getId());
        assertThat(refreshedBatch.getStatus()).isEqualTo(PayrollBatchStatus.PAID);
        assertThat(refreshedBatch.getPaymentBatchNo()).isNotBlank();

        PaymentBatch paymentBatch = paymentBatchService.getByBatchNo(refreshedBatch.getPaymentBatchNo());
        assertThat(paymentBatch.getDistributionId()).isEqualTo(distribution.getId());
        assertThat(paymentBatch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(paymentBatch.getSuccessCount()).isEqualTo(1);
        assertThat(paymentBatch.getFailedCount()).isZero();

        List<PaymentRecord> records = paymentRecordService.getByBatchNo(paymentBatch.getBatchNo(), null);
        assertThat(records).hasSize(1);
        PaymentRecord record = records.get(0);
        assertThat(record.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(record.getProviderCode()).isEqualTo("alipay");
        assertThat(record.getRecipientAccount()).isEqualTo("payee@example.com");

        List<PayrollDistributionItem> items = distributionService.listActiveItems(distribution.getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getPaymentRecordId()).isEqualTo(record.getId());
        assertThat(items.get(0).getItemStatus()).isEqualTo(PayrollDistributionItemStatus.SUCCESS);

        long reconciliationTasks = distributionService.count(new LambdaQueryWrapper<PayrollDistribution>()
                .eq(PayrollDistribution::getId, distribution.getId()));
        assertThat(reconciliationTasks).isEqualTo(1);
    }
}
