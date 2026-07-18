package com.yiyundao.compensation.interfaces.controller.openapi;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.app.ExternalAppTokenResponse;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;
import com.yiyundao.compensation.modules.app.entity.AppDataGrant;
import com.yiyundao.compensation.modules.app.service.AppRateLimitService;
import com.yiyundao.compensation.modules.app.service.AppDataGrantService;
import com.yiyundao.compensation.modules.app.service.AppRegistryService;
import com.yiyundao.compensation.modules.app.service.impl.AppRateLimitServiceImpl;
import com.yiyundao.compensation.security.ClientIpResolver;
import com.yiyundao.compensation.security.ExternalApiTokenService;
import com.yiyundao.compensation.security.DatabasePermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalOAuthControllerTest {

    @Test
    void tokenShouldReturnBadRequestForUnsupportedGrantType() {
        AppRegistryService appRegistryService = mock(AppRegistryService.class);
        ExternalOAuthController controller = controller(appRegistryService, mock(ExternalApiTokenService.class));

        ResponseEntity<ApiResponse<ExternalAppTokenResponse>> response = controller.token(
                new MockHttpServletRequest(),
                basicAuth("client-a", "secret-a"),
                "password",
                null
        );

        assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorCode.PARAM_INVALID, "grant_type");
        assertTokenResponseCacheHeaders(response);
        verify(appRegistryService, never()).findEnabledByClientId("client-a");
    }

    @Test
    void tokenShouldReturnUnauthorizedForMissingClientCredentials() {
        ExternalOAuthController controller =
                controller(mock(AppRegistryService.class), mock(ExternalApiTokenService.class));

        ResponseEntity<ApiResponse<ExternalAppTokenResponse>> response = controller.token(
                new MockHttpServletRequest(),
                null,
                "client_credentials",
                null
        );

        assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "客户端凭证");
        assertTokenResponseCacheHeaders(response);
    }

    @Test
    void tokenShouldRateLimitMissingClientCredentials() {
        AppRateLimitService appRateLimitService = mock(AppRateLimitService.class);
        doThrow(new AppRateLimitServiceImpl.RateLimitExceededException("__anonymous__", "127.0.0.1"))
                .when(appRateLimitService).checkRate(eq("__anonymous__"), eq("127.0.0.1"));
        ExternalOAuthController controller =
                controller(mock(AppRegistryService.class), mock(ExternalApiTokenService.class), appRateLimitService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        ResponseEntity<ApiResponse<ExternalAppTokenResponse>> response = controller.token(
                request,
                null,
                "client_credentials",
                null
        );

        assertErrorResponse(response, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁");
        assertTokenResponseCacheHeaders(response);
    }

    @Test
    void tokenShouldReturnUnauthorizedForInvalidSecret() {
        AppRegistry app = app("client-a");
        AppRegistryService appRegistryService = mock(AppRegistryService.class);
        when(appRegistryService.findEnabledByClientId("client-a")).thenReturn(app);
        when(appRegistryService.matchesSecret(app, "bad-secret")).thenReturn(false);

        ExternalOAuthController controller = controller(appRegistryService, mock(ExternalApiTokenService.class));

        ResponseEntity<ApiResponse<ExternalAppTokenResponse>> response = controller.token(
                new MockHttpServletRequest(),
                basicAuth("client-a", "bad-secret"),
                "client_credentials",
                null
        );

        assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "客户端凭证无效");
        assertTokenResponseCacheHeaders(response);
    }

    @Test
    void tokenShouldRateLimitClientBeforeSecretCheck() {
        AppRegistryService appRegistryService = mock(AppRegistryService.class);
        AppRateLimitService appRateLimitService = mock(AppRateLimitService.class);
        doThrow(new AppRateLimitServiceImpl.RateLimitExceededException("client-a", "127.0.0.1"))
                .when(appRateLimitService).checkRate(eq("client-a"), eq("127.0.0.1"));
        ExternalOAuthController controller =
                controller(appRegistryService, mock(ExternalApiTokenService.class), appRateLimitService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        ResponseEntity<ApiResponse<ExternalAppTokenResponse>> response = controller.token(
                request,
                basicAuth("client-a", "secret-a"),
                "client_credentials",
                null
        );

        assertErrorResponse(response, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁");
        assertTokenResponseCacheHeaders(response);
        verify(appRegistryService, never()).findEnabledByClientId("client-a");
    }

    @Test
    void tokenShouldReturnForbiddenWhenClientIpIsNotAllowed() {
        AppRegistry app = app("client-a");
        AppRegistryService appRegistryService = mock(AppRegistryService.class);
        when(appRegistryService.findEnabledByClientId("client-a")).thenReturn(app);
        when(appRegistryService.matchesSecret(app, "secret-a")).thenReturn(true);
        when(appRegistryService.isIpAllowed(app, "203.0.113.8")).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.8");
        ExternalOAuthController controller = controller(appRegistryService, mock(ExternalApiTokenService.class));

        ResponseEntity<ApiResponse<ExternalAppTokenResponse>> response = controller.token(
                request,
                basicAuth("client-a", "secret-a"),
                "client_credentials",
                null
        );

        assertErrorResponse(response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "IP 不在白名单内");
        assertTokenResponseCacheHeaders(response);
    }

    @Test
    void tokenShouldReturnBadRequestWhenRequestedScopeIsNotRegistered() {
        AppRegistry app = app("client-a");
        AppRegistryService appRegistryService = mock(AppRegistryService.class);
        when(appRegistryService.findEnabledByClientId("client-a")).thenReturn(app);
        when(appRegistryService.matchesSecret(app, "secret-a")).thenReturn(true);
        when(appRegistryService.isIpAllowed(app, "127.0.0.1")).thenReturn(true);
        when(appRegistryService.resolveScopes(app)).thenReturn(List.of("payroll:read"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        ExternalOAuthController controller = controller(appRegistryService, mock(ExternalApiTokenService.class));

        ResponseEntity<ApiResponse<ExternalAppTokenResponse>> response = controller.token(
                request,
                basicAuth("client-a", "secret-a"),
                "client_credentials",
                "payroll.write"
        );

        assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorCode.PARAM_INVALID, "请求范围无效");
        assertTokenResponseCacheHeaders(response);
    }

    @Test
    void tokenShouldReturnAccessTokenWhenCredentialsAndScopeAreValid() {
        AppRegistry app = app("client-a");
        AppRegistryService appRegistryService = mock(AppRegistryService.class);
        ExternalApiTokenService externalApiTokenService = mock(ExternalApiTokenService.class);
        when(appRegistryService.findEnabledByClientId("client-a")).thenReturn(app);
        when(appRegistryService.matchesSecret(app, "secret-a")).thenReturn(true);
        when(appRegistryService.isIpAllowed(app, "127.0.0.1")).thenReturn(true);
        when(appRegistryService.resolveScopes(app)).thenReturn(List.of("payroll:read", "payslip:read"));
        when(externalApiTokenService.generateToken(app, List.of("payslip:read"))).thenReturn(
                new ExternalApiTokenService.TokenResult(
                        "token-value",
                        Instant.now().plusSeconds(3600),
                        List.of("payslip:read")
                )
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.2, 10.0.0.1");
        request.setRemoteAddr("127.0.0.1");
        ExternalOAuthController controller = controller(appRegistryService, externalApiTokenService);

        ResponseEntity<ApiResponse<ExternalAppTokenResponse>> response = controller.token(
                request,
                basicAuth("client-a", "secret-a"),
                "client_credentials",
                "payslip:read"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(response.getBody().getData().getAccessToken()).isEqualTo("token-value");
        assertThat(response.getBody().getData().getTokenType()).isEqualTo("Bearer");
        assertThat(response.getBody().getData().getScope()).isEqualTo("payslip:read");
        assertTokenResponseCacheHeaders(response);
    }

    @Test
    void tokenShouldRejectPayrollScopeWithoutObjectGrant() {
        AppRegistry app = app("client-a");
        AppRegistryService appRegistryService = mock(AppRegistryService.class);
        AppDataGrantService appDataGrantService = mock(AppDataGrantService.class);
        when(appRegistryService.findEnabledByClientId("client-a")).thenReturn(app);
        when(appRegistryService.matchesSecret(app, "secret-a")).thenReturn(true);
        when(appRegistryService.isIpAllowed(app, "127.0.0.1")).thenReturn(true);
        when(appRegistryService.resolveScopes(app)).thenReturn(List.of("payroll:read"));
        when(appDataGrantService.listActiveByAppId(app.getId())).thenReturn(List.of());
        DatabasePermissionService permissionService = mock(DatabasePermissionService.class);
        when(permissionService.requiresDataGrant(List.of("payroll:read"))).thenReturn(true);

        ExternalOAuthController controller = controller(
                appRegistryService,
                mock(ExternalApiTokenService.class),
                noOpRateLimitService(),
                appDataGrantService
        );
        ReflectionTestUtils.setField(controller, "databasePermissionService", permissionService);

        ResponseEntity<ApiResponse<ExternalAppTokenResponse>> response = controller.token(
                new MockHttpServletRequest(),
                basicAuth("client-a", "secret-a"),
                "client_credentials",
                null
        );

        assertErrorResponse(response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "数据范围");
    }

    @Test
    void tokenShouldUseForwardedForOnlyFromTrustedProxy() {
        AppRegistry app = app("client-a");
        AppRegistryService appRegistryService = mock(AppRegistryService.class);
        ExternalApiTokenService externalApiTokenService = mock(ExternalApiTokenService.class);
        when(appRegistryService.findEnabledByClientId("client-a")).thenReturn(app);
        when(appRegistryService.matchesSecret(app, "secret-a")).thenReturn(true);
        when(appRegistryService.isIpAllowed(app, "198.51.100.2")).thenReturn(true);
        when(appRegistryService.resolveScopes(app)).thenReturn(List.of("payroll:read"));
        when(externalApiTokenService.generateToken(app, List.of("payroll:read"))).thenReturn(
                new ExternalApiTokenService.TokenResult(
                        "token-value",
                        Instant.now().plusSeconds(3600),
                        List.of("payroll:read")
                )
        );

        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("security.trusted-proxies", "10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "198.51.100.2, 10.0.0.9");
        ExternalOAuthController controller =
                new ExternalOAuthController(
                        appRegistryService,
                        grantServiceWithTenantAccess(),
                        externalApiTokenService,
                        new ClientIpResolver(environment),
                        noOpRateLimitService(),
                        org.mockito.Mockito.mock(com.yiyundao.compensation.security.DatabasePermissionService.class)
                );

        ResponseEntity<ApiResponse<ExternalAppTokenResponse>> response = controller.token(
                request,
                basicAuth("client-a", "secret-a"),
                "client_credentials",
                null
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(appRegistryService).isIpAllowed(app, "198.51.100.2");
    }

    private AppRegistry app(String clientId) {
        AppRegistry app = new AppRegistry();
        app.setId(1L);
        app.setAppName("External App");
        app.setClientId(clientId);
        return app;
    }

    private String basicAuth(String clientId, String clientSecret) {
        String raw = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private ExternalOAuthController controller(AppRegistryService appRegistryService,
                                               ExternalApiTokenService externalApiTokenService) {
        return controller(appRegistryService, externalApiTokenService, noOpRateLimitService());
    }

    private ExternalOAuthController controller(AppRegistryService appRegistryService,
                                               ExternalApiTokenService externalApiTokenService,
                                               AppRateLimitService appRateLimitService) {
        return controller(appRegistryService, externalApiTokenService, appRateLimitService, grantServiceWithTenantAccess());
    }

    private ExternalOAuthController controller(AppRegistryService appRegistryService,
                                               ExternalApiTokenService externalApiTokenService,
                                               AppRateLimitService appRateLimitService,
                                               AppDataGrantService appDataGrantService) {
        return new ExternalOAuthController(
                appRegistryService,
                appDataGrantService,
                externalApiTokenService,
                new ClientIpResolver(new MockEnvironment()),
                appRateLimitService,
                org.mockito.Mockito.mock(com.yiyundao.compensation.security.DatabasePermissionService.class)
        );
    }

    private AppDataGrantService grantServiceWithTenantAccess() {
        AppDataGrantService service = mock(AppDataGrantService.class);
        AppDataGrant grant = new AppDataGrant();
        grant.setScopeType(AppDataGrantService.TENANT);
        grant.setScopeValue("default");
        when(service.listActiveByAppId(1L)).thenReturn(List.of(grant));
        return service;
    }

    private AppRateLimitService noOpRateLimitService() {
        return (clientId, clientIp) -> {
        };
    }

    private void assertErrorResponse(ResponseEntity<ApiResponse<ExternalAppTokenResponse>> response,
                                     HttpStatus httpStatus,
                                     ErrorCode errorCode,
                                     String messagePart) {
        assertThat(response.getStatusCode()).isEqualTo(httpStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(errorCode.getCode());
        assertThat(response.getBody().getMessage()).contains(messagePart);
    }

    private void assertTokenResponseCacheHeaders(ResponseEntity<ApiResponse<ExternalAppTokenResponse>> response) {
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst(HttpHeaders.PRAGMA)).isEqualTo("no-cache");
    }
}
