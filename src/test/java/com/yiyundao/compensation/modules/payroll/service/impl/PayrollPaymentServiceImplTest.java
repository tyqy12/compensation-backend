package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.util.TransactionAfterCommitExecutor;
import com.yiyundao.compensation.enums.EmploymentType;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollConfirmationStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollDistributionItemMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollDistributionMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistributionItem;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollCalculationService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentFailureService;
import com.yiyundao.compensation.modules.payroll.support.PayrollValidationIssueSupport;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.service.EncryptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class PayrollPaymentServiceImplTest {

    @Mock
    private PaymentBatchService paymentBatchService;
    @Mock
    private PaymentRecordService paymentRecordService;
    @Mock
    private PayrollLineService payrollLineService;
    @Mock
    private PayrollCalculationService payrollCalculationService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private SettlementService settlementService;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private PayrollBatchMapper payrollBatchMapper;
    @Mock
    private PayrollDistributionItemMapper payrollDistributionItemMapper;
    @Mock
    private PayrollDistributionMapper payrollDistributionMapper;
    @Mock
    private TransactionAfterCommitExecutor afterCommitExecutor;
    @Mock
    private PayrollPaymentFailureService payrollPaymentFailureService;
    @Mock
    private PayrollValidationIssueSupport validationIssueSupport;

    @Test
    void createPaymentBatchShouldReservePayrollBatchBeforePersistingPaymentRows() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        when(payrollLineService.list(anyPayrollLineWrapper())).thenReturn(List.of(payrollLine()));
        when(employeeService.getById(1001L)).thenReturn(employee());
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        when(paymentBatchService.save(any(PaymentBatch.class))).thenReturn(true);
        when(paymentRecordService.saveBatch(any(Collection.class))).thenReturn(true);

        PaymentBatch result = service.createPaymentBatch(payrollBatch, approver(), false);

        assertThat(result).isNotNull();
        assertThat(result.getBatchNo()).startsWith("PAYROLL-10-");
        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(payrollBatch.getPaymentBatchNo()).isEqualTo(result.getBatchNo());
        assertThat(payrollBatch.getStatus()).isEqualTo(PayrollBatchStatus.PAY_PROCESSING);

        InOrder inOrder = inOrder(payrollBatchMapper, paymentBatchService, paymentRecordService);
        inOrder.verify(payrollBatchMapper).update(eq(null), any(UpdateWrapper.class));
        inOrder.verify(paymentBatchService).save(argThat(batch -> batch.getBatchNo().equals(result.getBatchNo())));
        inOrder.verify(paymentRecordService).saveBatch(argThat(records -> {
            PaymentRecord record = (PaymentRecord) records.iterator().next();
            return records.size() == 1
                    && result.getBatchNo().equals(record.getBatchNo())
                    && record.getStatus() == PaymentStatus.PENDING;
        }));

        ArgumentCaptor<UpdateWrapper<PayrollBatch>> payrollBatchUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollBatchMapper).update(eq(null), payrollBatchUpdateCaptor.capture());
        payrollBatchUpdateCaptor.getValue().getSqlSegment();
        assertThat(payrollBatchUpdateCaptor.getValue().getParamNameValuePairs().values())
                .contains(10L, PayrollBatchStatus.APPROVED.getCode(), result.getBatchNo(),
                        PayrollBatchStatus.PAY_PROCESSING.getCode());
    }

    @Test
    void createPaymentBatchShouldKeepConfiguredProviderForFullTimeAlipayAccount() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        Employee employee = employee();
        employee.setSettlementProviderCode("yunzhanghu");
        when(payrollLineService.list(anyPayrollLineWrapper())).thenReturn(List.of(payrollLine()));
        when(employeeService.getById(1001L)).thenReturn(employee);
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        when(paymentBatchService.save(any(PaymentBatch.class))).thenReturn(true);
        when(paymentRecordService.saveBatch(any(Collection.class))).thenReturn(true);

        service.createPaymentBatch(payrollBatch, approver(), false);

        verify(paymentRecordService).saveBatch(argThat(records -> {
            PaymentRecord record = (PaymentRecord) records.iterator().next();
            return record.getStatus() == PaymentStatus.PENDING
                    && "ALIPAY".equals(record.getPaymentMethod())
                    && "yunzhanghu".equals(record.getProviderCode());
        }));
    }

    @Test
    void createPaymentBatchShouldForceAlipayProviderForFullTimeBankCardAccount() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        Employee employee = employee();
        employee.setEmail(null);
        employee.setSettlementAccountType("bank_card");
        employee.setSettlementAccount("ENC_BANK");
        employee.setSettlementProviderCode("yunzhanghu");

        when(payrollLineService.list(anyPayrollLineWrapper())).thenReturn(List.of(payrollLine()));
        when(employeeService.getById(1001L)).thenReturn(employee);
        when(encryptionService.decrypt("ENC_BANK")).thenReturn("6222020202020202");
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        when(paymentBatchService.save(any(PaymentBatch.class))).thenReturn(true);
        when(paymentRecordService.saveBatch(any(Collection.class))).thenReturn(true);

        service.createPaymentBatch(payrollBatch, approver(), false);

        verify(paymentRecordService).saveBatch(argThat(records -> {
            PaymentRecord record = (PaymentRecord) records.iterator().next();
            return record.getStatus() == PaymentStatus.PENDING
                    && "BANK_CARD".equals(record.getPaymentMethod())
                    && "alipay".equals(record.getProviderCode())
                    && "6222020202020202".equals(record.getRecipientAccount());
        }));
    }

    @Test
    void createPaymentBatchShouldMarkRecordFailedWhenEncryptedAccountCannotBeDecrypted() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        Employee employee = employee();
        employee.setPhone("13800000000");
        employee.setSettlementAccountType("bank_card");
        employee.setSettlementAccount("ENC_BANK_VALUE");

        when(payrollLineService.list(anyPayrollLineWrapper())).thenReturn(List.of(payrollLine()));
        when(employeeService.getById(1001L)).thenReturn(employee);
        when(encryptionService.decrypt("ENC_BANK_VALUE")).thenThrow(new RuntimeException("bad key"));
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        when(paymentBatchService.save(any(PaymentBatch.class))).thenReturn(true);
        when(paymentRecordService.saveBatch(any(Collection.class))).thenReturn(true);

        PaymentBatch result = service.createPaymentBatch(payrollBatch, approver(), false);

        assertThat(result.getStatus()).isEqualTo(com.yiyundao.compensation.enums.BatchStatus.FAILED);
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentBatchProcessStatus.FAILED);
        verify(paymentRecordService).saveBatch(argThat(records -> {
            PaymentRecord record = (PaymentRecord) records.iterator().next();
            return record.getStatus() == PaymentStatus.FAILED
                    && "ACCOUNT_DECRYPT_FAILED".equals(record.getErrorCode())
                    && record.getRecipientAccount() == null;
        }));
        verify(afterCommitExecutor, never()).execute(any());
    }

    @Test
    void createPaymentBatchShouldKeepLegacyPlainAccountWhenDecryptFails() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        Employee employee = employee();
        employee.setEmail(null);
        employee.setPhone(null);
        employee.setSettlementAccountType("alipay");
        employee.setSettlementAccount("13800000000");

        when(payrollLineService.list(anyPayrollLineWrapper())).thenReturn(List.of(payrollLine()));
        when(employeeService.getById(1001L)).thenReturn(employee);
        when(encryptionService.decrypt("13800000000")).thenThrow(new RuntimeException("legacy plaintext"));
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        when(paymentBatchService.save(any(PaymentBatch.class))).thenReturn(true);
        when(paymentRecordService.saveBatch(any(Collection.class))).thenReturn(true);

        PaymentBatch result = service.createPaymentBatch(payrollBatch, approver(), false);

        assertThat(result.getStatus()).isEqualTo(com.yiyundao.compensation.enums.BatchStatus.SUBMITTED);
        verify(paymentRecordService).saveBatch(argThat(records -> {
            PaymentRecord record = (PaymentRecord) records.iterator().next();
            return record.getStatus() == PaymentStatus.PENDING
                    && "13800000000".equals(record.getRecipientAccount())
                    && "ALIPAY".equals(record.getPaymentMethod());
        }));
    }

    @Test
    void createPaymentBatchShouldLinkCurrentDistributionAndPaymentRecord() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        payrollBatch.setBatchRevision(2);
        PayrollDistribution distribution = new PayrollDistribution();
        distribution.setId(55L);
        distribution.setBatchId(10L);
        distribution.setBatchRevision(2);
        distribution.setDistributionStatus(com.yiyundao.compensation.enums.PayrollDistributionStatus.PLANNED);
        distribution.setCurrentAttempt(0);
        PayrollDistributionItem item = new PayrollDistributionItem();
        item.setId(701L);
        item.setDistributionId(55L);
        item.setLineId(2001L);
        item.setItemStatus(com.yiyundao.compensation.enums.PayrollDistributionItemStatus.PENDING);
        item.setAmount(new BigDecimal("9600.00"));

        when(payrollLineService.list(anyPayrollLineWrapper())).thenReturn(List.of(payrollLine()));
        when(employeeService.getById(1001L)).thenReturn(employee());
        when(payrollDistributionMapper.selectOne(any(Wrapper.class))).thenReturn(distribution);
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        when(paymentBatchService.save(any(PaymentBatch.class))).thenReturn(true);
        doAnswer(invocation -> {
            Collection<PaymentRecord> records = invocation.getArgument(0);
            records.iterator().next().setId(501L);
            return true;
        }).when(paymentRecordService).saveBatch(any(Collection.class));
        when(payrollDistributionItemMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        when(payrollDistributionItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
        when(payrollDistributionMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);

        PaymentBatch result = service.createPaymentBatch(payrollBatch, approver(), false);

        assertThat(result.getDistributionId()).isEqualTo(55L);
        ArgumentCaptor<PaymentBatch> paymentBatchCaptor = ArgumentCaptor.forClass(PaymentBatch.class);
        verify(paymentBatchService).save(paymentBatchCaptor.capture());
        assertThat(paymentBatchCaptor.getValue().getDistributionId()).isEqualTo(55L);

        ArgumentCaptor<UpdateWrapper<PayrollDistributionItem>> itemUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollDistributionItemMapper).update(eq(null), itemUpdateCaptor.capture());
        itemUpdateCaptor.getValue().getSqlSegment();
        assertThat(itemUpdateCaptor.getValue().getParamNameValuePairs().values())
                .contains(55L, 2001L, 501L, "pending", 0);

        ArgumentCaptor<UpdateWrapper<PayrollDistribution>> distributionUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollDistributionMapper).update(eq(null), distributionUpdateCaptor.capture());
        distributionUpdateCaptor.getValue().getSqlSegment();
        assertThat(distributionUpdateCaptor.getValue().getParamNameValuePairs().values())
                .contains(55L, "processing", 1, 0, 0);
    }

    @Test
    void createPaymentBatchShouldReturnConcurrentExistingBatchWhenReservationFails() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        PayrollBatch latest = payrollBatch();
        latest.setPaymentBatchNo("PAYROLL-10-existing");
        PaymentBatch existing = new PaymentBatch();
        existing.setBatchNo("PAYROLL-10-existing");

        when(payrollLineService.list(anyPayrollLineWrapper())).thenReturn(List.of(payrollLine()));
        when(employeeService.getById(1001L)).thenReturn(employee());
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(0);
        when(payrollBatchMapper.selectById(10L)).thenReturn(latest);
        when(paymentBatchService.getByBatchNo("PAYROLL-10-existing")).thenReturn(existing);

        PaymentBatch result = service.createPaymentBatch(payrollBatch, approver(), false);

        assertThat(result).isSameAs(existing);
        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
        verify(paymentRecordService, never()).saveBatch(any(Collection.class));
        verify(afterCommitExecutor, never()).execute(any());
    }

    @Test
    void createPaymentBatchShouldRejectWhenDatabaseStatusChangedBeforeReservation() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        PayrollBatch latest = payrollBatch();
        latest.setStatus(PayrollBatchStatus.CONFIRMED);

        when(payrollLineService.list(anyPayrollLineWrapper())).thenReturn(List.of(payrollLine()));
        when(employeeService.getById(1001L)).thenReturn(employee());
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(0);
        when(payrollBatchMapper.selectById(10L)).thenReturn(latest);

        assertThatThrownBy(() -> service.createPaymentBatch(payrollBatch, approver(), true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前薪资批次状态不可创建支付批次");

        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
        verify(paymentRecordService, never()).saveBatch(any(Collection.class));
        verify(afterCommitExecutor, never()).execute(any());
    }

    @Test
    void createPaymentBatchShouldRejectBatchThatIsNotApproved() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        payrollBatch.setStatus(PayrollBatchStatus.CONFIRMED);

        assertThatThrownBy(() -> service.createPaymentBatch(payrollBatch, approver(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已审批薪资批次可创建支付批次");

        verify(payrollLineService, never()).list(anyPayrollLineWrapper());
        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
        verify(paymentRecordService, never()).saveBatch(any(Collection.class));
    }

    @Test
    void createPaymentBatchShouldRejectUnconfirmedLinesWhenConfirmationIsRequired() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        payrollBatch.setConfirmationRequired(Boolean.TRUE);
        PayrollLine pendingLine = payrollLine();
        pendingLine.setConfirmationStatus(PayrollConfirmationStatus.PENDING.getCode());

        when(payrollLineService.list(anyPayrollLineWrapper())).thenReturn(List.of(pendingLine));

        assertThatThrownBy(() -> service.createPaymentBatch(payrollBatch, approver(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("还有员工待确认或异议未处理")
                .hasMessageContaining("lineIds=2001");

        verify(payrollBatchMapper, never()).update(eq(null), any(UpdateWrapper.class));
        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
        verify(paymentRecordService, never()).saveBatch(any(Collection.class));
    }

    @Test
    void createPaymentBatchShouldRejectBlockingPayrollLineIssues() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        PayrollLine line = payrollLine();
        line.setWarning("[{\"severity\":\"blocking\",\"message\":\"缺少必填薪资项：BASIC\"}]");

        when(payrollLineService.list(anyPayrollLineWrapper())).thenReturn(List.of(line));
        when(validationIssueSupport.deserialize(line.getWarning()))
                .thenReturn(List.of(com.yiyundao.compensation.interfaces.dto.payroll.PayrollValidationIssueDto.builder()
                        .severity("blocking")
                        .blocking(Boolean.TRUE)
                        .message("缺少必填薪资项：BASIC")
                        .build()));

        assertThatThrownBy(() -> service.createPaymentBatch(payrollBatch, approver(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在阻塞问题")
                .hasMessageContaining("缺少必填薪资项：BASIC");

        verify(payrollBatchMapper, never()).update(eq(null), any(UpdateWrapper.class));
        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
        verify(paymentRecordService, never()).saveBatch(any(Collection.class));
    }

    @Test
    void createPaymentBatchShouldMarkPayrollBatchFailedWhenNoRecordIsPayable() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        Employee employee = employee();
        employee.setEmail(null);
        employee.setPhone(null);
        employee.setSettlementAccount(null);
        employee.setBankAccount(null);

        when(payrollLineService.list(anyPayrollLineWrapper())).thenReturn(List.of(payrollLine()));
        when(employeeService.getById(1001L)).thenReturn(employee);
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        when(paymentBatchService.save(any(PaymentBatch.class))).thenReturn(true);
        when(paymentRecordService.saveBatch(any(Collection.class))).thenReturn(true);

        PaymentBatch result = service.createPaymentBatch(payrollBatch, approver(), true);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(com.yiyundao.compensation.enums.BatchStatus.FAILED);
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentBatchProcessStatus.FAILED);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(payrollBatch.getStatus()).isEqualTo(PayrollBatchStatus.PAY_FAILED);
        verify(afterCommitExecutor, never()).execute(any());
    }

    @Test
    void retryFailedPaymentShouldResolveOpenFailureRecordWhenResetSucceeds() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        payrollBatch.setStatus(PayrollBatchStatus.PAY_FAILED);
        payrollBatch.setPaymentBatchNo("PB-FAILED-10");
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-FAILED-10");
        paymentBatch.setStatus(com.yiyundao.compensation.enums.BatchStatus.FAILED);

        when(payrollBatchMapper.selectById(10L)).thenReturn(payrollBatch);
        when(paymentBatchService.getByBatchNo("PB-FAILED-10")).thenReturn(paymentBatch, paymentBatch);
        when(paymentRecordService.list(any(Wrapper.class))).thenReturn(List.of(failedRecord(501L)));
        when(paymentRecordService.count(any(Wrapper.class))).thenReturn(0L, 0L);
        when(paymentRecordService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(paymentBatchService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);

        PaymentBatch retried = service.retryFailedPayment(10L, false);

        assertThat(retried).isSameAs(paymentBatch);
        verify(payrollPaymentFailureService).markResolvedByPayrollBatchId(10L, "PB-FAILED-10");
        verify(afterCommitExecutor, never()).execute(any());
    }

    @Test
    void retryFailedPaymentShouldAllowPartialSuccessCompletedBatch() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        payrollBatch.setStatus(PayrollBatchStatus.PAY_FAILED);
        payrollBatch.setPaymentBatchNo("PB-PARTIAL-10");
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-PARTIAL-10");
        paymentBatch.setStatus(com.yiyundao.compensation.enums.BatchStatus.COMPLETED);
        paymentBatch.setPaymentStatus(PaymentBatchProcessStatus.PARTIAL_SUCCESS);

        when(payrollBatchMapper.selectById(10L)).thenReturn(payrollBatch);
        when(paymentBatchService.getByBatchNo("PB-PARTIAL-10")).thenReturn(paymentBatch, paymentBatch);
        when(paymentRecordService.list(any(Wrapper.class))).thenReturn(List.of(failedRecord(501L), failedRecord(502L)));
        when(paymentRecordService.count(any(Wrapper.class))).thenReturn(3L, 0L);
        when(paymentRecordService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(paymentBatchService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);

        PaymentBatch retried = service.retryFailedPayment(10L, false);

        assertThat(retried).isSameAs(paymentBatch);
        verify(payrollPaymentFailureService).markResolvedByPayrollBatchId(10L, "PB-PARTIAL-10");
        verify(afterCommitExecutor, never()).execute(any());
    }

    @Test
    void retryFailedPaymentShouldRejectFailedRecordsWithExistingProviderOrder() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        payrollBatch.setStatus(PayrollBatchStatus.PAY_FAILED);
        payrollBatch.setPaymentBatchNo("PB-FAILED-10");
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-FAILED-10");
        paymentBatch.setStatus(com.yiyundao.compensation.enums.BatchStatus.FAILED);
        PaymentRecord submittedFailedRecord = failedRecord(501L);
        submittedFailedRecord.setProviderOrderNo("ALI_ALREADY_SUBMITTED_501");

        when(payrollBatchMapper.selectById(10L)).thenReturn(payrollBatch);
        when(paymentBatchService.getByBatchNo("PB-FAILED-10")).thenReturn(paymentBatch);
        when(paymentRecordService.list(any(Wrapper.class))).thenReturn(List.of(submittedFailedRecord));

        assertThatThrownBy(() -> service.retryFailedPayment(10L, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已提交渠道");

        verify(paymentRecordService, never()).update(any(UpdateWrapper.class));
        verify(paymentBatchService, never()).update(any(UpdateWrapper.class));
        verify(afterCommitExecutor, never()).execute(any());
        verify(payrollPaymentFailureService, never()).markResolvedByPayrollBatchId(any(), any());
    }

    @Test
    void retryFailedPaymentShouldRejectFailedRecordsWithExistingProviderTrade() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        payrollBatch.setStatus(PayrollBatchStatus.PAY_FAILED);
        payrollBatch.setPaymentBatchNo("PB-FAILED-10");
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-FAILED-10");
        paymentBatch.setStatus(com.yiyundao.compensation.enums.BatchStatus.FAILED);
        PaymentRecord submittedFailedRecord = failedRecord(501L);
        submittedFailedRecord.setProviderTradeNo("ALI_TRADE_ALREADY_CONFIRMED_501");

        when(payrollBatchMapper.selectById(10L)).thenReturn(payrollBatch);
        when(paymentBatchService.getByBatchNo("PB-FAILED-10")).thenReturn(paymentBatch);
        when(paymentRecordService.list(any(Wrapper.class))).thenReturn(List.of(submittedFailedRecord));

        assertThatThrownBy(() -> service.retryFailedPayment(10L, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已提交渠道");

        verify(paymentRecordService, never()).update(any(UpdateWrapper.class));
        verify(paymentBatchService, never()).update(any(UpdateWrapper.class));
        verify(afterCommitExecutor, never()).execute(any());
        verify(payrollPaymentFailureService, never()).markResolvedByPayrollBatchId(any(), any());
    }

    @Test
    void retryFailedPaymentShouldSyncDistributionRetryStateWhenBatchIsDistributionBacked() {
        PayrollPaymentServiceImpl service = newService();
        PayrollBatch payrollBatch = payrollBatch();
        payrollBatch.setStatus(PayrollBatchStatus.PAY_FAILED);
        payrollBatch.setPaymentBatchNo("PB-DIST-10");
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-DIST-10");
        paymentBatch.setStatus(com.yiyundao.compensation.enums.BatchStatus.FAILED);
        paymentBatch.setDistributionId(55L);

        when(payrollBatchMapper.selectById(10L)).thenReturn(payrollBatch);
        when(paymentBatchService.getByBatchNo("PB-DIST-10")).thenReturn(paymentBatch, paymentBatch);
        when(paymentRecordService.list(any(Wrapper.class))).thenReturn(List.of(failedRecord(501L)));
        when(paymentRecordService.count(any(Wrapper.class))).thenReturn(0L, 0L);
        when(paymentRecordService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(paymentBatchService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(payrollDistributionItemMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        when(payrollDistributionMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);

        PaymentBatch retried = service.retryFailedPayment(10L, false);

        assertThat(retried).isSameAs(paymentBatch);
        ArgumentCaptor<UpdateWrapper<PayrollDistributionItem>> itemUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollDistributionItemMapper).update(eq(null), itemUpdateCaptor.capture());
        itemUpdateCaptor.getValue().getSqlSegment();
        assertThat(itemUpdateCaptor.getValue().getParamNameValuePairs().values())
                .contains(55L, 501L, "retrying");

        ArgumentCaptor<UpdateWrapper<PayrollDistribution>> distributionUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollDistributionMapper).update(eq(null), distributionUpdateCaptor.capture());
        distributionUpdateCaptor.getValue().getSqlSegment();
        assertThat(distributionUpdateCaptor.getValue().getParamNameValuePairs().values())
                .contains(55L, "processing");
    }

    private PayrollPaymentServiceImpl newService() {
        return new PayrollPaymentServiceImpl(
                paymentBatchService,
                paymentRecordService,
                payrollLineService,
                provider(payrollCalculationService),
                provider(employeeService),
                settlementService,
                encryptionService,
                payrollBatchMapper,
                payrollDistributionItemMapper,
                payrollDistributionMapper,
                afterCommitExecutor,
                provider(payrollPaymentFailureService),
                validationIssueSupport
        );
    }

    private PaymentRecord failedRecord(Long id) {
        PaymentRecord record = new PaymentRecord();
        record.setId(id);
        record.setBatchNo("PB-FAILED-10");
        record.setStatus(PaymentStatus.FAILED);
        return record;
    }

    private PayrollBatch payrollBatch() {
        PayrollBatch batch = new PayrollBatch();
        batch.setId(10L);
        batch.setPeriodLabel("2026-06");
        batch.setType(EmploymentType.FULL_TIME.getCode());
        batch.setCurrency("CNY");
        batch.setStatus(PayrollBatchStatus.APPROVED);
        batch.setConfirmationRequired(Boolean.FALSE);
        return batch;
    }

    private PayrollLine payrollLine() {
        PayrollLine line = new PayrollLine();
        line.setId(2001L);
        line.setBatchId(10L);
        line.setEmployeeId(1001L);
        line.setEmploymentType(EmploymentType.FULL_TIME.getCode());
        line.setNetAmount(new BigDecimal("9600.00"));
        return line;
    }

    private Employee employee() {
        Employee employee = new Employee();
        employee.setId(1001L);
        employee.setName("张三");
        employee.setEmploymentType(EmploymentType.FULL_TIME.getCode());
        employee.setEmail("payee@example.com");
        return employee;
    }

    private SysUser approver() {
        SysUser user = new SysUser();
        user.setId(99L);
        user.setUsername("finance");
        return user;
    }

    private <T> ObjectProvider<T> provider(T bean) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return bean;
            }

            @Override
            public T getIfAvailable() {
                return bean;
            }

            @Override
            public T getIfUnique() {
                return bean;
            }

            @Override
            public T getObject() {
                return bean;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Wrapper<PayrollLine> anyPayrollLineWrapper() {
        return (Wrapper<PayrollLine>) any(Wrapper.class);
    }
}
