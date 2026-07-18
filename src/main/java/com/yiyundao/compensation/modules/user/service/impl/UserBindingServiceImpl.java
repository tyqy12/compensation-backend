package com.yiyundao.compensation.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.infrastructure.dao.SysRoleMapper;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.dto.UserPlatformBindingResult;
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
    private final SysConfigService sysConfigService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<com.yiyundao.compensation.modules.approval.service.ApprovalEngine> approvalEngineProvider;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final ApprovalWorkflowMapper approvalWorkflowMapper;

    @Override
    @Transactional
    public UserPlatformBindingResult bindPlatform(Long userId, String provider, String subjectId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(subjectId)) {
            throw new IllegalArgumentException("平台类型与平台用户ID不能为空");
        }
        String normalizedProvider = normalize(provider);
        if (normalizedProvider == null) throw new IllegalArgumentException("不支持的平台类型");
        String normalizedSubjectId = subjectId.trim();

        // 1) 冲突：同一平台的该subjectId不可重复绑定用户（基于 external_identity）
        ExternalIdentity occupiedIdentity = externalIdentityService.findActiveIdentity(
                normalizedProvider,
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                normalizedSubjectId
        );
        Long occupiedUserId = occupiedIdentity != null ? occupiedIdentity.getUserId() : null;
        SysUser other = occupiedUserId != null && !occupiedUserId.equals(userId)
                ? sysUserService.getById(occupiedUserId)
                : null;
        if (other != null) {
            Long initiator = currentUserIdOrConfiguredAdmin();
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("userId", userId);
            data.put("employeeId", user.getEmployeeId());
            data.put("snapshotTargetUserEmployeeId", user.getEmployeeId());
            data.put("proposedProvider", normalizedProvider);
            data.put("proposedSubjectId", normalizedSubjectId);
            putOccupiedIdentitySnapshot(data, occupiedIdentity);
            data.put("snapshotUser", toJsonSafe(user));
            if (user.getEmployeeId() != null) {
                Employee e0 = employeeService.getById(user.getEmployeeId());
                if (e0 != null) data.put("snapshotEmployee", toJsonSafe(e0));
            }
            Long wfId = approvalEngineProvider.getObject().startWorkflow(
                    com.yiyundao.compensation.enums.WorkflowType.PLATFORM_BIND,
                    buildUserApprovalBusinessKey(userId),
                    "PLATFORM_LINK",
                    initiator,
                    data
            );
            return UserPlatformBindingResult.pendingApproval(wfId, "平台账号冲突，已发起审批");
        }

        // 2) 若存在对应员工（按平台账号匹配），建立关联
        Employee emp = employeeService.getByProviderAndSubjectId(normalizedProvider, normalizedSubjectId);
        if (emp != null) {
            assertUserCanBeLinkedToEmployee(user, emp.getId());
            // 冲突：该员工是否已绑定其他用户
            SysUser bound = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmployeeId, emp.getId())
                    .ne(SysUser::getId, userId)
                    .last("limit 1"));
            if (bound != null) {
                Long initiator = currentUserIdOrConfiguredAdmin();
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("userId", userId);
                data.put("employeeId", emp.getId());
                data.put("snapshotTargetUserEmployeeId", user.getEmployeeId());
                data.put("proposedProvider", normalizedProvider);
                data.put("proposedSubjectId", normalizedSubjectId);
                putOccupiedIdentitySnapshot(data, externalIdentityService.findActiveIdentity(
                        normalizedProvider,
                        ExternalIdentityService.DEFAULT_TENANT_KEY,
                        ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                        normalizedSubjectId
                ));
                data.put("snapshotBoundUserId", bound.getId());
                data.put("snapshotUser", toJsonSafe(user));
                data.put("snapshotEmployee", toJsonSafe(emp));
                Long wfId = approvalEngineProvider.getObject().startWorkflow(
                        com.yiyundao.compensation.enums.WorkflowType.PLATFORM_BIND,
                        buildUserApprovalBusinessKey(userId),
                        "PLATFORM_LINK",
                        initiator,
                        data
                );
                return UserPlatformBindingResult.pendingApproval(wfId, "员工关联冲突，已发起审批");
            }
            user.setEmployeeId(emp.getId());
        }

        // 3) 更新用户绑定关系并同步 external_identity
        if (!sysUserService.updateById(user)) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "用户员工关联已被其他操作修改，请刷新后重试");
        }
        syncIdentity(user, normalizedProvider, normalizedSubjectId, "manual");
        return UserPlatformBindingResult.success();
    }

    @Override
    @Transactional
    public void executeApprovedPlatformLink(Long workflowId, Long userId, Long employeeId, String provider, String subjectId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(subjectId)) {
            throw new IllegalArgumentException("平台类型与平台用户ID不能为空");
        }
        String normalizedProvider = normalize(provider);
        if (normalizedProvider == null) {
            throw new IllegalArgumentException("不支持的平台类型");
        }
        String normalizedSubjectId = subjectId.trim();

        assertApprovedPlatformLinkConflictStillMatches(
                workflowId, userId, employeeId, normalizedProvider, normalizedSubjectId);
        assertTargetUserEmployeeStillMatches(workflowId, user, employeeId);

        Employee employee = null;
        if (employeeId != null) {
            employee = employeeService.getById(employeeId);
            if (employee == null) {
                throw new IllegalArgumentException("员工不存在");
            }
        }

        ExternalIdentity occupiedIdentity = externalIdentityService.findActiveIdentity(
                normalizedProvider,
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                normalizedSubjectId
        );
        if (shouldReassignIdentity(occupiedIdentity, userId, employeeId)) {
            externalIdentityService.deactivatePlatformIdentity(
                    occupiedIdentity.getProvider(),
                    occupiedIdentity.getTenantKey(),
                    occupiedIdentity.getSubjectType(),
                    occupiedIdentity.getSubjectId(),
                    occupiedIdentity.getEmployeeId(),
                    occupiedIdentity.getUserId(),
                    "approval:" + workflowId
            );
        }

        if (employee != null) {
            assertEmployeeBindingStillMatches(workflowId, userId, employee.getId());
            unlinkOtherUsersFromEmployee(userId, employee.getId());
            user.setEmployeeId(employee.getId());
            boolean updated = sysUserService.update(new UpdateWrapper<SysUser>()
                    .eq("id", userId)
                    .set("employee_id", employee.getId()));
            if (!updated) {
                throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "用户员工关联已被其他操作修改，请刷新后重试");
            }
        }

        syncIdentity(user, normalizedProvider, normalizedSubjectId, "approval:" + workflowId);
    }

    @Override
    @Transactional
    public void bindEmployee(Long userId, Long employeeId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        Employee emp = employeeService.getById(employeeId);
        if (emp == null) throw new IllegalArgumentException("员工不存在");
        assertUserCanBeLinkedToEmployee(user, employeeId);

        // 冲突：该员工是否已绑定其他用户
        SysUser bound = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmployeeId, employeeId)
                .ne(SysUser::getId, userId)
                .last("limit 1"));
        if (bound != null) throw new IllegalStateException("该员工已绑定其他用户");

        user.setEmployeeId(employeeId);
        if (!sysUserService.updateById(user)) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "用户员工关联已被其他操作修改，请刷新后重试");
        }

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
            ExternalIdentity employeeIdentity = externalIdentityService.findPrimaryByEmployeeId(linkedEmployeeId);
            if (employeeIdentity != null
                    && (employeeIdentity.getUserId() == null || userId.equals(employeeIdentity.getUserId()))) {
                identity = employeeIdentity;
            }
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
        if (alsoUnlinkEmployee && linkedEmployeeId != null) {
            sysUserService.update(new UpdateWrapper<SysUser>()
                    .eq("id", userId)
                    .eq("employee_id", linkedEmployeeId)
                    .set("employee_id", null));
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
        if (employee.getId() == null) {
            throw new IllegalArgumentException("员工未持久化，无法创建或绑定系统账号");
        }
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

        if (user != null && employee.getId() != null) {
            assertUserCanBeLinkedToEmployee(user, employee.getId());
            assertEmployeeNotLinkedToOtherUser(user.getId(), employee.getId());
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
            if (!java.util.Objects.equals(user.getRealName(), employee.getName())) {
                user.setRealName(employee.getName());
                changed = true;
            }
            if (!java.util.Objects.equals(user.getPhone(), employee.getPhone())) {
                user.setPhone(employee.getPhone());
                changed = true;
            }
            if (!java.util.Objects.equals(user.getEmail(), employee.getEmail())) {
                user.setEmail(employee.getEmail());
                changed = true;
            }
            if (changed && !sysUserService.updateById(user)) {
                throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "系统用户资料已被其他操作修改，请刷新后重试");
            }
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

    private void assertUserCanBeLinkedToEmployee(SysUser user, Long employeeId) {
        if (user == null || employeeId == null) {
            return;
        }
        if (user.getEmployeeId() != null && !user.getEmployeeId().equals(employeeId)) {
            throw new IllegalStateException("外部身份已绑定其他员工，禁止自动改绑用户");
        }
    }

    private void assertTargetUserEmployeeStillMatches(Long workflowId, SysUser user, Long targetEmployeeId) {
        if (user == null) {
            return;
        }
        java.util.Map<String, Object> data = loadWorkflowData(workflowId);
        if (data.containsKey("snapshotTargetUserEmployeeId")) {
            Long snapshotTargetUserEmployeeId = toLong(data.get("snapshotTargetUserEmployeeId"));
            if (!java.util.Objects.equals(snapshotTargetUserEmployeeId, user.getEmployeeId())) {
                throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "目标用户员工关联已变更，请重新提交审批");
            }
        }
        if (targetEmployeeId != null && user.getEmployeeId() != null && !targetEmployeeId.equals(user.getEmployeeId())) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "目标用户员工关联已变更，请重新提交审批");
        }
    }

    private void assertEmployeeNotLinkedToOtherUser(Long userId, Long employeeId) {
        if (employeeId == null) {
            return;
        }
        SysUser bound = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmployeeId, employeeId)
                .ne(userId != null, SysUser::getId, userId)
                .last("limit 1"));
        if (bound != null) {
            throw new IllegalStateException("员工已绑定其他用户，禁止自动改绑员工");
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

    private Long currentUserIdOrConfiguredAdmin() {
        try {
            String name = SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName() : null;
            if (name != null) {
                SysUser u = sysUserService.findByUsername(name);
                if (u != null) return u.getId();
            }
        } catch (Exception ignored) {
        }
        return sysConfigService.getLong("system.admin_user_id", 1L);
    }

    private String toJsonSafe(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return null; }
    }

    private void putOccupiedIdentitySnapshot(java.util.Map<String, Object> data, ExternalIdentity identity) {
        if (data == null || identity == null) {
            return;
        }
        data.put("snapshotOccupiedEmployeeId", identity.getEmployeeId());
        data.put("snapshotOccupiedUserId", identity.getUserId());
        data.put("snapshotOccupiedProvider", identity.getProvider());
        data.put("snapshotOccupiedSubjectId", identity.getSubjectId());
    }

    private void assertApprovedPlatformLinkConflictStillMatches(Long workflowId, Long targetUserId, Long targetEmployeeId,
                                                                String provider, String subjectId) {
        java.util.Map<String, Object> data = loadWorkflowData(workflowId);
        Long snapshotEmployeeId = toLong(data.get("snapshotOccupiedEmployeeId"));
        Long snapshotUserId = toLong(data.get("snapshotOccupiedUserId"));
        String snapshotProvider = toTrimmedString(data.get("snapshotOccupiedProvider"));
        String snapshotSubjectId = toTrimmedString(data.get("snapshotOccupiedSubjectId"));
        if ((snapshotEmployeeId == null && snapshotUserId == null)
                || !StringUtils.hasText(snapshotProvider)
                || !StringUtils.hasText(snapshotSubjectId)) {
            return;
        }
        if (!provider.equals(normalize(snapshotProvider)) || !subjectId.equals(snapshotSubjectId)) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "平台绑定审批数据已过期，请重新提交审批");
        }

        ExternalIdentity currentIdentity = externalIdentityService.findActiveIdentity(
                provider,
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                subjectId
        );
        if (currentIdentity == null || belongsToTarget(currentIdentity, targetUserId, targetEmployeeId)) {
            return;
        }
        if (!java.util.Objects.equals(snapshotEmployeeId, currentIdentity.getEmployeeId())
                || !java.util.Objects.equals(snapshotUserId, currentIdentity.getUserId())) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "平台账号占用关系已变更，请重新提交审批");
        }
    }

    private void assertEmployeeBindingStillMatches(Long workflowId, Long targetUserId, Long employeeId) {
        java.util.Map<String, Object> data = loadWorkflowData(workflowId);
        Long snapshotBoundUserId = toLong(data.get("snapshotBoundUserId"));
        if (snapshotBoundUserId == null || employeeId == null) {
            return;
        }
        SysUser currentBound = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmployeeId, employeeId)
                .ne(SysUser::getId, targetUserId)
                .last("limit 1"));
        if (currentBound == null) {
            return;
        }
        if (!snapshotBoundUserId.equals(currentBound.getId())) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "员工绑定关系已变更，请重新提交审批");
        }
    }

    private boolean belongsToTarget(ExternalIdentity identity, Long targetUserId, Long targetEmployeeId) {
        if (identity == null) {
            return false;
        }
        boolean userMatches = targetUserId != null && targetUserId.equals(identity.getUserId());
        boolean employeeMatches = targetEmployeeId != null && targetEmployeeId.equals(identity.getEmployeeId());
        return userMatches || employeeMatches;
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> loadWorkflowData(Long workflowId) {
        if (workflowId == null || workflowId < 1) {
            return java.util.Map.of();
        }
        ApprovalWorkflow workflow = approvalWorkflowMapper.selectById(workflowId);
        if (workflow == null || !StringUtils.hasText(workflow.getWorkflowData())) {
            return java.util.Map.of();
        }
        try {
            return objectMapper.readValue(workflow.getWorkflowData(), java.util.Map.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解析审批数据失败");
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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

    private boolean shouldReassignIdentity(ExternalIdentity identity, Long userId, Long employeeId) {
        if (identity == null) {
            return false;
        }
        boolean userConflict = identity.getUserId() != null && userId != null && !identity.getUserId().equals(userId);
        boolean employeeConflict = identity.getEmployeeId() != null
                && employeeId != null
                && !identity.getEmployeeId().equals(employeeId);
        return userConflict || employeeConflict;
    }

    private void unlinkOtherUsersFromEmployee(Long userId, Long employeeId) {
        if (employeeId == null) {
            return;
        }
        sysUserService.update(new UpdateWrapper<SysUser>()
                .eq("employee_id", employeeId)
                .ne("id", userId)
                .set("employee_id", null));
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
                userRoleService.grantRole(
                        userId,
                        userRole.getId(),
                        sysConfigService.getLong("system.admin_user_id", 1L),
                        null,
                        "自动授予默认用户角色"
                );
            } else {
                log.warn("未找到 USER 角色，无法授予默认角色给用户: userId={}", userId);
            }
        } catch (Exception e) {
            log.error("授予默认用户角色失败: userId={}", userId, e);
        }
    }
}
