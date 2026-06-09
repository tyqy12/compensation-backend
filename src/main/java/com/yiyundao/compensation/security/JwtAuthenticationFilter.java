package com.yiyundao.compensation.security;

import com.yiyundao.compensation.enums.UserStatus;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器
 * <p>
 * 核心职责：从 Token 中提取用户身份，然后从数据库动态获取最新权限。
 * 设计原则：Token 只负责身份认证，权限信息每次请求都从 Redis 缓存/数据库动态获取。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final com.yiyundao.compensation.service.AuthTokenService authTokenService;
    private final UserRoleService userRoleService;
    private final SysUserService sysUserService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = getApplicationPath(request);
        return isPathOrChild(uri, "/openapi")
                || isPathOrChild(uri, "/v1/payroll")
                || isPathOrChild(uri, "/v1/payslips")
                || uri.equals("/v1/ping")
                || uri.equals("/v1/oauth/token")
                || uri.equals("/api/v1/oauth/token");
    }

    private boolean isPathOrChild(String uri, String path) {
        return uri.equals(path) || uri.startsWith(path + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String jwt = getJwtFromRequest(request);

        log.trace("JwtAuthenticationFilter: uri={}", request.getRequestURI());

        if (StringUtils.hasText(jwt)
                && isValid(jwt)
                && !tokenProvider.isRefreshToken(jwt)
                && !authTokenService.isBlacklisted(jwt)) {
            String username = tokenProvider.getUsernameFromToken(jwt);

            if (username != null) {
                SysUser user = sysUserService.findByUsername(username);
                if (user == null || !UserStatus.ACTIVE.equals(user.getStatus())) {
                    log.debug("JwtAuthenticationFilter: skip inactive or missing user={}", username);
                    filterChain.doFilter(request, response);
                    return;
                }
                // 从数据库动态获取最新角色权限（通过 UserRoleService，支持 Redis 缓存）
                Set<String> roleCodes = userRoleService.getUserRoleCodesByUsername(username);
                List<SimpleGrantedAuthority> grantedAuthorities;

                if (roleCodes != null && !roleCodes.isEmpty()) {
                    grantedAuthorities = roleCodes.stream()
                            .map(code -> {
                                String role = code.trim();
                                // 确保角色编码以 ROLE_ 前缀开头（Spring Security 规范）
                                if (!role.startsWith("ROLE_")) {
                                    role = "ROLE_" + role;
                                }
                                return new SimpleGrantedAuthority(role);
                            })
                            .collect(Collectors.toList());
                } else {
                    // 如果没有角色，授予默认 USER 角色
                    grantedAuthorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
                }

                log.debug("JwtAuthenticationFilter: user={}, authorities={} (from DB)", username, grantedAuthorities);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, grantedAuthorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isValid(String jwt) {
        try {
            return tokenProvider.validateToken(jwt);
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 从请求头提取 JWT Token
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private String getApplicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath + "/")) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
