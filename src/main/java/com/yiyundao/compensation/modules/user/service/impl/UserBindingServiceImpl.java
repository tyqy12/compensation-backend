package com.yiyundao.compensation.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.infrastructure.dao.SysRoleMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
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
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<com.yiyundao.compensation.modules.approval.service.ApprovalEngine> approvalEngineProvider;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    @Transactional
    public void bindPlatform(Long userId, String platformType, String platformUserId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        if (!StringUtils.hasText(platformType) || !StringUtils.hasText(platformUserId)) {
            throw new IllegalArgumentException("平台类型与平台用户ID不能为空");
        }
        String pt = normalize(platformType);
        if (pt == null) throw new IllegalArgumentException("不支持的平台类型");

        // 1) 冲突：同一平台的该platformUserId不可重复绑定用户
        SysUser other = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPlatformType, pt)
                .eq(SysUser::getPlatformUserId, platformUserId)
                .ne(SysUser::getId, userId)
                .last("limit 1"));
        if (other != null) {
            Long initiator = currentUserIdOrAdmin();
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("userId", userId);
            data.put("employeeId", user.getEmployeeId());
            data.put("proposedPlatformType", pt);
            data.put("proposedPlatformUserId", platformUserId);
            data.put("snapshotUser", toJsonSafe(user));
            if (user.getEmployeeId() != null) {
                Employee e0 = employeeService.getById(user.getEmployeeId());
                if (e0 != null) data.put("snapshotEmployee", toJsonSafe(e0));
            }
            Long wfId = approvalEngineProvider.getObject().startWorkflow(
                    com.yiyundao.compensation.enums.WorkflowType.OFFLINE,
                    "USER-" + userId,
                    "PLATFORM_LINK",
                    initiator,
                    data
            );
            throw new IllegalStateException("平台账号冲突，已发起审批，workflowId=" + wfId);
        }

        // 2) 若存在对应员工（按平台账号匹配），建立关联
        Employee emp = employeeService.getOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getPlatformType, pt)
                .eq(Employee::getPlatformUserId, platformUserId)
                .last("limit 1"));
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
                data.put("proposedPlatformType", pt);
                data.put("proposedPlatformUserId", platformUserId);
                data.put("snapshotUser", toJsonSafe(user));
                data.put("snapshotEmployee", toJsonSafe(emp));
                Long wfId = approvalEngineProvider.getObject().startWorkflow(
                        com.yiyundao.compensation.enums.WorkflowType.OFFLINE,
                        "USER-" + userId,
                        "PLATFORM_LINK",
                        initiator,
                        data
                );
                throw new IllegalStateException("员工关联冲突，已发起审批，workflowId=" + wfId);
            }
            user.setEmployeeId(emp.getId());
            // 补齐员工平台信息（若为空）
            if (!StringUtils.hasText(emp.getPlatformType())) emp.setPlatformType(pt);
            if (!StringUtils.hasText(emp.getPlatformUserId())) emp.setPlatformUserId(platformUserId);
            employeeService.updateById(emp);
        } else if (user.getEmployeeId() != null) {
            // 若用户已有关联员工，但员工未设置平台账号，则补齐
            Employee e = employeeService.getById(user.getEmployeeId());
            if (e != null) {
                if (!StringUtils.hasText(e.getPlatformType())) e.setPlatformType(pt);
                if (!StringUtils.hasText(e.getPlatformUserId())) e.setPlatformUserId(platformUserId);
                employeeService.updateById(e);
            }
        }

        // 3) 更新用户平台绑定
        user.setPlatformType(pt);
        user.setPlatformUserId(platformUserId);
        sysUserService.updateById(user);
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

        // 双向补齐平台账号（谁有就用谁的）
        if (StringUtils.hasText(user.getPlatformType()) && StringUtils.hasText(user.getPlatformUserId())) {
            if (!StringUtils.hasText(emp.getPlatformType())) emp.setPlatformType(user.getPlatformType());
            if (!StringUtils.hasText(emp.getPlatformUserId())) emp.setPlatformUserId(user.getPlatformUserId());
            employeeService.updateById(emp);
        } else if (StringUtils.hasText(emp.getPlatformType()) && StringUtils.hasText(emp.getPlatformUserId())) {
            user.setPlatformType(emp.getPlatformType());
            user.setPlatformUserId(emp.getPlatformUserId());
        }

        sysUserService.updateById(user);
    }

    @Override
    @Transactional
    public void unbindPlatform(Long userId, boolean alsoUnlinkEmployee) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) return;
        String pt = user.getPlatformType();
        String puid = user.getPlatformUserId();
        user.setPlatformType(null);
        user.setPlatformUserId(null);
        sysUserService.updateById(user);

        if (alsoUnlinkEmployee && user.getEmployeeId() != null) {
            Employee emp = employeeService.getById(user.getEmployeeId());
            if (emp != null && eq(pt, emp.getPlatformType()) && eq(puid, emp.getPlatformUserId())) {
                emp.setPlatformType(null);
                emp.setPlatformUserId(null);
                employeeService.updateById(emp);
            }
        }
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
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
        // 若已有绑定用户，确保平台字段一致
        SysUser user = null;
        if (employee.getId() != null) {
            user = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmployeeId, employee.getId())
                    .last("limit 1"));
        }
        if (user == null && StringUtils.hasText(employee.getPlatformType()) && StringUtils.hasText(employee.getPlatformUserId())) {
            user = sysUserService.findByPlatform(employee.getPlatformType(), employee.getPlatformUserId());
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
            user.setPlatformType(employee.getPlatformType());
            user.setPlatformUserId(employee.getPlatformUserId());
            sysUserService.save(user);

            // 授予默认角色（使用 UserRoleService）
            grantDefaultUserRole(user.getId());
        } else {
            // 回填关联与平台字段
            boolean changed = false;
            if (employee.getId() != null && (user.getEmployeeId() == null || !user.getEmployeeId().equals(employee.getId()))) {
                user.setEmployeeId(employee.getId());
                changed = true;
            }
            if (StringUtils.hasText(employee.getPlatformType()) && !StringUtils.hasText(user.getPlatformType())) {
                user.setPlatformType(employee.getPlatformType());
                changed = true;
            }
            if (StringUtils.hasText(employee.getPlatformUserId()) && !StringUtils.hasText(user.getPlatformUserId())) {
                user.setPlatformUserId(employee.getPlatformUserId());
                changed = true;
            }
            if (changed) sysUserService.updateById(user);
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
        if (StringUtils.hasText(e.getPlatformUserId()))
            return ensureUnique(((e.getPlatformType() != null ? e.getPlatformType() : "emp") + "_" + e.getPlatformUserId()).toLowerCase());
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
