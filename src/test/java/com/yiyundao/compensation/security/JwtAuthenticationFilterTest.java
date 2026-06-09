package com.yiyundao.compensation.security;

import com.yiyundao.compensation.enums.UserStatus;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.service.AuthTokenService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
    private final AuthTokenService authTokenService = mock(AuthTokenService.class);
    private final UserRoleService userRoleService = mock(UserRoleService.class);
    private final SysUserService sysUserService = mock(SysUserService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            tokenProvider,
            authTokenService,
            userRoleService,
            sysUserService
    );

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSkipExternalApiBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payroll/batches");
        request.addHeader("Authorization", "Bearer external-api-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(tokenProvider, never()).validateToken(anyString());
        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldSkipExternalApiBearerTokenUnderServletContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payroll/batches");
        request.setContextPath("/api");
        request.addHeader("Authorization", "Bearer external-api-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(tokenProvider, never()).validateToken(anyString());
        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldAuthenticateInternalBusinessJwt() throws Exception {
        when(tokenProvider.validateToken("internal-token")).thenReturn(true);
        when(tokenProvider.isRefreshToken("internal-token")).thenReturn(false);
        when(authTokenService.isBlacklisted("internal-token")).thenReturn(false);
        when(tokenProvider.getUsernameFromToken("internal-token")).thenReturn("admin");
        SysUser user = new SysUser();
        user.setUsername("admin");
        user.setStatus(UserStatus.ACTIVE);
        when(sysUserService.findByUsername("admin")).thenReturn(user);
        when(userRoleService.getUserRoleCodesByUsername("admin")).thenReturn(Set.of("ADMIN"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users/search");
        request.addHeader("Authorization", "Bearer internal-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN");
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldAuthenticateInternalJwtForPathWithOnlyPayslipsPrefix() throws Exception {
        when(tokenProvider.validateToken("internal-token")).thenReturn(true);
        when(tokenProvider.isRefreshToken("internal-token")).thenReturn(false);
        when(authTokenService.isBlacklisted("internal-token")).thenReturn(false);
        when(tokenProvider.getUsernameFromToken("internal-token")).thenReturn("admin");
        SysUser user = new SysUser();
        user.setUsername("admin");
        user.setStatus(UserStatus.ACTIVE);
        when(sysUserService.findByUsername("admin")).thenReturn(user);
        when(userRoleService.getUserRoleCodesByUsername("admin")).thenReturn(Set.of("ADMIN"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payslips-extra");
        request.addHeader("Authorization", "Bearer internal-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticateRefreshTokenAsBearerAccessToken() throws Exception {
        when(tokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(tokenProvider.isRefreshToken("refresh-token")).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users/search");
        request.addHeader("Authorization", "Bearer refresh-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(authTokenService, never()).isBlacklisted(anyString());
        verify(tokenProvider, never()).getUsernameFromToken(anyString());
        verify(sysUserService, never()).findByUsername(anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticateInactiveUserEvenWithValidJwt() throws Exception {
        when(tokenProvider.validateToken("internal-token")).thenReturn(true);
        when(tokenProvider.isRefreshToken("internal-token")).thenReturn(false);
        when(authTokenService.isBlacklisted("internal-token")).thenReturn(false);
        when(tokenProvider.getUsernameFromToken("internal-token")).thenReturn("disabled-user");
        SysUser user = new SysUser();
        user.setUsername("disabled-user");
        user.setStatus(UserStatus.INACTIVE);
        when(sysUserService.findByUsername("disabled-user")).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users/search");
        request.addHeader("Authorization", "Bearer internal-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userRoleService, never()).getUserRoleCodesByUsername(anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticateMissingUserEvenWithValidJwt() throws Exception {
        when(tokenProvider.validateToken("internal-token")).thenReturn(true);
        when(tokenProvider.isRefreshToken("internal-token")).thenReturn(false);
        when(authTokenService.isBlacklisted("internal-token")).thenReturn(false);
        when(tokenProvider.getUsernameFromToken("internal-token")).thenReturn("deleted-user");
        when(sysUserService.findByUsername("deleted-user")).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users/search");
        request.addHeader("Authorization", "Bearer internal-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userRoleService, never()).getUserRoleCodesByUsername(anyString());
        verify(chain).doFilter(request, response);
    }
}
