package com.yiyundao.compensation.interfaces.controller.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.modules.payment.provider.SettlementCallbackResult;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementCallbackControllerTest {

    @Mock
    private SettlementService settlementService;

    private SettlementCallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new SettlementCallbackController(settlementService, new ObjectMapper());
    }

    @Test
    void handleCallback_shouldNotExposeInternalFailureMessage() {
        when(settlementService.handleCallback(eq("yunzhanghu"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(SettlementCallbackResult.builder()
                        .success(false)
                        .errorMsg("signature invalid for order ORDER_SECRET_123 access_token=secret")
                        .build());

        ResponseEntity<String> response = controller.handleCallback(
                "yunzhanghu",
                Map.of("provider_order_no", "ORDER_SECRET_123"),
                null
        );

        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
        assertThat(response.getBody()).isEqualTo("FAIL");
        assertThat(response.getBody()).doesNotContain("ORDER_SECRET_123", "access_token", "secret");
    }

    @Test
    void handleCallback_shouldMergeJsonBodyWithQueryParams() {
        when(settlementService.handleCallback(eq("yunzhanghu"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(SettlementCallbackResult.builder().success(true).build());

        ResponseEntity<String> response = controller.handleCallback(
                "yunzhanghu",
                Map.of("timestamp", "2026-06-02T03:00:00"),
                "{\"data\":\"encrypted-payload\",\"sign\":\"raw-signature\"}"
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo("SUCCESS");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(settlementService).handleCallback(eq("yunzhanghu"), captor.capture());
        assertThat(captor.getValue()).containsEntry("timestamp", "2026-06-02T03:00:00");
        assertThat(captor.getValue()).containsEntry("data", "encrypted-payload");
        assertThat(captor.getValue()).containsEntry("sign", "raw-signature");
    }

    @Test
    void handleCallback_shouldNotExposeExceptionMessage() {
        when(settlementService.handleCallback(eq("alipay"), org.mockito.ArgumentMatchers.anyMap()))
                .thenThrow(new RuntimeException("database down access_token=secret"));

        ResponseEntity<String> response = controller.handleCallback("alipay", Map.of("trade_no", "TRADE_001"), "");

        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
        assertThat(response.getBody()).isEqualTo("ERROR");
        assertThat(response.getBody()).doesNotContain("database", "access_token", "secret");
    }
}
