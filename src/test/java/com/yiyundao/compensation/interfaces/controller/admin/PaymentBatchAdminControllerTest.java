package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.PaymentType;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollPaymentFailure;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentFailureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class PaymentBatchAdminControllerTest {

    private PaymentBatchService paymentBatchService;
    private PaymentRecordService paymentRecordService;
    private PayrollPaymentFailureService payrollPaymentFailureService;
    private PaymentBatchAdminController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        paymentBatchService = mock(PaymentBatchService.class);
        paymentRecordService = mock(PaymentRecordService.class);
        payrollPaymentFailureService = mock(PayrollPaymentFailureService.class);
        controller = new PaymentBatchAdminController(
                paymentBatchService,
                paymentRecordService,
                payrollPaymentFailureService
        );
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void createShouldReturnPaymentBatchResponseWithoutPersistenceFields() throws Exception {
        when(paymentBatchService.save(any(PaymentBatch.class))).thenAnswer(invocation -> {
            PaymentBatch batch = invocation.getArgument(0);
            batch.setId(1001L);
            batch.setCreateTime(LocalDateTime.of(2026, 6, 2, 12, 0));
            batch.setUpdateTime(LocalDateTime.of(2026, 6, 2, 12, 1));
            batch.setCreateBy("admin");
            batch.setUpdateBy("admin");
            batch.setDeleted(0);
            batch.setVersion(9);
            return true;
        });
        PaymentBatchAdminController.CreateBatchForm form = new PaymentBatchAdminController.CreateBatchForm();
        form.setBatchNo("PAY-202606");
        form.setBatchName("June payroll");
        form.setPaymentType(PaymentType.SALARY);
        form.setTotalAmount(new BigDecimal("1200.00"));
        form.setTotalCount(3);

        String json = objectMapper.writeValueAsString(controller.create(form));

        assertPublicResponseShape(json);
        assertThat(json)
                .contains("\"batchNo\":\"PAY-202606\"")
                .contains("\"paymentType\":\"salary\"")
                .contains("\"status\":\"draft\"");
    }

    @Test
    void paymentFailuresShouldReturnResponsesWithoutPersistenceFields() throws Exception {
        when(payrollPaymentFailureService.listUnresolved(50)).thenReturn(List.of(paymentFailure()));

        String json = objectMapper.writeValueAsString(controller.paymentFailures(50));

        assertPublicResponseShape(json);
        assertThat(json)
                .contains("\"workflowId\":9001")
                .contains("\"payrollBatchId\":1001")
                .contains("\"businessKey\":\"payroll_batch:1001\"")
                .contains("\"errorMessage\":\"payment provider rejected\"");
    }

    @Test
    void retryPaymentFailureShouldReturnResponseWithoutPersistenceFields() throws Exception {
        PayrollPaymentFailure retried = paymentFailure();
        retried.setStatus("resolved");
        retried.setResolvedTime(LocalDateTime.of(2026, 6, 2, 13, 0));
        retried.setPaymentBatchNo("PAY-RETRY-202606");
        when(payrollPaymentFailureService.retry(7L, true)).thenReturn(retried);

        String json = objectMapper.writeValueAsString(controller.retryPaymentFailure(7L, true));

        assertPublicResponseShape(json);
        assertThat(json)
                .contains("\"status\":\"resolved\"")
                .contains("\"paymentBatchNo\":\"PAY-RETRY-202606\"");
    }

    @Test
    void cancelShouldCancelPendingRecordsBeforeFailingBatch() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(88L);
        batch.setBatchNo("PB-CANCEL-88");
        batch.setStatus(BatchStatus.PROCESSING);
        when(paymentBatchService.getById(88L)).thenReturn(batch);
        PaymentRecord cancelled = new PaymentRecord();
        cancelled.setStatus(PaymentStatus.CANCELLED);
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any()))
                .thenReturn(List.of(cancelled));

        controller.cancel(88L);

        verify(paymentBatchService).updateTerminalState(argThat(updated ->
                updated.getStatus() == BatchStatus.FAILED
                        && updated.getPaymentStatus() == PaymentBatchProcessStatus.FAILED));
        verify(paymentRecordService).update(argThat((UpdateWrapper<PaymentRecord> wrapper) -> {
            wrapper.getSqlSegment();
            return wrapper.getExpression().getNormal().getSqlSegment().contains("status =")
                    && wrapper.getParamNameValuePairs().values().contains(PaymentStatus.PENDING.getCode())
                    && !wrapper.getParamNameValuePairs().values().contains(PaymentStatus.PROCESSING.getCode())
                    && wrapper.getParamNameValuePairs().values().contains(PaymentStatus.CANCELLED.getCode());
        }));
    }

    @Test
    void cancelShouldRejectProcessingRecordsBeforeUpdatingAnything() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(90L);
        batch.setBatchNo("PB-CANCEL-90");
        batch.setStatus(BatchStatus.PROCESSING);
        when(paymentBatchService.getById(90L)).thenReturn(batch);
        PaymentRecord processing = new PaymentRecord();
        processing.setStatus(PaymentStatus.PROCESSING);
        PaymentRecord cancelled = new PaymentRecord();
        cancelled.setStatus(PaymentStatus.CANCELLED);
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any()))
                .thenReturn(List.of(processing, cancelled));

        assertThatThrownBy(() -> controller.cancel(90L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已提交渠道");

        verify(paymentRecordService, never()).update(any(UpdateWrapper.class));
        verify(paymentBatchService, never()).updateTerminalState(any(PaymentBatch.class));
    }

    @Test
    void cancelShouldRejectPendingRecordWithProviderOrderBeforeUpdatingAnything() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(91L);
        batch.setBatchNo("PB-CANCEL-91");
        batch.setStatus(BatchStatus.SUBMITTED);
        when(paymentBatchService.getById(91L)).thenReturn(batch);
        PaymentRecord pendingWithProviderOrder = new PaymentRecord();
        pendingWithProviderOrder.setStatus(PaymentStatus.PENDING);
        pendingWithProviderOrder.setProviderOrderNo("ALI_ALREADY_SUBMITTED");
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any()))
                .thenReturn(List.of(pendingWithProviderOrder));

        assertThatThrownBy(() -> controller.cancel(91L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已提交渠道");

        verify(paymentRecordService, never()).update(any(UpdateWrapper.class));
        verify(paymentBatchService, never()).updateTerminalState(any(PaymentBatch.class));
    }

    @Test
    void cancelShouldRejectPendingRecordWithProviderTradeBeforeUpdatingAnything() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(92L);
        batch.setBatchNo("PB-CANCEL-92");
        batch.setStatus(BatchStatus.SUBMITTED);
        when(paymentBatchService.getById(92L)).thenReturn(batch);
        PaymentRecord pendingWithProviderTrade = new PaymentRecord();
        pendingWithProviderTrade.setStatus(PaymentStatus.PENDING);
        pendingWithProviderTrade.setProviderTradeNo("ALI_PROVIDER_TRADE_92");
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any()))
                .thenReturn(List.of(pendingWithProviderTrade));

        assertThatThrownBy(() -> controller.cancel(92L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已提交渠道");

        verify(paymentRecordService, never()).update(any(UpdateWrapper.class));
        verify(paymentBatchService, never()).updateTerminalState(any(PaymentBatch.class));
    }

    @Test
    void cancelShouldRollbackWhenRecordBecomesProcessingDuringCancellation() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(93L);
        batch.setBatchNo("PB-CANCEL-93");
        batch.setStatus(BatchStatus.SUBMITTED);
        when(paymentBatchService.getById(93L)).thenReturn(batch);
        PaymentRecord pending = new PaymentRecord();
        pending.setStatus(PaymentStatus.PENDING);
        PaymentRecord processing = new PaymentRecord();
        processing.setStatus(PaymentStatus.PROCESSING);
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any()))
                .thenReturn(List.of(pending), List.of(processing));

        assertThatThrownBy(() -> controller.cancel(93L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("正在处理");

        verify(paymentBatchService, never()).updateTerminalState(any(PaymentBatch.class));
    }

    @Test
    void cancelShouldRejectCompletedBatch() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(89L);
        batch.setBatchNo("PB-CANCEL-89");
        batch.setStatus(BatchStatus.COMPLETED);
        when(paymentBatchService.getById(89L)).thenReturn(batch);

        assertThatThrownBy(() -> controller.cancel(89L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许取消");
    }

    private static PayrollPaymentFailure paymentFailure() {
        PayrollPaymentFailure failure = new PayrollPaymentFailure();
        failure.setId(7L);
        failure.setWorkflowId(9001L);
        failure.setPayrollBatchId(1001L);
        failure.setBusinessKey("payroll_batch:1001");
        failure.setErrorMessage("payment provider rejected");
        failure.setStatus("unresolved");
        failure.setRetryCount(2);
        failure.setLastFailedTime(LocalDateTime.of(2026, 6, 2, 11, 30));
        failure.setLastRetryTime(LocalDateTime.of(2026, 6, 2, 12, 30));
        failure.setCreateTime(LocalDateTime.of(2026, 6, 2, 11, 30));
        failure.setUpdateTime(LocalDateTime.of(2026, 6, 2, 12, 30));
        failure.setCreateBy("system");
        failure.setUpdateBy("system");
        failure.setDeleted(0);
        failure.setVersion(4);
        return failure;
    }

    private static void assertPublicResponseShape(String json) {
        assertThat(json)
                .doesNotContain("deleted")
                .doesNotContain("version")
                .doesNotContain("createBy")
                .doesNotContain("updateBy");
    }
}
