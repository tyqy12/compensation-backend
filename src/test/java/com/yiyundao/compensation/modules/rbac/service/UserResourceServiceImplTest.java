package com.yiyundao.compensation.modules.rbac.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.SysResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserResourceMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserResource;
import com.yiyundao.compensation.modules.rbac.service.impl.UserResourceServiceImpl;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserResourceServiceImplTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, SysUserResource.class.getName());
        assistant.setCurrentNamespace(SysUserResource.class.getName());
        TableInfoHelper.initTableInfo(assistant, SysUserResource.class);
    }

    @Test
    void getUserResourcesShouldParseLegacyListToStringActions() {
        SysUserResourceMapper userResourceMapper = mock(SysUserResourceMapper.class);
        SysUserResource resource = new SysUserResource();
        resource.setUserId(10L);
        resource.setResourceId(20L);
        resource.setActionsJson("[read, write]");

        when(userResourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(resource));

        UserResourceServiceImpl service = new UserResourceServiceImpl(
                userResourceMapper,
                mock(SysResourceMapper.class),
                mock(SysUserService.class),
                mock(ResourceCacheService.class),
                new ObjectMapper()
        );

        Map<Long, Set<String>> result = service.getUserResources(10L);

        assertThat(result).containsEntry(20L, Set.of("read", "write"));
    }

    @Test
    void assignResourcesShouldWriteStandardJsonActionsAndRefreshCache() {
        SysUserResourceMapper userResourceMapper = mock(SysUserResourceMapper.class);
        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserService sysUserService = mock(SysUserService.class);
        ResourceCacheService resourceCacheService = mock(ResourceCacheService.class);
        UserResourceServiceImpl service = new UserResourceServiceImpl(
                userResourceMapper,
                resourceMapper,
                sysUserService,
                resourceCacheService,
                new ObjectMapper()
        );
        when(sysUserService.getById(10L)).thenReturn(user(10L));
        when(resourceMapper.selectBatchIds(List.of(20L, 21L))).thenReturn(List.of(
                resource(20L, "enabled"),
                resource(21L, "enabled")
        ));

        service.assignResources(
                10L,
                List.of(20L, 21L),
                Map.of(20L, List.of("read"), 21L, List.of("read", "write")),
                100L
        );

        org.mockito.ArgumentCaptor<SysUserResource> inserted =
                org.mockito.ArgumentCaptor.forClass(SysUserResource.class);
        verify(userResourceMapper).delete(any(LambdaQueryWrapper.class));
        verify(userResourceMapper, times(2)).insert(inserted.capture());
        assertThat(inserted.getAllValues())
                .extracting(SysUserResource::getActionsJson)
                .containsExactly("[\"read\"]", "[\"read\",\"write\"]");
        verify(sysUserService).incrementPermissionVersion(10L);
        verify(resourceCacheService).evictByUserId(10L);
    }

    @Test
    void assignResourcesShouldRejectMissingUserBeforeWritingResources() {
        SysUserResourceMapper userResourceMapper = mock(SysUserResourceMapper.class);
        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserService sysUserService = mock(SysUserService.class);
        UserResourceServiceImpl service = new UserResourceServiceImpl(
                userResourceMapper,
                resourceMapper,
                sysUserService,
                mock(ResourceCacheService.class),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> service.assignResources(10L, List.of(20L), Map.of(), 100L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                    assertThat(ex.getMessage()).contains("用户不存在");
                });

        verify(userResourceMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(userResourceMapper, never()).insert(any(SysUserResource.class));
        verify(resourceMapper, never()).selectBatchIds(any());
    }

    @Test
    void assignResourcesShouldRejectMissingOrDisabledResourcesBeforeReplacingExistingGrants() {
        SysUserResourceMapper userResourceMapper = mock(SysUserResourceMapper.class);
        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserService sysUserService = mock(SysUserService.class);
        UserResourceServiceImpl service = new UserResourceServiceImpl(
                userResourceMapper,
                resourceMapper,
                sysUserService,
                mock(ResourceCacheService.class),
                new ObjectMapper()
        );
        when(sysUserService.getById(10L)).thenReturn(user(10L));
        when(resourceMapper.selectBatchIds(List.of(20L, 21L))).thenReturn(List.of(resource(20L, "enabled")));

        assertThatThrownBy(() -> service.assignResources(10L, List.of(20L, 21L), Map.of(), 100L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(ex.getMessage()).contains("资源不存在或已禁用", "21");
                });

        verify(userResourceMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(userResourceMapper, never()).insert(any(SysUserResource.class));
    }

    @Test
    void assignResourcesShouldRevokeAllWhenResourceIdsAreEmpty() {
        SysUserResourceMapper userResourceMapper = mock(SysUserResourceMapper.class);
        SysUserService sysUserService = mock(SysUserService.class);
        ResourceCacheService resourceCacheService = mock(ResourceCacheService.class);
        UserResourceServiceImpl service = new UserResourceServiceImpl(
                userResourceMapper,
                mock(SysResourceMapper.class),
                sysUserService,
                resourceCacheService,
                new ObjectMapper()
        );
        when(sysUserService.getById(10L)).thenReturn(user(10L));

        service.assignResources(10L, List.of(), Map.of(), 100L);

        verify(userResourceMapper).delete(any(LambdaQueryWrapper.class));
        verify(userResourceMapper, never()).insert(any(SysUserResource.class));
        verify(sysUserService).incrementPermissionVersion(10L);
        verify(resourceCacheService).evictByUserId(10L);
    }

    private SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        return user;
    }

    private SysResource resource(Long id, String status) {
        SysResource resource = new SysResource();
        resource.setId(id);
        resource.setStatus(status);
        return resource;
    }
}
