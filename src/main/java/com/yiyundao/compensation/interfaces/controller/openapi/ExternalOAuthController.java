package com.yiyundao.compensation.interfaces.controller.openapi;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.app.ExternalAppTokenResponse;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;
import com.yiyundao.compensation.modules.app.service.AppRateLimitService;
import com.yiyundao.compensation.modules.app.service.AppDataGrantService;
import com.yiyundao.compensation.modules.app.service.AppRegistryService;
import com.yiyundao.compensation.modules.app.service.impl.AppRateLimitServiceImpl;
import com.yiyundao.compensation.security.ClientIpResolver;
import com.yiyundao.compensation.security.DatabasePermissionService;
import com.yiyundao.compensation.security.ExternalApiTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/v1/oauth")
@RequiredArgsConstructor
public class ExternalOAuthController {

    private final AppRegistryService appRegistryService;
    private final AppDataGrantService appDataGrantService;
    private final ExternalApiTokenService externalApiTokenService;
    private final ClientIpResolver clientIpResolver;
    private final AppRateLimitService appRateLimitService;
    private final DatabasePermissionService databasePermissionService;

    @PostMapping("/token")
    public ResponseEntity<ApiResponse<ExternalAppTokenResponse>> token(
            HttpServletRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("grant_type") String grantType,
            @RequestParam(value = "scope", required = false) String scope) {

        if (!"client_credentials".equalsIgnoreCase(grantType)) {
            return error(ErrorCode.PARAM_INVALID, "grant_type 仅支持 client_credentials");
        }

        String clientIp = clientIpResolver.resolve(request);
        ClientCredentials credentials = resolveCredentials(authorization);
        if (credentials == null) {
            if (isRateLimited("__anonymous__", clientIp)) {
                return error(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
            }
            return error(ErrorCode.UNAUTHORIZED, "缺少或无效的客户端凭证");
        }

        if (isRateLimited(credentials.clientId(), clientIp)) {
            return error(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }

        AppRegistry app = appRegistryService.findEnabledByClientId(credentials.clientId());
        if (app == null || !appRegistryService.matchesSecret(app, credentials.clientSecret())) {
            log.warn("应用鉴权失败: clientId={}", credentials.clientId());
            return error(ErrorCode.UNAUTHORIZED, "客户端凭证无效");
        }

        if (!appRegistryService.isIpAllowed(app, clientIp)) {
            return error(ErrorCode.FORBIDDEN, "IP 不在白名单内");
        }

        List<String> registeredScopes = appRegistryService.resolveScopes(app);
        if (registeredScopes.isEmpty()) {
            return error(ErrorCode.FORBIDDEN, "应用未配置访问范围");
        }

        List<String> requestedScopes = resolveRequestedScopes(scope, registeredScopes);
        if (requestedScopes.isEmpty()) {
            return error(ErrorCode.PARAM_INVALID, "请求范围无效");
        }
        if (databasePermissionService.requiresDataGrant(requestedScopes)
                && appDataGrantService.listActiveByAppId(app.getId()).isEmpty()) {
            return error(ErrorCode.FORBIDDEN, "应用未配置薪酬数据范围，禁止签发薪酬访问令牌");
        }

        ExternalApiTokenService.TokenResult tokenResult = externalApiTokenService.generateToken(app, requestedScopes);

        ExternalAppTokenResponse response = ExternalAppTokenResponse.builder()
                .accessToken(tokenResult.accessToken())
                .tokenType("Bearer")
                .expiresIn(tokenResult.expiresInSeconds())
                .scope(tokenResult.scopeString())
                .build();

        return tokenResponse(ApiResponse.success(response), ErrorCode.SUCCESS);
    }

    private boolean isRateLimited(String clientId, String clientIp) {
        try {
            appRateLimitService.checkRate(clientId, clientIp);
            return false;
        } catch (AppRateLimitServiceImpl.RateLimitExceededException e) {
            return true;
        }
    }

    private ResponseEntity<ApiResponse<ExternalAppTokenResponse>> error(ErrorCode errorCode, String message) {
        return tokenResponse(ApiResponse.error(errorCode, message), errorCode);
    }

    private ResponseEntity<ApiResponse<ExternalAppTokenResponse>> tokenResponse(
            ApiResponse<ExternalAppTokenResponse> body,
            ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(body);
    }

    private ClientCredentials resolveCredentials(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Basic ")) {
            return null;
        }
        String token = authorization.substring(6);
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int idx = decoded.indexOf(':');
        if (idx <= 0) {
            return null;
        }
        String clientId = decoded.substring(0, idx).trim();
        String clientSecret = decoded.substring(idx + 1);
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            return null;
        }
        return new ClientCredentials(clientId, clientSecret);
    }

    private List<String> resolveRequestedScopes(String scope, List<String> registeredScopes) {
        if (!StringUtils.hasText(scope)) {
            return registeredScopes;
        }
        String[] parts = scope.split("\\s+");
        Set<String> requestedSet = new HashSet<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                requestedSet.add(part.trim());
            }
        }
        if (requestedSet.isEmpty()) {
            return List.of();
        }
        if (!registeredScopes.containsAll(requestedSet)) {
            return List.of();
        }
        return registeredScopes.stream()
                .filter(requestedSet::contains)
                .toList();
    }

    private record ClientCredentials(String clientId, String clientSecret) {}
}
