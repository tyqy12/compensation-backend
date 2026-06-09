package com.yiyundao.compensation.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    @Test
    void shouldGenerateAndValidateAccessTokenWithStrongSecret() {
        JwtTokenProvider provider = new JwtTokenProvider(
                "strong-jwt-signing-secret-64-bytes-2026-random-value",
                3_600_000,
                86_400_000,
                new SecretKeyPolicy(new MockEnvironment())
        );

        String token = provider.generateToken("admin");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUsernameFromToken(token)).isEqualTo("admin");
    }

    @Test
    void shouldGenerateUniqueRefreshTokensForSameUser() {
        JwtTokenProvider provider = new JwtTokenProvider(
                "strong-jwt-signing-secret-64-bytes-2026-random-value",
                3_600_000,
                86_400_000,
                new SecretKeyPolicy(new MockEnvironment())
        );

        String first = provider.generateRefreshToken("admin");
        String second = provider.generateRefreshToken("admin");

        assertThat(second).isNotEqualTo(first);
        assertThat(provider.validateToken(first)).isTrue();
        assertThat(provider.validateToken(second)).isTrue();
        assertThat(provider.isRefreshToken(first)).isTrue();
        assertThat(provider.isRefreshToken(second)).isTrue();
    }

    @Test
    void shouldRejectShortJwtSecret() {
        assertThatThrownBy(() -> new JwtTokenProvider(
                "short",
                3_600_000,
                86_400_000,
                new SecretKeyPolicy(new MockEnvironment())
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret must be at least 32 bytes");
    }

    @Test
    void shouldRejectPlaceholderJwtSecretInProdLikeProfile() {
        assertThatThrownBy(() -> new JwtTokenProvider(
                "change-me-dev-jwt-secret-key-at-least-32-chars",
                3_600_000,
                86_400_000,
                new SecretKeyPolicy(new MockEnvironment().withProperty("spring.profiles.active", "staging"))
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production deployment cannot use placeholder secret");
    }
}
