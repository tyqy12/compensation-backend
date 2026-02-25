package com.yiyundao.compensation.modules.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.infrastructure.dao.SysRoleResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysRoleResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserRole;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 资源变更事件监听器
 * <p>
 * 当资源发生变更（新增、更新、删除）时，自动清除相关缓存
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceChangeListener {

    private final SysUserService sysUserService;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleResourceMapper roleResourceMapper;
    private final SysUserResourceMapper userResourceMapper;

    /**
     * 监听资源变更事件
     *
     * @param event 资源变更事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleResourceChange(ResourceChangeEvent event) {
        log.info("收到资源变更事件: type={}, resourceId={}", event.changeType(), event.resourceId());

        try {
            Set<Long> affectedUserIds = collectAffectedUserIds(event);

            if (affectedUserIds.isEmpty()) {
                log.debug("没有用户受资源变更影响");
                return;
            }

            // 批量清除用户权限缓存
            sysUserService.batchIncrementPermissionVersion(affectedUserIds);

            log.info("已清除 {} 个用户的权限缓存", affectedUserIds.size());

        } catch (Exception e) {
            log.error("处理资源变更事件失败", e);
        }
    }

    /**
     * 收集受影响的用户ID
     */
    private Set<Long> collectAffectedUserIds(ResourceChangeEvent event) {
        Set<Long> userIds = new HashSet<>();

        switch (event.changeType()) {
            case CREATE:
            case UPDATE:
                // 资源更新时，关联该资源的所有用户都需要清除缓存
                userIds.addAll(getUsersByResourceId(event.resourceId()));
                // 同时，关联该资源所属角色的用户也需要清除缓存
                userIds.addAll(getUsersByResourceIdRecursive(event.resourceId()));
                break;

            case DELETE:
                // 资源删除时，先收集用户，再清理关联
                userIds.addAll(getUsersByResourceId(event.resourceId()));
                userIds.addAll(getUsersByResourceIdRecursive(event.resourceId()));
                break;
        }

        return userIds;
    }

    /**
     * 直接关联该资源的用户
     */
    private Set<Long> getUsersByResourceId(Long resourceId) {
        Set<Long> userIds = new HashSet<>();

        // 用户直接授权的资源
        List<SysUserResource> userResources = userResourceMapper.selectList(
                new LambdaQueryWrapper<SysUserResource>()
                        .eq(SysUserResource::getResourceId, resourceId));
        userIds.addAll(userResources.stream()
                .map(SysUserResource::getUserId)
                .toList());

        return userIds;
    }

    /**
     * 递归获取关联该资源的角色下的所有用户（包括子角色）
     */
    private Set<Long> getUsersByResourceIdRecursive(Long resourceId) {
        Set<Long> userIds = new HashSet<>();

        // 获取拥有该资源的角色
        List<SysRoleResource> roleResources = roleResourceMapper.selectList(
                new LambdaQueryWrapper<SysRoleResource>()
                        .eq(SysRoleResource::getResourceId, resourceId));

        Set<Long> roleIds = roleResources.stream()
                .map(SysRoleResource::getRoleId)
                .collect(java.util.stream.Collectors.toSet());

        if (roleIds.isEmpty()) {
            return userIds;
        }

        // 获取这些角色下的所有用户
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .in(SysUserRole::getRoleId, roleIds));

        userIds.addAll(userRoles.stream()
                .map(SysUserRole::getUserId)
                .toList());

        return userIds;
    }

    /**
     * 资源变更事件
     */
    public record ResourceChangeEvent(ChangeType changeType, Long resourceId) {
        public enum ChangeType {
            CREATE, UPDATE, DELETE
        }
    }
}
