package com.yiyundao.compensation.modules.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.SysRoleMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.enums.UserStatus;
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

import java.time.Duration;
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
    private final ResourceCacheService resourceCacheService;

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
                            .map(this::normalizeRoleCode)
                            .filter(StringUtils::hasText)
                            .collect(Collectors.toSet());
                }
                log.trace("UserRoleService: cache miss for userId={}", userId);
            } catch (Exception e) {
                log.warn("UserRoleService: 缓存读取失败，回退到数据库查询, userId={}", userId, e);
            }
        }

        // 2. 从关联表查询
        UserRoleCodeSnapshot snapshot = fetchUserRoleCodesFromDb(userId);
        Set<String> roleCodes = snapshot.roleCodes();

        // 3. 写入缓存
        if (redisTemplate != null && !roleCodes.isEmpty()) {
            try {
                redisTemplate.opsForSet().add(cacheKey, roleCodes.toArray());
                redisTemplate.expire(cacheKey, cacheTtlSeconds(snapshot.nearestExpiresAt()), TimeUnit.SECONDS);
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
        // SysUserRole 继承 BaseEntity，逻辑删除条件由 MyBatis-Plus 自动追加。
        LocalDateTime now = LocalDateTime.now();
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .and(w -> w.isNull(SysUserRole::getExpiresAt)
                                .or()
                                .gt(SysUserRole::getExpiresAt, now))
        );

        Set<Long> roleIds = userRoles.stream()
                .filter(userRole -> isEffective(userRole, now))
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

        // 快速路径：先检查缓存
        Set<String> roleCodes = getUserRoleCodes(userId);
        if (hasRoleCode(roleCodes, roleCode)) {
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

        boolean changed = grantRoleInternal(userId, roleId, grantedBy, expiresAt, remarks);
        if (changed) {
            refreshPermissionCaches(userId);
        }
    }

    private boolean grantRoleInternal(Long userId, Long roleId, Long grantedBy,
                                      LocalDateTime expiresAt, String remarks) {
        LocalDateTime now = LocalDateTime.now();
        String operator = String.valueOf(grantedBy);
        int restored = userRoleMapper.restoreDeletedRole(
                userId, roleId, grantedBy, now, expiresAt, remarks, operator, now);
        if (restored > 0) {
            return true;
        }

        // MyBatis-Plus 逻辑删除会自动过滤 deleted=1，这里只处理当前活跃关联。
        SysUserRole existing = userRoleMapper.selectOne(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(SysUserRole::getRoleId, roleId)
        );

        if (existing != null) {
            // 已存在，更新授权信息。
            existing.setExpiresAt(expiresAt);
            existing.setRemarks(remarks);
            existing.setGrantedBy(grantedBy);
            existing.setGrantedAt(now);
            existing.setUpdateBy(operator);
            existing.setUpdateTime(now);
            userRoleMapper.updateById(existing);
        } else {
            // 不存在，创建新记录
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRole.setGrantedBy(grantedBy);
            userRole.setGrantedAt(now);
            userRole.setExpiresAt(expiresAt);
            userRole.setRemarks(remarks);
            userRole.setCreateBy(operator);
            userRole.setCreateTime(now);
            userRole.setDeleted(0);
            userRoleMapper.insert(userRole);
        }

        return true;
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

        boolean changed = false;
        for (Long roleId : roleIds) {
            changed |= grantRoleInternal(userId, roleId, grantedBy, null, null);
        }
        if (changed) {
            refreshPermissionCaches(userId);
        }
    }

    /**
     * 覆盖式设置用户角色。
     * <p>
     * 撤销、授予、权限版本递增必须处于同一事务，避免部分成功导致用户角色被清空。
     * </p>
     *
     * @param userId    用户ID
     * @param roleIds   目标角色ID集合
     * @param operatorId 操作人ID
     */
    @Transactional
    public void replaceUserRoles(Long userId, Collection<Long> roleIds, Long operatorId) {
        revokeAllRolesInternal(userId, operatorId);

        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                grantRoleInternal(userId, roleId, operatorId, null, null);
            }
        }

        refreshPermissionCaches(userId);
    }

    private void incrementPermissionVersion(Long userId) {
        if (userId == null) {
            return;
        }
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .setSql("permission_version = COALESCE(permission_version, 0) + 1")
                .set(SysUser::getUpdateTime, LocalDateTime.now())
                .eq(SysUser::getId, userId));
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
            refreshPermissionCaches(userId);
        }
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

        if (revokeAllRolesInternal(userId, revokedBy)) {
            refreshPermissionCaches(userId);
        }
    }

    private boolean revokeAllRolesInternal(Long userId, Long revokedBy) {
        int updated = userRoleMapper.update(null,
                new LambdaUpdateWrapper<SysUserRole>()
                        .set(SysUserRole::getDeleted, 1)
                        .set(SysUserRole::getUpdateBy, String.valueOf(revokedBy))
                        .set(SysUserRole::getUpdateTime, LocalDateTime.now())
                        .eq(SysUserRole::getUserId, userId)
        );
        return updated > 0;
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

    private void refreshPermissionCaches(Long userId) {
        incrementPermissionVersion(userId);
        invalidateUserRolesCache(userId);
        try {
            resourceCacheService.evictByUserId(userId);
        } catch (Exception e) {
            log.warn("清除用户资源权限缓存失败: userId={}", userId, e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 规范化角色编码并检查是否匹配（支持 ROLE_ 前缀）
     */
    private boolean hasRoleCode(Set<String> userRoleCodes, String roleCode) {
        String normalized = normalizeRoleCode(roleCode);
        return userRoleCodes.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeRoleCode)
                .anyMatch(normalized::equals);
    }

    /**
     * 规范化角色编码（移除 ROLE_ 前缀，转大写）
     */
    private String normalizeRoleCode(String roleCode) {
        if (roleCode == null) return "";
        String normalized = roleCode.trim();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        if (normalized.regionMatches(true, 0, "role.", 0, "role.".length())) {
            normalized = normalized.substring("role.".length());
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (normalized.endsWith(".ALL")) {
            normalized = normalized.substring(0, normalized.length() - ".ALL".length());
        }
        return normalized;
    }

    /**
     * 从数据库获取用户角色编码
     */
    private UserRoleCodeSnapshot fetchUserRoleCodesFromDb(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        // SysUserRole 继承 BaseEntity，逻辑删除条件由 MyBatis-Plus 自动追加。
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .and(w -> w.isNull(SysUserRole::getExpiresAt)
                                .or()
                                .gt(SysUserRole::getExpiresAt, now))
        );

        List<SysUserRole> effectiveUserRoles = userRoles.stream()
                .filter(userRole -> isEffective(userRole, now))
                .toList();

        Set<Long> roleIds = userRoles.stream()
                .filter(userRole -> isEffective(userRole, now))
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (roleIds.isEmpty()) {
            return new UserRoleCodeSnapshot(Collections.emptySet(), null);
        }

        // 只返回启用的角色
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds).stream()
                .filter(Objects::nonNull)
                .filter(role -> SysRole.Status.ENABLED.getCode().equals(role.getStatus()))
                .collect(Collectors.toList());

        Set<String> roleCodes = roles.stream()
                .map(SysRole::getCode)
                .map(this::normalizeRoleCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        LocalDateTime nearestExpiresAt = effectiveUserRoles.stream()
                .map(SysUserRole::getExpiresAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        return new UserRoleCodeSnapshot(roleCodes, nearestExpiresAt);
    }

    /**
     * 从数据库检查用户是否有指定角色
     */
    private boolean hasRoleFromDb(Long userId, String roleCode) {
        List<SysRole> roles = findEnabledRolesByCode(roleCode);
        Set<Long> roleIds = roles.stream()
                .map(SysRole::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (roleIds.isEmpty()) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        // 检查关联
        return userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .in(SysUserRole::getRoleId, roleIds)
                .and(w -> w.isNull(SysUserRole::getExpiresAt)
                        .or()
                        .gt(SysUserRole::getExpiresAt, now))
        ) > 0;
    }

    /**
     * 根据角色编码查找拥有该角色的第一个用户
     *
     * @param roleCode 角色编码
     * @return 第一个匹配的用户
     */
    public SysUser findFirstUserByRole(String roleCode) {
        return findFirstUserByRoleExcluding(roleCode, null);
    }

    /**
     * 根据角色编码查找拥有该角色且不是排除用户的第一个有效用户。
     *
     * @param roleCode 角色编码
     * @param excludedUserId 需要排除的用户ID
     * @return 第一个匹配的激活用户
     */
    public SysUser findFirstUserByRoleExcluding(String roleCode, Long excludedUserId) {
        List<SysRole> roles = findEnabledRolesByCode(roleCode);
        Set<Long> roleIds = roles.stream()
                .map(SysRole::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (roleIds.isEmpty()) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .in(SysUserRole::getRoleId, roleIds)
                        .ne(excludedUserId != null, SysUserRole::getUserId, excludedUserId)
                        .and(w -> w.isNull(SysUserRole::getExpiresAt)
                                .or()
                                .gt(SysUserRole::getExpiresAt, now))
                        .orderByAsc(SysUserRole::getUserId)
        );

        for (SysUserRole userRole : userRoles) {
            if (!isEffective(userRole, now) || Objects.equals(userRole.getUserId(), excludedUserId)) {
                continue;
            }
            SysUser user = sysUserMapper.selectById(userRole.getUserId());
            if (user != null && UserStatus.ACTIVE.equals(user.getStatus())) {
                return user;
            }
        }
        return null;
    }

    private List<SysRole> findEnabledRolesByCode(String roleCode) {
        Set<String> candidates = roleCodeCandidates(roleCode);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getCode, candidates)
                .eq(SysRole::getStatus, SysRole.Status.ENABLED.getCode()));
    }

    private Set<String> roleCodeCandidates(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return Collections.emptySet();
        }
        String normalized = normalizeRoleCode(roleCode);
        if (!StringUtils.hasText(normalized)) {
            return Collections.emptySet();
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(roleCode.trim());
        candidates.add(normalized);
        candidates.add("ROLE_" + normalized);
        candidates.add("role." + lower);
        return candidates;
    }

    private boolean isEffective(SysUserRole userRole, LocalDateTime now) {
        return userRole != null && (userRole.getExpiresAt() == null || userRole.getExpiresAt().isAfter(now));
    }

    private long cacheTtlSeconds(LocalDateTime nearestExpiresAt) {
        if (nearestExpiresAt == null) {
            return TimeUnit.HOURS.toSeconds(CACHE_EXPIRE_HOURS);
        }
        long seconds = Duration.between(LocalDateTime.now(), nearestExpiresAt).getSeconds();
        if (seconds <= 0) {
            return 1;
        }
        return Math.min(seconds, TimeUnit.HOURS.toSeconds(CACHE_EXPIRE_HOURS));
    }

    private record UserRoleCodeSnapshot(Set<String> roleCodes, LocalDateTime nearestExpiresAt) {
    }
}
