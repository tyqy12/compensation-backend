package com.yiyundao.compensation.modules.payment.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollApprovalProjection;
import com.yiyundao.compensation.modules.payroll.service.PayrollApprovalProjectionService;
import com.yiyundao.compensation.modules.payroll.service.PayrollProcessManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationSchedulerTest {

    @Mock
    private SettlementService settlementService;

    @Mock
    private PaymentBatchService paymentBatchService;

    @Mock
    private PayrollApprovalProjectionService approvalProjectionService;

    @Mock
    private PayrollProcessManager payrollProcessManager;

    private PaymentReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PaymentReconciliationScheduler(
                settlementService,
                paymentBatchService,
                approvalProjectionService,
                payrollProcessManager
        );
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "batchLimit", 20);
        ReflectionTestUtils.setField(scheduler, "recordLimitPerBatch", 200);
        ReflectionTestUtils.setField(scheduler, "recoverStaleSubmittedEnabled", true);
        ReflectionTestUtils.setField(scheduler, "staleSubmittedMinutes", 10);
        ReflectionTestUtils.setField(scheduler, "recoverStaleApprovedPayrollEnabled", true);
        ReflectionTestUtils.setField(scheduler, "staleApprovedPayrollMinutes", 10);
    }

    @Test
    void reconcileInFlightPaymentsShouldRecoverStalePayrollSubmission() {
        PaymentBatch batch = new PaymentBatch();
        batch.setBatchNo("PDS-STALE-001");
        when(settlementService.reconcileProcessingBatches(20, 200)).thenReturn(0);
        when(paymentBatchService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any()))
                .thenReturn(List.of(batch));

        scheduler.reconcileInFlightPayments();

        verify(settlementService).batchTransfer("PDS-STALE-001");
    }

    @Test
    void reconcileInFlightPaymentsShouldRecoverApprovedPayrollWithStaleProjection() {
        PayrollApprovalProjection projection = new PayrollApprovalProjection();
        projection.setWorkflowId(9001L);
        projection.setDistributionId(55L);
        projection.setBusinessStatus("IN_PROGRESS");

        when(settlementService.reconcileProcessingBatches(20, 200)).thenReturn(0);
        when(paymentBatchService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any()))
                .thenReturn(List.of());
        when(approvalProjectionService.listStalePendingDistributionApprovals(any(), anyInt()))
                .thenReturn(List.of(projection));

        scheduler.reconcileInFlightPayments();

        verify(payrollProcessManager).onApprovalApproved(55L, 9001L, null);
    }

    @Test
    void reconcileInFlightPaymentsShouldRecoverApprovedAdminBypassWithPseudoWorkflow() {
        PayrollApprovalProjection projection = new PayrollApprovalProjection();
        projection.setWorkflowId(-55L);
        projection.setDistributionId(55L);
        projection.setBusinessStatus("APPROVED");

        when(settlementService.reconcileProcessingBatches(20, 200)).thenReturn(0);
        when(paymentBatchService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any()))
                .thenReturn(List.of());
        when(approvalProjectionService.listStalePendingDistributionApprovals(any(), anyInt()))
                .thenReturn(List.of(projection));

        scheduler.reconcileInFlightPayments();

        verify(payrollProcessManager).onApprovalApproved(55L, -55L, null);
    }
}
