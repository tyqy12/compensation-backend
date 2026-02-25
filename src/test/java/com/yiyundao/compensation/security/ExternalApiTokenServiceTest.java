package com.yiyundao.compensation.security;

import com.yiyundao.compensation.common.config.ExternalApiAuthProperties;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalApiTokenServiceTest {

    private ExternalApiTokenService tokenService;

    @BeforeEach
    void setUp() {
        ExternalApiAuthProperties properties = new ExternalApiAuthProperties();
        properties.getJwt().setSecret("external-api-secret-key-must-be-32-bytes-123");
        properties.getJwt().setExpirationSeconds(600);
        tokenService = new ExternalApiTokenService(properties);
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
                .contains("ROLE_APP", "SCOPE_payroll:read", "SCOPE_payslip:read");
    }
}
