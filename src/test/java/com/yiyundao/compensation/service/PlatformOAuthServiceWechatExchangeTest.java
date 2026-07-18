package com.yiyundao.compensation.service;

import com.yiyundao.compensation.interfaces.dto.config.WechatConfigDto;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformOAuthServiceWechatExchangeTest {

    @Test
    void shouldReadUserIdFromEnterpriseWechatResponse() {
        IntegrationConfigService integrationConfigService = mock(IntegrationConfigService.class);
        WechatConfigDto config = new WechatConfigDto();
        config.setCorpId("ww-test-corp");
        config.setCorpSecret("test-secret");
        when(integrationConfigService.getWechatConfig()).thenReturn(config);

        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    String responseBody = request.url().getPath().contains("/gettoken")
                            ? "{\"errcode\":0,\"errmsg\":\"ok\",\"access_token\":\"access-token\"}"
                            : "{\"errcode\":0,\"errmsg\":\"ok\",\"UserId\":\"wx-user-1001\"}";
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(responseBody)
                            .build());
                })
                .build();

        PlatformOAuthService service = new PlatformOAuthService(
                integrationConfigService,
                webClient,
                null,
                null,
                null
        );

        PlatformOAuthService.PlatformUser user = service.exchangeCode("wechat", "oauth-code");

        assertNotNull(user);
        assertEquals("wechat", user.getProvider());
        assertEquals("ww-test-corp", user.getTenantKey());
        assertEquals("user_id", user.getSubjectType());
        assertEquals("wx-user-1001", user.getSubjectId());
    }
}
