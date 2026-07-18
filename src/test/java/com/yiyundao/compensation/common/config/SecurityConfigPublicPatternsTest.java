package com.yiyundao.compensation.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigPublicPatternsTest {

    @Test
    void springSecurityShouldNotContainASecondStaticPermissionPolicy() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/yiyundao/compensation/common/config/SecurityConfig.java"));

        assertThat(source).contains("anyRequest().permitAll()");
        assertThat(source).contains("apiResourceAuthorizationFilter");
        assertThat(source).doesNotContain("hasRole(", "hasAnyRole(", "getPublicPatterns", "securityMatcher(\"/openapi");
    }

    @Test
    void databaseDrivenFilterMustBePlacedAfterAuthentication() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/yiyundao/compensation/common/config/SecurityConfig.java"));

        assertThat(source).contains("addFilterBefore(jwtAuthenticationFilter")
                .contains("addFilterAfter(apiResourceAuthorizationFilter, JwtAuthenticationFilter.class)");
    }
}
