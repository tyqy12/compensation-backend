package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.PayrollConfirmationStatus;
import com.yiyundao.compensation.enums.PayrollDistributionItemStatus;
import com.yiyundao.compensation.enums.PayrollDistributionStatus;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollDistributionItemMapper;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistributionItem;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.PayrollReconciliationTaskService;
import com.yiyundao.compensation.modules.payroll.support.PayrollDistributionRoutingSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doNothing;

class PayrollDistributionServiceImplTest {

    @Test
    void createOrReuseForBatchShouldResetCancelledDistributionForResubmission() {
        PayrollBatchMapper payrollBatchMapper = mock(PayrollBatchMapper.class);
        PayrollDistributionItemMapper itemMapper = mock(PayrollDistributionItemMapper.class);
        PayrollLineService payrollLineService = mock(PayrollLineService.class);
        PayrollDistributionServiceImpl service = spy(new PayrollDistributionServiceImpl(
                payrollBatchMapper,
                itemMapper,
                payrollLineService,
                mock(EmployeeMapper.class),
                mock(PaymentRecordService.class),
                mock(PayrollDistributionRoutingSupport.class),
                mock(PayrollReconciliationTaskService.class)
        ));

        PayrollBatch batch = new PayrollBatch();
        batch.setId(20L);
        batch.setBatchRevision(1);
        batch.setConfirmationRequired(Boolean.FALSE);

        PayrollDistribution cancelled = new PayrollDistribution();
        cancelled.setId(10L);
        cancelled.setBatchId(20L);
        cancelled.setBatchRevision(1);
        cancelled.setDistributionStatus(PayrollDistributionStatus.CANCELLED);
        cancelled.setApprovalWorkflowId(9001L);

        PayrollLine line = line(101L, 501L, "100.00");
        doNothing().when(service).supersedeObsolete(20L, 1);
        doReturn(cancelled).when(service).getByBatchIdAndRevision(20L, 1);
        when(payrollLineService.list(org.mockito.ArgumentMatchers.<Wrapper<PayrollLine>>any()))
                .thenReturn(List.of(line));
        doReturn(true).when(service).updateById(cancelled);
        doReturn(List.of()).when(service).createOrRefreshItems(cancelled);

        PayrollDistribution result = service.createOrReuseForBatch(batch);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getDistributionStatus()).isEqualTo(PayrollDistributionStatus.PLANNED);
        assertThat(result.getApprovalWorkflowId()).isEqualTo(9001L);
        verify(service).updateById(cancelled);
        verify(service).createOrRefreshItems(cancelled);
    }

    @Test
    void createOrRefreshItemsShouldRejectNonPositiveNetAmountBeforeRouting() {
        PayrollBatchMapper payrollBatchMapper = mock(PayrollBatchMapper.class);
        PayrollDistributionItemMapper itemMapper = mock(PayrollDistributionItemMapper.class);
        PayrollLineService payrollLineService = mock(PayrollLineService.class);
        EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
        PayrollDistributionRoutingSupport routingSupport = mock(PayrollDistributionRoutingSupport.class);
        PayrollDistributionServiceImpl service = new PayrollDistributionServiceImpl(
                payrollBatchMapper,
                itemMapper,
                payrollLineService,
                employeeMapper,
                mock(PaymentRecordService.class),
                routingSupport,
                mock(PayrollReconciliationTaskService.class)
        );

        PayrollDistribution distribution = new PayrollDistribution();
        distribution.setId(10L);
        distribution.setBatchId(20L);
        distribution.setBatchRevision(1);
        distribution.setRetryLimit(3);
        distribution.setDistributionStatus(PayrollDistributionStatus.PLANNED);

        PayrollBatch batch = new PayrollBatch();
        batch.setId(20L);
        batch.setConfirmationRequired(Boolean.FALSE);
        when(payrollBatchMapper.selectById(20L)).thenReturn(batch);

        PayrollLine zeroLine = line(101L, 501L, "0.00");
        PayrollLine negativeLine = line(102L, 502L, "-10.00");
        when(payrollLineService.list(org.mockito.ArgumentMatchers.<Wrapper<PayrollLine>>any()))
                .thenReturn(List.of(zeroLine, negativeLine));

        Employee employee1 = employee(501L, "零额员工");
        Employee employee2 = employee(502L, "负额员工");
        when(employeeMapper.selectBatchIds(any())).thenReturn(List.of(employee1, employee2));
        when(itemMapper.selectList(any())).thenReturn(List.of());

        service.createOrRefreshItems(distribution);

        ArgumentCaptor<PayrollDistributionItem> itemCaptor = ArgumentCaptor.forClass(PayrollDistributionItem.class);
        verify(itemMapper, org.mockito.Mockito.times(2)).insert(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .extracting(PayrollDistributionItem::getItemStatus)
                .containsExactly(PayrollDistributionItemStatus.FAILED, PayrollDistributionItemStatus.FAILED);
        assertThat(itemCaptor.getAllValues())
                .extracting(PayrollDistributionItem::getFailureReason)
                .containsExactly("净发金额必须大于0", "净发金额必须大于0");
        assertThat(itemCaptor.getAllValues())
                .extracting(PayrollDistributionItem::getRetryCount)
                .containsExactly(3, 3);
        assertThat(itemCaptor.getAllValues())
                .extracting(PayrollDistributionItem::getPaymentMethod)
                .containsExactly("UNKNOWN", "UNKNOWN");
        assertThat(itemCaptor.getAllValues())
                .extracting(PayrollDistributionItem::getAmount)
                .containsExactly(new BigDecimal("0.00"), new BigDecimal("-10.00"));

        verify(routingSupport, never()).buildSnapshot(any(), any(), any());
    }

    @Test
    void createOrRefreshItemsShouldRejectStaleDistributionRevision() {
        PayrollBatchMapper payrollBatchMapper = mock(PayrollBatchMapper.class);
        PayrollLineService payrollLineService = mock(PayrollLineService.class);
        PayrollDistributionServiceImpl service = new PayrollDistributionServiceImpl(
                payrollBatchMapper,
                mock(PayrollDistributionItemMapper.class),
                payrollLineService,
                mock(EmployeeMapper.class),
                mock(PaymentRecordService.class),
                mock(PayrollDistributionRoutingSupport.class),
                mock(PayrollReconciliationTaskService.class)
        );

        PayrollDistribution distribution = new PayrollDistribution();
        distribution.setId(12L);
        distribution.setBatchId(22L);
        distribution.setBatchRevision(1);

        PayrollBatch batch = new PayrollBatch();
        batch.setId(22L);
        batch.setBatchRevision(2);
        when(payrollBatchMapper.selectById(22L)).thenReturn(batch);

        assertThatThrownBy(() -> service.createOrRefreshItems(distribution))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("发放单已过期");
        verify(payrollLineService, never()).list(org.mockito.ArgumentMatchers.<Wrapper<PayrollLine>>any());
    }

    @Test
    void createOrRefreshItemsShouldRejectUnconfirmedLinesWhenConfirmationIsRequired() {
        PayrollBatchMapper payrollBatchMapper = mock(PayrollBatchMapper.class);
        PayrollDistributionItemMapper itemMapper = mock(PayrollDistributionItemMapper.class);
        PayrollLineService payrollLineService = mock(PayrollLineService.class);
        EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
        PayrollDistributionRoutingSupport routingSupport = mock(PayrollDistributionRoutingSupport.class);
        PayrollDistributionServiceImpl service = new PayrollDistributionServiceImpl(
                payrollBatchMapper,
                itemMapper,
                payrollLineService,
                employeeMapper,
                mock(PaymentRecordService.class),
                routingSupport,
                mock(PayrollReconciliationTaskService.class)
        );

        PayrollDistribution distribution = new PayrollDistribution();
        distribution.setId(11L);
        distribution.setBatchId(21L);
        distribution.setBatchRevision(1);
        distribution.setRetryLimit(3);
        distribution.setDistributionStatus(PayrollDistributionStatus.PLANNED);

        PayrollBatch batch = new PayrollBatch();
        batch.setId(21L);
        batch.setConfirmationRequired(Boolean.TRUE);
        when(payrollBatchMapper.selectById(21L)).thenReturn(batch);

        PayrollLine pendingLine = line(103L, 503L, "100.00");
        pendingLine.setConfirmationStatus(PayrollConfirmationStatus.PENDING.getCode());
        when(payrollLineService.list(org.mockito.ArgumentMatchers.<Wrapper<PayrollLine>>any()))
                .thenReturn(List.of(pendingLine));

        assertThatThrownBy(() -> service.createOrRefreshItems(distribution))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("还有员工待确认或异议未处理")
                .hasMessageContaining("lineIds=103");

        verify(itemMapper, never()).insert(any(PayrollDistributionItem.class));
        verify(itemMapper, never()).updateById(any(PayrollDistributionItem.class));
        verify(employeeMapper, never()).selectBatchIds(any());
        verify(routingSupport, never()).buildSnapshot(any(), any(), any());
    }

    @Test
    void syncFromPaymentBatchShouldKeepRetryableFailurePlannedWhenProviderOrderWasNotSubmitted() {
        PayrollDistributionItemMapper itemMapper = mock(PayrollDistributionItemMapper.class);
        PaymentRecordService paymentRecordService = mock(PaymentRecordService.class);
        PayrollReconciliationTaskService reconciliationTaskService = mock(PayrollReconciliationTaskService.class);
        PayrollDistributionServiceImpl service = spy(new PayrollDistributionServiceImpl(
                mock(PayrollBatchMapper.class),
                itemMapper,
                mock(PayrollLineService.class),
                mock(EmployeeMapper.class),
                paymentRecordService,
                mock(PayrollDistributionRoutingSupport.class),
                reconciliationTaskService
        ));

        PayrollDistribution distribution = distributionForSync();
        PayrollDistributionItem item = distributionItemForSync();
        PaymentRecord failedRecord = failedRecordForSync(null);
        PaymentBatch paymentBatch = paymentBatchForSync();

        doReturn(distribution).when(service).getById(10L);
        doReturn(List.of(item)).when(service).listActiveItems(10L);
        doReturn(List.of(item)).when(service).listRetryableItems(10L);
        doReturn(true).when(service).updateById(distribution);
        when(paymentRecordService.getByBatchNo("PB-DIST-SYNC", null)).thenReturn(List.of(failedRecord));
        when(paymentRecordService.getById(9001L)).thenReturn(failedRecord);

        service.syncFromPaymentBatch(paymentBatch);

        assertThat(distribution.getDistributionStatus()).isEqualTo(PayrollDistributionStatus.PLANNED);
        verify(reconciliationTaskService, never()).createOrRefresh(any(PayrollDistribution.class));
    }

    @Test
    void syncFromPaymentBatchShouldFailReconciliationWhenFailedRecordHasProviderOrder() {
        PayrollDistributionItemMapper itemMapper = mock(PayrollDistributionItemMapper.class);
        PaymentRecordService paymentRecordService = mock(PaymentRecordService.class);
        PayrollReconciliationTaskService reconciliationTaskService = mock(PayrollReconciliationTaskService.class);
        PayrollDistributionServiceImpl service = spy(new PayrollDistributionServiceImpl(
                mock(PayrollBatchMapper.class),
                itemMapper,
                mock(PayrollLineService.class),
                mock(EmployeeMapper.class),
                paymentRecordService,
                mock(PayrollDistributionRoutingSupport.class),
                reconciliationTaskService
        ));

        PayrollDistribution distribution = distributionForSync();
        PayrollDistributionItem item = distributionItemForSync();
        PaymentRecord failedRecord = failedRecordForSync("ALI_SUBMITTED_9001");
        PaymentBatch paymentBatch = paymentBatchForSync();

        doReturn(distribution).when(service).getById(10L);
        doReturn(List.of(item)).when(service).listActiveItems(10L);
        doReturn(List.of(item)).when(service).listRetryableItems(10L);
        doReturn(true).when(service).updateById(distribution);
        when(paymentRecordService.getByBatchNo("PB-DIST-SYNC", null)).thenReturn(List.of(failedRecord));
        when(paymentRecordService.getById(9001L)).thenReturn(failedRecord);

        service.syncFromPaymentBatch(paymentBatch);

        assertThat(distribution.getDistributionStatus()).isEqualTo(PayrollDistributionStatus.FAILED);
        verify(reconciliationTaskService).createOrRefresh(distribution);
    }

    private static PaymentBatch paymentBatchForSync() {
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-DIST-SYNC");
        paymentBatch.setDistributionId(10L);
        return paymentBatch;
    }

    private static PayrollDistribution distributionForSync() {
        PayrollDistribution distribution = new PayrollDistribution();
        distribution.setId(10L);
        distribution.setTotalAmount(new BigDecimal("100.00"));
        distribution.setActualAmount(BigDecimal.ZERO);
        distribution.setRetryLimit(3);
        distribution.setAllowPartial(Boolean.FALSE);
        distribution.setDistributionStatus(PayrollDistributionStatus.PROCESSING);
        return distribution;
    }

    private static PayrollDistributionItem distributionItemForSync() {
        PayrollDistributionItem item = new PayrollDistributionItem();
        item.setId(1001L);
        item.setDistributionId(10L);
        item.setPaymentRecordId(9001L);
        item.setAmount(new BigDecimal("100.00"));
        item.setRetryCount(1);
        item.setItemStatus(PayrollDistributionItemStatus.RETRYING);
        return item;
    }

    private static PaymentRecord failedRecordForSync(String providerOrderNo) {
        PaymentRecord record = new PaymentRecord();
        record.setId(9001L);
        record.setBatchNo("PB-DIST-SYNC");
        record.setStatus(PaymentStatus.FAILED);
        record.setErrorMsg("渠道失败");
        record.setProviderOrderNo(providerOrderNo);
        return record;
    }

    private static PayrollLine line(Long lineId, Long employeeId, String netAmount) {
        PayrollLine line = new PayrollLine();
        line.setId(lineId);
        line.setBatchId(20L);
        line.setEmployeeId(employeeId);
        line.setNetAmount(new BigDecimal(netAmount));
        return line;
    }

    private static Employee employee(Long id, String name) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setName(name);
        return employee;
    }
}
