package com.yiyundao.compensation.interfaces.controller.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PaymentType;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollCalculationStatus;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchCreateRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchUpdateRequest;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.service.PayCycleService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayrollBatchControllerResponseTest {

    private PayrollBatchService payrollBatchService;
    private PayrollPaymentService payrollPaymentService;
    private PayCycleService payCycleService;
    private PayrollBatchMapper payrollBatchMapper;
    private PayrollBatchController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        payrollBatchService = mock(PayrollBatchService.class);
        payrollPaymentService = mock(PayrollPaymentService.class);
        payCycleService = mock(PayCycleService.class);
        payrollBatchMapper = mock(PayrollBatchMapper.class);
        controller = new PayrollBatchController(
                payrollBatchService,
                null,
                payrollBatchMapper,
                payrollPaymentService,
                null,
                null,
                null,
                payCycleService
        );
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void createShouldReturnPublicPayrollBatchResponse() throws Exception {
        when(payCycleService.getById(88L)).thenReturn(openCycle("2026-05"));
        when(payrollBatchService.save(any(PayrollBatch.class))).thenAnswer(invocation -> {
            PayrollBatch batch = invocation.getArgument(0);
            batch.setId(1001L);
            batch.setCreateTime(LocalDateTime.of(2026, 6, 2, 9, 0));
            batch.setUpdateTime(LocalDateTime.of(2026, 6, 2, 9, 1));
            batch.setCreateBy("finance");
            batch.setUpdateBy("finance");
            batch.setDeleted(0);
            batch.setVersion(7);
            return true;
        });
        PayrollBatchCreateRequest request = new PayrollBatchCreateRequest();
        request.setPayCycleId(88L);
        request.setPeriodLabel("2026-05");
        request.setType("full_time");
        request.setScopeJson("{\"department\":\"R&D\"}");
        request.setCurrency("CNY");
        request.setConfirmationRequired(true);
        request.setConfirmationMode("group");
        request.setRemark("monthly payroll");

        String json = objectMapper.writeValueAsString(controller.create(request));

        verify(payCycleService).getById(88L);
        assertPayrollBatchResponseShape(json);
        assertThat(json)
                .contains("\"id\":1001")
                .contains("\"status\":\"draft\"")
                .contains("\"calculationStatus\":\"draft\"")
                .contains("\"confirmationMode\":\"group\"")
                .contains("\"scopeJson\":\"{\\\"department\\\":\\\"R&D\\\"}\"");
    }

    @Test
    void createShouldFillPeriodLabelFromOpenPayCycle() throws Exception {
        when(payCycleService.getById(88L)).thenReturn(openCycle("2026-07"));
        when(payrollBatchService.save(any(PayrollBatch.class))).thenAnswer(invocation -> {
            PayrollBatch batch = invocation.getArgument(0);
            batch.setId(1002L);
            return true;
        });
        PayrollBatchCreateRequest request = new PayrollBatchCreateRequest();
        request.setPayCycleId(88L);
        request.setType("full_time");

        String json = objectMapper.writeValueAsString(controller.create(request));

        assertThat(json).contains("\"periodLabel\":\"2026-07\"");
    }

    @Test
    void createShouldNormalizePayrollTypeBeforeSaving() {
        when(payCycleService.getById(88L)).thenReturn(openCycle("2026-07"));
        when(payrollBatchService.save(any(PayrollBatch.class))).thenAnswer(invocation -> {
            PayrollBatch batch = invocation.getArgument(0);
            assertThat(batch.getType()).isEqualTo("part_time");
            return true;
        });
        PayrollBatchCreateRequest request = new PayrollBatchCreateRequest();
        request.setPayCycleId(88L);
        request.setType("PART_TIME");

        PayCycle cycle = openCycle("2026-07");
        cycle.setType("part_time");
        when(payCycleService.getById(88L)).thenReturn(cycle);

        controller.create(request);

        verify(payrollBatchService).save(any(PayrollBatch.class));
    }

    @Test
    void createShouldRejectInvalidPayrollTypeBeforeSaving() {
        PayrollBatchCreateRequest request = new PayrollBatchCreateRequest();
        request.setType("contractor");

        assertThatThrownBy(() -> controller.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_INVALID);

        verify(payrollBatchService, never()).save(any(PayrollBatch.class));
    }

    @Test
    void createShouldRejectClosedPayCycle() {
        when(payCycleService.getById(88L)).thenReturn(cycle("2026-05", "closed"));
        PayrollBatchCreateRequest request = new PayrollBatchCreateRequest();
        request.setPayCycleId(88L);
        request.setPeriodLabel("2026-05");
        request.setType("full_time");

        assertThatThrownBy(() -> controller.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATUS);
    }

    @Test
    void createShouldRejectMissingPayCycle() {
        PayrollBatchCreateRequest request = new PayrollBatchCreateRequest();
        request.setType("full_time");

        assertThatThrownBy(() -> controller.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_INVALID);
        verify(payrollBatchService, never()).save(any(PayrollBatch.class));
    }

    @Test
    void createShouldRejectPayCycleWithDifferentPayrollType() {
        PayCycle cycle = openCycle("2026-05");
        cycle.setType("part_time");
        when(payCycleService.getById(88L)).thenReturn(cycle);

        PayrollBatchCreateRequest request = new PayrollBatchCreateRequest();
        request.setPayCycleId(88L);
        request.setPeriodLabel("2026-05");
        request.setType("full_time");

        assertThatThrownBy(() -> controller.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_INVALID);
        verify(payrollBatchService, never()).save(any(PayrollBatch.class));
    }

    @Test
    void updateShouldReturnPublicPayrollBatchResponse() throws Exception {
        PayrollBatch existing = payrollBatch();
        existing.setStatus(PayrollBatchStatus.DRAFT);
        when(payrollBatchService.getById(1001L)).thenReturn(existing);
        when(payCycleService.getById(88L)).thenReturn(openCycle("2026-06"));
        PayrollBatchUpdateRequest request = new PayrollBatchUpdateRequest();
        request.setPeriodLabel("2026-06");
        request.setCurrency("USD");
        request.setConfirmationRequired(false);
        request.setConfirmationMode("individual");
        request.setRemark("adjusted");

        String json = objectMapper.writeValueAsString(controller.update(1001L, request));

        verify(payrollBatchService).updateById(existing);
        assertPayrollBatchResponseShape(json);
        assertThat(json)
                .contains("\"periodLabel\":\"2026-06\"")
                .contains("\"currency\":\"USD\"")
                .contains("\"confirmationRequired\":false")
                .contains("\"confirmationMode\":\"individual\"")
                .contains("\"remark\":\"adjusted\"");
    }

    @Test
    void updateShouldRejectPeriodLabelDifferentFromPayCycle() {
        PayrollBatch existing = payrollBatch();
        existing.setStatus(PayrollBatchStatus.DRAFT);
        when(payrollBatchService.getById(1001L)).thenReturn(existing);
        when(payCycleService.getById(88L)).thenReturn(openCycle("2026-05"));
        PayrollBatchUpdateRequest request = new PayrollBatchUpdateRequest();
        request.setPeriodLabel("2026-06");

        assertThatThrownBy(() -> controller.update(1001L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void getShouldReturnPublicPayrollBatchResponse() throws Exception {
        when(payrollBatchService.getById(1001L)).thenReturn(payrollBatch());

        String json = objectMapper.writeValueAsString(controller.get(1001L));

        assertPayrollBatchResponseShape(json);
        assertThat(json)
                .contains("\"paymentBatchNo\":\"PMT-202606\"")
                .contains("\"settlementProviderCode\":\"alipay\"")
                .contains("\"approvalWorkflowId\":9001");
    }

    @Test
    void getShouldRejectMissingBatch() {
        when(payrollBatchService.getById(404L)).thenReturn(null);

        assertThatThrownBy(() -> controller.get(404L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void listShouldRejectInvalidFilters() {
        assertThatThrownBy(() -> controller.list(1, 10, "unknown", null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_INVALID);
        assertThatThrownBy(() -> controller.list(1, 10, null, null, "unknown"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void listShouldClampPageAndSizeBeforeQuerying() {
        when(payrollBatchMapper.selectBatchSummaryList(null, null, null, 0, 200))
                .thenReturn(List.of());

        var response = controller.list(-1, 1000, null, null, null);

        assertThat(response.getData().getPageNum()).isEqualTo(1);
        assertThat(response.getData().getPageSize()).isEqualTo(200);
        verify(payrollBatchMapper).selectBatchSummaryList(null, null, null, 0, 200);
    }

    @Test
    void retryPaymentShouldReturnPublicPaymentBatchResponse() throws Exception {
        when(payrollBatchService.getById(1001L)).thenReturn(payrollBatch());
        when(payrollPaymentService.retryFailedPayment(1001L, true)).thenReturn(paymentBatch());

        String json = objectMapper.writeValueAsString(controller.retryPayment(1001L, true));

        assertThat(json)
                .contains("\"batchNo\":\"PAY-202606\"")
                .contains("\"paymentType\":\"salary\"")
                .contains("\"status\":\"processing\"")
                .contains("\"paymentStatus\":\"processing\"")
                .contains("\"distributionId\":3001")
                .doesNotContain("deleted")
                .doesNotContain("version")
                .doesNotContain("createBy")
                .doesNotContain("updateBy");
    }

    private void assertPayrollBatchResponseShape(String json) {
        assertThat(json)
                .contains("\"payCycleId\":88")
                .contains("\"periodLabel\"")
                .contains("\"batchRevision\"")
                .doesNotContain("deleted")
                .doesNotContain("version")
                .doesNotContain("createBy")
                .doesNotContain("updateBy");
    }

    private PayrollBatch payrollBatch() {
        PayrollBatch batch = new PayrollBatch();
        batch.setId(1001L);
        batch.setPayCycleId(88L);
        batch.setPeriodLabel("2026-05");
        batch.setType("full_time");
        batch.setScopeJson("{\"department\":\"R&D\"}");
        batch.setCurrency("CNY");
        batch.setCalculationStatus(PayrollCalculationStatus.CALCULATED);
        batch.setBatchRevision(2);
        batch.setStatus(PayrollBatchStatus.PAY_FAILED);
        batch.setApprovalWorkflowId(9001L);
        batch.setPaymentBatchNo("PMT-202606");
        batch.setSettlementProviderCode("alipay");
        batch.setConfirmationRequired(true);
        batch.setConfirmationMode("group");
        batch.setConfirmationCompletedTime(LocalDateTime.of(2026, 6, 2, 10, 30));
        batch.setRemark("monthly payroll");
        batch.setCreateTime(LocalDateTime.of(2026, 6, 2, 9, 0));
        batch.setUpdateTime(LocalDateTime.of(2026, 6, 2, 9, 1));
        batch.setCreateBy("finance");
        batch.setUpdateBy("finance");
        batch.setDeleted(0);
        batch.setVersion(7);
        return batch;
    }

    private PaymentBatch paymentBatch() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(7001L);
        batch.setBatchNo("PAY-202606");
        batch.setBatchName("June payroll retry");
        batch.setPaymentType(PaymentType.SALARY);
        batch.setTotalAmount(new BigDecimal("1200.00"));
        batch.setTotalCount(10);
        batch.setSuccessCount(3);
        batch.setFailedCount(1);
        batch.setStatus(BatchStatus.PROCESSING);
        batch.setDistributionId(3001L);
        batch.setPaymentStatus(PaymentBatchProcessStatus.PROCESSING);
        batch.setSubmitTime(LocalDateTime.of(2026, 6, 2, 11, 0));
        batch.setProcessStartTime(LocalDateTime.of(2026, 6, 2, 11, 5));
        batch.setRemark("retry");
        batch.setCreateTime(LocalDateTime.of(2026, 6, 2, 10, 50));
        batch.setUpdateTime(LocalDateTime.of(2026, 6, 2, 11, 10));
        batch.setCreateBy("finance");
        batch.setUpdateBy("finance");
        batch.setDeleted(0);
        batch.setVersion(2);
        return batch;
    }

    private PayCycle openCycle(String periodLabel) {
        return cycle(periodLabel, "open");
    }

    private PayCycle cycle(String periodLabel, String status) {
        PayCycle cycle = new PayCycle();
        cycle.setId(88L);
        cycle.setType("full_time");
        cycle.setRuleTemplateId(20L);
        cycle.setRuleTemplateVersion(1L);
        cycle.setPeriodLabel(periodLabel);
        cycle.setStatus(status);
        return cycle;
    }
}
