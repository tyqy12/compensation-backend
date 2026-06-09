package com.yiyundao.compensation.interfaces.controller.payment;

import com.yiyundao.compensation.modules.payment.provider.SettlementCallbackResult;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentNotifyControllerTest {

    @Mock
    private SettlementService settlementService;

    @Mock
    private HttpServletRequest request;

    @Test
    void alipayNotifyShouldFlattenMalformedParameterValues() {
        PaymentNotifyController controller = new PaymentNotifyController(settlementService);
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("out_biz_no", new String[]{"ORDER_001", "ignored"});
        params.put("empty_value", new String[0]);
        params.put("null_value", null);
        when(request.getParameterMap()).thenReturn(params);
        when(settlementService.handleCallback(eq("alipay"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(SettlementCallbackResult.builder().success(true).build());

        String response = controller.alipayNotify(request);

        assertThat(response).isEqualTo("success");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(settlementService).handleCallback(eq("alipay"), captor.capture());
        assertThat(captor.getValue()).containsEntry("out_biz_no", "ORDER_001");
        assertThat(captor.getValue()).containsEntry("empty_value", "");
        assertThat(captor.getValue()).containsEntry("null_value", "");
    }

    @Test
    void alipayNotifyShouldReturnFailureWhenCallbackResultIsNull() {
        PaymentNotifyController controller = new PaymentNotifyController(settlementService);
        when(request.getParameterMap()).thenReturn(Map.of("out_biz_no", new String[]{"ORDER_002"}));
        when(settlementService.handleCallback(eq("alipay"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(null);

        String response = controller.alipayNotify(request);

        assertThat(response).isEqualTo("failure");
    }

    @Test
    void alipayNotifyShouldReturnFailureWhenParameterMapIsNull() {
        PaymentNotifyController controller = new PaymentNotifyController(settlementService);
        when(request.getParameterMap()).thenReturn(null);
        when(settlementService.handleCallback(eq("alipay"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(SettlementCallbackResult.builder().success(false).build());

        String response = controller.alipayNotify(request);

        assertThat(response).isEqualTo("failure");
    }
}
