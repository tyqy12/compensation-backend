package com.yiyundao.compensation.modules.payment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yiyundao.compensation.enums.SettlementProviderType;
import com.yiyundao.compensation.modules.payment.dto.SettlementProviderConfigDto;
import com.yiyundao.compensation.modules.payment.entity.SettlementProviderConfig;
import com.yiyundao.compensation.modules.payment.service.SettlementProviderConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettlementProviderConfigControllerTest {

    private final SettlementProviderConfigService configService = mock(SettlementProviderConfigService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private SettlementProviderConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new SettlementProviderConfigController(configService);
    }

    @Test
    void getConfigShouldNotExposeApiCredentials() throws JsonProcessingException {
        when(configService.getConfigById(10L)).thenReturn(configWithSecrets());

        String json = objectMapper.writeValueAsString(controller.getConfig(10L));

        assertThat(json)
                .contains("\"providerCode\":\"yunzhanghu\"")
                .doesNotContain("apiKey")
                .doesNotContain("apiSecret")
                .doesNotContain("raw-api-key")
                .doesNotContain("raw-api-secret");
    }

    @Test
    void getAllConfigsShouldNotExposeApiCredentials() throws JsonProcessingException {
        when(configService.getAllConfigs()).thenReturn(List.of(configWithSecrets()));

        String json = objectMapper.writeValueAsString(controller.getAllConfigs());

        assertThat(json)
                .contains("\"providerCode\":\"yunzhanghu\"")
                .doesNotContain("apiKey")
                .doesNotContain("apiSecret")
                .doesNotContain("raw-api-key")
                .doesNotContain("raw-api-secret");
    }

    @Test
    void createConfigShouldNotEchoSubmittedCredentials() throws JsonProcessingException {
        when(configService.createConfig(any())).thenReturn(configWithSecrets());
        SettlementProviderConfigDto request = new SettlementProviderConfigDto();
        request.setProviderCode("yunzhanghu");
        request.setProviderName("云账户");
        request.setProviderType(SettlementProviderType.YUNZHANGHU);
        request.setApiKey("raw-api-key");
        request.setApiSecret("raw-api-secret");

        String json = objectMapper.writeValueAsString(controller.createConfig(request));

        assertThat(json)
                .contains("\"providerCode\":\"yunzhanghu\"")
                .doesNotContain("apiKey")
                .doesNotContain("apiSecret")
                .doesNotContain("raw-api-key")
                .doesNotContain("raw-api-secret");
    }

    private SettlementProviderConfig configWithSecrets() {
        SettlementProviderConfig config = new SettlementProviderConfig();
        config.setId(10L);
        config.setProviderCode("yunzhanghu");
        config.setProviderName("云账户");
        config.setProviderType(SettlementProviderType.YUNZHANGHU);
        config.setApiEndpoint("https://api.example.test");
        config.setApiKey("raw-api-key");
        config.setApiSecret("raw-api-secret");
        config.setMerchantId("merchant-001");
        config.setNotifyUrl("https://callback.example.test/notify");
        config.setReturnUrl("https://callback.example.test/return");
        config.setPriority(10);
        config.setEnabled(true);
        config.setRemark("prod route");
        config.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        config.setUpdateTime(LocalDateTime.of(2026, 1, 2, 0, 0));
        return config;
    }
}
