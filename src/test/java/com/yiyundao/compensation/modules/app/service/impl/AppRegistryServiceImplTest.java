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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    void registerNormalizesSupportedScopeAliases() {
        when(appRegistryMapper.insert(any(AppRegistry.class))).thenReturn(1);

        AppRegistryService.AppRegistryCreateCommand cmd = new AppRegistryService.AppRegistryCreateCommand(
                "Alias App",
                List.of("payroll.read", "payslip.read", "payroll:read"),
                null,
                null,
                "enabled"
        );

        AppRegistry saved = service.register(cmd).appRegistry();

        assertThat(saved.getScopes()).isEqualTo("payroll:read,payslip:read");
        assertThat(service.resolveScopes(saved)).containsExactly("payroll:read", "payslip:read");
    }

    @Test
    void registerRejectsUnsupportedScopes() {
        AppRegistryService.AppRegistryCreateCommand cmd = new AppRegistryService.AppRegistryCreateCommand(
                "Bad Scope App",
                List.of("payroll:write"),
                null,
                null,
                "enabled"
        );

        assertThatThrownBy(() -> service.register(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的 scope");
    }

    @Test
    void registerRejectsUnsupportedStatus() {
        AppRegistryService.AppRegistryCreateCommand cmd = new AppRegistryService.AppRegistryCreateCommand(
                "Bad Status App",
                List.of("payroll:read"),
                null,
                null,
                "archived"
        );

        assertThatThrownBy(() -> service.register(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("应用状态仅支持");
    }

    @Test
    void updateRejectsUnsupportedStatus() {
        AppRegistry app = new AppRegistry();
        app.setId(10L);
        app.setStatus("enabled");
        when(appRegistryMapper.selectById(10L)).thenReturn(app);

        AppRegistryService.AppRegistryUpdateCommand cmd = new AppRegistryService.AppRegistryUpdateCommand(
                null,
                null,
                null,
                null,
                "archived"
        );

        assertThatThrownBy(() -> service.updateApp(10L, cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("应用状态仅支持");
    }

    @Test
    void isIpAllowedShouldFailClosedWhenPersistedWhitelistIsInvalidJson() {
        AppRegistry app = new AppRegistry();
        app.setIpWhitelist("not-json");

        assertThat(service.isIpAllowed(app, "127.0.0.1")).isFalse();
    }

    @Test
    void resolveScopesIgnoresUnsupportedPersistedScopes() {
        AppRegistry app = new AppRegistry();
        app.setScopes("payroll.read,unknown:scope,payslip:read");

        assertThat(service.resolveScopes(app)).containsExactly("payroll:read", "payslip:read");
    }
}
