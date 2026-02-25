package com.yiyundao.compensation.modules.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.SysRoleMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysRole;
import com.yiyundao.compensation.modules.rbac.entity.SysUserRole;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户角色服务
 * <p>
 * 统一管理用户与角色的关联关系，提供角色查询、缓存、同步等功能。
 * 核心特性：
 * - Redis 缓存用户角色，支持 24 小时过期
 * - 角色变更时自动清除缓存
 * - 支持按用户 ID 或用户名查询
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRoleService extends ServiceImpl<SysUserRoleMapper, SysUserRole> {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper sysUserMapper;
    private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;

    private static final String USER_ROLES_CACHE_KEY = "user:roles:";
    private static final long CACHE_EXPIRE_HOURS = 24;

    // ==================== 角色查询 ====================

    /**
     * 获取用户所有角色编码（从关联表查询，不依赖 roles 字段）
     * <p>
     * 查询流程：
     * 1. 尝试从 Redis 缓存获取
     * 2. 缓存未命中则查询数据库
     * 3. 将结果写入缓存
     * </p>
     *
     * @param userId 用户ID
     * @return 角色编码集合
     */
    public Set<String> getUserRoleCodes(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }

        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getObject();
        String cacheKey = USER_ROLES_CACHE_KEY + userId;

        // 1. 尝试从缓存获取
        if (redisTemplate != null) {
            try {
                Set<Object> cached = redisTemplate.opsForSet().members(cacheKey);
                if (cached != null && !cached.isEmpty()) {
                    log.trace("UserRoleService: cache hit for userId={}", userId);
                    return cached.stream()
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            .collect(Collectors.toSet());
                }
                log.trace("UserRoleService: cache miss for userId={}", userId);
            } catch (Exception e) {
                log.warn("UserRoleService: 缓存读取失败，回退到数据库查询, userId={}", userId, e);
            }
        }

        // 2. 从关联表查询
        Set<String> roleCodes = fetchUserRoleCodesFromDb(userId);

        // 3. 写入缓存
        if (redisTemplate != null && !roleCodes.isEmpty()) {
            try {
                redisTemplate.opsForSet().add(cacheKey, roleCodes.toArray());
                redisTemplate.expire(cacheKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                log.debug("UserRoleService: 缓存已更新, userId={}, roles={}", userId, roleCodes);
            } catch (Exception e) {
                log.warn("UserRoleService: 缓存写入失败, userId={}", userId, e);
            }
        }

        return roleCodes;
    }

    /**
     * 根据用户名获取用户所有角色编码
     * <p>
     * 先根据用户名查找用户ID，再获取角色编码。
     * </p>
     *
     * @param username 用户名
     * @return 角色编码集合
     */
    public Set<String> getUserRoleCodesByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return Collections.emptySet();
        }

        // 先根据用户名查找用户ID
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .last("limit 1")
        );

        if (user == null || user.getId() == null) {
            log.debug("UserRoleService: 用户不存在或无用户ID, username={}", username);
            return Collections.emptySet();
        }

        return getUserRoleCodes(user.getId());
    }

    /**
     * 获取用户所有角色（带角色信息）
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    public List<SysRole> getUserRoles(Long userId) {
        // sys_user_role 表没有 deleted 字段，直接查询
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
        );

        Set<Long> roleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        return roleMapper.selectBatchIds(roleIds).stream()
                .filter(Objects::nonNull)
                .filter(role -> SysRole.Status.ENABLED.getCode().equals(role.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * 检查用户是否有指定角色
     *
     * @param userId   用户ID
     * @param roleCode 角色编码
     * @return 是否有该角色
     */
    public boolean hasRole(Long userId, String roleCode) {
        if (userId == null || roleCode == null) {
            return false;
        }

        // 规范化角色编码
        String normalizedCode = roleCode.toUpperCase();
        if (normalizedCode.startsWith("ROLE_")) {
            normalizedCode = normalizedCode.substring(5);
        }

        // 快速路径：先检查缓存
        Set<String> roleCodes = getUserRoleCodes(userId);
        if (roleCodes.contains(normalizedCode) || roleCodes.contains("ROLE_" + normalizedCode)) {
            return true;
        }

        // 兜底路径：直接查询数据库
        return hasRoleFromDb(userId, roleCode);
    }

    /**
     * 检查用户是否有任一指定角色
     *
     * @param userId    用户ID
     * @param roleCodes 角色编码数组
     * @return 是否有任一角色
     */
    public boolean hasAnyRole(Long userId, String... roleCodes) {
        if (userId == null || roleCodes == null) {
            return false;
        }

        Set<String> userRoleCodes = getUserRoleCodes(userId);
        for (String roleCode : roleCodes) {
            if (roleCode != null && hasRoleCode(userRoleCodes, roleCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查用户是否有所有指定角色
     *
     * @param userId    用户ID
     * @param roleCodes 角色编码数组
     * @return 是否有所有角色
     */
    public boolean hasAllRoles(Long userId, String... roleCodes) {
        if (userId == null || roleCodes == null) {
            return false;
        }

        Set<String> userRoleCodes = getUserRoleCodes(userId);
        for (String roleCode : roleCodes) {
            if (roleCode == null || !hasRoleCode(userRoleCodes, roleCode)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查用户是否为管理员
     *
     * @param userId 用户ID
     * @return 是否为管理员
     */
    public boolean isAdmin(Long userId) {
        return hasRole(userId, "ROLE_ADMIN");
    }

    // ==================== 角色分配 ====================

    /**
     * 授予用户角色（带审计信息）
     *
     * @param userId    用户ID
     * @param roleId    角色ID
     * @param grantedBy 授权人ID
     * @param expiresAt 过期时间（可选）
     * @param remarks   备注
     */
    @Transactional
    public void grantRole(Long userId, Long roleId, Long grantedBy,
                          LocalDateTime expiresAt, String remarks) {
        log.info("授予用户角色: userId={}, roleId={}, grantedBy={}", userId, roleId, grantedBy);

        // 检查是否已存在
        SysUserRole existing = userRoleMapper.selectOne(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(SysUserRole::getRoleId, roleId)
        );

        if (existing != null) {
            // 已存在，更新过期时间
            existing.setExpiresAt(expiresAt);
            existing.setRemarks(remarks);
            existing.setUpdateBy(String.valueOf(grantedBy));
            existing.setUpdateTime(LocalDateTime.now());
            userRoleMapper.updateById(existing);
        } else {
            // 不存在，创建新记录
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRole.setGrantedBy(grantedBy);
            userRole.setGrantedAt(LocalDateTime.now());
            userRole.setExpiresAt(expiresAt);
            userRole.setRemarks(remarks);
            userRole.setCreateBy(String.valueOf(grantedBy));
            userRole.setCreateTime(LocalDateTime.now());
            userRole.setDeleted(0);
            userRoleMapper.insert(userRole);
        }

        // 清除缓存 - 角色变更后立即生效
        invalidateUserRolesCache(userId);
    }

    /**
     * 批量授予用户角色
     *
     * @param userId    用户ID
     * @param roleIds   角色ID集合
     * @param grantedBy 授权人ID
     */
    @Transactional
    public void grantRoles(Long userId, Collection<Long> roleIds, Long grantedBy) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        for (Long roleId : roleIds) {
            grantRole(userId, roleId, grantedBy, null, null);
        }
    }

    /**
     * 撤销用户角色
     *
     * @param userId    用户ID
     * @param roleId    角色ID
     * @param revokedBy 撤销人ID
     */
    @Transactional
    public void revokeRole(Long userId, Long roleId, Long revokedBy) {
        log.info("撤销用户角色: userId={}, roleId={}, revokedBy={}", userId, roleId, revokedBy);

        SysUserRole userRole = userRoleMapper.selectOne(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(SysUserRole::getRoleId, roleId)
        );

        if (userRole != null) {
            userRole.setDeleted(1);
            userRole.setDeleteBy(String.valueOf(revokedBy));
            userRole.setDeleteTime(LocalDateTime.now());
            userRoleMapper.updateById(userRole);
        }

        // 清除缓存
        invalidateUserRolesCache(userId);
    }

    /**
     * 批量撤销用户所有角色
     *
     * @param userId    用户ID
     * @param revokedBy 撤销人ID
     */
    @Transactional
    public void revokeAllRoles(Long userId, Long revokedBy) {
        log.info("撤销用户所有角色: userId={}, revokedBy={}", userId, revokedBy);

        userRoleMapper.update(null,
                new LambdaUpdateWrapper<SysUserRole>()
                        .set(SysUserRole::getDeleted, 1)
                        .set(SysUserRole::getUpdateBy, String.valueOf(revokedBy))
                        .set(SysUserRole::getUpdateTime, LocalDateTime.now())
                        .eq(SysUserRole::getUserId, userId)
        );

        // 清除缓存
        invalidateUserRolesCache(userId);
    }

    // ==================== 缓存管理 ====================

    /**
     * 清除用户角色缓存
     * <p>
     * 角色变更时调用，确保下次查询从数据库获取最新数据。
     * </p>
     *
     * @param userId 用户ID
     */
    public void invalidateUserRolesCache(Long userId) {
        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getObject();
        if (redisTemplate != null) {
            String cacheKey = USER_ROLES_CACHE_KEY + userId;
            try {
                redisTemplate.delete(cacheKey);
                log.debug("清除用户角色缓存: userId={}", userId);
            } catch (Exception e) {
                log.warn("清除用户角色缓存失败: userId={}", userId, e);
            }
        }
    }

    /**
     * 清除所有角色缓存
     */
    public void invalidateAllCache() {
        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getObject();
        if (redisTemplate != null) {
            Set<String> keys = redisTemplate.keys(USER_ROLES_CACHE_KEY + "*");
            if (keys != null && !keys.isEmpty()) {
                try {
                    redisTemplate.delete(keys);
                    log.info("清除所有用户角色缓存: count={}", keys.size());
                } catch (Exception e) {
                    log.warn("清除所有用户角色缓存失败", e);
                }
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 规范化角色编码并检查是否匹配（支持 ROLE_ 前缀）
     */
    private boolean hasRoleCode(Set<String> userRoleCodes, String roleCode) {
        String normalized = normalizeRoleCode(roleCode);
        return userRoleCodes.contains(normalized) || userRoleCodes.contains("ROLE_" + normalized);
    }

    /**
     * 规范化角色编码（移除 ROLE_ 前缀，转大写）
     */
    private String normalizeRoleCode(String roleCode) {
        if (roleCode == null) return "";
        String normalized = roleCode.toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        return normalized;
    }

    /**
     * 从数据库获取用户角色编码
     */
    private Set<String> fetchUserRoleCodesFromDb(Long userId) {
        // sys_user_role 表没有 deleted 字段，直接查询
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
        );

        Set<Long> roleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (roleIds.isEmpty()) {
            return Collections.emptySet();
        }

        // 只返回启用的角色
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds).stream()
                .filter(Objects::nonNull)
                .filter(role -> SysRole.Status.ENABLED.getCode().equals(role.getStatus()))
                .collect(Collectors.toList());

        return roles.stream()
                .map(SysRole::getCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    /**
     * 从数据库检查用户是否有指定角色
     */
    private boolean hasRoleFromDb(Long userId, String roleCode) {
        // 规范化角色编码（用于 lambda 中需要 final 变量）
        String normalized = roleCode.toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        final String normalizedCode = normalized;

        // 查找角色ID（支持带 ROLE_ 前缀和不带前缀的编码）
        SysRole role = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>()
                        .and(w -> w.eq(SysRole::getCode, normalizedCode)
                                .or()
                                .eq(SysRole::getCode, "ROLE_" + normalizedCode))
                        .eq(SysRole::getStatus, SysRole.Status.ENABLED.getCode())
        );

        if (role == null) {
            return false;
        }

        // 检查关联
        return userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, role.getId())
        ) > 0;
    }

    /**
     * 根据角色编码查找拥有该角色的第一个用户
     *
     * @param roleCode 角色编码
     * @return 第一个匹配的用户
     */
    public SysUser findFirstUserByRole(String roleCode) {
        String normalized = roleCode.toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        final String normalizedCode = normalized;

        SysRole role = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>()
                        .and(w -> w.eq(SysRole::getCode, normalizedCode)
                                .or()
                                .eq(SysRole::getCode, "ROLE_" + normalizedCode))
                        .eq(SysRole::getStatus, SysRole.Status.ENABLED.getCode())
        );

        if (role == null) {
            return null;
        }

        SysUserRole userRole = userRoleMapper.selectOne(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, role.getId())
                        .orderByAsc(SysUserRole::getUserId)
                        .last("limit 1")
        );

        if (userRole == null) {
            return null;
        }

        return sysUserMapper.selectById(userRole.getUserId());
    }
}
