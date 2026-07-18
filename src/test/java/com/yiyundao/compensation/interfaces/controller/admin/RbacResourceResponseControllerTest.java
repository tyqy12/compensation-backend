package com.yiyundao.compensation.interfaces.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.infrastructure.dao.SysRoleResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.interfaces.dto.admin.ResourceResponseDto;
import com.yiyundao.compensation.interfaces.dto.admin.UserResourceResponseDto;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserResource;
import com.yiyundao.compensation.modules.rbac.service.ResourceService;
import com.yiyundao.compensation.modules.rbac.service.UserResourceService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.DatabasePermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RbacResourceResponseControllerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void resourceTreeShouldReturnResponseWithoutPersistenceFields() throws Exception {
        ResourceService resourceService = mock(ResourceService.class);
        when(resourceService.getResourceTree("MENU")).thenReturn(List.of(resource()));
        AdminResourceV2Controller controller = new AdminResourceV2Controller(
                resourceService,
                objectMapper,
                mock(ApplicationEventPublisher.class),
                mock(SysRoleResourceMapper.class),
                mock(SysUserResourceMapper.class),
                mock(SysUserRoleMapper.class)
        );

        String json = objectMapper.writeValueAsString(controller.tree("MENU"));

        assertResourceResponseShape(json);
        assertThat(json)
                .contains("\"_children\":[2,3]")
                .contains("\"meta\":{\"hidden\":false,\"_children\":[2,3]}");
    }

    @Test
    void effectiveResourcesShouldReturnResponseWithoutPersistenceFields() throws Exception {
        ResourceService resourceService = mock(ResourceService.class);
        when(resourceService.getUserResources(10L)).thenReturn(List.of(resource()));
        AdminRoleController controller = new AdminRoleController(
                mock(SysUserRoleMapper.class),
                mock(com.yiyundao.compensation.infrastructure.dao.SysRoleMapper.class),
                resourceService,
                mock(UserRoleService.class),
                mock(SysUserService.class),
                mock(EmployeeService.class),
                mock(ExternalIdentityService.class),
                objectMapper
        );

        String json = objectMapper.writeValueAsString(controller.getUserEffectiveResources(10L));

        assertResourceResponseShape(json);
        assertThat(json).contains("\"code\":\"admin.dashboard\"");
    }

    @Test
    void userResourcesShouldReturnResponseWithoutPersistenceFields() throws Exception {
        SysUserResourceMapper userResourceMapper = mock(SysUserResourceMapper.class);
        DatabasePermissionService permissionService = mock(DatabasePermissionService.class);
        when(permissionService.getUserDirectActionCodes(10L))
                .thenReturn(java.util.Map.of(20L, java.util.Set.of("read", "write")));
        AdminUserAuthorizationController controller = new AdminUserAuthorizationController(
                userResourceMapper,
                mock(SysUserRoleMapper.class),
                mock(SysRoleResourceMapper.class),
                mock(ApprovalWorkflowMapper.class),
                mock(ApprovalEngine.class),
                mock(SysUserService.class),
                objectMapper,
                mock(UserRoleService.class),
                mock(UserResourceService.class),
                permissionService
        );

        ApiResponse<List<UserResourceResponseDto>> response = controller.userResources(10L);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json)
                .contains("\"userId\":10")
                .contains("\"resourceId\":20")
                .contains("\"actions\":[\"read\",\"write\"]")
                .contains("\"inheritFromRole\":false")
                .doesNotContain("deleted")
                .doesNotContain("version")
                .doesNotContain("createBy")
                .doesNotContain("updateBy");
    }

    private static SysResource resource() {
        SysResource resource = new SysResource();
        resource.setId(1L);
        resource.setType("MENU");
        resource.setCode("admin.dashboard");
        resource.setName("Dashboard");
        resource.setPath("/admin/dashboard");
        resource.setComponent("DashboardPage");
        resource.setIcon("dashboard");
        resource.setParentId(null);
        resource.setOrderNum(10);
        resource.setPropsJson("{\"hidden\":false,\"_children\":[2,3]}");
        resource.setStatus("enabled");
        resource.setCreateTime(LocalDateTime.of(2026, 6, 2, 12, 0));
        resource.setUpdateTime(LocalDateTime.of(2026, 6, 2, 12, 1));
        resource.setCreateBy("admin");
        resource.setUpdateBy("admin");
        resource.setDeleted(0);
        resource.setVersion(4);
        return resource;
    }

    private static SysUserResource userResource() {
        SysUserResource resource = new SysUserResource();
        resource.setId(7L);
        resource.setUserId(10L);
        resource.setResourceId(20L);
        resource.setActionsJson("[\"read\",\"write\"]");
        resource.setCreateTime(LocalDateTime.of(2026, 6, 2, 12, 0));
        resource.setUpdateTime(LocalDateTime.of(2026, 6, 2, 12, 1));
        resource.setCreateBy("admin");
        resource.setUpdateBy("admin");
        resource.setDeleted(0);
        resource.setVersion(2);
        return resource;
    }

    private static void assertResourceResponseShape(String json) {
        assertThat(json)
                .contains("\"id\":1")
                .contains("\"type\":\"MENU\"")
                .contains("\"orderNum\":10")
                .doesNotContain("propsJson")
                .doesNotContain("deleted")
                .doesNotContain("version")
                .doesNotContain("createBy")
                .doesNotContain("updateBy");
    }
}
