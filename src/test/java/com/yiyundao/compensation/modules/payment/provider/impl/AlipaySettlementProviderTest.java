package com.yiyundao.compensation.modules.payment.provider.impl;

import com.alipay.api.AlipayApiException;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.provider.SettlementRequest;
import com.yiyundao.compensation.modules.payment.provider.SettlementStatus;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.service.AlipayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlipaySettlementProviderTest {

    private final AlipayService alipayService = mock(AlipayService.class);
    private final PaymentRecordService paymentRecordService = mock(PaymentRecordService.class);
    private AlipaySettlementProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AlipaySettlementProvider(alipayService, paymentRecordService);
    }

    @Test
    void handleCallbackShouldNotAckUnknownTradeStatus() {
        var result = provider.handleCallback(Map.of(
                "out_biz_no", "ALI_ORDER_001",
                "trade_no", "ALI_TRADE_001",
                "trade_status", "WAIT_BUYER_PAY"
        ));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getBizNo()).isEqualTo("ALI_ORDER_001");
        assertThat(result.getStatus()).isEqualTo(SettlementStatus.PROCESSING);
        assertThat(result.getErrorMsg()).contains("未知支付宝回调状态");
        verify(alipayService, never()).handleNotification(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void handleCallbackShouldNotAckWhenLocalRecordMissing() {
        doThrow(new IllegalStateException("支付宝回调未匹配到支付记录")).when(alipayService).handleNotification(
                org.mockito.ArgumentMatchers.eq("ALI_ORDER_MISSING"),
                org.mockito.ArgumentMatchers.eq("ALI_TRADE_MISSING"),
                org.mockito.ArgumentMatchers.eq("TRADE_SUCCESS")
        );

        var result = provider.handleCallback(Map.of(
                "out_biz_no", "ALI_ORDER_MISSING",
                "trade_no", "ALI_TRADE_MISSING",
                "trade_status", "TRADE_SUCCESS"
        ));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getBizNo()).isEqualTo("ALI_ORDER_MISSING");
        assertThat(result.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(result.getErrorMsg()).contains("未匹配到支付记录");
        verifyNoInteractions(paymentRecordService);
    }

    @Test
    void handleCallbackShouldTreatTradeFinishedAsSuccess() {
        var result = provider.handleCallback(Map.of(
                "out_biz_no", "ALI_ORDER_FINISHED",
                "trade_no", "ALI_TRADE_FINISHED",
                "trade_status", "TRADE_FINISHED"
        ));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBizNo()).isEqualTo("ALI_ORDER_FINISHED");
        assertThat(result.getStatus()).isEqualTo(SettlementStatus.SUCCESS);
        verify(alipayService).handleNotification("ALI_ORDER_FINISHED", "ALI_TRADE_FINISHED", "TRADE_FINISHED");
    }

    @Test
    void queryStatusShouldFailFastForLocalAlipayKeyConfigurationError() throws Exception {
        doThrow(new IllegalStateException("支付宝应用私钥格式错误：请配置 PKCS8 格式 RSA 私钥"))
                .when(alipayService).queryTransferStatus("ALI_BAD_KEY");

        SettlementStatus status = provider.queryStatus("ALI_BAD_KEY");

        assertThat(status).isEqualTo(SettlementStatus.FAILED);
    }

    @Test
    void singleTransferShouldKeepUnknownRemoteResultProcessing() throws Exception {
        PaymentRecord record = new PaymentRecord();
        record.setId(42L);
        record.setStatus(PaymentStatus.PROCESSING);
        record.setProviderOrderNo("COMP_UNKNOWN_42");
        record.setErrorCode(AlipayService.RESULT_UNKNOWN_ERROR_CODE);
        record.setErrorMsg("支付宝转账结果未知");
        when(paymentRecordService.getById(42L)).thenReturn(record);
        doThrow(new AlipayApiException(AlipayService.RESULT_UNKNOWN_ERROR_CODE, "支付宝转账结果未知"))
                .when(alipayService).singleTransfer(42L);

        var result = provider.singleTransfer(SettlementRequest.builder()
                .paymentRecordId(42L)
                .build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(SettlementStatus.PROCESSING);
        assertThat(result.getProviderOrderNo()).isEqualTo("COMP_UNKNOWN_42");
        verify(paymentRecordService).getById(42L);
    }
}
