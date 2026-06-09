package com.yiyundao.compensation.interfaces.controller.payment;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.interfaces.vo.payment.PaymentRecordItemVO;
import com.yiyundao.compensation.modules.payment.provider.SettlementResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementStatus;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import com.yiyundao.compensation.service.EncryptionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentRecordControllerTest {

    private final PaymentRecordService paymentRecordService = mock(PaymentRecordService.class);
    private final SettlementService settlementService = mock(SettlementService.class);
    private final EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
    private final EncryptionService encryptionService = mock(EncryptionService.class);
    private final PaymentRecordController controller = new PaymentRecordController(
            paymentRecordService,
            settlementService,
            employeeMapper,
            encryptionService
    );

    @Test
    void detailShouldReturnNotFoundWhenRecordMissing() {
        when(paymentRecordService.getById(10L)).thenReturn(null);

        ApiResponse<PaymentRecordItemVO> response = controller.detail(10L);

        assertThat(response.getCode()).isEqualTo(1002);
        assertThat(response.getMessage()).contains("支付记录不存在");
    }

    @Test
    void retryShouldDelegateToFailedRecordRetryFlow() {
        when(settlementService.retryFailedRecord(10L)).thenReturn(SettlementResult.builder()
                .success(true)
                .status(SettlementStatus.SUCCESS)
                .providerTradeNo("TRADE-10")
                .build());

        ApiResponse<String> response = controller.retry(10L);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).isEqualTo("TRADE-10");
        verify(settlementService).retryFailedRecord(10L);
        verify(settlementService, never()).singleTransfer(10L);
    }
}
