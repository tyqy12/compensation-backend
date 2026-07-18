package com.yiyundao.compensation.security;

import com.yiyundao.compensation.common.config.ExternalApiAuthProperties;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalApiTokenServiceTest {

    private static final String TEST_SECRET = "external-api-secret-key-must-be-32-bytes-123";

    private ExternalApiTokenService tokenService;

    @BeforeEach
    void setUp() {
        ExternalApiAuthProperties properties = new ExternalApiAuthProperties();
        properties.getJwt().setSecret(TEST_SECRET);
        properties.getJwt().setExpirationSeconds(600);
        tokenService = new ExternalApiTokenService(properties, new SecretKeyPolicy(new MockEnvironment()));
        tokenService.init();
    }

    @Test
    void generateAndParseToken() {
        AppRegistry app = new AppRegistry();
        app.setId(100L);
        app.setAppName("Payroll Partner");
        app.setClientId("client-abc");

        List<String> scopes = List.of("payroll:read", "payslip:read");

        ExternalApiTokenService.TokenResult result = tokenService.generateToken(app, scopes);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.scopeString()).isEqualTo("payroll:read payslip:read");
        assertThat(result.expiresAt()).isAfter(Instant.now());

        ExternalApiTokenService.ParsedToken parsed = tokenService.parseToken(result.accessToken());
        assertThat(parsed.clientId()).isEqualTo("client-abc");
        assertThat(parsed.appId()).isEqualTo(100L);
        assertThat(parsed.appName()).isEqualTo("Payroll Partner");
        assertThat(parsed.scopes()).containsExactlyInAnyOrder("payroll:read", "payslip:read");
        assertThat(parsed.toAuthorities())
                .extracting(SimpleGrantedAuthority::getAuthority)
                .contains("SCOPE_payroll:read", "SCOPE_payslip:read")
                .doesNotContain("ROLE_APP");
        assertThat(parsed.authorities()).isNull();
    }

    @Test
    void toAuthoritiesShouldIgnoreLegacyAuthoritiesClaim() {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject("client-abc")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .claim("appId", 100L)
                .claim("appName", "Payroll Partner")
                .claim("scp", List.of("payroll:read"))
                .claim("authorities", "ROLE_APP,SCOPE_payslip:read")
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        ExternalApiTokenService.ParsedToken parsed = tokenService.parseToken(token);

        assertThat(parsed.authorities()).contains("SCOPE_payslip:read");
        assertThat(parsed.toAuthorities())
                .extracting(SimpleGrantedAuthority::getAuthority)
                .contains("SCOPE_payroll:read")
                .doesNotContain("ROLE_APP")
                .doesNotContain("SCOPE_payslip:read");
    }

    @Test
    void shouldRejectPlaceholderSecretInProdLikeProfile() {
        ExternalApiAuthProperties properties = new ExternalApiAuthProperties();
        properties.getJwt().setSecret("change-me-external-api-secret-at-least-32-chars");
        ExternalApiTokenService service = new ExternalApiTokenService(
                properties,
                new SecretKeyPolicy(new MockEnvironment().withProperty("spring.profiles.active", "prod"))
        );

        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production deployment cannot use placeholder secret");
    }
}
