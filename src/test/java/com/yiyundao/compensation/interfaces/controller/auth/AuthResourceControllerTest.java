package com.yiyundao.compensation.interfaces.controller.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.modules.rbac.service.ResourceService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthResourceControllerTest {

    @Mock
    private SysUserService sysUserService;
    @Mock
    private UserRoleService userRoleService;
    @Mock
    private ResourceService resourceService;

    private AuthResourceController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthResourceController(sysUserService, userRoleService, resourceService, new ObjectMapper());
    }

    @Test
    void meShouldExposeEmployeeBindingState() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setEmployeeId(2001L);

        Authentication authentication = new UsernamePasswordAuthenticationToken("admin", "N/A");
        when(sysUserService.findByUsername("admin")).thenReturn(user);
        when(userRoleService.getUserRoleCodes(1L)).thenReturn(Set.of("ADMIN"));

        ApiResponse<Map<String, Object>> response = controller.me(authentication);

        assertEquals(0, response.getCode());
        assertNotNull(response.getData());
        assertEquals("1", response.getData().get("id"));
        assertEquals("admin", response.getData().get("username"));
        assertEquals(2001L, response.getData().get("employeeId"));
        assertEquals(Boolean.TRUE, response.getData().get("hasEmployeeProfile"));
    }
}
