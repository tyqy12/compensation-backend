package com.yiyundao.compensation.service;

import com.yiyundao.compensation.modules.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TrustedRedirectUrlValidator {

    private static final String TRUSTED_REDIRECT_HOSTS_CONFIG_KEY = "oauth.trusted.redirect.hosts";
    private static final String TRUSTED_REDIRECT_HOSTS_PROPERTY = "oauth.trusted-redirect-hosts";
    private static final String TRUSTED_REDIRECT_HOSTS_ENV = "OAUTH_TRUSTED_REDIRECT_HOSTS";

    private final SysConfigService sysConfigService;
    private final Environment env;

    public void validateTrustedHttpUrl(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " required");
        }
        URI uri = URI.create(value);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        if (!StringUtils.hasText(scheme) || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException(fieldName + " scheme not allowed");
        }
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException(fieldName + " invalid");
        }
        Set<String> trustedHosts = resolveTrustedRedirectHosts();
        if (trustedHosts.isEmpty()) {
            throw new IllegalStateException("trusted hosts not configured");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        String hostWithPort = port > 0 ? normalizedHost + ":" + port : normalizedHost;
        if (!(trustedHosts.contains(hostWithPort) || trustedHosts.contains(normalizedHost))) {
            throw new IllegalArgumentException(fieldName + " host not trusted");
        }
    }

    private Set<String> resolveTrustedRedirectHosts() {
        String allow = sysConfigService.getString(TRUSTED_REDIRECT_HOSTS_CONFIG_KEY, null);
        if (!StringUtils.hasText(allow)) {
            allow = env.getProperty(TRUSTED_REDIRECT_HOSTS_PROPERTY);
        }
        if (!StringUtils.hasText(allow)) {
            allow = env.getProperty(TRUSTED_REDIRECT_HOSTS_CONFIG_KEY);
        }
        if (!StringUtils.hasText(allow)) {
            allow = env.getProperty(TRUSTED_REDIRECT_HOSTS_ENV);
        }
        Set<String> trustedHosts = new LinkedHashSet<>();
        if (!StringUtils.hasText(allow)) {
            return trustedHosts;
        }
        for (String item : allow.split(",")) {
            String normalized = normalizeTrustedHost(item);
            if (StringUtils.hasText(normalized)) {
                trustedHosts.add(normalized);
            }
        }
        return trustedHosts;
    }

    private String normalizeTrustedHost(String item) {
        if (!StringUtils.hasText(item)) {
            return null;
        }
        String token = item.trim();
        if (!StringUtils.hasText(token)) {
            return null;
        }
        if (token.contains("://")) {
            URI uri = URI.create(token);
            if (!StringUtils.hasText(uri.getHost())) {
                return null;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return uri.getPort() > 0 ? host + ":" + uri.getPort() : host;
        }
        String normalized = token;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
