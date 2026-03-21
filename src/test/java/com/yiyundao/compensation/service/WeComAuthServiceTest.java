package com.yiyundao.compensation.service;

import com.yiyundao.compensation.interfaces.dto.config.WechatConfigDto;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeComAuthService redirectUri 白名单校验测试")
class WeComAuthServiceTest {

    @Mock
    private IntegrationConfigService integrationConfigService;
    @Mock
    private SysConfigService sysConfigService;
    @Mock
    private AuthTokenService authTokenService;
    @Mock
    private PlatformTokenCacheService platformTokenCacheService;
    @Mock
    private WebClient webClient;

    private MockEnvironment environment;
    private WeComAuthService service;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        service = new WeComAuthService(
                integrationConfigService,
                sysConfigService,
                authTokenService,
                platformTokenCacheService,
                webClient,
                environment
        );
        when(integrationConfigService.getWechatConfig()).thenReturn(validConfig());
    }

    @Test
    @DisplayName("sys_config 未配置时，应兜底读取 application 配置")
    void buildAuthorize_shouldFallbackToApplicationPropertyHosts() {
        environment.setProperty("oauth.trusted-redirect-hosts", "localhost:5173,127.0.0.1:5173");
        when(sysConfigService.getString("oauth.trusted.redirect.hosts", null)).thenReturn(null);
        when(sysConfigService.getInt("auth.oauth.state.ttl.seconds", null)).thenReturn(null);

        WeComAuthService.AuthorizeOut out =
                service.buildAuthorize("web", "http://localhost:5173/oauth/callback/wecom");

        assertNotNull(out);
        assertNotNull(out.getUrl());
        verify(authTokenService).storeOAuthState(eq("wecom"), anyString(), eq(300L));
    }

    @Test
    @DisplayName("应优先使用 sys_config 白名单")
    void buildAuthorize_shouldPreferSysConfigHosts() {
        environment.setProperty("oauth.trusted-redirect-hosts", "localhost:5173");
        when(sysConfigService.getString("oauth.trusted.redirect.hosts", null)).thenReturn("trusted.example.com");
        when(sysConfigService.getInt("auth.oauth.state.ttl.seconds", null)).thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.buildAuthorize("web", "http://localhost:5173/oauth/callback/wecom")
        );
        assertDoesNotThrow(
                () -> service.buildAuthorize("web", "https://trusted.example.com/oauth/callback/wecom")
        );
    }

    @Test
    @DisplayName("非 http/https 协议应拒绝")
    void buildAuthorize_shouldRejectNonHttpScheme() {
        environment.setProperty("oauth.trusted-redirect-hosts", "localhost:5173");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.buildAuthorize("web", "ftp://localhost:5173/oauth/callback/wecom")
        );
    }

    private WechatConfigDto validConfig() {
        WechatConfigDto dto = new WechatConfigDto();
        dto.setCorpId("wwa45e13d8804607c0");
        dto.setAgentId("1000003");
        dto.setCorpSecret("secret");
        return dto;
    }
}
