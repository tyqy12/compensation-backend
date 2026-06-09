package com.yiyundao.compensation.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.infrastructure.dao.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;


@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final JdbcTemplate jdbcTemplate;
    private final UserRoleService userRoleService;
    private final ExternalIdentityService externalIdentityService;

    @Override
    public SysUser findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).last("limit 1"));
    }

    @Override
    public SysUser findByPlatform(String provider, String subjectId) {
        Long userId = externalIdentityService.findBoundUserId(
                provider,
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                subjectId
        );
        return userId != null ? getById(userId) : null;
    }

    @Override
    public SysUser findByEmployeeId(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmployeeId, employeeId)
                .last("limit 1"));
    }

    @Override
    public SysUser findFirstByRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        // 使用 UserRoleService 从关联表查询
        return userRoleService.findFirstUserByRole(roleCode);
    }

    @Override
    public SysUser findFirstByRoleExcluding(String roleCode, Long excludedUserId) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        return userRoleService.findFirstUserByRoleExcluding(roleCode, excludedUserId);
    }

    @Override
    public void incrementPermissionVersion(Long userId) {
        if (userId == null) {
            return;
        }
        String sql = "UPDATE sys_user SET permission_version = COALESCE(permission_version, 0) + 1, update_time = ? WHERE id = ?";
        int rows = jdbcTemplate.update(sql, LocalDateTime.now(), userId);
        if (rows > 0) {
            log.debug("用户权限版本已递增: userId={}", userId);
        }
    }

    @Override
    public void batchIncrementPermissionVersion(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        // 使用 CASE WHEN 批量更新，性能优于逐条更新
        StringBuilder sql = new StringBuilder("UPDATE sys_user SET permission_version = COALESCE(permission_version, 0) + 1, update_time = ? WHERE id IN (");
        for (int i = 0; i < userIds.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
        }
        sql.append(")");

        Object[] params = new Object[userIds.size() + 1];
        params[0] = LocalDateTime.now();
        int idx = 1;
        for (Long userId : userIds) {
            params[idx++] = userId;
        }

        int rows = jdbcTemplate.update(sql.toString(), params);
        log.info("批量更新用户权限版本: count={}", rows);
    }
}
