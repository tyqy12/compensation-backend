package com.yiyundao.compensation.interfaces.controller.openapi;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.app.ExternalAppTokenResponse;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;
import com.yiyundao.compensation.modules.app.service.AppRegistryService;
import com.yiyundao.compensation.security.ExternalApiTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ExternalApiTokenService externalApiTokenService;

    @PostMapping("/token")
    public ApiResponse<ExternalAppTokenResponse> token(HttpServletRequest request,
                                                       @RequestHeader(value = "Authorization", required = false) String authorization,
                                                       @RequestParam("grant_type") String grantType,
                                                       @RequestParam(value = "scope", required = false) String scope) {

        if (!"client_credentials".equalsIgnoreCase(grantType)) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "grant_type 仅支持 client_credentials");
        }

        ClientCredentials credentials = resolveCredentials(authorization);
        if (credentials == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED, "缺少或无效的客户端凭证");
        }

        AppRegistry app = appRegistryService.findEnabledByClientId(credentials.clientId());
        if (app == null || !appRegistryService.matchesSecret(app, credentials.clientSecret())) {
            log.warn("应用鉴权失败: clientId={}", credentials.clientId());
            return ApiResponse.error(ErrorCode.UNAUTHORIZED, "客户端凭证无效");
        }

        String clientIp = resolveClientIp(request);
        if (!appRegistryService.isIpAllowed(app, clientIp)) {
            return ApiResponse.error(ErrorCode.FORBIDDEN, "IP 不在白名单内");
        }

        List<String> registeredScopes = appRegistryService.resolveScopes(app);
        if (registeredScopes.isEmpty()) {
            return ApiResponse.error(ErrorCode.FORBIDDEN, "应用未配置访问范围");
        }

        List<String> requestedScopes = resolveRequestedScopes(scope, registeredScopes);
        if (requestedScopes.isEmpty()) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "请求范围无效");
        }

        ExternalApiTokenService.TokenResult tokenResult = externalApiTokenService.generateToken(app, requestedScopes);

        ExternalAppTokenResponse response = ExternalAppTokenResponse.builder()
                .accessToken(tokenResult.accessToken())
                .tokenType("Bearer")
                .expiresIn(tokenResult.expiresInSeconds())
                .scope(tokenResult.scopeString())
                .build();

        return ApiResponse.success(response);
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

    private record ClientCredentials(String clientId, String clientSecret) {}
}
