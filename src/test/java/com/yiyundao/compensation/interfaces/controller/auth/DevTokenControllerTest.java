package com.yiyundao.compensation.interfaces.controller.auth;

import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.auth.DevTokenRequest;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevTokenControllerTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final SysUserService sysUserService = mock(SysUserService.class);

    @Test
    void shouldRequireExplicitUsername() {
        DevTokenController controller = newController("dev");

        var response = controller.generateDevToken(new DevTokenRequest());

        assertThat(response.getCode()).isEqualTo(ErrorCode.PARAM_MISSING.getCode());
        verify(jwtTokenProvider, never()).generateToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldRejectWhenDevProfileIsMixedWithProdLikeProfile() {
        DevTokenController controller = newController("dev", "prod");
        DevTokenRequest request = new DevTokenRequest();
        request.setUsername("alice");

        var response = controller.generateDevToken(request);

        assertThat(response.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
        verify(jwtTokenProvider, never()).generateToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldIssueTokenForExistingUserInDevOnlyProfile() {
        DevTokenController controller = newController("development");
        DevTokenRequest request = new DevTokenRequest();
        request.setUsername(" alice ");
        request.setRoles(List.of("ADMIN"));
        request.setAuthorities(List.of("payment:execute"));

        SysUser user = new SysUser();
        user.setUsername("alice");
        when(sysUserService.findByUsername("alice")).thenReturn(user);
        when(jwtTokenProvider.generateToken("alice")).thenReturn("dev-token");

        var response = controller.generateDevToken(request);

        assertThat(response.getCode()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(response.getData()).containsEntry("token", "dev-token");
        verify(jwtTokenProvider).generateToken("alice");
    }

    @Test
    void shouldReturnNotFoundForUnknownUser() {
        DevTokenController controller = newController("dev");
        DevTokenRequest request = new DevTokenRequest();
        request.setUsername("missing");
        when(sysUserService.findByUsername("missing")).thenReturn(null);

        var response = controller.generateDevToken(request);

        assertThat(response.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
        verify(jwtTokenProvider, never()).generateToken(org.mockito.ArgumentMatchers.anyString());
    }

    private DevTokenController newController(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new DevTokenController(jwtTokenProvider, sysUserService, environment);
    }
}
