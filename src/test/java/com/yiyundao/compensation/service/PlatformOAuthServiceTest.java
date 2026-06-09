package com.yiyundao.compensation.service;

import com.yiyundao.compensation.interfaces.dto.config.FeishuConfigDto;
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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformOAuthService redirectUri 白名单校验测试")
class PlatformOAuthServiceTest {

    @Mock
    private IntegrationConfigService integrationConfigService;
    @Mock
    private WebClient webClient;
    @Mock
    private AuthTokenService authTokenService;
    @Mock
    private SysConfigService sysConfigService;

    private MockEnvironment environment;
    private PlatformOAuthService service;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        TrustedRedirectUrlValidator trustedRedirectUrlValidator =
                new TrustedRedirectUrlValidator(sysConfigService, environment);
        service = new PlatformOAuthService(
                integrationConfigService,
                webClient,
                authTokenService,
                sysConfigService,
                trustedRedirectUrlValidator
        );
    }

    @Test
    @DisplayName("应拒绝非可信 redirectUri，且不写入 OAuth state")
    void buildAuthorize_shouldRejectUntrustedRedirectUriBeforeStoringState() {
        environment.setProperty("oauth.trusted-redirect-hosts", "trusted.example.com");
        when(sysConfigService.getString("oauth.trusted.redirect.hosts", null)).thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.buildAuthorize("wechat", "https://evil.example.com/oauth/callback")
        );

        verify(authTokenService, never()).storeOAuthState(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("应拒绝非 http/https redirectUri")
    void buildAuthorize_shouldRejectNonHttpScheme() {
        environment.setProperty("oauth.trusted-redirect-hosts", "trusted.example.com");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.buildAuthorize("dingtalk", "javascript:alert(1)")
        );

        verify(authTokenService, never()).storeOAuthState(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("可信 redirectUri 应生成授权 URL 并保存 state")
    void buildAuthorize_shouldCreateWechatAuthorizeUrlForTrustedRedirectUri() {
        environment.setProperty("oauth.trusted-redirect-hosts", "trusted.example.com");
        when(sysConfigService.getString("oauth.trusted.redirect.hosts", null)).thenReturn(null);
        when(integrationConfigService.getWechatConfig()).thenReturn(validWechatConfig());
        when(sysConfigService.getInt("auth.oauth.state.ttl.seconds", 300)).thenReturn(120);

        PlatformOAuthService.Authorize out =
                service.buildAuthorize("wechat", "https://trusted.example.com/oauth/callback?from=web");

        assertNotNull(out);
        assertNotNull(out.getState());
        assertNotNull(out.getUrl());
        assertEquals(
                "https://trusted.example.com/oauth/callback?from=web",
                decodeQueryParam(out.getUrl(), "redirect_uri")
        );
        verify(authTokenService).storeOAuthState(eq("wechat"), eq(out.getState()), eq(120L));
    }

    @Test
    @DisplayName("企业微信内置环境应生成应用内 OAuth URL，state 仍按 wechat 存储")
    void buildAuthorize_shouldCreateWecomInAppAuthorizeUrlWhenChannelIsWecom() {
        environment.setProperty("oauth.trusted-redirect-hosts", "trusted.example.com");
        when(sysConfigService.getString("oauth.trusted.redirect.hosts", null)).thenReturn(null);
        when(integrationConfigService.getWechatConfig()).thenReturn(validWechatConfig());
        when(sysConfigService.getInt("auth.oauth.state.ttl.seconds", 300)).thenReturn(120);

        PlatformOAuthService.Authorize out =
                service.buildAuthorize("wechat", "https://trusted.example.com/oauth/callback/wechat", "wecom");

        assertNotNull(out);
        assertEquals("wecom", out.getChannel());
        assertEquals("https://open.weixin.qq.com/connect/oauth2/authorize", out.getUrl().split("\\?")[0]);
        assertEquals(
                "https://trusted.example.com/oauth/callback/wechat",
                decodeQueryParam(out.getUrl(), "redirect_uri")
        );
        verify(authTokenService).storeOAuthState(eq("wechat"), eq(out.getState()), eq(120L));
    }

    @Test
    @DisplayName("应优先使用 sys_config 白名单")
    void buildAuthorize_shouldPreferSysConfigHosts() {
        environment.setProperty("oauth.trusted-redirect-hosts", "trusted.example.com");
        when(sysConfigService.getString("oauth.trusted.redirect.hosts", null)).thenReturn("sys.example.com");
        when(integrationConfigService.getFeishuConfig()).thenReturn(validFeishuConfig());
        when(sysConfigService.getInt("auth.oauth.state.ttl.seconds", 300)).thenReturn(300);

        PlatformOAuthService.Authorize out =
                service.buildAuthorize("feishu", "https://sys.example.com/oauth/callback");

        assertNotNull(out);
        verify(authTokenService).storeOAuthState(eq("feishu"), eq(out.getState()), eq(300L));
    }

    @Test
    @DisplayName("缺失白名单配置时应拒绝授权入口")
    void buildAuthorize_shouldRejectWhenTrustedHostsMissing() {
        when(sysConfigService.getString("oauth.trusted.redirect.hosts", null)).thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.buildAuthorize("dingtalk", "https://trusted.example.com/oauth/callback")
        );

        verify(authTokenService, never()).storeOAuthState(anyString(), anyString(), anyLong());
    }

    private WechatConfigDto validWechatConfig() {
        WechatConfigDto dto = new WechatConfigDto();
        dto.setCorpId("wwa45e13d8804607c0");
        dto.setAgentId("1000003");
        dto.setCorpSecret("secret");
        return dto;
    }

    private FeishuConfigDto validFeishuConfig() {
        FeishuConfigDto dto = new FeishuConfigDto();
        dto.setAppId("cli_aabbcc");
        dto.setAppSecret("secret");
        return dto;
    }

    private String decodeQueryParam(String url, String name) {
        String marker = name + "=";
        int start = url.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        int end = url.indexOf("&", start);
        String value = end > start ? url.substring(start, end) : url.substring(start);
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
