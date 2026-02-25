package com.yiyundao.compensation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;
import com.yiyundao.compensation.modules.app.service.AppRateLimitService;
import com.yiyundao.compensation.modules.app.service.AppRegistryService;
import com.yiyundao.compensation.modules.app.service.impl.AppRateLimitServiceImpl;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalApiAuthenticationFilter extends OncePerRequestFilter {

    private final AppRegistryService appRegistryService;
    private final AppRateLimitService appRateLimitService;
    private final AuditLogService auditLogService;
    private final ExternalApiContext externalApiContext;
    private final ObjectMapper objectMapper;
    private final ExternalApiTokenService externalApiTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (uri.startsWith("/api/v1/oauth/token")) {
            filterChain.doFilter(request, response);
            return;
        }

        long begin = System.currentTimeMillis();
        String clientIp = resolveClientIp(request);
        try {
            ExternalApiTokenService.ParsedToken parsedToken = authenticate(request, response);
            if (parsedToken == null) {
                return;
            }
            AppRegistry app = loadAndValidateApp(parsedToken, clientIp, response);
            if (app == null) {
                return;
            }
            appRateLimitService.checkRate(app.getClientId(), clientIp);

            List<String> scopes = parsedToken.scopes();
            ExternalApiContext.ExternalApiClient clientContext = ExternalApiContext.ExternalApiClient.builder()
                    .appId(app.getId())
                    .clientId(app.getClientId())
                    .appName(app.getAppName())
                    .clientIp(clientIp)
                    .scopes(scopes)
                    .build();
            externalApiContext.set(clientContext);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "APP:" + parsedToken.clientId(),
                    null,
                    parsedToken.toAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
            auditLogService.record(
                    "外部API调用",
                    request.getMethod(),
                    uri,
                    clientIp,
                    request.getHeader("User-Agent"),
                    "EXTERNAL_API",
                    parsedToken.clientId(),
                    parsedToken.clientId(),
                    "clientId=" + parsedToken.clientId(),
                    "OK",
                    null,
                    System.currentTimeMillis() - begin
            );
        } catch (AppRateLimitServiceImpl.RateLimitExceededException e) {
            writeJson(response, ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
            auditLogService.record(
                    "外部API调用",
                    request.getMethod(),
                    uri,
                    clientIp,
                    request.getHeader("User-Agent"),
                    "EXTERNAL_API",
                    null,
                    null,
                    null,
                    "FAILED",
                    "rate limit", System.currentTimeMillis() - begin
            );
        } catch (ExpiredJwtException e) {
            writeJson(response, ErrorCode.UNAUTHORIZED, "访问令牌已过期");
            auditLogService.record(
                    "外部API调用",
                    request.getMethod(),
                    uri,
                    clientIp,
                    request.getHeader("User-Agent"),
                    "EXTERNAL_API",
                    null,
                    null,
                    null,
                    "FAILED",
                    "token expired", System.currentTimeMillis() - begin
            );
        } catch (JwtException e) {
            log.warn("外部 API 令牌解析失败: {}", e.getMessage());
            writeJson(response, ErrorCode.TOKEN_INVALID, "访问令牌无效");
            auditLogService.record(
                    "外部API调用",
                    request.getMethod(),
                    uri,
                    clientIp,
                    request.getHeader("User-Agent"),
                    "EXTERNAL_API",
                    null,
                    null,
                    null,
                    "FAILED",
                    "token invalid", System.currentTimeMillis() - begin
            );
        } catch (Exception e) {
            log.warn("外部 API 鉴权失败: {}", e.getMessage());
            writeJson(response, ErrorCode.UNAUTHORIZED, e.getMessage());
            auditLogService.record(
                    "外部API调用",
                    request.getMethod(),
                    uri,
                    clientIp,
                    request.getHeader("User-Agent"),
                    "EXTERNAL_API",
                    null,
                    null,
                    null,
                    "FAILED",
                    e.getMessage(), System.currentTimeMillis() - begin
            );
        } finally {
            externalApiContext.clear();
        }
    }

    private ExternalApiTokenService.ParsedToken authenticate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            writeJson(response, ErrorCode.UNAUTHORIZED, "缺少 Bearer 访问令牌");
            return null;
        }
        String token = header.substring(7);
        if (!StringUtils.hasText(token)) {
            writeJson(response, ErrorCode.UNAUTHORIZED, "缺少 Bearer 访问令牌");
            return null;
        }
        ExternalApiTokenService.ParsedToken parsedToken = externalApiTokenService.parseToken(token);
        if (parsedToken.isExpired()) {
            throw new ExpiredJwtException(null, null, "访问令牌已过期");
        }
        return parsedToken;
    }

    private AppRegistry loadAndValidateApp(ExternalApiTokenService.ParsedToken parsedToken,
                                           String clientIp,
                                           HttpServletResponse response) throws IOException {
        if (!StringUtils.hasText(parsedToken.clientId())) {
            writeJson(response, ErrorCode.TOKEN_INVALID, "访问令牌无效");
            return null;
        }
        AppRegistry app;
        if (parsedToken.appId() != null) {
            app = appRegistryService.getById(parsedToken.appId());
        } else {
            app = appRegistryService.findEnabledByClientId(parsedToken.clientId());
        }
        if (app == null || !StringUtils.hasText(app.getStatus()) || !"enabled".equalsIgnoreCase(app.getStatus())) {
            writeJson(response, ErrorCode.UNAUTHORIZED, "应用未启用或不存在");
            return null;
        }
        if (!app.getClientId().equals(parsedToken.clientId())) {
            writeJson(response, ErrorCode.UNAUTHORIZED, "访问令牌不匹配");
            return null;
        }
        if (!appRegistryService.isIpAllowed(app, clientIp)) {
            writeJson(response, ErrorCode.FORBIDDEN, "IP 不在白名单内");
            return null;
        }
        return app;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private void writeJson(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.getHttpStatusCode());
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> body = ApiResponse.error(errorCode, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
