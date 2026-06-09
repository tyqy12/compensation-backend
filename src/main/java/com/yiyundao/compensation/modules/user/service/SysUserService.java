package com.yiyundao.compensation.modules.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.user.entity.SysUser;

import java.util.Set;

public interface SysUserService extends IService<SysUser> {
    SysUser findByUsername(String username);
    SysUser findByPlatform(String provider, String subjectId);
    SysUser findByEmployeeId(Long employeeId);
    SysUser findFirstByRole(String roleCode);
    SysUser findFirstByRoleExcluding(String roleCode, Long excludedUserId);

    /**
     * 递增用户权限版本号
     * <p>
     * 用于清除用户权限缓存
     * </p>
     *
     * @param userId 用户ID
     */
    void incrementPermissionVersion(Long userId);

    /**
     * 批量递增用户权限版本号
     * <p>
     * 性能优化：使用单条 SQL 批量更新
     * </p>
     *
     * @param userIds 用户ID集合
     */
    void batchIncrementPermissionVersion(Set<Long> userIds);
}
