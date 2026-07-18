package com.yiyundao.compensation.modules.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.infrastructure.dao.AppRegistryMapper;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;
import com.yiyundao.compensation.modules.app.service.AppRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppRegistryServiceImpl extends ServiceImpl<AppRegistryMapper, AppRegistry> implements AppRegistryService {

    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final JdbcTemplate jdbcTemplate;

    @Override
    public RegisteredClient register(AppRegistryCreateCommand cmd) {
        AppRegistry entity = new AppRegistry();
        entity.setAppName(cmd.appName());
        entity.setClientId(generateClientId());
        entity.setScopes(joinScopes(cmd.scopes()));
        entity.setIpWhitelist(writeIpWhitelist(cmd.ipWhitelist()));
        entity.setWebhookUrl(cmd.webhookUrl());
        entity.setStatus(resolveStatus(cmd.status()));

        String secretPlain = generateSecret();
        entity.setClientSecret(passwordEncoder.encode(secretPlain));

        boolean saved = false;
        int retries = 0;
        while (!saved && retries < 3) {
            try {
                save(entity);
                saved = true;
            } catch (DuplicateKeyException e) {
                retries++;
                entity.setClientId(generateClientId());
            }
        }
        if (!saved) {
            throw new DuplicateKeyException("无法生成唯一的 clientId，请稍后再试");
        }
        return new RegisteredClient(entity, secretPlain);
    }

    @Override
    public RegisteredClient rotateSecret(Long appId) {
        AppRegistry app = getById(appId);
        if (app == null) {
            throw new IllegalArgumentException("应用不存在");
        }
        String secretPlain = generateSecret();
        app.setClientSecret(passwordEncoder.encode(secretPlain));
        updateById(app);
        return new RegisteredClient(app, secretPlain);
    }

    @Override
    public AppRegistry updateApp(Long appId, AppRegistryUpdateCommand cmd) {
        AppRegistry app = getById(appId);
        if (app == null) {
            throw new IllegalArgumentException("应用不存在");
        }
        if (StringUtils.hasText(cmd.appName())) {
            app.setAppName(cmd.appName());
        }
        if (cmd.scopes() != null) {
            app.setScopes(joinScopes(cmd.scopes()));
        }
        if (cmd.ipWhitelist() != null) {
            app.setIpWhitelist(writeIpWhitelist(cmd.ipWhitelist()));
        }
        if (cmd.webhookUrl() != null) {
            app.setWebhookUrl(cmd.webhookUrl());
        }
        if (cmd.status() != null) {
            app.setStatus(resolveStatus(cmd.status()));
        }
        updateById(app);
        return app;
    }

    @Override
    public AppRegistry findEnabledByClientId(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<AppRegistry>()
                .eq(AppRegistry::getClientId, clientId)
                .eq(AppRegistry::getStatus, "enabled")
                .last("limit 1"));
    }

    @Override
    public boolean matchesSecret(AppRegistry app, String rawSecret) {
        if (app == null || !StringUtils.hasText(rawSecret)) {
            return false;
        }
        return passwordEncoder.matches(rawSecret, app.getClientSecret());
    }

    @Override
    public boolean isIpAllowed(AppRegistry app, String clientIp) {
        if (app == null) {
            return false;
        }
        List<String> whitelist = parseWhitelistStrict(app.getIpWhitelist());
        if (whitelist == null) {
            return false;
        }
        if (CollectionUtils.isEmpty(whitelist)) {
            return true;
        }
        if (!StringUtils.hasText(clientIp)) {
            return false;
        }
        return whitelist.contains(clientIp.trim());
    }

    @Override
    public List<String> resolveScopes(AppRegistry app) {
        if (app == null || !StringUtils.hasText(app.getScopes())) {
            return List.of();
        }
        String[] parts = app.getScopes().split(",");
        Set<String> unique = new HashSet<>();
        for (String p : parts) {
            if (StringUtils.hasText(p)) {
                String normalized = normalizeScope(p.trim());
                if (normalized != null && isRegisteredExternalScope(normalized)) {
                    unique.add(normalized);
                }
            }
        }
        return unique.stream().sorted().collect(Collectors.toList());
    }

    @Override
    public List<String> resolveIpWhitelist(AppRegistry app) {
        if (app == null) {
            return List.of();
        }
        return parseWhitelist(app.getIpWhitelist());
    }

    private String resolveStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "enabled";
        }
        String lower = status.trim().toLowerCase();
        if ("enabled".equals(lower) || "disabled".equals(lower)) {
            return lower;
        }
        throw new IllegalArgumentException("应用状态仅支持 enabled/disabled");
    }

    private String generateClientId() {
        byte[] buffer = new byte[24];
        secureRandom.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private String generateSecret() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    private String joinScopes(List<String> scopes) {
        if (CollectionUtils.isEmpty(scopes)) {
            throw new IllegalArgumentException("至少需要配置一个 scope");
        }
        Set<String> normalizedScopes = new HashSet<>();
        for (String scope : scopes) {
            String normalized = normalizeScope(scope);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (!isRegisteredExternalScope(normalized)) {
                throw new IllegalArgumentException("不支持的 scope: " + scope);
            }
            normalizedScopes.add(normalized);
        }
        if (normalizedScopes.isEmpty()) {
            throw new IllegalArgumentException("至少需要配置一个有效 scope");
        }
        return normalizedScopes.stream().sorted().collect(Collectors.joining(","));
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return null;
        }
        String normalized = scope.trim();
        if (normalized.indexOf(':') < 0) {
            int separator = normalized.lastIndexOf('.');
            if (separator > 0 && separator < normalized.length() - 1) {
                return normalized.substring(0, separator) + ":" + normalized.substring(separator + 1);
            }
        }
        return normalized;
    }

    /**
     * 外部应用可申请的 scope 来自权限操作目录，而不是代码中的业务白名单。
     * 只有绑定到 EXTERNAL API 资源且配置了 authority 的操作才可作为 OAuth scope。
     */
    private boolean isRegisteredExternalScope(String scope) {
        if (jdbcTemplate == null) {
            return false;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_permission_action a "
                            + "JOIN sys_resource_action ra ON ra.action_id=a.id "
                            + "JOIN sys_resource r ON r.id=ra.resource_id "
                            + "WHERE a.code=? AND a.status='enabled' AND a.deleted=0 "
                            + "AND a.authority IS NOT NULL AND a.authority<>'' "
                            + "AND ra.status='enabled' AND ra.deleted=0 "
                            + "AND r.type='API' AND r.access_mode='EXTERNAL' "
                            + "AND r.status='enabled' AND r.deleted=0",
                    Integer.class, scope);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("读取外部 scope 操作目录失败，拒绝 scope: {}", scope, e);
            return false;
        }
    }

    private String writeIpWhitelist(List<String> ipList) {
        if (CollectionUtils.isEmpty(ipList)) {
            return null;
        }
        try {
            List<String> normalized = ipList.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            if (normalized.isEmpty()) {
                return null;
            }
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new IllegalArgumentException("IP 白名单格式错误", e);
        }
    }

    private List<String> parseWhitelist(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return normalizeWhitelist(objectMapper.readValue(json, new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            log.warn("解析 IP 白名单失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> parseWhitelistStrict(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return normalizeWhitelist(objectMapper.readValue(json, new TypeReference<List<String>>() {}));
        } catch (JsonProcessingException e) {
            log.warn("解析 IP 白名单失败，拒绝外部应用访问: {}", e.getMessage());
            return null;
        }
    }

    private List<String> normalizeWhitelist(List<String> list) {
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
