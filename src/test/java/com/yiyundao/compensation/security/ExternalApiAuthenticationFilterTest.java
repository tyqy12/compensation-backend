package com.yiyundao.compensation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yiyundao.compensation.common.config.ExternalApiAuthProperties;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;
import com.yiyundao.compensation.modules.app.service.AppRateLimitService;
import com.yiyundao.compensation.modules.app.service.AppRegistryService;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalApiAuthenticationFilterTest {

    private AppRegistryService appRegistryService;
    private AppRateLimitService appRateLimitService;
    private AuditLogService auditLogService;
    private ExternalApiContext externalApiContext;
    private ExternalApiTokenService tokenService;
    private MockEnvironment environment;
    private ExternalApiAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        appRegistryService = mock(AppRegistryService.class);
        appRateLimitService = mock(AppRateLimitService.class);
        auditLogService = mock(AuditLogService.class);
        externalApiContext = new ExternalApiContext();
        environment = new MockEnvironment();

        ExternalApiAuthProperties properties = new ExternalApiAuthProperties();
        properties.getJwt().setSecret("external-api-secret-key-must-be-32-bytes-123");
        properties.getJwt().setExpirationSeconds(600);
        tokenService = new ExternalApiTokenService(properties, new SecretKeyPolicy(new MockEnvironment()));
        tokenService.init();

        filter = new ExternalApiAuthenticationFilter(
                appRegistryService,
                appRateLimitService,
                auditLogService,
                externalApiContext,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                tokenService,
                new ClientIpResolver(environment)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        externalApiContext.clear();
    }

    @Test
    void shouldAuthenticateMountedV1OpenApiRequest() throws Exception {
        AppRegistry app = enabledApp();
        when(appRegistryService.getById(100L)).thenReturn(app);
        when(appRegistryService.isIpAllowed(app, "127.0.0.1")).thenReturn(true);
        when(appRegistryService.resolveScopes(app)).thenReturn(List.of("payroll:read"));

        String token = tokenService.generateToken(app, List.of("payroll:read")).accessToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payroll/batches");
        request.addHeader("Authorization", "Bearer " + token);
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .contains("ROLE_APP", "SCOPE_payroll:read");
    }

    @Test
    void shouldAuthenticateMountedV1OpenApiRequestUnderServletContextPath() throws Exception {
        AppRegistry app = enabledApp();
        when(appRegistryService.getById(100L)).thenReturn(app);
        when(appRegistryService.isIpAllowed(app, "127.0.0.1")).thenReturn(true);
        when(appRegistryService.resolveScopes(app)).thenReturn(List.of("payroll:read"));

        String token = tokenService.generateToken(app, List.of("payroll:read")).accessToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payroll/batches");
        request.setContextPath("/api");
        request.addHeader("Authorization", "Bearer " + token);
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .contains("ROLE_APP", "SCOPE_payroll:read");
    }

    @Test
    void shouldRequireBearerTokenForV1OpenApiRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payslips");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new RuntimeException("audit down")).when(auditLogService).record(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Long.class)
        );

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("缺少 Bearer 访问令牌");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void shouldPropagateDownstreamExceptionAfterAuthentication() throws Exception {
        AppRegistry app = enabledApp();
        when(appRegistryService.getById(100L)).thenReturn(app);
        when(appRegistryService.isIpAllowed(app, "127.0.0.1")).thenReturn(true);
        when(appRegistryService.resolveScopes(app)).thenReturn(List.of("payroll:read"));

        String token = tokenService.generateToken(app, List.of("payroll:read")).accessToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payroll/batches");
        request.addHeader("Authorization", "Bearer " + token);
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new ServletException("downstream failed")).when(chain).doFilter(any(), any());

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("downstream failed");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(externalApiContext.current()).isNull();
    }

    @Test
    void shouldNotFailSuccessfulRequestWhenAuditWriteFails() throws Exception {
        AppRegistry app = enabledApp();
        when(appRegistryService.getById(100L)).thenReturn(app);
        when(appRegistryService.isIpAllowed(app, "127.0.0.1")).thenReturn(true);
        when(appRegistryService.resolveScopes(app)).thenReturn(List.of("payroll:read"));
        doThrow(new RuntimeException("audit down")).when(auditLogService).record(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Long.class)
        );

        String token = tokenService.generateToken(app, List.of("payroll:read")).accessToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payroll/batches");
        request.addHeader("Authorization", "Bearer " + token);
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(externalApiContext.current()).isNull();
    }

    @Test
    void shouldRecordFailedAuditWhenDownstreamRejectsAuthenticatedRequest() throws Exception {
        AppRegistry app = enabledApp();
        when(appRegistryService.getById(100L)).thenReturn(app);
        when(appRegistryService.isIpAllowed(app, "127.0.0.1")).thenReturn(true);
        when(appRegistryService.resolveScopes(app)).thenReturn(List.of("payroll:read"));

        String token = tokenService.generateToken(app, List.of("payroll:read")).accessToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payroll/batches");
        request.addHeader("Authorization", "Bearer " + token);
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(403);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(auditLogService).record(
                eq("外部API调用"),
                eq("GET"),
                eq("/v1/payroll/batches"),
                eq("127.0.0.1"),
                any(),
                eq("EXTERNAL_API"),
                eq("client-abc"),
                eq("client-abc"),
                eq("clientId=client-abc"),
                eq("FAILED"),
                eq("http status 403"),
                any(Long.class)
        );
    }

    @Test
    void shouldSkipExternalOauthTokenEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/oauth/token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        verify(appRegistryService, never()).getById(any());
    }

    @Test
    void shouldIgnoreForwardedForFromUntrustedRemoteAddr() throws Exception {
        AppRegistry app = enabledApp();
        when(appRegistryService.getById(100L)).thenReturn(app);
        when(appRegistryService.isIpAllowed(app, "203.0.113.10")).thenReturn(false);

        String token = tokenService.generateToken(app, List.of("payroll:read")).accessToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payroll/batches");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Forwarded-For", "198.51.100.7");
        request.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(appRegistryService).isIpAllowed(app, "203.0.113.10");
        verify(appRegistryService, never()).isIpAllowed(app, "198.51.100.7");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void shouldUseForwardedForFromTrustedProxy() throws Exception {
        environment.setProperty("security.trusted-proxies", "10.0.0.0/8");
        AppRegistry app = enabledApp();
        when(appRegistryService.getById(100L)).thenReturn(app);
        when(appRegistryService.isIpAllowed(app, "198.51.100.7")).thenReturn(true);
        when(appRegistryService.resolveScopes(app)).thenReturn(List.of("payroll:read"));

        String token = tokenService.generateToken(app, List.of("payroll:read")).accessToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payroll/batches");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.9");
        request.setRemoteAddr("10.0.0.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(appRegistryService).isIpAllowed(app, "198.51.100.7");
        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldIgnoreInternalMountedV1FileRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/files/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        verify(appRegistryService, never()).getById(any());
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldIgnorePathWithOnlyPayslipsPrefix() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payslips-extra");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        verify(appRegistryService, never()).getById(any());
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldRejectTokenWhenAppScopesWereRevokedAfterIssue() throws Exception {
        AppRegistry app = enabledApp();
        when(appRegistryService.getById(100L)).thenReturn(app);
        when(appRegistryService.isIpAllowed(app, "127.0.0.1")).thenReturn(true);
        when(appRegistryService.resolveScopes(app)).thenReturn(List.of("payslip:read"));

        String token = tokenService.generateToken(app, List.of("payroll:read")).accessToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payroll/batches");
        request.addHeader("Authorization", "Bearer " + token);
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("访问令牌访问范围已失效");
        verify(chain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(externalApiContext.current()).isNull();
    }

    private AppRegistry enabledApp() {
        AppRegistry app = new AppRegistry();
        app.setId(100L);
        app.setClientId("client-abc");
        app.setAppName("Payroll Partner");
        app.setStatus("enabled");
        return app;
    }
}
