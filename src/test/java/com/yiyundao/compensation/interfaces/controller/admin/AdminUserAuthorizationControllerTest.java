package com.yiyundao.compensation.interfaces.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.infrastructure.dao.SysRoleResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.rbac.entity.SysUserResource;
import com.yiyundao.compensation.modules.rbac.service.UserResourceService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import com.yiyundao.compensation.security.DatabasePermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserAuthorizationControllerTest {

    @Mock
    private SysUserResourceMapper userResourceMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRoleResourceMapper roleResourceMapper;
    @Mock
    private ApprovalWorkflowMapper approvalWorkflowMapper;
    @Mock
    private ApprovalEngine approvalEngine;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private UserRoleService userRoleService;
    @Mock
    private UserResourceService userResourceService;
    @Mock
    private DatabasePermissionService databasePermissionService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userResourceEndpointsShouldKeepQueryAdminOnlyAndAllowManagerUpdateRequests() throws Exception {
        assertThat(AdminUserAuthorizationController.class.isAnnotationPresent(SecurityAnnotations.IsAdmin.class))
                .isFalse();

        assertThat(AdminUserAuthorizationController.class
                .getMethod("userResources", Long.class)
                .isAnnotationPresent(SecurityAnnotations.IsAdmin.class)).isTrue();
        assertThat(AdminUserAuthorizationController.class
                .getMethod("userAggregateResources", Long.class)
                .isAnnotationPresent(SecurityAnnotations.IsAdmin.class)).isTrue();
        assertThat(AdminUserAuthorizationController.class
                .getMethod("updateUserResources", Long.class, AdminUserAuthorizationController.UserGrantRequest.class)
                .isAnnotationPresent(SecurityAnnotations.IsManagerOrAdmin.class)).isTrue();
    }

    @Test
    void updateUserResourcesShouldRequireApprovalWhenActionsAreExpanded() {
        mockOperator("manager", "ROLE_MANAGER");
        when(databasePermissionService.getUserDirectActionCodes(10L))
                .thenReturn(Map.of(20L, Set.of("read")));
        when(approvalWorkflowMapper.selectCount(any())).thenReturn(0L);
        when(approvalEngine.startWorkflow(any(), any(), any(), anyLong(), any())).thenReturn(9001L);

        AdminUserAuthorizationController.UserGrantRequest request = new AdminUserAuthorizationController.UserGrantRequest();
        request.setResourceIds(List.of(20L));
        request.setActions(Map.of(20L, List.of("read", "write")));

        ApiResponse<Map<String, Object>> response = controller().updateUserResources(10L, request);

        assertThat(response.getCode()).as(response.getMessage()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(response.getMessage()).isEqualTo("已提交审批");
        assertThat(response.getData()).containsEntry("workflowId", 9001L);
        verify(approvalEngine).startWorkflow(
                any(),
                org.mockito.ArgumentMatchers.argThat((String key) -> key != null && key.matches("USER-10-\\d+")),
                org.mockito.ArgumentMatchers.eq("USER_RESOURCE_GRANT"),
                org.mockito.ArgumentMatchers.eq(100L),
                any()
        );
        verify(userResourceMapper, never()).delete(any());
        verify(userResourceMapper, never()).insert(org.mockito.ArgumentMatchers.any(SysUserResource.class));
    }

    @Test
    void updateUserResourcesShouldApplyDirectlyWhenActionsAreReduced() {
        mockOperator("manager", "ROLE_MANAGER");
        when(databasePermissionService.getUserDirectActionCodes(10L))
                .thenReturn(Map.of(20L, Set.of("read", "write")));

        AdminUserAuthorizationController.UserGrantRequest request = new AdminUserAuthorizationController.UserGrantRequest();
        request.setResourceIds(List.of(20L));
        request.setActions(Map.of(20L, List.of("read")));

        ApiResponse<Map<String, Object>> response = controller().updateUserResources(10L, request);

        assertThat(response.getCode()).as(response.getMessage()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(response.getMessage()).isEqualTo("权限已取消");
        assertThat(response.getData()).containsEntry("workflowId", null);
        verify(approvalEngine, never()).startWorkflow(any(), any(), any(), anyLong(), any());
        verify(userResourceService).assignResources(
                eq(10L),
                eq(List.of(20L)),
                eq(Map.of(20L, List.of("read"))),
                eq(100L)
        );
        verify(userResourceMapper, never()).delete(any());
        verify(userResourceMapper, never()).insert(org.mockito.ArgumentMatchers.any(SysUserResource.class));
    }

    @Test
    void updateUserResourcesShouldApplyDirectlyWhenWildcardActionsAreReduced() {
        mockOperator("manager", "ROLE_MANAGER");
        when(databasePermissionService.getUserDirectActionCodes(10L))
                .thenReturn(Map.of(20L, Set.of("*")));

        AdminUserAuthorizationController.UserGrantRequest request = new AdminUserAuthorizationController.UserGrantRequest();
        request.setResourceIds(List.of(20L));
        request.setActions(Map.of(20L, List.of("read")));

        ApiResponse<Map<String, Object>> response = controller().updateUserResources(10L, request);

        assertThat(response.getCode()).as(response.getMessage()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(response.getMessage()).isEqualTo("权限已取消");
        verify(approvalEngine, never()).startWorkflow(any(), any(), any(), anyLong(), any());
        verify(userResourceService).assignResources(
                eq(10L),
                eq(List.of(20L)),
                eq(Map.of(20L, List.of("read"))),
                eq(100L)
        );
    }

    @Test
    void updateUserResourcesShouldRejectWhenPendingGrantExists() {
        mockOperator("manager", "ROLE_MANAGER");
        when(databasePermissionService.getUserDirectActionCodes(10L))
                .thenReturn(Map.of(20L, Set.of("read")));
        when(approvalWorkflowMapper.selectCount(any())).thenReturn(1L);

        AdminUserAuthorizationController.UserGrantRequest request = new AdminUserAuthorizationController.UserGrantRequest();
        request.setResourceIds(List.of(20L, 21L));
        request.setActions(Map.of(20L, List.of("read"), 21L, List.of("read")));

        ApiResponse<Map<String, Object>> response = controller().updateUserResources(10L, request);

        assertThat(response.getCode()).isEqualTo(ErrorCode.REQUEST_CONFLICT.getCode());
        assertThat(response.getMessage()).contains("已有待审批的用户资源授权申请");
        verify(approvalEngine, never()).startWorkflow(any(), any(), any(), anyLong(), any());
    }

    @Test
    void updateUserResourcesShouldApplyDirectlyForAdminRoleFromUserRoleTableEvenWhenLegacyRolesEmpty() {
        mockOperator("admin", null);
        when(databasePermissionService.hasCurrentRequestScope(100L, "ALL")).thenReturn(true);

        AdminUserAuthorizationController.UserGrantRequest request = new AdminUserAuthorizationController.UserGrantRequest();
        request.setResourceIds(List.of(20L, 21L));
        request.setActions(Map.of(20L, List.of("read"), 21L, List.of("read", "write")));

        ApiResponse<Map<String, Object>> response = controller().updateUserResources(10L, request);

        assertThat(response.getCode()).as(response.getMessage()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(response.getMessage()).isEqualTo("已直接生效(管理员)");
        assertThat(response.getData()).containsEntry("workflowId", null);
        verify(approvalEngine, never()).startWorkflow(any(), any(), any(), anyLong(), any());
        verify(approvalWorkflowMapper, never()).selectCount(any());
        verify(userResourceService).assignResources(
                eq(10L),
                eq(List.of(20L, 21L)),
                eq(Map.of(20L, List.of("read"), 21L, List.of("read", "write"))),
                eq(100L)
        );
        verify(userResourceMapper, never()).delete(any());
        verify(userResourceMapper, never()).insert(org.mockito.ArgumentMatchers.any(SysUserResource.class));
    }

    private AdminUserAuthorizationController controller() {
        AdminUserAuthorizationController controller = new AdminUserAuthorizationController(
                userResourceMapper,
                userRoleMapper,
                roleResourceMapper,
                approvalWorkflowMapper,
                approvalEngine,
                sysUserService,
                new ObjectMapper(),
                userRoleService,
                userResourceService,
                databasePermissionService
        );
        return controller;
    }

    private void mockOperator(String username, String roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of())
        );
        SysUser operator = new SysUser();
        operator.setId(100L);
        operator.setUsername(username);
        operator.setRoles(roles);
        when(sysUserService.findByUsername(username)).thenReturn(operator);
    }

}
