package com.yiyundao.compensation.common.config;

import com.yiyundao.compensation.security.ApiResourceAuthorizationFilter;
import com.yiyundao.compensation.security.ExternalApiAuthenticationFilter;
import com.yiyundao.compensation.security.JwtAuthenticationFilter;
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
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * 集中管理认证管线。具体资源和操作权限由数据库权限决策服务负责。
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
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
    public ApiResourceAuthorizationFilter apiResourceAuthorizationFilter(
            com.yiyundao.compensation.security.DatabasePermissionService permissionService) {
        return new ApiResourceAuthorizationFilter(permissionService, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<ApiResourceAuthorizationFilter> apiResourceAuthorizationFilterRegistration(
            ApiResourceAuthorizationFilter filter) {
        FilterRegistrationBean<ApiResourceAuthorizationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    // ==================== 唯一 FilterChain ====================

    /**
     * Spring Security 只负责认证和异常出口；是否公开、哪个应用可访问、用户具有什么操作，
     * 全部交给数据库权限过滤器。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ApiResourceAuthorizationFilter apiResourceAuthorizationFilter)
            throws Exception {
        http.securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint())
                        .accessDeniedHandler(restAccessDeniedHandler())
                )
                .addFilterBefore(corsFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(externalApiAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(apiResourceAuthorizationFilter, JwtAuthenticationFilter.class);
        return http.build();
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
