package com.yiyundao.compensation.modules.rbac.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.SysResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysRoleMapper;
import com.yiyundao.compensation.infrastructure.dao.SysRoleResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.rbac.entity.SysRole;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.DatabasePermissionAssignmentService;
import com.yiyundao.compensation.security.DatabasePermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysRoleResourceMapper roleResourceMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysResourceMapper resourceMapper;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private DatabasePermissionAssignmentService databasePermissionAssignmentService;
    @Mock
    private DatabasePermissionService databasePermissionService;

    @Test
    void getRoleResourcesShouldRejectMissingRoleBeforeReturningEmptyPermissions() {
        RoleServiceImpl service = service();
        doReturn(null).when(service).getById(99L);

        assertThatThrownBy(() -> service.getRoleResources(99L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                    assertThat(ex.getMessage()).contains("角色不存在");
                });

        verify(roleResourceMapper, never()).selectList(any());
    }

    @Test
    void getRoleResourcesShouldReturnEmptyMapWhenExistingRoleHasNoResources() {
        RoleServiceImpl service = service();
        SysRole role = new SysRole();
        role.setId(10L);
        doReturn(role).when(service).getById(10L);
        when(databasePermissionService.getRoleActionCodes(10L)).thenReturn(java.util.Map.of());

        assertThat(service.getRoleResources(10L)).isEmpty();

        verify(roleResourceMapper, never()).selectList(any());
    }

    private RoleServiceImpl service() {
        return spy(new RoleServiceImpl(
                roleMapper,
                roleResourceMapper,
                userRoleMapper,
                resourceMapper,
                sysUserService,
                auditLogService,
                new ObjectMapper(),
                databasePermissionAssignmentService,
                databasePermissionService
        ));
    }
}
