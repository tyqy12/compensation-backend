package com.yiyundao.compensation.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.infrastructure.dao.SysRoleMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.modules.user.service.UserBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.ObjectProvider;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserBindingServiceImpl implements UserBindingService {

    private final SysUserService sysUserService;
    private final EmployeeService employeeService;
    private final UserRoleService userRoleService;
    private final SysRoleMapper roleMapper;
    private final ExternalIdentityService externalIdentityService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<com.yiyundao.compensation.modules.approval.service.ApprovalEngine> approvalEngineProvider;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    @Transactional
    public void bindPlatform(Long userId, String provider, String subjectId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(subjectId)) {
            throw new IllegalArgumentException("平台类型与平台用户ID不能为空");
        }
        String normalizedProvider = normalize(provider);
        if (normalizedProvider == null) throw new IllegalArgumentException("不支持的平台类型");
        String normalizedSubjectId = subjectId.trim();

        // 1) 冲突：同一平台的该subjectId不可重复绑定用户（基于 external_identity）
        Long occupiedUserId = externalIdentityService.findBoundUserId(
                normalizedProvider,
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                normalizedSubjectId
        );
        SysUser other = occupiedUserId != null && !occupiedUserId.equals(userId)
                ? sysUserService.getById(occupiedUserId)
                : null;
        if (other != null) {
            Long initiator = currentUserIdOrAdmin();
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("userId", userId);
            data.put("employeeId", user.getEmployeeId());
            data.put("proposedProvider", normalizedProvider);
            data.put("proposedSubjectId", normalizedSubjectId);
            data.put("snapshotUser", toJsonSafe(user));
            if (user.getEmployeeId() != null) {
                Employee e0 = employeeService.getById(user.getEmployeeId());
                if (e0 != null) data.put("snapshotEmployee", toJsonSafe(e0));
            }
            Long wfId = approvalEngineProvider.getObject().startWorkflow(
                    com.yiyundao.compensation.enums.WorkflowType.OFFLINE,
                    buildUserApprovalBusinessKey(userId),
                    "PLATFORM_LINK",
                    initiator,
                    data
            );
            throw new IllegalStateException("平台账号冲突，已发起审批，workflowId=" + wfId);
        }

        // 2) 若存在对应员工（按平台账号匹配），建立关联
        Employee emp = employeeService.getByProviderAndSubjectId(normalizedProvider, normalizedSubjectId);
        if (emp != null) {
            // 冲突：该员工是否已绑定其他用户
            SysUser bound = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmployeeId, emp.getId())
                    .ne(SysUser::getId, userId)
                    .last("limit 1"));
            if (bound != null) {
                Long initiator = currentUserIdOrAdmin();
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("userId", userId);
                data.put("employeeId", emp.getId());
                data.put("proposedProvider", normalizedProvider);
                data.put("proposedSubjectId", normalizedSubjectId);
                data.put("snapshotUser", toJsonSafe(user));
                data.put("snapshotEmployee", toJsonSafe(emp));
                Long wfId = approvalEngineProvider.getObject().startWorkflow(
                        com.yiyundao.compensation.enums.WorkflowType.OFFLINE,
                        buildUserApprovalBusinessKey(userId),
                        "PLATFORM_LINK",
                        initiator,
                        data
                );
                throw new IllegalStateException("员工关联冲突，已发起审批，workflowId=" + wfId);
            }
            user.setEmployeeId(emp.getId());
        }

        // 3) 更新用户绑定关系并同步 external_identity
        sysUserService.updateById(user);
        syncIdentity(user, normalizedProvider, normalizedSubjectId, "manual");
    }

    @Override
    @Transactional
    public void bindEmployee(Long userId, Long employeeId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        Employee emp = employeeService.getById(employeeId);
        if (emp == null) throw new IllegalArgumentException("员工不存在");

        // 冲突：该员工是否已绑定其他用户
        SysUser bound = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmployeeId, employeeId)
                .ne(SysUser::getId, userId)
                .last("limit 1"));
        if (bound != null) throw new IllegalStateException("该员工已绑定其他用户");

        user.setEmployeeId(employeeId);
        sysUserService.updateById(user);

        ExternalIdentity identity = externalIdentityService.findPrimaryByUserId(userId);
        if (identity == null) {
            identity = externalIdentityService.findPrimaryByEmployeeId(employeeId);
        }
        if (identity != null && StringUtils.hasText(identity.getSubjectId())) {
            externalIdentityService.upsertPlatformIdentity(
                    identity.getProvider(),
                    identity.getTenantKey(),
                    identity.getSubjectType(),
                    identity.getSubjectId(),
                    employeeId,
                    userId,
                    "manual",
                    Boolean.TRUE.equals(identity.getPrimaryFlag())
            );
        } else if (StringUtils.hasText(emp.getProvider()) && StringUtils.hasText(emp.getSubjectId())) {
            syncIdentity(user, emp.getProvider(), emp.getSubjectId(), "manual");
        }
    }

    @Override
    @Transactional
    public void unbindPlatform(Long userId, boolean alsoUnlinkEmployee) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) return;
        Long linkedEmployeeId = user.getEmployeeId();
        ExternalIdentity identity = externalIdentityService.findPrimaryByUserId(userId);
        if (identity == null && linkedEmployeeId != null) {
            identity = externalIdentityService.findPrimaryByEmployeeId(linkedEmployeeId);
        }
        if (identity != null && StringUtils.hasText(identity.getSubjectId())) {
            externalIdentityService.deactivatePlatformIdentity(
                    identity.getProvider(),
                    identity.getTenantKey(),
                    identity.getSubjectType(),
                    identity.getSubjectId(),
                    linkedEmployeeId,
                    userId,
                    "manual"
            );
        }
    }

    private String normalize(String platform) {
        if (platform == null) return null;
        String p = platform.trim().toLowerCase();
        switch (p) {
            case "wechat":
            case "wecom":
            case "qywx":
            case "wx":
                return "wechat";
            case "dingtalk":
            case "dingding":
            case "dd":
                return "dingtalk";
            case "feishu":
            case "lark":
                return "feishu";
            default:
                return null;
        }
    }

    @Override
    @Transactional
    public void ensureUserForEmployee(Employee employee) {
        ensureUserForEmployee(employee, null);
    }

    @Override
    @Transactional
    public void ensureUserForEmployee(Employee employee, String preferredUsername) {
        if (employee == null) return;
        ExternalIdentity employeeIdentity = employee.getId() != null
                ? externalIdentityService.findPrimaryByEmployeeId(employee.getId())
                : null;
        String provider = StringUtils.hasText(employee.getProvider()) ? normalize(employee.getProvider()) : null;
        String subjectId = StringUtils.hasText(employee.getSubjectId()) ? employee.getSubjectId().trim() : null;

        SysUser user = null;
        if (employee.getId() != null) {
            user = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmployeeId, employee.getId())
                    .last("limit 1"));
        }
        if (user == null && employeeIdentity != null && employeeIdentity.getUserId() != null) {
            user = sysUserService.getById(employeeIdentity.getUserId());
        }
        if (user == null && StringUtils.hasText(provider) && StringUtils.hasText(subjectId)) {
            user = sysUserService.findByPlatform(provider, subjectId);
        }

        if (user == null) {
            // 创建账号
            user = new SysUser();
            String username = buildUsername(employee, preferredUsername);
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(randomInitPassword()));
            user.setRealName(employee.getName());
            user.setEmail(employee.getEmail());
            user.setPhone(employee.getPhone());
            user.setEmployeeId(employee.getId());
            sysUserService.save(user);

            // 授予默认角色（使用 UserRoleService）
            grantDefaultUserRole(user.getId());
        } else {
            // 回填关联关系
            boolean changed = false;
            if (employee.getId() != null && (user.getEmployeeId() == null || !user.getEmployeeId().equals(employee.getId()))) {
                user.setEmployeeId(employee.getId());
                changed = true;
            }
            if (changed) sysUserService.updateById(user);
        }

        if (employeeIdentity != null && StringUtils.hasText(employeeIdentity.getSubjectId())) {
            externalIdentityService.upsertPlatformIdentity(
                    employeeIdentity.getProvider(),
                    employeeIdentity.getTenantKey(),
                    employeeIdentity.getSubjectType(),
                    employeeIdentity.getSubjectId(),
                    employee.getId(),
                    user.getId(),
                    "sync",
                    Boolean.TRUE.equals(employeeIdentity.getPrimaryFlag())
            );
        } else if (StringUtils.hasText(provider) && StringUtils.hasText(subjectId)) {
            syncIdentity(user, provider, subjectId, "sync");
        }
    }

    private String buildUsername(Employee e, String preferred) {
        // If client provided a preferred username, sanitize and try to use it (ensure uniqueness)
        String base = null;
        if (StringUtils.hasText(preferred)) {
            base = preferred.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "");
        } else if (StringUtils.hasText(e.getName())) {
            // Generate by name pinyin
            String py = com.yiyundao.compensation.common.utils.PinyinUtils.toPinyinSlug(e.getName());
            if (py != null && !py.isBlank()) {
                base = py;
            }
        }

        // Employment type rule: part-time -> prefix wb
        String empType = e.getEmploymentType();
        boolean isPartTime = empType != null && empType.equalsIgnoreCase("part_time");
        if (base != null && !base.isBlank()) {
            if (isPartTime) base = "wb" + base; // 虚拟账号前缀
            return ensureUnique(base);
        }

        // Fallbacks when name/pinyin not available
        if (StringUtils.hasText(e.getEmployeeId())) return ensureUnique(e.getEmployeeId().toLowerCase());
        if (StringUtils.hasText(e.getPhone())) return ensureUnique(e.getPhone());
        if (StringUtils.hasText(e.getEmail())) return ensureUnique(e.getEmail().toLowerCase());
        ExternalIdentity identity = e.getId() != null ? externalIdentityService.findPrimaryByEmployeeId(e.getId()) : null;
        if (identity != null && StringUtils.hasText(identity.getSubjectId())) {
            String provider = StringUtils.hasText(identity.getProvider()) ? identity.getProvider() : "emp";
            return ensureUnique((provider + "_" + identity.getSubjectId()).toLowerCase());
        }
        if (StringUtils.hasText(e.getSubjectId())) {
            String provider = StringUtils.hasText(e.getProvider()) ? e.getProvider() : "emp";
            return ensureUnique((provider + "_" + e.getSubjectId()).toLowerCase());
        }
        return ensureUnique(("emp_" + (e.getId() != null ? e.getId() : System.currentTimeMillis())).toLowerCase());
    }

    private String ensureUnique(String base) {
        String candidate = base;
        int i = 1;
        while (sysUserService.findByUsername(candidate) != null) {
            candidate = base + i;
            i++;
        }
        return candidate;
    }

    private String randomInitPassword() {
        return "Init@" + Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE));
    }

    private String buildUserApprovalBusinessKey(Long userId) {
        return "USER-" + userId + "-" + System.currentTimeMillis();
    }

    private Long currentUserIdOrAdmin() {
        try {
            String name = SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName() : null;
            if (name != null) {
                SysUser u = sysUserService.findByUsername(name);
                if (u != null) return u.getId();
            }
        } catch (Exception ignored) {}
        return 1L; // 默认管理员
    }

    private String toJsonSafe(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return null; }
    }

    private void syncIdentity(SysUser user, String provider, String subjectId, String source) {
        if (user == null || !StringUtils.hasText(provider) || !StringUtils.hasText(subjectId)) {
            return;
        }
        externalIdentityService.upsertPlatformIdentity(
                provider,
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                subjectId,
                user.getEmployeeId(),
                user.getId(),
                source,
                true
        );
    }

    /**
     * 授予用户默认角色
     */
    private void grantDefaultUserRole(Long userId) {
        try {
            // 使用 RoleMapper 查找 USER 角色
            com.yiyundao.compensation.modules.rbac.entity.SysRole userRole = roleMapper.selectOne(
                    new LambdaQueryWrapper<com.yiyundao.compensation.modules.rbac.entity.SysRole>()
                            .eq(com.yiyundao.compensation.modules.rbac.entity.SysRole::getCode, "USER")
                            .eq(com.yiyundao.compensation.modules.rbac.entity.SysRole::getStatus,
                                com.yiyundao.compensation.modules.rbac.entity.SysRole.Status.ENABLED.getCode())
                            .last("limit 1")
            );
            if (userRole != null) {
                userRoleService.grantRole(userId, userRole.getId(), 1L, null, "自动授予默认用户角色");
            } else {
                log.warn("未找到 USER 角色，无法授予默认角色给用户: userId={}", userId);
            }
        } catch (Exception e) {
            log.error("授予默认用户角色失败: userId={}", userId, e);
        }
    }
}
