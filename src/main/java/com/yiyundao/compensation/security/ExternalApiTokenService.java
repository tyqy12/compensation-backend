package com.yiyundao.compensation.security;

import com.yiyundao.compensation.common.config.ExternalApiAuthProperties;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExternalApiTokenService {

    private final ExternalApiAuthProperties properties;
    private final SecretKeyPolicy secretKeyPolicy;

    private SecretKey secretKey;
    private long expirationSeconds;

    @PostConstruct
    void init() {
        String secret = properties.getJwt().getSecret();
        secretKeyPolicy.validateSigningSecret("external-api.jwt.secret", secret);
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = Math.max(properties.getJwt().getExpirationSeconds(), 60L);
    }

    public TokenResult generateToken(AppRegistry app, List<String> requestedScopes) {
        Assert.notNull(app, "app must not be null");

        List<String> scopes = normalizeScopes(requestedScopes);
        if (CollectionUtils.isEmpty(scopes)) {
            throw new IllegalArgumentException("未授权访问范围");
        }

        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds);

        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(app.getClientId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("appId", app.getId())
                .claim("appName", app.getAppName())
                .claim("scp", scopes)
                .signWith(secretKey)
                .compact();

        return new TokenResult(token, expiry, scopes);
    }

    public ParsedToken parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String clientId = claims.getSubject();
        Long appId = convertLong(claims.get("appId"));
        String appName = claims.get("appName", String.class);

        List<?> scopeRaw = claims.get("scp", List.class);
        List<String> scopes = new ArrayList<>();
        if (scopeRaw != null) {
            scopeRaw.forEach(item -> {
                if (item != null) {
                    scopes.add(item.toString());
                }
            });
        }

        String authorities = claims.get("authorities", String.class);
        Instant expiresAt = claims.getExpiration() != null ? claims.getExpiration().toInstant() : null;

        return new ParsedToken(clientId, appId, appName, scopes, authorities, expiresAt);
    }

    private List<String> normalizeScopes(List<String> scopes) {
        if (CollectionUtils.isEmpty(scopes)) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String scope : scopes) {
            if (scope != null) {
                String trimmed = scope.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    private Long convertLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    public record TokenResult(String accessToken, Instant expiresAt, List<String> scopes) {
        public long expiresInSeconds() {
            return expiresAt != null ? Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond()) : 0;
        }

        public String scopeString() {
            return String.join(" ", scopes);
        }
    }

    public record ParsedToken(String clientId,
                              Long appId,
                              String appName,
                              List<String> scopes,
                              String authorities,
                              Instant expiresAt) {

        public boolean isExpired() {
            return expiresAt != null && expiresAt.isBefore(Instant.now());
        }

        public List<org.springframework.security.core.authority.SimpleGrantedAuthority> toAuthorities() {
            List<org.springframework.security.core.authority.SimpleGrantedAuthority> grantedAuthorities = new ArrayList<>();
            if (scopes != null) {
                scopes.stream()
                        .filter(scope -> scope != null && !scope.isBlank())
                        .map(scope -> new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + scope.trim()))
                        .forEach(grantedAuthorities::add);
            }
            return List.copyOf(grantedAuthorities);
        }
    }
}
