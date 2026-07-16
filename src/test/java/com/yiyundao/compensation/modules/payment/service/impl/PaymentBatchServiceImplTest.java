package com.yiyundao.compensation.modules.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payroll.service.PayrollDistributionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentBatchServiceImplTest {

    @Mock
    private PayrollBatchMapper payrollBatchMapper;
    @Mock
    private ObjectProvider<PayrollDistributionService> payrollDistributionServiceProvider;
    @Mock
    private PayrollDistributionService payrollDistributionService;

    @Test
    void updateStatusShouldSyncPayrollBatchWhenMarkingFailed() {
        PaymentBatchServiceImpl service = spy(new PaymentBatchServiceImpl(
                payrollBatchMapper,
                payrollDistributionServiceProvider
        ));
        PaymentBatch batch = new PaymentBatch();
        batch.setId(1101L);
        batch.setBatchNo("PB-ADMIN-FAIL-1");
        doReturn(true).when(service).update(org.mockito.ArgumentMatchers.any());
        doReturn(batch).when(service).getById(1101L);

        service.updateStatus(1101L, BatchStatus.FAILED);

        ArgumentCaptor<UpdateWrapper<com.yiyundao.compensation.modules.payroll.entity.PayrollBatch>> wrapperCaptor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollBatchMapper).update(isNull(), wrapperCaptor.capture());
        UpdateWrapper<com.yiyundao.compensation.modules.payroll.entity.PayrollBatch> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("status IN");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(PayrollBatchStatus.PAY_FAILED.getCode(),
                        PayrollBatchStatus.PAY_PROCESSING.getCode(),
                        PaymentBatchProcessStatus.FAILED.getCode());
    }

    @Test
    void updateStatusShouldKeepPayrollBatchPayFailedWhenCompletedBatchHasPartialSuccess() {
        PaymentBatchServiceImpl service = spy(new PaymentBatchServiceImpl(
                payrollBatchMapper,
                payrollDistributionServiceProvider
        ));
        PaymentBatch batch = new PaymentBatch();
        batch.setId(1103L);
        batch.setBatchNo("PB-ADMIN-PARTIAL-1");
        batch.setStatus(BatchStatus.COMPLETED);
        batch.setPaymentStatus(PaymentBatchProcessStatus.SUCCESS);
        batch.setSuccessCount(1);
        batch.setFailedCount(1);
        doReturn(true).when(service).update(org.mockito.ArgumentMatchers.any());
        doReturn(batch).when(service).getById(1103L);

        service.updateStatus(1103L, BatchStatus.COMPLETED);

        ArgumentCaptor<UpdateWrapper<com.yiyundao.compensation.modules.payroll.entity.PayrollBatch>> wrapperCaptor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollBatchMapper).update(isNull(), wrapperCaptor.capture());
        UpdateWrapper<com.yiyundao.compensation.modules.payroll.entity.PayrollBatch> wrapper = wrapperCaptor.getValue();
        wrapper.getSqlSegment();
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(PayrollBatchStatus.PAY_FAILED.getCode())
                .doesNotContain(PayrollBatchStatus.PAID.getCode());
    }

    @Test
    void updateTerminalStateShouldSyncPayrollBatchAndDistribution() {
        PaymentBatchServiceImpl service = spy(new PaymentBatchServiceImpl(
                payrollBatchMapper,
                payrollDistributionServiceProvider
        ));
        PaymentBatch batch = new PaymentBatch();
        batch.setId(1102L);
        batch.setBatchNo("PB-ADMIN-FAIL-2");
        batch.setDistributionId(2202L);
        batch.setStatus(BatchStatus.FAILED);
        when(payrollDistributionServiceProvider.getIfAvailable()).thenReturn(payrollDistributionService);
        doReturn(true).when(service).updateById(batch);

        service.updateTerminalState(batch);

        ArgumentCaptor<UpdateWrapper<com.yiyundao.compensation.modules.payroll.entity.PayrollBatch>> wrapperCaptor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollBatchMapper).update(isNull(), wrapperCaptor.capture());
        UpdateWrapper<com.yiyundao.compensation.modules.payroll.entity.PayrollBatch> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("status IN");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(PayrollBatchStatus.PAY_FAILED.getCode(),
                        PayrollBatchStatus.PAY_PROCESSING.getCode());
        verify(payrollDistributionService).syncFromPaymentBatch(batch);
    }

    @Test
    void pagePaymentBatchesShouldRejectInvalidFilters() {
        PaymentBatchServiceImpl service = new PaymentBatchServiceImpl(
                payrollBatchMapper,
                payrollDistributionServiceProvider
        );

        assertThatThrownBy(() -> service.pagePaymentBatches(
                1, 10, null, "unknown", null, null, null, "submitTime", "desc"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(ex.getMessage()).contains("无效的批次状态");
                });
        assertThatThrownBy(() -> service.pagePaymentBatches(
                1, 10, null, null, "unknown", null, null, "submitTime", "desc"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(ex.getMessage()).contains("无效的支付类型");
                });
        assertThatThrownBy(() -> service.pagePaymentBatches(
                1, 10, null, null, null, "2026-99-01", null, "submitTime", "desc"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(ex.getMessage()).contains("无效的开始日期");
                });
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pagePaymentBatchesShouldClampPageAndSize() {
        PaymentBatchServiceImpl service = spy(new PaymentBatchServiceImpl(
                payrollBatchMapper,
                payrollDistributionServiceProvider
        ));
        doReturn(new Page<PaymentBatch>(1, 200, 0))
                .when(service)
                .page(org.mockito.ArgumentMatchers.any(Page.class), org.mockito.ArgumentMatchers.any());

        Page<PaymentBatch> result = service.pagePaymentBatches(
                -1,
                1000,
                null,
                null,
                null,
                null,
                null,
                "submitTime",
                "desc"
        );

        assertThat(result.getCurrent()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(200);
    }
}
