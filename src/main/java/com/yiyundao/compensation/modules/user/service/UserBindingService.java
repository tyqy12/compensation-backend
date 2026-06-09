package com.yiyundao.compensation.modules.user.service;

import com.yiyundao.compensation.modules.user.dto.UserPlatformBindingResult;

public interface UserBindingService {

    UserPlatformBindingResult bindPlatform(Long userId, String provider, String subjectId);

    void executeApprovedPlatformLink(Long workflowId, Long userId, Long employeeId, String provider, String subjectId);

    void bindEmployee(Long userId, Long employeeId);

    void unbindPlatform(Long userId, boolean alsoUnlinkEmployee);

    /**
     * 确保为指定员工创建并关联后台用户账号（若不存在），并保持平台账号与员工一致。
     * 允许幂等调用。
     */
    void ensureUserForEmployee(com.yiyundao.compensation.modules.employee.entity.Employee employee);
    /**
     * Overload allowing caller to propose a preferred username.
     */
    void ensureUserForEmployee(com.yiyundao.compensation.modules.employee.entity.Employee employee, String preferredUsername);
}
