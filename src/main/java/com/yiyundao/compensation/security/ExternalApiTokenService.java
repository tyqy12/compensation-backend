package com.yiyundao.compensation.security;

import com.yiyundao.compensation.common.config.ExternalApiAuthProperties;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.AuthorityUtils;
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

    private static final String ROLE_APP = "ROLE_APP";

    private final ExternalApiAuthProperties properties;

    private SecretKey secretKey;
    private long expirationSeconds;

    @PostConstruct
    void init() {
        String secret = properties.getJwt().getSecret();
        Assert.hasText(secret, "external-api.jwt.secret must be configured");
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

        String authorities = buildAuthorityString(scopes);

        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(app.getClientId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("appId", app.getId())
                .claim("appName", app.getAppName())
                .claim("scp", scopes)
                .claim("authorities", authorities)
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

    private String buildAuthorityString(List<String> scopes) {
        List<String> authorities = new ArrayList<>();
        authorities.add(ROLE_APP);
        for (String scope : scopes) {
            authorities.add("SCOPE_" + scope);
        }
        return String.join(",", authorities);
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
            if (authorities == null || authorities.isEmpty()) {
                return List.of();
            }
            return AuthorityUtils.commaSeparatedStringToAuthorityList(authorities)
                    .stream()
                    .map(auth -> new org.springframework.security.core.authority.SimpleGrantedAuthority(auth.getAuthority()))
                    .toList();
        }
    }
}
