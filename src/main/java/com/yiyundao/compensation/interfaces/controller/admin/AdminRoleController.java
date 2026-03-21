package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.infrastructure.dao.SysRoleMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.entity.SysRole;
import com.yiyundao.compensation.modules.rbac.entity.SysUserRole;
import com.yiyundao.compensation.modules.rbac.service.ResourceService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@SecurityAnnotations.IsAdmin
public class AdminRoleController {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final ResourceService resourceService;
    private final UserRoleService userRoleService;
    private final SysUserService sysUserService;
    private final EmployeeService employeeService;
    private final ExternalIdentityService externalIdentityService;

    // 用户拥有角色列表
    @GetMapping("/users/{id}/roles")
    public ApiResponse<List<Long>> getUserRoleIds(@PathVariable Long id) {
        List<SysUserRole> list = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, id));
        List<Long> roleIds = list.stream().map(SysUserRole::getRoleId).toList();
        return ApiResponse.success(roleIds);
    }

    // 设置用户角色（覆盖式）
    @PutMapping("/users/{id}/roles")
    public ApiResponse<String> setUserRoles(@PathVariable Long id, @RequestBody SetUserRolesRequest req) {
        // 获取当前操作人ID
        Long operatorId = getCurrentUserId();

        // 1. 先撤销用户所有现有角色
        userRoleService.revokeAllRoles(id, operatorId);

        // 2. 授予新角色（使用 UserRoleService，带审计信息）
        List<Long> roleIds = req.getRoleIds();
        if (roleIds != null && !roleIds.isEmpty()) {
            userRoleService.grantRoles(id, roleIds, operatorId);
        }

        // 3. 递增 permission_version 使缓存失效
        sysUserService.incrementPermissionVersion(id);

        return ApiResponse.success("OK");
    }

    /**
     * 获取当前登录用户ID
     */
    private Long getCurrentUserId() {
        try {
            String username = SecurityContextHolder.getContext() != null
                    && SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName() : null;
            if (username != null) {
                SysUser user = sysUserService.findByUsername(username);
                if (user != null) return user.getId();
            }
        } catch (Exception ignored) {}
        return 1L; // 默认管理员
    }
    // 用户有效权限资源列表（合并角色授权与个性授权）
    @GetMapping("/users/{id}/effective-resources")
    public ApiResponse<List<SysResource>> getUserEffectiveResources(@PathVariable Long id) {
        return ApiResponse.success(resourceService.getUserResources(id));
    }

    // 用户聚合搜索：支持用户名/真实姓名/邮箱/手机号 + 员工姓名/工号 关联
    @GetMapping("/users/search")
    public ApiResponse<java.util.Map<String,Object>> userAggregateSearch(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        String kw = (q == null) ? "" : q.trim();
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 100);

        // 使用单条分页查询（包含子查询命中 employee）
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.yiyundao.compensation.modules.user.entity.SysUser> qw =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (!kw.isBlank()) {
            qw.and(w -> w.like(com.yiyundao.compensation.modules.user.entity.SysUser::getUsername, kw)
                         .or().like(com.yiyundao.compensation.modules.user.entity.SysUser::getRealName, kw)
                         .or().like(com.yiyundao.compensation.modules.user.entity.SysUser::getEmail, kw)
                         .or().like(com.yiyundao.compensation.modules.user.entity.SysUser::getPhone, kw)
                         .or().inSql(com.yiyundao.compensation.modules.user.entity.SysUser::getEmployeeId,
                                 "SELECT id FROM employee WHERE deleted=0 AND (name LIKE '%" + kw.replace("'","''") + "%'"
                                 + " OR employee_id LIKE '%" + kw.replace("'","''") + "%'"
                                 + " OR phone LIKE '%" + kw.replace("'","''") + "%'"
                                 + " OR email LIKE '%" + kw.replace("'","''") + "%')"));
        }
        qw.orderByAsc(com.yiyundao.compensation.modules.user.entity.SysUser::getCreateTime);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.yiyundao.compensation.modules.user.entity.SysUser> pg =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(p, s);
        var result = sysUserService.page(pg, qw);

        java.util.List<com.yiyundao.compensation.modules.user.entity.SysUser> users = result.getRecords();
        java.util.Map<Long, com.yiyundao.compensation.modules.employee.entity.Employee> empMap = new java.util.HashMap<>();
        java.util.Set<Long> eids = new java.util.HashSet<>();
        for (var u : users) if (u.getEmployeeId() != null) eids.add(u.getEmployeeId());
        if (!eids.isEmpty()) {
            java.util.List<com.yiyundao.compensation.modules.employee.entity.Employee> es = employeeService.listByIds(eids);
            for (var e : es) empMap.put(e.getId(), e);
        }

        // 批量查询用户角色关联（优化性能）
        java.util.Set<Long> userIds = users.stream().map(com.yiyundao.compensation.modules.user.entity.SysUser::getId).collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, ExternalIdentity> userIdentityMap = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            java.util.List<ExternalIdentity> identities = externalIdentityService.list(new LambdaQueryWrapper<ExternalIdentity>()
                    .select(
                            ExternalIdentity::getId,
                            ExternalIdentity::getUserId,
                            ExternalIdentity::getProvider,
                            ExternalIdentity::getSubjectId,
                            ExternalIdentity::getLastSeenAt
                    )
                    .in(ExternalIdentity::getUserId, userIds)
                    .eq(ExternalIdentity::getStatus, ExternalIdentityService.STATUS_ACTIVE)
                    .orderByDesc(ExternalIdentity::getPrimaryFlag)
                    .orderByDesc(ExternalIdentity::getLastSeenAt)
                    .orderByDesc(ExternalIdentity::getId));
            for (ExternalIdentity identity : identities) {
                if (identity.getUserId() != null) {
                    userIdentityMap.putIfAbsent(identity.getUserId(), identity);
                }
            }
        }
        java.util.Map<Long, java.util.List<Long>> userRoleIdsMap = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            java.util.List<SysUserRole> userRoles = userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds));
            for (SysUserRole ur : userRoles) {
                userRoleIdsMap.computeIfAbsent(ur.getUserId(), k -> new java.util.ArrayList<>()).add(ur.getRoleId());
            }
        }

        // 批量查询角色信息
        java.util.Set<Long> allRoleIds = new java.util.HashSet<>();
        for (java.util.List<Long> rids : userRoleIdsMap.values()) {
            allRoleIds.addAll(rids);
        }
        java.util.Map<Long, SysRole> roleMap = new java.util.HashMap<>();
        if (!allRoleIds.isEmpty()) {
            java.util.List<SysRole> roles = roleMapper.selectBatchIds(allRoleIds);
            for (SysRole r : roles) roleMap.put(r.getId(), r);
        }

        java.util.List<com.yiyundao.compensation.interfaces.dto.admin.UserAggregateDto> records = new java.util.ArrayList<>();
        for (var u : users) {
            var dto = new com.yiyundao.compensation.interfaces.dto.admin.UserAggregateDto();
            dto.setUserId(u.getId()); dto.setUsername(u.getUsername()); dto.setRealName(u.getRealName());
            dto.setEmail(u.getEmail()); dto.setPhone(u.getPhone());
            ExternalIdentity identity = userIdentityMap.get(u.getId());
            if (identity != null) {
                dto.setProvider(identity.getProvider());
                dto.setSubjectId(identity.getSubjectId());
            }

            // 从关联表查询角色编码（确保数据一致性）
            java.util.List<Long> roleIdList = userRoleIdsMap.getOrDefault(u.getId(), java.util.List.of());
            String roleCodes = roleIdList.isEmpty() ? "" :
                    roleIdList.stream()
                            .map(rid -> roleMap.get(rid))
                            .filter(Objects::nonNull)
                            .map(SysRole::getCode)
                            .sorted()
                            .reduce((a, b) -> a + "," + b)
                            .orElse("");
            dto.setRoles(roleCodes);

            if (u.getEmployeeId() != null) {
                var e = empMap.get(u.getEmployeeId());
                if (e != null) { dto.setEmployeeId(e.getId()); dto.setEmployeeNo(e.getEmployeeId()); dto.setEmployeeName(e.getName()); }
            }
            records.add(dto);
        }
        java.util.Map<String,Object> resp = new java.util.HashMap<>();
        resp.put("records", records);
        resp.put("total", result.getTotal());
        resp.put("current", result.getCurrent());
        resp.put("size", result.getSize());
        return ApiResponse.success(resp);
    }

    // 用户有效权限详情（含actions）
    @GetMapping("/users/{id}/effective-resource-details")
    public ApiResponse<List<com.yiyundao.compensation.interfaces.dto.admin.ResourceDetailDto>> userEffectiveResourceDetails(@PathVariable Long id) {
        List<SysResource> list = resourceService.getUserResources(id);
        java.util.Map<Long, java.util.List<String>> actions = resourceService.getUserActions(id);
        List<com.yiyundao.compensation.interfaces.dto.admin.ResourceDetailDto> out = new java.util.ArrayList<>();
        for (SysResource r : list) {
            com.yiyundao.compensation.interfaces.dto.admin.ResourceDetailDto dto = new com.yiyundao.compensation.interfaces.dto.admin.ResourceDetailDto();
            dto.setId(r.getId()); dto.setType(r.getType()); dto.setCode(r.getCode());
            dto.setName(r.getName()); dto.setPath(r.getPath()); dto.setComponent(r.getComponent());
            dto.setIcon(r.getIcon()); dto.setParentId(r.getParentId()); dto.setOrderNum(r.getOrderNum()); dto.setStatus(r.getStatus());
            dto.setActions(actions.getOrDefault(r.getId(), java.util.List.of()));
            out.add(dto);
        }
        out.sort(java.util.Comparator.comparing(d -> d.getOrderNum() == null ? 0 : d.getOrderNum()));
        return ApiResponse.success(out);
    }

    @Data
    public static class SetUserRolesRequest {
        private List<Long> roleIds;
    }
}
