package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistributionItem;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.common.util.TransactionAfterCommitExecutor;
import com.yiyundao.compensation.enums.PayrollConfirmationSheetStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollDistributionItemStatus;
import com.yiyundao.compensation.enums.PayrollDistributionStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollDistributionItemMapper;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollConfirmation;
import com.yiyundao.compensation.modules.payroll.entity.PayrollApprovalProjection;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.service.EncryptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollProcessManagerTest {

    @Mock
    private PayrollBatchMapper payrollBatchMapper;
    @Mock
    private PayrollDistributionItemMapper distributionItemMapper;
    @Mock
    private PayrollCalculationService payrollCalculationService;
    @Mock
    private PayrollConfirmationAggregateService confirmationAggregateService;
    @Mock
    private PayrollDistributionService distributionService;
    @Mock
    private PayrollApprovalProjectionService approvalProjectionService;
    @Mock
    private PayrollReconciliationTaskService reconciliationTaskService;
    @Mock
    private PaymentBatchService paymentBatchService;
    @Mock
    private PaymentRecordService paymentRecordService;
    @Mock
    private SettlementService settlementService;
    @Mock
    private TransactionAfterCommitExecutor afterCommitExecutor;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private TransactionOperations transactionOperations;

    @Test
    void computeAndInitializeShouldMarkSkippedConfirmationBatchConfirmedBeforeCreatingDistribution() {
        PayrollProcessManager manager = newManager();
        PayrollBatch batch = payrollBatch();
        batch.setStatus(PayrollBatchStatus.LOCKED);
        batch.setConfirmationRequired(Boolean.FALSE);
        batch.setBatchRevision(1);
        PayrollConfirmation confirmation = new PayrollConfirmation();
        confirmation.setConfirmationStatus(PayrollConfirmationSheetStatus.SKIPPED);
        PayrollDistribution distribution = distribution();

        when(payrollCalculationService.computeAndSave(10L)).thenReturn(true);
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch, batch);
        when(confirmationAggregateService.createOrRefreshForBatch(batch)).thenReturn(confirmation);
        when(confirmationAggregateService.getByBatchIdAndRevision(10L, 1)).thenReturn(confirmation);
        when(distributionService.createOrReuseForBatch(batch)).thenReturn(distribution);

        boolean result = manager.computeAndInitialize(10L);

        assertThat(result).isTrue();
        verify(payrollBatchMapper).updateById(argThat((PayrollBatch updated) ->
                updated.getId().equals(10L)
                        && updated.getStatus() == PayrollBatchStatus.CONFIRMED
                        && updated.getConfirmationCompletedTime() != null));
        verify(distributionService).supersedeObsolete(10L, 1);
        verify(distributionService).createOrReuseForBatch(argThat(updated ->
                updated.getId().equals(10L)
                        && updated.getStatus() == PayrollBatchStatus.CONFIRMED));
    }

    @Test
    void prepareDistributionSubmissionShouldReserveDistributionAndBatchBeforePersistingPaymentRows() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        PayrollBatch batch = payrollBatch();
        when(distributionService.getById(55L)).thenReturn(distribution);
        when(distributionService.listActiveItems(55L)).thenReturn(List.of(distributionItem()));
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);
        when(distributionService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        when(paymentBatchService.save(any(PaymentBatch.class))).thenReturn(true);
        when(encryptionService.decrypt("encrypted-account")).thenReturn("payee@example.com");
        when(paymentRecordService.save(any(PaymentRecord.class))).thenReturn(true);

        PaymentBatch result = manager.prepareDistributionSubmission(55L);

        assertThat(result.getBatchNo()).startsWith("PDS-55-A1-");
        assertThat(result.getDistributionId()).isEqualTo(55L);

        InOrder inOrder = inOrder(distributionService, payrollBatchMapper, paymentBatchService, paymentRecordService);
        inOrder.verify(distributionService).update(any(UpdateWrapper.class));
        inOrder.verify(payrollBatchMapper).update(eq(null), any(UpdateWrapper.class));
        inOrder.verify(paymentBatchService).save(argThat(paymentBatch -> paymentBatch.getBatchNo().equals(result.getBatchNo())));
        inOrder.verify(paymentRecordService).save(argThat(record -> result.getBatchNo().equals(record.getBatchNo())));
    }

    @Test
    void prepareDistributionSubmissionShouldRejectUndecryptableEncryptedAccountBeforeReservation() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        PayrollBatch batch = payrollBatch();

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(distributionService.listActiveItems(55L)).thenReturn(List.of(distributionItem()));
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);
        when(encryptionService.decrypt("encrypted-account")).thenThrow(new RuntimeException("bad key"));

        assertThatThrownBy(() -> manager.prepareDistributionSubmission(55L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("发放快照收款账号解密失败");

        verify(distributionService, never()).update(any(UpdateWrapper.class));
        verify(payrollBatchMapper, never()).update(eq(null), any(UpdateWrapper.class));
        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
        verify(paymentRecordService, never()).save(any(PaymentRecord.class));
    }

    @Test
    void prepareDistributionSubmissionShouldUseLegacyPlainAccountWhenDecryptFails() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        PayrollBatch batch = payrollBatch();
        PayrollDistributionItem item = distributionItem();
        item.setAccountNoEncrypted("13800000000");

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(distributionService.listActiveItems(55L)).thenReturn(List.of(item));
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);
        when(encryptionService.decrypt("13800000000")).thenThrow(new RuntimeException("legacy plaintext"));
        when(distributionService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        when(paymentBatchService.save(any(PaymentBatch.class))).thenReturn(true);
        when(paymentRecordService.save(any(PaymentRecord.class))).thenReturn(true);

        PaymentBatch result = manager.prepareDistributionSubmission(55L);

        assertThat(result.getStatus()).isEqualTo(BatchStatus.SUBMITTED);
        verify(paymentRecordService).save(argThat(record ->
                "13800000000".equals(record.getRecipientAccount())
                        && result.getBatchNo().equals(record.getBatchNo())));
    }

    @Test
    void prepareDistributionSubmissionShouldReturnConcurrentExistingBatchWhenReservationFails() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        PayrollBatch initialBatch = payrollBatch();
        PayrollBatch latestBatch = payrollBatch();
        latestBatch.setPaymentBatchNo("PDS-existing");
        PaymentBatch existing = new PaymentBatch();
        existing.setBatchNo("PDS-existing");

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(distributionService.listActiveItems(55L)).thenReturn(List.of(distributionItem()));
        when(payrollBatchMapper.selectById(10L)).thenReturn(initialBatch, latestBatch);
        when(encryptionService.decrypt("encrypted-account")).thenReturn("payee@example.com");
        when(distributionService.update(any(UpdateWrapper.class))).thenReturn(false);
        when(paymentBatchService.getByBatchNo("PDS-existing")).thenReturn(existing);

        PaymentBatch result = manager.prepareDistributionSubmission(55L);

        assertThat(result).isSameAs(existing);
        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
        verify(paymentRecordService, never()).save(any(PaymentRecord.class));
        verify(distributionItemMapper, never()).updateById(any(PayrollDistributionItem.class));
    }

    @Test
    void prepareDistributionSubmissionShouldRejectStaleDistributionRevision() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setBatchRevision(1);
        PayrollBatch batch = payrollBatch();
        batch.setBatchRevision(2);

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(distributionService.listActiveItems(55L)).thenReturn(List.of(distributionItem()));
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);

        assertThatThrownBy(() -> manager.prepareDistributionSubmission(55L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("发放单已过期");

        verify(distributionService, never()).update(any(UpdateWrapper.class));
        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
        verify(paymentRecordService, never()).save(any(PaymentRecord.class));
    }

    @Test
    void prepareDistributionSubmissionShouldRejectBatchThatIsNotApprovedYet() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        PayrollBatch batch = payrollBatch();
        batch.setStatus(PayrollBatchStatus.CONFIRMED);

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(distributionService.listActiveItems(55L)).thenReturn(List.of(distributionItem()));
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);

        assertThatThrownBy(() -> manager.prepareDistributionSubmission(55L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前薪资批次状态不可发放");

        verify(distributionService, never()).update(any(UpdateWrapper.class));
        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
        verify(paymentRecordService, never()).save(any(PaymentRecord.class));
    }

    @Test
    void prepareDistributionSubmissionShouldReplaceFailedPaymentBatchOnNextAttempt() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setDistributionStatus(PayrollDistributionStatus.PLANNED);
        distribution.setCurrentAttempt(1);
        PayrollDistributionItem item = distributionItem();
        item.setItemStatus(PayrollDistributionItemStatus.FAILED);
        item.setRetryCount(1);
        PayrollBatch batch = payrollBatch();
        batch.setPaymentBatchNo("PDS-old-failed");
        PaymentBatch oldPaymentBatch = new PaymentBatch();
        oldPaymentBatch.setBatchNo("PDS-old-failed");
        oldPaymentBatch.setStatus(BatchStatus.FAILED);
        oldPaymentBatch.setPaymentStatus(PaymentBatchProcessStatus.FAILED);

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(distributionService.listActiveItems(55L)).thenReturn(List.of(item));
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);
        when(distributionService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(paymentBatchService.getByBatchNo("PDS-old-failed")).thenReturn(oldPaymentBatch);
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        when(paymentBatchService.save(any(PaymentBatch.class))).thenReturn(true);
        when(encryptionService.decrypt("encrypted-account")).thenReturn("payee@example.com");
        when(paymentRecordService.save(any(PaymentRecord.class))).thenReturn(true);

        PaymentBatch result = manager.prepareDistributionSubmission(55L);

        assertThat(result.getBatchNo()).startsWith("PDS-55-A2-");
        ArgumentCaptor<UpdateWrapper<PayrollBatch>> batchUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollBatchMapper).update(eq(null), batchUpdateCaptor.capture());
        UpdateWrapper<PayrollBatch> wrapper = batchUpdateCaptor.getValue();
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(result.getBatchNo(), PayrollBatchStatus.PAY_PROCESSING.getCode());
    }

    @Test
    void prepareDistributionSubmissionShouldRetryOnlyFailedItemsAfterPartialCompletion() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setDistributionStatus(PayrollDistributionStatus.PARTIALLY_COMPLETED);
        distribution.setCurrentAttempt(1);

        PayrollDistributionItem successItem = distributionItem();
        successItem.setId(3001L);
        successItem.setEmployeeId(1001L);
        successItem.setItemStatus(PayrollDistributionItemStatus.SUCCESS);
        successItem.setPaymentRecordId(9001L);

        PayrollDistributionItem failedItem = distributionItem();
        failedItem.setId(3002L);
        failedItem.setEmployeeId(1002L);
        failedItem.setItemStatus(PayrollDistributionItemStatus.FAILED);
        failedItem.setRetryCount(1);
        failedItem.setPaymentRecordId(9002L);

        PayrollBatch batch = payrollBatch();
        batch.setPaymentBatchNo("PDS-old-partial");
        PaymentBatch oldPaymentBatch = new PaymentBatch();
        oldPaymentBatch.setBatchNo("PDS-old-partial");
        oldPaymentBatch.setStatus(BatchStatus.COMPLETED);
        oldPaymentBatch.setPaymentStatus(PaymentBatchProcessStatus.PARTIAL_SUCCESS);
        oldPaymentBatch.setFailedCount(1);

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(distributionService.listActiveItems(55L)).thenReturn(List.of(successItem, failedItem));
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);
        when(distributionService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(paymentBatchService.getByBatchNo("PDS-old-partial")).thenReturn(oldPaymentBatch);
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        when(paymentBatchService.save(any(PaymentBatch.class))).thenReturn(true);
        when(encryptionService.decrypt("encrypted-account")).thenReturn("payee@example.com");
        when(paymentRecordService.save(any(PaymentRecord.class))).thenAnswer(invocation -> {
            PaymentRecord record = invocation.getArgument(0);
            record.setId(9102L);
            return true;
        });

        PaymentBatch result = manager.prepareDistributionSubmission(55L);

        assertThat(result.getBatchNo()).startsWith("PDS-55-A2-");
        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("9600.00");
        ArgumentCaptor<UpdateWrapper<PayrollDistribution>> distributionUpdateCaptor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(distributionService).update(distributionUpdateCaptor.capture());
        UpdateWrapper<PayrollDistribution> distributionWrapper = distributionUpdateCaptor.getValue();
        distributionWrapper.getSqlSegment();
        assertThat(distributionWrapper.getParamNameValuePairs().values())
                .contains(PayrollDistributionStatus.PARTIALLY_COMPLETED.getCode(),
                        PayrollDistributionStatus.SUBMITTING.getCode());
        verify(distributionService).updateById(argThat(updated ->
                updated.getId().equals(55L)
                        && updated.getDistributionStatus() == PayrollDistributionStatus.PROCESSING));
        verify(paymentRecordService).save(argThat(record -> record.getEmployeeId().equals(1002L)));
        verify(distributionItemMapper).updateById(argThat((PayrollDistributionItem item) ->
                item.getId().equals(3002L)
                        && item.getPaymentRecordId().equals(9102L)
                        && item.getItemStatus() == PayrollDistributionItemStatus.RETRYING));
        verify(distributionItemMapper, never()).updateById(argThat((PayrollDistributionItem item) ->
                item.getId().equals(3001L)));
    }

    @Test
    void prepareDistributionSubmissionShouldRejectNextAttemptWhenPreviousPaymentBatchIsProcessing() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setCurrentAttempt(1);
        PayrollDistributionItem item = distributionItem();
        item.setItemStatus(PayrollDistributionItemStatus.FAILED);
        item.setRetryCount(1);
        PayrollBatch batch = payrollBatch();
        batch.setPaymentBatchNo("PDS-old-processing");
        PaymentBatch oldPaymentBatch = new PaymentBatch();
        oldPaymentBatch.setBatchNo("PDS-old-processing");
        oldPaymentBatch.setStatus(BatchStatus.PROCESSING);
        oldPaymentBatch.setPaymentStatus(PaymentBatchProcessStatus.PROCESSING);

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(distributionService.listActiveItems(55L)).thenReturn(List.of(item));
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);
        when(paymentBatchService.getByBatchNo("PDS-old-processing")).thenReturn(oldPaymentBatch);

        assertThatThrownBy(() -> manager.prepareDistributionSubmission(55L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前支付批次状态不可创建新的发放尝试");

        verify(payrollBatchMapper, never()).update(eq(null), any(UpdateWrapper.class));
        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
        verify(paymentRecordService, never()).save(any(PaymentRecord.class));
        verify(distributionService, never()).update(any(UpdateWrapper.class));
    }

    @Test
    void prepareDistributionSubmissionShouldRejectRetryWhenPreviousRecordHasProviderOrder() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setCurrentAttempt(1);
        PayrollDistributionItem item = distributionItem();
        item.setItemStatus(PayrollDistributionItemStatus.FAILED);
        item.setRetryCount(1);
        item.setPaymentRecordId(9002L);

        PaymentRecord previousRecord = new PaymentRecord();
        previousRecord.setId(9002L);
        previousRecord.setStatus(PaymentStatus.FAILED);
        previousRecord.setProviderOrderNo("ALI_ALREADY_SUBMITTED");

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(distributionService.listActiveItems(55L)).thenReturn(List.of(item));
        when(paymentRecordService.getById(9002L)).thenReturn(previousRecord);

        assertThatThrownBy(() -> manager.prepareDistributionSubmission(55L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("等待对账确认");

        verify(payrollBatchMapper, never()).selectById(10L);
        verify(distributionService, never()).update(any(UpdateWrapper.class));
        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
        verify(paymentRecordService, never()).save(any(PaymentRecord.class));
    }

    @Test
    void prepareDistributionSubmissionShouldPersistFailedStateWhenNoCandidateItemsRemain() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setCurrentAttempt(1);
        distribution.setRetryLimit(1);
        PayrollDistributionItem item = distributionItem();
        item.setItemStatus(PayrollDistributionItemStatus.FAILED);
        item.setRetryCount(1);

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(distributionService.listActiveItems(55L)).thenReturn(List.of(item));

        PaymentBatch result = manager.prepareDistributionSubmission(55L);

        assertThat(result).isNull();
        verify(distributionService).updateById(argThat(updated ->
                updated.getId().equals(55L)
                        && updated.getDistributionStatus() == PayrollDistributionStatus.FAILED));
        verify(reconciliationTaskService).createOrRefresh(argThat(updated ->
                updated.getId().equals(55L)
                        && updated.getDistributionStatus() == PayrollDistributionStatus.FAILED));
        verify(payrollBatchMapper, never()).selectById(10L);
        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
        verify(paymentRecordService, never()).save(any(PaymentRecord.class));
    }

    @Test
    void submitDueDistributionsShouldSubmitApprovedDueDistributionInsideTransaction() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setScheduledDate(java.time.LocalDate.now());
        PayrollApprovalProjection projection = new PayrollApprovalProjection();
        projection.setBusinessStatus("APPROVED");

        when(distributionService.list(org.mockito.ArgumentMatchers.<Wrapper<PayrollDistribution>>any()))
                .thenReturn(List.of(distribution));
        when(approvalProjectionService.getByDistributionId(55L)).thenReturn(projection);
        when(transactionOperations.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        });
        when(distributionService.getById(55L)).thenReturn(distribution);
        when(distributionService.listActiveItems(55L)).thenReturn(List.of(distributionItem()));
        when(payrollBatchMapper.selectById(10L)).thenReturn(payrollBatch());
        when(distributionService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(payrollBatchMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        when(paymentBatchService.save(any(PaymentBatch.class))).thenReturn(true);
        when(encryptionService.decrypt("encrypted-account")).thenReturn("payee@example.com");
        when(paymentRecordService.save(any(PaymentRecord.class))).thenReturn(true);

        manager.submitDueDistributions();

        verify(transactionOperations).execute(any());
        verify(paymentBatchService).save(any(PaymentBatch.class));
        verify(afterCommitExecutor).execute(any(Runnable.class));
    }

    @Test
    void onApprovalApprovedShouldScheduleImmediateSubmissionAfterApprovalStateIsPersisted() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setScheduledDate(java.time.LocalDate.now());
        PayrollBatch batch = payrollBatch();

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);

        manager.onApprovalApproved(55L, 9001L, 8001L);

        verify(approvalProjectionService).markApproved(9001L, 8001L);
        ArgumentCaptor<UpdateWrapper<PayrollBatch>> batchUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollBatchMapper).update(eq(null), batchUpdateCaptor.capture());
        batchUpdateCaptor.getValue().getSqlSegment();
        assertThat(batchUpdateCaptor.getValue().getParamNameValuePairs().values())
                .contains(PayrollBatchStatus.APPROVED.getCode(), 9001L);
        verify(afterCommitExecutor).execute(any(Runnable.class));
        verify(transactionOperations, never()).execute(any());
        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
    }

    @Test
    void onApprovalApprovedShouldNotThrowWhenScheduledImmediateSubmissionFails() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setScheduledDate(java.time.LocalDate.now());
        PayrollBatch batch = payrollBatch();

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);
        when(transactionOperations.execute(any())).thenThrow(new RuntimeException("submit failed"));

        manager.onApprovalApproved(55L, 9001L, 8001L);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommitExecutor).execute(runnableCaptor.capture());

        assertThatCode(runnableCaptor.getValue()::run).doesNotThrowAnyException();
        verify(transactionOperations).execute(any());
    }

    @Test
    void onApprovalApprovedShouldNotMovePayingBatchBackToApprovedForFutureSchedule() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setScheduledDate(java.time.LocalDate.now().plusDays(1));
        PayrollBatch batch = payrollBatch();
        batch.setStatus(PayrollBatchStatus.PAY_PROCESSING);

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);

        manager.onApprovalApproved(55L, 9001L, 8001L);

        verify(approvalProjectionService).markApproved(9001L, 8001L);
        ArgumentCaptor<UpdateWrapper<PayrollBatch>> batchUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollBatchMapper).update(eq(null), batchUpdateCaptor.capture());
        UpdateWrapper<PayrollBatch> wrapper = batchUpdateCaptor.getValue();
        wrapper.getSqlSegment();
        assertThat(wrapper.getExpression().getNormal().getSqlSegment())
                .contains("status IN");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(PayrollBatchStatus.APPROVED.getCode())
                .doesNotContain(PayrollBatchStatus.PAY_PROCESSING.getCode());
        verify(distributionService).updateById(argThat(updated ->
                updated.getId().equals(55L)
                        && updated.getDistributionStatus() == PayrollDistributionStatus.PLANNED));
        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
    }

    @Test
    void onApprovalApprovedShouldNotSubmitAlreadyProcessingDistributionAgain() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setDistributionStatus(PayrollDistributionStatus.PROCESSING);
        PayrollBatch batch = payrollBatch();
        batch.setStatus(PayrollBatchStatus.PAY_PROCESSING);

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);

        manager.onApprovalApproved(55L, 9001L, 8001L);

        verify(approvalProjectionService).markApproved(9001L, 8001L);
        verify(distributionService, never()).listActiveItems(55L);
        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
        verify(paymentRecordService, never()).save(any(PaymentRecord.class));
        verify(afterCommitExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    void onApprovalApprovedShouldIgnoreStaleDistributionRevision() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setBatchRevision(1);
        PayrollBatch batch = payrollBatch();
        batch.setBatchRevision(2);
        batch.setStatus(PayrollBatchStatus.CONFIRMING);

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);

        manager.onApprovalApproved(55L, 9001L, 8001L);

        verify(approvalProjectionService).markApproved(9001L, 8001L);
        verify(payrollBatchMapper, never()).update(eq(null), any(UpdateWrapper.class));
        verify(distributionService, never()).listActiveItems(55L);
        verify(paymentBatchService, never()).save(any(PaymentBatch.class));
        verify(afterCommitExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    void onApprovalCancelledShouldCancelDistributionAndRestoreBatchToConfirmed() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setDistributionStatus(PayrollDistributionStatus.PLANNED);
        PayrollBatch batch = payrollBatch();
        batch.setStatus(PayrollBatchStatus.SUBMITTED);

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);

        manager.onApprovalCancelled(55L, 9001L, 8001L, "CANCELLED");

        verify(approvalProjectionService).markCancelled(9001L, 8001L, "CANCELLED");
        ArgumentCaptor<UpdateWrapper<PayrollDistribution>> distributionUpdateCaptor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(distributionService).update(distributionUpdateCaptor.capture());
        distributionUpdateCaptor.getValue().getSqlSegment();
        assertThat(distributionUpdateCaptor.getValue().getExpression().getNormal().getSqlSegment())
                .contains("distribution_status =");
        assertThat(distributionUpdateCaptor.getValue().getParamNameValuePairs().values())
                .contains(PayrollDistributionStatus.CANCELLED.getCode());
        ArgumentCaptor<UpdateWrapper<PayrollBatch>> batchUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollBatchMapper).update(eq(null), batchUpdateCaptor.capture());
        batchUpdateCaptor.getValue().getSqlSegment();
        assertThat(batchUpdateCaptor.getValue().getExpression().getNormal().getSqlSegment())
                .contains("status =");
        assertThat(batchUpdateCaptor.getValue().getParamNameValuePairs().values())
                .contains(PayrollBatchStatus.CONFIRMED.getCode());
    }

    @Test
    void onApprovalRejectedShouldCancelDistributionAndRejectBatch() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setDistributionStatus(PayrollDistributionStatus.PLANNED);
        PayrollBatch batch = payrollBatch();
        batch.setStatus(PayrollBatchStatus.SUBMITTED);

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);

        manager.onApprovalRejected(55L, 9001L, 8001L, "REJECTED");

        verify(approvalProjectionService).markRejected(9001L, 8001L, "REJECTED");
        ArgumentCaptor<UpdateWrapper<PayrollBatch>> batchUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollBatchMapper).update(eq(null), batchUpdateCaptor.capture());
        batchUpdateCaptor.getValue().getSqlSegment();
        assertThat(batchUpdateCaptor.getValue().getExpression().getNormal().getSqlSegment())
                .contains("status =");
        assertThat(batchUpdateCaptor.getValue().getParamNameValuePairs().values())
                .contains(PayrollBatchStatus.REJECTED.getCode());
    }

    @Test
    void onApprovalCancelledShouldIgnoreStaleWorkflowAfterResubmission() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setDistributionStatus(PayrollDistributionStatus.PLANNED);
        distribution.setApprovalWorkflowId(9002L);

        when(distributionService.getById(55L)).thenReturn(distribution);

        manager.onApprovalCancelled(55L, 9001L, 8001L, "CANCELLED");

        verify(approvalProjectionService).markCancelled(9001L, 8001L, "CANCELLED");
        verify(distributionService, never()).update(any(UpdateWrapper.class));
        verify(payrollBatchMapper, never()).selectById(any());
        verify(payrollBatchMapper, never()).update(eq(null), any(UpdateWrapper.class));
    }

    @Test
    void onApprovalCancelledShouldNotCancelProcessingDistributionOrPayingBatch() {
        PayrollProcessManager manager = newManager();
        PayrollDistribution distribution = distribution();
        distribution.setDistributionStatus(PayrollDistributionStatus.PROCESSING);
        PayrollBatch batch = payrollBatch();
        batch.setStatus(PayrollBatchStatus.PAY_PROCESSING);

        when(distributionService.getById(55L)).thenReturn(distribution);
        when(payrollBatchMapper.selectById(10L)).thenReturn(batch);

        manager.onApprovalCancelled(55L, 9001L, 8001L, "CANCELLED");

        verify(approvalProjectionService).markCancelled(9001L, 8001L, "CANCELLED");
        ArgumentCaptor<UpdateWrapper<PayrollDistribution>> distributionUpdateCaptor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(distributionService).update(distributionUpdateCaptor.capture());
        distributionUpdateCaptor.getValue().getSqlSegment();
        assertThat(distributionUpdateCaptor.getValue().getExpression().getNormal().getSqlSegment())
                .contains("distribution_status =");
        assertThat(distributionUpdateCaptor.getValue().getParamNameValuePairs().values())
                .contains(PayrollDistributionStatus.CANCELLED.getCode())
                .doesNotContain(PayrollDistributionStatus.PROCESSING.getCode());
        ArgumentCaptor<UpdateWrapper<PayrollBatch>> batchUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollBatchMapper).update(eq(null), batchUpdateCaptor.capture());
        batchUpdateCaptor.getValue().getSqlSegment();
        assertThat(batchUpdateCaptor.getValue().getExpression().getNormal().getSqlSegment())
                .contains("status =");
        assertThat(batchUpdateCaptor.getValue().getParamNameValuePairs().values())
                .contains(PayrollBatchStatus.CONFIRMED.getCode())
                .doesNotContain(PayrollBatchStatus.PAY_PROCESSING.getCode());
    }

    private PayrollProcessManager newManager() {
        return new PayrollProcessManager(
                payrollBatchMapper,
                distributionItemMapper,
                payrollCalculationService,
                confirmationAggregateService,
                distributionService,
                approvalProjectionService,
                reconciliationTaskService,
                paymentBatchService,
                paymentRecordService,
                settlementService,
                afterCommitExecutor,
                encryptionService,
                transactionOperations
        );
    }

    private PayrollDistribution distribution() {
        PayrollDistribution distribution = new PayrollDistribution();
        distribution.setId(55L);
        distribution.setBatchId(10L);
        distribution.setBatchRevision(1);
        distribution.setDistributionStatus(PayrollDistributionStatus.PLANNED);
        return distribution;
    }

    private PayrollBatch payrollBatch() {
        PayrollBatch batch = new PayrollBatch();
        batch.setId(10L);
        batch.setBatchRevision(1);
        batch.setPeriodLabel("2026-06");
        batch.setCurrency("CNY");
        batch.setStatus(PayrollBatchStatus.APPROVED);
        return batch;
    }

    private PayrollDistributionItem distributionItem() {
        PayrollDistributionItem item = new PayrollDistributionItem();
        item.setId(3001L);
        item.setDistributionId(55L);
        item.setEmployeeId(1001L);
        item.setRecipientName("张三");
        item.setAccountNoEncrypted("encrypted-account");
        item.setPaymentMethod("ALIPAY");
        item.setProviderCode("alipay");
        item.setAmount(new BigDecimal("9600.00"));
        item.setItemStatus(PayrollDistributionItemStatus.PENDING);
        return item;
    }
}
