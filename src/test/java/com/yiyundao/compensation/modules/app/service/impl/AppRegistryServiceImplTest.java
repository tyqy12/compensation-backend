package com.yiyundao.compensation.modules.app.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.infrastructure.dao.AppRegistryMapper;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;
import com.yiyundao.compensation.modules.app.service.AppRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppRegistryServiceImplTest {

    @Mock
    private AppRegistryMapper appRegistryMapper;

    private PasswordEncoder passwordEncoder;
    private ObjectMapper objectMapper;

    private AppRegistryServiceImpl service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        objectMapper = new ObjectMapper();
        service = new AppRegistryServiceImpl(passwordEncoder, objectMapper);
        ReflectionTestUtils.setField(service, "baseMapper", appRegistryMapper);
    }

    @Test
    void registerGeneratesCredentialsAndPersists() {
        when(appRegistryMapper.insert(any(AppRegistry.class))).thenReturn(1);

        AppRegistryService.AppRegistryCreateCommand cmd = new AppRegistryService.AppRegistryCreateCommand(
                "Test App",
                List.of("payroll:read", "payslip:read"),
                List.of("127.0.0.1"),
                null,
                "enabled"
        );

        var result = service.register(cmd);

        assertThat(result.clientSecretPlain()).isNotBlank();
        AppRegistry saved = result.appRegistry();
        assertThat(saved.getClientId()).isNotBlank();
        assertThat(saved.getClientSecret()).isNotBlank();
        assertThat(passwordEncoder.matches(result.clientSecretPlain(), saved.getClientSecret())).isTrue();
        assertThat(saved.getScopes()).contains("payroll:read");
        assertThat(service.resolveScopes(saved)).containsExactlyInAnyOrder("payroll:read", "payslip:read");
        assertThat(service.resolveIpWhitelist(saved)).containsExactly("127.0.0.1");

        ArgumentCaptor<AppRegistry> captor = ArgumentCaptor.forClass(AppRegistry.class);
        verify(appRegistryMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getClientId()).isEqualTo(saved.getClientId());
    }

    @Test
    void matchesSecretValidatesHash() {
        AppRegistry app = new AppRegistry();
        app.setClientSecret(passwordEncoder.encode("secret"));

        assertThat(service.matchesSecret(app, "secret")).isTrue();
        assertThat(service.matchesSecret(app, "wrong")).isFalse();
    }
}

