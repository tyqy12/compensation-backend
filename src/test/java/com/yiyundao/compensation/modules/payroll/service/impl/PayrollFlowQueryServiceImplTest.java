package com.yiyundao.compensation.modules.payroll.service.impl;

import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollDistributionItemMapper;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payroll.service.PayrollApprovalProjectionService;
import com.yiyundao.compensation.modules.payroll.service.PayrollDistributionService;
import com.yiyundao.compensation.modules.payroll.service.PayrollReconciliationTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PayrollFlowQueryServiceImplTest {

    @Mock
    private PayrollDistributionService payrollDistributionService;
    @Mock
    private PayrollReconciliationTaskService reconciliationTaskService;
    @Mock
    private PayrollDistributionItemMapper payrollDistributionItemMapper;
    @Mock
    private PayrollApprovalProjectionService approvalProjectionService;
    @Mock
    private PaymentBatchService paymentBatchService;
    @Mock
    private PaymentRecordService paymentRecordService;
    @Mock
    private PayrollBatchMapper payrollBatchMapper;

    @Test
    void pageDistributionsShouldRejectInvalidStatusFilter() {
        PayrollFlowQueryServiceImpl service = newService();

        assertThatThrownBy(() -> service.pageDistributions(1, 10, null, null, "unknown"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
                    assertThat(ex.getMessage()).contains("无效的发放状态");
                });

        verify(payrollDistributionService, never()).page(any(), any());
    }

    private PayrollFlowQueryServiceImpl newService() {
        return new PayrollFlowQueryServiceImpl(
                payrollDistributionService,
                reconciliationTaskService,
                payrollDistributionItemMapper,
                approvalProjectionService,
                paymentBatchService,
                paymentRecordService,
                payrollBatchMapper);
    }
}
