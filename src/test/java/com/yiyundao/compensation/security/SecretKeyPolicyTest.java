package com.yiyundao.compensation.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretKeyPolicyTest {

    @Test
    void shouldRejectShortSigningSecretInAnyProfile() {
        SecretKeyPolicy policy = new SecretKeyPolicy(new MockEnvironment().withProperty("spring.profiles.active", "test"));

        assertThatThrownBy(() -> policy.validateSigningSecret("jwt.secret", "too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void shouldRejectPlaceholderSigningSecretInProdLikeProfile() {
        SecretKeyPolicy policy = new SecretKeyPolicy(new MockEnvironment().withProperty("spring.profiles.active", "prod"));

        assertThatThrownBy(() -> policy.validateSigningSecret(
                "external-api.jwt.secret",
                "change-me-external-api-secret-at-least-32-chars"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production deployment cannot use placeholder secret");
    }

    @Test
    void shouldAllowDevPlaceholderSigningSecretOutsideProdLikeProfile() {
        SecretKeyPolicy policy = new SecretKeyPolicy(new MockEnvironment().withProperty("spring.profiles.active", "dev"));

        assertThatNoException().isThrownBy(() -> policy.validateSigningSecret(
                "jwt.secret",
                "change-me-dev-jwt-secret-key-at-least-32-chars"
        ));
    }

    @Test
    void shouldAllowStrongSigningSecretInProdLikeProfile() {
        SecretKeyPolicy policy = new SecretKeyPolicy(new MockEnvironment().withProperty("spring.profiles.active", "staging"));

        assertThatNoException().isThrownBy(() -> policy.validateSigningSecret(
                "jwt.secret",
                "prod-jwt-signing-secret-64-bytes-2026-random-value"
        ));
    }
}
