package com.yiyundao.compensation.modules.rbac.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yiyundao.compensation.enums.UserStatus;
import com.yiyundao.compensation.infrastructure.dao.SysRoleMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysRole;
import com.yiyundao.compensation.modules.rbac.entity.SysUserRole;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRoleServiceTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        initTableInfo(configuration, SysUserRole.class);
        initTableInfo(configuration, SysUser.class);
        initTableInfo(configuration, SysRole.class);
    }

    private static void initTableInfo(MybatisConfiguration configuration, Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, entityType.getName());
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    @Test
    void replaceUserRolesShouldBeSingleTransactionalBoundary() throws NoSuchMethodException {
        Method method = UserRoleService.class.getMethod("replaceUserRoles", Long.class, java.util.Collection.class, Long.class);

        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void grantRoleShouldRestoreSoftDeletedRelationInsteadOfInsertingDuplicate() {
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        ObjectProvider<RedisTemplate<String, Object>> redisProvider = mock(ObjectProvider.class);
        ResourceCacheService resourceCacheService = mock(ResourceCacheService.class);
        UserRoleService service = new UserRoleService(
                userRoleMapper,
                roleMapper,
                sysUserMapper,
                redisProvider,
                resourceCacheService
        );

        when(userRoleMapper.restoreDeletedRole(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        service.grantRole(10L, 20L, 99L, null, "restore");

        verify(userRoleMapper).restoreDeletedRole(any(), any(), any(), any(), any(), any(), any(), any());
        verify(userRoleMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(userRoleMapper, never()).insert(any(SysUserRole.class));
        verify(userRoleMapper, never()).updateById(any(SysUserRole.class));
        verify(sysUserMapper).update(any(), any());
        verify(resourceCacheService).evictByUserId(10L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void grantRoleShouldUpdateActiveRelationWhenAlreadyGranted() {
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        ObjectProvider<RedisTemplate<String, Object>> redisProvider = mock(ObjectProvider.class);
        ResourceCacheService resourceCacheService = mock(ResourceCacheService.class);
        UserRoleService service = new UserRoleService(
                userRoleMapper,
                roleMapper,
                sysUserMapper,
                redisProvider,
                resourceCacheService
        );

        SysUserRole existing = new SysUserRole();
        existing.setId(7L);
        existing.setUserId(10L);
        existing.setRoleId(20L);
        existing.setDeleted(0);
        when(userRoleMapper.restoreDeletedRole(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(userRoleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        service.grantRole(10L, 20L, 99L, null, "update");

        assertThat(existing.getGrantedBy()).isEqualTo(99L);
        assertThat(existing.getRemarks()).isEqualTo("update");
        assertThat(existing.getUpdateBy()).isEqualTo("99");
        verify(userRoleMapper).updateById(existing);
        verify(userRoleMapper, never()).insert(any(SysUserRole.class));
        verify(sysUserMapper).update(any(), any());
        verify(resourceCacheService).evictByUserId(10L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceUserRolesShouldIncrementPermissionVersionInSameServiceOperation() {
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        ObjectProvider<RedisTemplate<String, Object>> redisProvider = mock(ObjectProvider.class);
        ResourceCacheService resourceCacheService = mock(ResourceCacheService.class);
        UserRoleService service = new UserRoleService(
                userRoleMapper,
                roleMapper,
                sysUserMapper,
                redisProvider,
                resourceCacheService
        );

        when(userRoleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        service.replaceUserRoles(10L, List.of(20L), 99L);

        verify(userRoleMapper).update(any(), any());
        verify(userRoleMapper).insert(any(SysUserRole.class));
        verify(sysUserMapper).update(any(), any());
        verify(resourceCacheService).evictByUserId(10L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findFirstUserByRoleExcludingShouldSkipExcludedAndInactiveUsers() {
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        ObjectProvider<RedisTemplate<String, Object>> redisProvider = mock(ObjectProvider.class);
        ResourceCacheService resourceCacheService = mock(ResourceCacheService.class);
        UserRoleService service = new UserRoleService(
                userRoleMapper,
                roleMapper,
                sysUserMapper,
                redisProvider,
                resourceCacheService
        );

        SysRole role = role(20L, "FINANCE");
        SysUserRole excluded = userRole(100L, 20L, null);
        SysUserRole inactive = userRole(101L, 20L, null);
        SysUserRole active = userRole(102L, 20L, null);
        SysUser inactiveUser = user(101L, UserStatus.INACTIVE);
        SysUser activeUser = user(102L, UserStatus.ACTIVE);

        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(role));
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(excluded, inactive, active));
        when(sysUserMapper.selectById(101L)).thenReturn(inactiveUser);
        when(sysUserMapper.selectById(102L)).thenReturn(activeUser);

        SysUser result = service.findFirstUserByRoleExcluding("ROLE_FINANCE", 100L);

        assertThat(result).isSameAs(activeUser);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getUserRoleCodesShouldIgnoreExpiredAssignmentsAndNormalizeLegacyCodes() {
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        ObjectProvider<RedisTemplate<String, Object>> redisProvider = mock(ObjectProvider.class);
        ResourceCacheService resourceCacheService = mock(ResourceCacheService.class);
        UserRoleService service = new UserRoleService(
                userRoleMapper,
                roleMapper,
                sysUserMapper,
                redisProvider,
                resourceCacheService
        );

        SysUserRole expiredAdmin = userRole(10L, 1L, LocalDateTime.now().minusMinutes(1));
        SysUserRole activeFinance = userRole(10L, 2L, LocalDateTime.now().plusHours(1));
        SysRole finance = role(2L, "role.finance");

        when(redisProvider.getObject()).thenReturn(null);
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(expiredAdmin, activeFinance));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(finance));

        Set<String> roleCodes = service.getUserRoleCodes(10L);

        assertThat(roleCodes).containsExactly("FINANCE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getUserRoleCodesShouldNormalizeCachedLegacyCodes() {
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        ObjectProvider<RedisTemplate<String, Object>> redisProvider = mock(ObjectProvider.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        org.springframework.data.redis.core.SetOperations<String, Object> setOperations =
                mock(org.springframework.data.redis.core.SetOperations.class);
        ResourceCacheService resourceCacheService = mock(ResourceCacheService.class);
        UserRoleService service = new UserRoleService(
                userRoleMapper,
                roleMapper,
                sysUserMapper,
                redisProvider,
                resourceCacheService
        );

        when(redisProvider.getObject()).thenReturn(redisTemplate);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(eq("user:roles:10"))).thenReturn(Set.of("role.finance", "ROLE_ADMIN"));

        Set<String> roleCodes = service.getUserRoleCodes(10L);

        assertThat(roleCodes).containsExactlyInAnyOrder("FINANCE", "ADMIN");
        verify(userRoleMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    private static SysRole role(Long id, String code) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setCode(code);
        role.setStatus(SysRole.Status.ENABLED.getCode());
        return role;
    }

    private static SysUserRole userRole(Long userId, Long roleId, LocalDateTime expiresAt) {
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRole.setExpiresAt(expiresAt);
        return userRole;
    }

    private static SysUser user(Long id, UserStatus status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(status);
        return user;
    }
}
