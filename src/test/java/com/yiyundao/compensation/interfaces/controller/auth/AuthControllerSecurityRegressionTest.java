package com.yiyundao.compensation.interfaces.controller.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerSecurityRegressionTest {

    private static final Path AUTH_CONTROLLER = Path.of(
            "src/main/java/com/yiyundao/compensation/interfaces/controller/auth/AuthController.java"
    );

    @Test
    void authControllerShouldNotExposeDevelopmentPasswordResetBackdoor() throws Exception {
        String source = Files.readString(AUTH_CONTROLLER);

        assertThat(source).doesNotContain("/dev/reset-admin-password");
        assertThat(source).doesNotContain("dev_secret_2024");
        assertThat(source).doesNotContain("密码已重置为 admin123");
    }
}
