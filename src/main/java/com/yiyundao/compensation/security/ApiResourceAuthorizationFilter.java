package com.yiyundao.compensation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 数据库权限决策的 HTTP 入口。
 *
 * <p>该过滤器只负责请求上下文适配、调用统一决策服务和输出拒绝响应。路径、角色、操作和
 * 管理员例外均不在过滤器中定义。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class ApiResourceAuthorizationFilter extends OncePerRequestFilter {

    private final DatabasePermissionService permissionService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        DatabasePermissionService.PermissionDecision decision;
        try {
            decision = permissionService.decide(request, authentication);
        } catch (Exception e) {
            log.error("数据库权限决策失败，拒绝继续访问: method={}, uri={}",
                    request.getMethod(), request.getRequestURI(), e);
            writeJson(response, ErrorCode.SERVICE_UNAVAILABLE, "权限服务暂不可用，请稍后重试");
            return;
        }
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("数据库权限拒绝: method={}, uri={}, resource={}, action={}, reason={}",
                request.getMethod(), request.getRequestURI(), decision.resourceCode(),
                decision.actionCode(), decision.message());
        writeJson(response,
                decision.errorCode() == null ? ErrorCode.FORBIDDEN : decision.errorCode(),
                decision.message() == null ? ErrorCode.FORBIDDEN.getMessage() : decision.message());
    }

    private void writeJson(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.getHttpStatusCode());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(errorCode, message)));
    }
}
