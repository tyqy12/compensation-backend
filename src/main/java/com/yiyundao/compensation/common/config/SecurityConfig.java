package com.yiyundao.compensation.common.config;

import com.yiyundao.compensation.security.ApiResourceAuthorizationFilter;
import com.yiyundao.compensation.security.ExternalApiAuthenticationFilter;
import com.yiyundao.compensation.security.JwtAuthenticationFilter;
import com.yiyundao.compensation.security.SecurityConstants;
import com.yiyundao.compensation.common.handler.GlobalResponseHandler;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Spring Security 配置类
 * <p>
 * 集中管理所有安全配置，使用 SecurityConstants 消除硬编码
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ExternalApiAuthenticationFilter externalApiAuthenticationFilter;
    private final com.yiyundao.compensation.infrastructure.dao.SysResourceMapper sysResourceMapper;
    private final com.yiyundao.compensation.infrastructure.dao.SysUserMapper sysUserMapper;
    private final com.yiyundao.compensation.modules.rbac.service.ResourceService resourceService;
    private final com.yiyundao.compensation.modules.rbac.service.UserRoleService userRoleService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // ==================== 公共路径配置 ====================

    /**
     * 获取所有公共路径模式
     */
    private String[] getPublicPatterns() {
        return new String[]{
                // OpenAPI 文档
                SecurityConstants.PATTERNS_OPENAPI_DOCS[0],
                SecurityConstants.PATTERNS_OPENAPI_DOCS[1],
                SecurityConstants.PATTERNS_OPENAPI_DOCS[2],
                SecurityConstants.PATTERNS_OPENAPI_DOCS[3],
                // 健康检查
                SecurityConstants.PATTERNS_HEALTH[0],
                SecurityConstants.PATTERNS_HEALTH[1],
                // 认证相关
                SecurityConstants.PATTERNS_AUTH_PUBLIC[0],
                SecurityConstants.PATTERNS_AUTH_PUBLIC[1],
                SecurityConstants.PATTERNS_AUTH_PUBLIC[2],
                SecurityConstants.PATTERNS_AUTH_PUBLIC[3],
                SecurityConstants.PATTERNS_AUTH_PUBLIC[4],
                // 支付通知
                SecurityConstants.PATTERNS_PAYMENT_NOTIFY[0],
                SecurityConstants.PATTERNS_PAYMENT_NOTIFY[1]
        };
    }

    // ==================== CORS 过滤器 ====================

    /**
     * 全局 CORS 过滤器 - 在 Spring Security 之前处理 OPTIONS 预检请求
     */
    @Bean
    public Filter corsFilter() {
        return new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
                    throws IOException, ServletException {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;

                // 允许来自 localhost 的跨域请求
                String origin = httpRequest.getHeader("Origin");
                if (origin != null && (origin.startsWith("http://localhost") || origin.startsWith("http://127.0.0.1"))) {
                    httpResponse.setHeader("Access-Control-Allow-Origin", origin);
                    httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
                    httpResponse.setHeader("Access-Control-Allow-Headers", "*");
                    httpResponse.setHeader("Access-Control-Exposed-Headers", "X-Total-Count, X-Page-Num, Content-Disposition");
                    httpResponse.setHeader("Access-Control-Max-Age", "3600");
                }

                // OPTIONS 预检请求直接返回成功
                if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
                    httpResponse.setStatus(HttpServletResponse.SC_OK);
                    return;
                }

                filterChain.doFilter(request, response);
            }
        };
    }

    // ==================== API 资源授权过滤器 ====================

    @Bean
    public ApiResourceAuthorizationFilter apiResourceAuthorizationFilter() {
        return new ApiResourceAuthorizationFilter(
                sysResourceMapper, sysUserMapper, resourceService, userRoleService, objectMapper
        );
    }

    // ==================== FilterChain 配置 ====================

    /**
     * 公共资源 FilterChain - 无需认证即可访问
     */
    @Bean
    @Order(1)
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(getPublicPatterns())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(corsFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * OpenAPI FilterChain - 使用外部API认证
     */
    @Bean
    @Order(2)
    public SecurityFilterChain openApiFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/openapi/**", "/v1/oauth/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint())
                        .accessDeniedHandler(restAccessDeniedHandler())
                )
                .addFilterBefore(corsFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(externalApiAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * API FilterChain - 主业务接口
     */
    @Bean
    @Order(3)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/logout").authenticated()
                        // 使用常量消除硬编码
                        .requestMatchers(SecurityConstants.PATTERN_SYSTEM_INTEGRATION)
                                .hasRole(removeRolePrefix(SecurityConstants.ROLE_ADMIN))
                        .requestMatchers(SecurityConstants.PATTERN_ADMIN)
                                .hasRole(removeRolePrefix(SecurityConstants.ROLE_ADMIN))
                        .requestMatchers(SecurityConstants.PATTERN_MANAGER)
                                .hasAnyRole(
                                        removeRolePrefix(SecurityConstants.ROLE_ADMIN),
                                        removeRolePrefix(SecurityConstants.ROLE_MANAGER)
                                )
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint())
                        .accessDeniedHandler(restAccessDeniedHandler())
                )
                .addFilterBefore(corsFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(externalApiAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(apiResourceAuthorizationFilter(), JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 移除 ROLE_ 前缀（Spring Security hasRole 要求）
     */
    private String removeRolePrefix(String role) {
        if (role != null && role.startsWith("ROLE_")) {
            return role.substring(5);
        }
        return role;
    }

    @Bean
    public AuthenticationEntryPoint restAuthenticationEntryPoint() {
        return (request, response, authException) -> writeErrorResponse(
                request, response, ErrorCode.UNAUTHORIZED, "未登录或 Token 已过期"
        );
    }

    @Bean
    public AccessDeniedHandler restAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeErrorResponse(
                request, response, ErrorCode.FORBIDDEN, "无权限访问"
        );
    }

    private void writeErrorResponse(HttpServletRequest request,
                                    HttpServletResponse response,
                                    ErrorCode errorCode,
                                    String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        String traceId = GlobalResponseHandler.getCurrentTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get(GlobalResponseHandler.TRACE_ID_KEY);
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toLowerCase();
        }
        MDC.put(GlobalResponseHandler.TRACE_ID_KEY, traceId);

        ApiResponse<Void> payload = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(message != null ? message : errorCode.getMessage())
                .traceId(traceId)
                .timestamp(LocalDateTime.now())
                .build();

        response.setStatus(errorCode.getHttpStatusCode());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(payload));
    }
}
