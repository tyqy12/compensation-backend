package com.yiyundao.compensation.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.infrastructure.dao.SysResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserMapper;
import com.yiyundao.compensation.modules.rbac.service.ResourceService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.security.ExternalApiAuthenticationFilter;
import com.yiyundao.compensation.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigPublicPatternsTest {

    @Test
    void publicPatternsShouldExposeOpenApiDocsOutsideProdLikeProfiles() {
        Set<String> publicPatterns = publicPatternsFor("dev");

        assertThat(publicPatterns).contains("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**");
    }

    @Test
    void publicPatternsShouldExposeDevTokenOnlyOutsideProdLikeProfiles() {
        assertThat(publicPatternsFor("dev")).contains("/auth/dev-token");
        assertThat(publicPatternsFor("prod")).doesNotContain("/auth/dev-token");
        assertThat(publicPatternsFor("staging")).doesNotContain("/auth/dev-token");
    }

    @Test
    void publicPatternsShouldHideOpenApiDocsForProdLikeProfiles() {
        assertThat(publicPatternsFor("prod")).doesNotContain("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**");
        assertThat(publicPatternsFor("production")).doesNotContain("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**");
        assertThat(publicPatternsFor("staging")).doesNotContain("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**");
    }

    @Test
    void publicPatternsShouldSeparateExternalOauthTokenFromPaymentNotify() {
        assertThat(Set.of(com.yiyundao.compensation.security.SecurityConstants.PATTERNS_PAYMENT_NOTIFY))
                .contains("/alipay/notify", "/v1/settlement/callback/**")
                .doesNotContain("/v1/oauth/token");

        assertThat(publicPatternsFor("prod")).contains("/v1/oauth/token");
    }

    @Test
    void publicPatternsShouldExposeSystemHealthEndpoint() {
        assertThat(publicPatternsFor("prod")).contains("/system/health");
    }

    @Test
    void publicPatternsShouldNotExposeWeComSignatureEndpoints() {
        assertThat(publicPatternsFor("prod"))
                .contains("/auth/login", "/auth/refresh", "/auth/oauth/**")
                .doesNotContain("/auth/wecom/**");
        assertThat(publicPatternsFor("dev")).doesNotContain("/auth/wecom/**");
    }

    @Test
    void adminUserResourceUpdateMatcherShouldPrecedeGenericAdminMatcher() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/yiyundao/compensation/common/config/SecurityConfig.java"));

        int userResourceUpdateMatcher = source.indexOf(
                ".requestMatchers(org.springframework.http.HttpMethod.PUT, \"/admin/users/*/resources\")");
        int genericAdminMatcher = source.indexOf(".requestMatchers(SecurityConstants.PATTERN_ADMIN)");

        assertThat(userResourceUpdateMatcher).isGreaterThanOrEqualTo(0);
        assertThat(genericAdminMatcher).isGreaterThanOrEqualTo(0);
        assertThat(userResourceUpdateMatcher).isLessThan(genericAdminMatcher);
        assertThat(source.substring(userResourceUpdateMatcher, genericAdminMatcher))
                .contains("SecurityConstants.ROLE_ADMIN")
                .contains("SecurityConstants.ROLE_MANAGER");
    }

    private Set<String> publicPatternsFor(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        SecurityConfig securityConfig = new SecurityConfig(
                mock(JwtAuthenticationFilter.class),
                mock(ExternalApiAuthenticationFilter.class),
                mock(SysResourceMapper.class),
                mock(SysUserMapper.class),
                mock(ResourceService.class),
                mock(UserRoleService.class),
                new ObjectMapper(),
                environment
        );
        String[] patterns = ReflectionTestUtils.invokeMethod(securityConfig, "getPublicPatterns");
        assertThat(patterns).isNotNull();
        return Set.of(patterns);
    }
}
