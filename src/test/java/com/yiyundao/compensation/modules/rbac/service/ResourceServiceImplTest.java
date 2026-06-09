package com.yiyundao.compensation.modules.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yiyundao.compensation.infrastructure.dao.SysResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysRoleResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.entity.SysRole;
import com.yiyundao.compensation.modules.rbac.entity.SysRoleResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserRole;
import com.yiyundao.compensation.modules.rbac.service.impl.ResourceServiceImpl;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceServiceImplTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        initTableInfo(configuration, SysUser.class);
        initTableInfo(configuration, SysUserRole.class);
        initTableInfo(configuration, SysRole.class);
        initTableInfo(configuration, SysRoleResource.class);
        initTableInfo(configuration, SysUserResource.class);
        initTableInfo(configuration, SysResource.class);
    }

    private static void initTableInfo(MybatisConfiguration configuration, Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, entityType.getName());
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    @Test
    void getUserResourcesShouldIgnoreResourcesFromDisabledRoles() {
        SysRoleResourceMapper roleResourceMapper = mock(SysRoleResourceMapper.class);
        SysUserResourceMapper userResourceMapper = mock(SysUserResourceMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysUserService sysUserService = mock(SysUserService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        ResourceCacheService resourceCacheService = new NoopResourceCacheService();
        TestableResourceServiceImpl service = new TestableResourceServiceImpl(
                roleResourceMapper,
                userResourceMapper,
                userRoleMapper,
                sysUserService,
                userRoleService,
                resourceCacheService
        );
        SysUser user = new SysUser();
        user.setId(1001L);
        user.setPermissionVersion(3);
        SysUserRole disabledUserRole = new SysUserRole();
        disabledUserRole.setUserId(1001L);
        disabledUserRole.setRoleId(2001L);
        SysRole disabledRole = new SysRole();
        disabledRole.setId(2001L);
        disabledRole.setStatus(SysRole.Status.DISABLED.getCode());
        SysRoleResource roleResource = new SysRoleResource();
        roleResource.setRoleId(2001L);
        roleResource.setResourceId(3001L);
        roleResource.setActionsJson("[\"read\"]");
        SysResource resource = new SysResource();
        resource.setId(3001L);
        resource.setCode("api.disabled.role.resource");

        when(sysUserService.getById(1001L)).thenReturn(user);
        when(userRoleService.hasRole(1001L, "ROLE_ADMIN")).thenReturn(false);
        when(userRoleService.getUserRoles(1001L)).thenReturn(List.of());
        when(userResourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        service.resources = List.of(resource);

        List<SysResource> resources = service.getUserResources(1001L);

        assertThat(resources).isEmpty();
        assertThat(service.getUserActions(1001L)).isEmpty();
    }

    @Test
    void getUserResourcesShouldIncludeResourcesFromEnabledRoles() {
        SysRoleResourceMapper roleResourceMapper = mock(SysRoleResourceMapper.class);
        SysUserResourceMapper userResourceMapper = mock(SysUserResourceMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysUserService sysUserService = mock(SysUserService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        ResourceCacheService resourceCacheService = new NoopResourceCacheService();
        TestableResourceServiceImpl service = new TestableResourceServiceImpl(
                roleResourceMapper,
                userResourceMapper,
                userRoleMapper,
                sysUserService,
                userRoleService,
                resourceCacheService
        );
        SysUser user = new SysUser();
        user.setId(1002L);
        user.setPermissionVersion(4);
        SysRole enabledRole = new SysRole();
        enabledRole.setId(2002L);
        enabledRole.setStatus(SysRole.Status.ENABLED.getCode());
        SysRoleResource roleResource = new SysRoleResource();
        roleResource.setRoleId(2002L);
        roleResource.setResourceId(3002L);
        roleResource.setActionsJson("[\"read\"]");
        SysResource resource = new SysResource();
        resource.setId(3002L);
        resource.setCode("api.enabled.role.resource");
        resource.setStatus("enabled");

        when(sysUserService.getById(1002L)).thenReturn(user);
        when(userRoleService.hasRole(1002L, "ROLE_ADMIN")).thenReturn(false);
        when(userRoleService.getUserRoles(1002L)).thenReturn(List.of(enabledRole));
        when(roleResourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(roleResource));
        when(userResourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        service.resources = List.of(resource);

        List<SysResource> resources = service.getUserResources(1002L);

        assertThat(resources).extracting(SysResource::getId).containsExactly(3002L);
        assertThat(service.getUserActions(1002L)).containsEntry(3002L, List.of("read"));
    }

    @Test
    void getUserResourcesShouldIgnoreDisabledResourcesEvenWhenStillGranted() {
        SysRoleResourceMapper roleResourceMapper = mock(SysRoleResourceMapper.class);
        SysUserResourceMapper userResourceMapper = mock(SysUserResourceMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysUserService sysUserService = mock(SysUserService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        ResourceCacheService resourceCacheService = new NoopResourceCacheService();
        TestableResourceServiceImpl service = new TestableResourceServiceImpl(
                roleResourceMapper,
                userResourceMapper,
                userRoleMapper,
                sysUserService,
                userRoleService,
                resourceCacheService
        );
        SysUser user = new SysUser();
        user.setId(1004L);
        user.setPermissionVersion(6);
        SysRole enabledRole = new SysRole();
        enabledRole.setId(2004L);
        enabledRole.setStatus(SysRole.Status.ENABLED.getCode());
        SysRoleResource roleResource = new SysRoleResource();
        roleResource.setRoleId(2004L);
        roleResource.setResourceId(3004L);
        roleResource.setActionsJson("[\"read\"]");
        SysResource disabledResource = new SysResource();
        disabledResource.setId(3004L);
        disabledResource.setCode("api.disabled.resource");
        disabledResource.setStatus("disabled");

        when(sysUserService.getById(1004L)).thenReturn(user);
        when(userRoleService.hasRole(1004L, "ROLE_ADMIN")).thenReturn(false);
        when(userRoleService.getUserRoles(1004L)).thenReturn(List.of(enabledRole));
        when(roleResourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(roleResource));
        when(userResourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        service.resources = List.of(disabledResource);

        List<SysResource> resources = service.getUserResources(1004L);

        assertThat(resources).isEmpty();
        assertThat(service.getUserActions(1004L)).doesNotContainKey(3004L);
    }

    @Test
    void getUserActionsShouldLetUserSpecificActionsOverrideRoleActions() {
        SysRoleResourceMapper roleResourceMapper = mock(SysRoleResourceMapper.class);
        SysUserResourceMapper userResourceMapper = mock(SysUserResourceMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysUserService sysUserService = mock(SysUserService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        ResourceCacheService resourceCacheService = new NoopResourceCacheService();
        TestableResourceServiceImpl service = new TestableResourceServiceImpl(
                roleResourceMapper,
                userResourceMapper,
                userRoleMapper,
                sysUserService,
                userRoleService,
                resourceCacheService
        );
        SysUser user = new SysUser();
        user.setId(1003L);
        user.setPermissionVersion(5);
        SysRole enabledRole = new SysRole();
        enabledRole.setId(2003L);
        enabledRole.setStatus(SysRole.Status.ENABLED.getCode());
        SysRoleResource roleResource = new SysRoleResource();
        roleResource.setRoleId(2003L);
        roleResource.setResourceId(3003L);
        roleResource.setActionsJson("[\"read\",\"write\"]");
        SysUserResource userResource = new SysUserResource();
        userResource.setUserId(1003L);
        userResource.setResourceId(3003L);
        userResource.setActionsJson("[\"read\"]");
        SysResource resource = new SysResource();
        resource.setId(3003L);
        resource.setCode("api.user.override.resource");
        resource.setStatus("enabled");

        when(sysUserService.getById(1003L)).thenReturn(user);
        when(userRoleService.hasRole(1003L, "ROLE_ADMIN")).thenReturn(false);
        when(userRoleService.getUserRoles(1003L)).thenReturn(List.of(enabledRole));
        when(roleResourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(roleResource));
        when(userResourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(userResource));
        service.resources = List.of(resource);

        List<SysResource> resources = service.getUserResources(1003L);

        assertThat(resources).extracting(SysResource::getId).containsExactly(3003L);
        assertThat(service.getUserActions(1003L)).containsEntry(3003L, List.of("read"));
    }

    private static class TestableResourceServiceImpl extends ResourceServiceImpl {
        private List<SysResource> resources = List.of();

        private TestableResourceServiceImpl(SysRoleResourceMapper roleResourceMapper,
                                            SysUserResourceMapper userResourceMapper,
                                            SysUserRoleMapper userRoleMapper,
                                            SysUserService sysUserService,
                                            UserRoleService userRoleService,
                                            ResourceCacheService resourceCacheService) {
            super(
                    roleResourceMapper,
                    userResourceMapper,
                    userRoleMapper,
                    sysUserService,
                    userRoleService,
                    resourceCacheService
            );
        }

        @Override
        public List<SysResource> listByIds(Collection<? extends Serializable> idList) {
            return resources.stream()
                    .filter(resource -> idList.contains(resource.getId()))
                    .toList();
        }

    }

    private static class NoopResourceCacheService implements ResourceCacheService {
        @Override
        public java.util.Optional<UserPermissionBundle> get(Long userId, Integer version) {
            return java.util.Optional.empty();
        }

        @Override
        public void put(Long userId, Integer version, UserPermissionBundle bundle) {
        }

        @Override
        public void evictByUserId(Long userId) {
        }
    }
}
