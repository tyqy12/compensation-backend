package com.yiyundao.compensation.security;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class SecretKeyPolicy {

    private final Environment environment;

    public void validateSigningSecret(String propertyName, String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(propertyName + " must be configured");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(propertyName + " must be at least 32 bytes");
        }
        if (isProdLikeProfile() && looksLikePlaceholder(secret)) {
            throw new IllegalStateException("Production deployment cannot use placeholder secret: " + propertyName);
        }
    }

    private boolean isProdLikeProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("prod")
                        || profile.equals("production")
                        || profile.equals("staging"));
    }

    private boolean looksLikePlaceholder(String secret) {
        String normalized = secret.toLowerCase(Locale.ROOT);
        return normalized.contains("change-me")
                || normalized.contains("please-change")
                || normalized.contains("default")
                || normalized.contains("your_")
                || normalized.contains("your-")
                || normalized.contains("example")
                || normalized.contains("placeholder");
    }
}
