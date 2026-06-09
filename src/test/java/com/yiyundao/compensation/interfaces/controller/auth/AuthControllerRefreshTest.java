package com.yiyundao.compensation.interfaces.controller.auth;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.UserStatus;
import com.yiyundao.compensation.interfaces.dto.auth.LoginRequest;
import com.yiyundao.compensation.interfaces.dto.auth.LoginResponse;
import com.yiyundao.compensation.interfaces.dto.auth.LogoutRequest;
import com.yiyundao.compensation.interfaces.dto.auth.RefreshRequest;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.ClientIpResolver;
import com.yiyundao.compensation.security.JwtTokenProvider;
import com.yiyundao.compensation.service.AuthTokenService;
import com.yiyundao.compensation.service.LoginRateLimiterService;
import com.yiyundao.compensation.service.PlatformOAuthService;
import com.yiyundao.compensation.service.WeComAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerRefreshTest {

    @Mock
    private SysUserService sysUserService;

    @Mock
    private UserRoleService userRoleService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PlatformOAuthService platformOAuthService;

    @Mock
    private WeComAuthService weComAuthService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ExternalIdentityService externalIdentityService;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private LoginRateLimiterService loginRateLimiterService;

    private MockEnvironment environment;
    private ClientIpResolver clientIpResolver;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        clientIpResolver = new ClientIpResolver(environment);
        controller = new AuthController(
                sysUserService,
                userRoleService,
                passwordEncoder,
                jwtTokenProvider,
                platformOAuthService,
                weComAuthService,
                auditLogService,
                externalIdentityService,
                authTokenService,
                loginRateLimiterService,
                clientIpResolver
        );
    }

    @Test
    void loginRejectsNullBodyWithoutRateLimitOrUserLookup() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("127.0.0.1");

        ApiResponse<LoginResponse> response = controller.login(null, servletRequest);

        assertThat(response.getCode()).isEqualTo(ErrorCode.PARAM_MISSING.getCode());
        assertThat(response.getMessage()).isEqualTo("用户名或密码为空");
        verify(loginRateLimiterService, never()).isLocked(anyString(), anyString());
        verify(sysUserService, never()).getOne(any());
    }

    @Test
    void authorizeShouldPassWecomChannelToPlatformOAuthService() {
        PlatformOAuthService.Authorize authorize = new PlatformOAuthService.Authorize();
        authorize.setUrl("https://open.weixin.qq.com/connect/oauth2/authorize");
        authorize.setState("state-token");
        authorize.setChannel("wecom");
        when(platformOAuthService.buildAuthorize("wechat", "https://trusted.example.com/oauth/callback/wechat", "wecom"))
                .thenReturn(authorize);

        ApiResponse<com.yiyundao.compensation.interfaces.dto.auth.OAuthAuthorizeResponse> response =
                controller.authorize("wechat", "wecom", "https://trusted.example.com/oauth/callback/wechat");

        assertThat(response.getCode()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(response.getData().getUrl()).isEqualTo("https://open.weixin.qq.com/connect/oauth2/authorize");
        assertThat(response.getData().getState()).isEqualTo("state-token");
        assertThat(response.getData().getChannel()).isEqualTo("wecom");
        verify(platformOAuthService)
                .buildAuthorize("wechat", "https://trusted.example.com/oauth/callback/wechat", "wecom");
    }

    @Test
    void refreshConsumesOldTokenBeforeIssuingRotatedTokens() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("old-refresh");
        SysUser user = new SysUser();
        user.setId(10L);
        user.setUsername("alice");
        user.setStatus(UserStatus.ACTIVE);

        when(jwtTokenProvider.validateToken("old-refresh")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("old-refresh")).thenReturn(true);
        when(authTokenService.consumeRefreshToken("old-refresh")).thenReturn("alice");
        when(sysUserService.findByUsername("alice")).thenReturn(user);
        when(userRoleService.getUserRoleCodes(10L)).thenReturn(Collections.emptySet());
        when(jwtTokenProvider.generateToken("alice")).thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken("alice")).thenReturn("new-refresh");
        when(jwtTokenProvider.getExpiration("new-refresh")).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        when(authTokenService.storeRefreshToken(org.mockito.ArgumentMatchers.eq("alice"),
                org.mockito.ArgumentMatchers.eq("new-refresh"), anyLong())).thenReturn(true);

        ApiResponse<LoginResponse> response = controller.refresh(request);

        assertThat(response.getCode()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(response.getData().getToken()).isEqualTo("new-access");
        assertThat(response.getData().getRefreshToken()).isEqualTo("new-refresh");
        assertThat(response.getData().getUsername()).isEqualTo("alice");
        assertThat(response.getData().getRoles()).containsExactly("ROLE_USER");
        verify(authTokenService).consumeRefreshToken("old-refresh");
        verify(authTokenService, never()).getRefreshOwner(anyString());
        verify(authTokenService, never()).deleteRefreshToken("old-refresh");
        verify(authTokenService).storeRefreshToken(org.mockito.ArgumentMatchers.eq("alice"),
                org.mockito.ArgumentMatchers.eq("new-refresh"), anyLong());
    }

    @Test
    void refreshRejectsWhenRotatedRefreshTokenCannotBePersisted() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("old-refresh");
        SysUser user = new SysUser();
        user.setId(10L);
        user.setUsername("alice");
        user.setStatus(UserStatus.ACTIVE);

        when(jwtTokenProvider.validateToken("old-refresh")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("old-refresh")).thenReturn(true);
        when(authTokenService.consumeRefreshToken("old-refresh")).thenReturn("alice");
        when(sysUserService.findByUsername("alice")).thenReturn(user);
        when(userRoleService.getUserRoleCodes(10L)).thenReturn(Collections.emptySet());
        when(jwtTokenProvider.generateToken("alice")).thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken("alice")).thenReturn("new-refresh");
        when(jwtTokenProvider.getExpiration("new-refresh")).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        when(authTokenService.storeRefreshToken(org.mockito.ArgumentMatchers.eq("alice"),
                org.mockito.ArgumentMatchers.eq("new-refresh"), anyLong())).thenReturn(false);

        ApiResponse<LoginResponse> response = controller.refresh(request);

        assertThat(response.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo("刷新令牌签发失败，请重新登录");
        assertThat(response.getData()).isNull();
    }

    @Test
    void refreshRejectsAlreadyConsumedTokenWithoutIssuingNewTokens() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("old-refresh");

        when(jwtTokenProvider.validateToken("old-refresh")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("old-refresh")).thenReturn(true);
        when(authTokenService.consumeRefreshToken("old-refresh")).thenReturn(null);

        ApiResponse<LoginResponse> response = controller.refresh(request);

        assertThat(response.getCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID.getCode());
        assertThat(response.getMessage()).isEqualTo("refreshToken已失效");
        verify(jwtTokenProvider, never()).generateToken(anyString());
        verify(jwtTokenProvider, never()).generateRefreshToken(anyString());
        verify(authTokenService, never()).storeRefreshToken(anyString(), anyString(), anyLong());
    }

    @Test
    void refreshRejectsInactiveUserWithoutIssuingNewTokens() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("old-refresh");
        SysUser user = new SysUser();
        user.setId(10L);
        user.setUsername("alice");
        user.setStatus(UserStatus.INACTIVE);

        when(jwtTokenProvider.validateToken("old-refresh")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("old-refresh")).thenReturn(true);
        when(authTokenService.consumeRefreshToken("old-refresh")).thenReturn("alice");
        when(sysUserService.findByUsername("alice")).thenReturn(user);

        ApiResponse<LoginResponse> response = controller.refresh(request);

        assertThat(response.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
        assertThat(response.getMessage()).isEqualTo("账号已禁用，请联系管理员");
        verify(jwtTokenProvider, never()).generateToken(anyString());
        verify(jwtTokenProvider, never()).generateRefreshToken(anyString());
        verify(authTokenService, never()).storeRefreshToken(anyString(), anyString(), anyLong());
    }

    @Test
    void loginRejectsInactiveUserWithoutIssuingTokens() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("127.0.0.1");
        SysUser user = new SysUser();
        user.setId(10L);
        user.setUsername("alice");
        user.setPassword("encoded");
        user.setStatus(UserStatus.INACTIVE);

        when(loginRateLimiterService.isLocked("alice", "127.0.0.1")).thenReturn(false);
        when(sysUserService.getOne(org.mockito.ArgumentMatchers.any())).thenReturn(user);
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);

        ApiResponse<LoginResponse> response = controller.login(request, servletRequest);

        assertThat(response.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
        assertThat(response.getMessage()).isEqualTo("账号已禁用，请联系管理员");
        verify(loginRateLimiterService).onFail("alice", "127.0.0.1");
        verify(jwtTokenProvider, never()).generateToken(anyString());
        verify(jwtTokenProvider, never()).generateRefreshToken(anyString());
        verify(authTokenService, never()).storeRefreshToken(anyString(), anyString(), anyLong());
    }

    @Test
    void loginRateLimitShouldUseForwardedIpOnlyFromTrustedProxy() {
        environment.setProperty("security.trusted-proxies", "10.0.0.0/8");
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("10.0.0.8");
        servletRequest.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.8");

        when(loginRateLimiterService.isLocked("alice", "198.51.100.7")).thenReturn(false);
        when(sysUserService.getOne(org.mockito.ArgumentMatchers.any())).thenReturn(null);

        ApiResponse<LoginResponse> response = controller.login(request, servletRequest);

        assertThat(response.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
        verify(loginRateLimiterService).isLocked("alice", "198.51.100.7");
        verify(loginRateLimiterService).onFail("alice", "198.51.100.7");
    }

    @Test
    void loginRejectsWhenRefreshTokenCannotBePersisted() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("127.0.0.1");
        SysUser user = new SysUser();
        user.setId(10L);
        user.setUsername("alice");
        user.setPassword("encoded");
        user.setStatus(UserStatus.ACTIVE);

        when(loginRateLimiterService.isLocked("alice", "127.0.0.1")).thenReturn(false);
        when(sysUserService.getOne(org.mockito.ArgumentMatchers.any())).thenReturn(user);
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(userRoleService.getUserRoleCodes(10L)).thenReturn(Set.of("ADMIN"));
        when(jwtTokenProvider.generateToken("alice")).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken("alice")).thenReturn("refresh-token");
        when(jwtTokenProvider.getExpiration("refresh-token")).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        when(authTokenService.storeRefreshToken(org.mockito.ArgumentMatchers.eq("alice"),
                org.mockito.ArgumentMatchers.eq("refresh-token"), anyLong())).thenReturn(false);

        ApiResponse<LoginResponse> response = controller.login(request, servletRequest);

        assertThat(response.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo("登录失败，请稍后重试");
        assertThat(response.getData()).isNull();
        verify(loginRateLimiterService, never()).onSuccess(anyString(), anyString());
    }

    @Test
    void oauthCallbackRejectsInactiveBoundUserWithoutIssuingTokens() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        PlatformOAuthService.PlatformUser platformUser = new PlatformOAuthService.PlatformUser();
        platformUser.setSubjectId("wx-user");
        SysUser user = new SysUser();
        user.setId(10L);
        user.setUsername("alice");
        user.setStatus(UserStatus.INACTIVE);

        when(authTokenService.consumeOAuthState("wechat", "state-token")).thenReturn(true);
        when(platformOAuthService.exchangeCode("wechat", "code")).thenReturn(platformUser);
        when(externalIdentityService.findBoundUserId("wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-user")).thenReturn(10L);
        when(sysUserService.getById(10L)).thenReturn(user);

        ApiResponse<LoginResponse> response = controller.oauthCallback("wechat", "code", "state-token", servletRequest);

        assertThat(response.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
        assertThat(response.getMessage()).isEqualTo("账号已禁用，请联系管理员");
        verify(jwtTokenProvider, never()).generateToken(anyString());
        verify(jwtTokenProvider, never()).generateRefreshToken(anyString());
        verify(authTokenService, never()).storeRefreshToken(anyString(), anyString(), anyLong());
    }

    @Test
    void oauthCallbackRejectsWhenRefreshTokenCannotBePersisted() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        PlatformOAuthService.PlatformUser platformUser = new PlatformOAuthService.PlatformUser();
        platformUser.setSubjectId("wx-user");
        SysUser user = new SysUser();
        user.setId(10L);
        user.setUsername("alice");
        user.setStatus(UserStatus.ACTIVE);

        when(authTokenService.consumeOAuthState("wechat", "state-token")).thenReturn(true);
        when(platformOAuthService.exchangeCode("wechat", "code")).thenReturn(platformUser);
        when(externalIdentityService.findBoundUserId("wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-user")).thenReturn(10L);
        when(sysUserService.getById(10L)).thenReturn(user);
        when(userRoleService.getUserRoleCodes(10L)).thenReturn(Set.of("EMPLOYEE"));
        when(jwtTokenProvider.generateToken("alice")).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken("alice")).thenReturn("refresh-token");
        when(jwtTokenProvider.getExpiration("refresh-token")).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        when(authTokenService.storeRefreshToken(org.mockito.ArgumentMatchers.eq("alice"),
                org.mockito.ArgumentMatchers.eq("refresh-token"), anyLong())).thenReturn(false);

        ApiResponse<LoginResponse> response = controller.oauthCallback("wechat", "code", "state-token", servletRequest);

        assertThat(response.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo("第三方登录失败，请稍后重试");
        assertThat(response.getData()).isNull();
    }

    @Test
    void logoutReportsFailureWhenAccessTokenCannotBeBlacklisted() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("Authorization", "Bearer access-token");
        when(jwtTokenProvider.getExpiration("access-token"))
                .thenReturn(new java.util.Date(System.currentTimeMillis() + 60_000));
        when(authTokenService.blacklistToken(org.mockito.ArgumentMatchers.eq("access-token"), anyLong()))
                .thenReturn(false);

        ApiResponse<Void> response = controller.logout(null, servletRequest);

        assertThat(response.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo("登出未完全完成，请稍后重试");
    }

    @Test
    void logoutReportsFailureWhenRefreshTokenCannotBeDeleted() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("refresh-token");
        when(authTokenService.deleteRefreshToken("refresh-token")).thenReturn(false);

        ApiResponse<Void> response = controller.logout(request, servletRequest);

        assertThat(response.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo("登出未完全完成，请稍后重试");
    }
}
