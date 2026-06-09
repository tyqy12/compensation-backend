package com.yiyundao.compensation.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigDecryptionServiceTest {

    @Test
    void shouldEncryptAndDecryptWithConfiguredKey() {
        ConfigDecryptionService service = new ConfigDecryptionService(
                "configured-aes-key-for-test",
                new MockEnvironment().withProperty("spring.profiles.active", "test")
        );
        service.init();

        String encrypted = service.encrypt("sensitive-config-value");

        assertThat(encrypted).isNotEqualTo("sensitive-config-value");
        assertThat(service.decrypt(encrypted)).isEqualTo("sensitive-config-value");
    }

    @Test
    void shouldRejectDefaultKeyInProdLikeProfile() {
        ConfigDecryptionService service = new ConfigDecryptionService(
                "default_aes_key_32_chars_long_here",
                new MockEnvironment().withProperty("spring.profiles.active", "prod")
        );

        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production deployment cannot use default encryption.aes.key");
    }

    @Test
    void shouldRejectWeakKeyInAnyProfile() {
        ConfigDecryptionService service = new ConfigDecryptionService(
                "short",
                new MockEnvironment().withProperty("spring.profiles.active", "test")
        );

        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be at least 16 characters");
    }
}
