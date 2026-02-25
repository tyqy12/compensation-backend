package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.SysRoleResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.rbac.entity.SysRoleResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserRole;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.yiyundao.compensation.security.SecurityAnnotations;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@SecurityAnnotations.IsAdmin
public class AdminUserAuthorizationController {

    private final SysUserResourceMapper userResourceMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleResourceMapper roleResourceMapper;
    private final ApprovalEngine approvalEngine;
    private final com.yiyundao.compensation.modules.user.service.SysUserService sysUserService;
    private final com.yiyundao.compensation.modules.rbac.service.ResourceCacheService resourceCacheService;

    // 用户已授权资源（个性化权限）
    @GetMapping("/users/{id}/resources")
    public ApiResponse<List<SysUserResource>> userResources(@PathVariable Long id) {
        List<SysUserResource> list = userResourceMapper.selectList(new LambdaQueryWrapper<SysUserResource>()
                .eq(SysUserResource::getUserId, id));
        return ApiResponse.success(list);
    }

    /**
     * 获取用户聚合权限（角色权限 + 个性化权限）
     * <p>
     * 合并用户的所有角色权限和个性化配置权限，去重后返回
     * 个性化权限可以覆盖角色权限中的 actions 配置
     */
    @GetMapping("/users/{id}/aggregate-resources")
    public ApiResponse<List<AggregateResourceItem>> userAggregateResources(@PathVariable Long id) {
        // 1. 查询用户的所有角色
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, id));

        Set<Long> roleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toSet());

        // 2. 查询所有角色的资源权限
        Map<Long, Set<String>> resourceActionsMap = new HashMap<>();

        if (!roleIds.isEmpty()) {
            List<SysRoleResource> roleResources = roleResourceMapper.selectList(
                    new LambdaQueryWrapper<SysRoleResource>()
                            .in(SysRoleResource::getRoleId, roleIds));

            for (SysRoleResource rr : roleResources) {
                Long resourceId = rr.getResourceId();
                Set<String> actions = parseActions(rr.getActionsJson());

                resourceActionsMap.computeIfAbsent(resourceId, k -> new HashSet<>())
                        .addAll(actions);
            }
        }

        // 3. 查询用户的个性化权限（可以覆盖角色权限）
        List<SysUserResource> userResources = userResourceMapper.selectList(
                new LambdaQueryWrapper<SysUserResource>()
                        .eq(SysUserResource::getUserId, id));

        for (SysUserResource ur : userResources) {
            Long resourceId = ur.getResourceId();
            Set<String> actions = parseActions(ur.getActionsJson());

            // 个性化权限覆盖角色权限
            resourceActionsMap.put(resourceId, actions);
        }

        // 4. 构建返回结果
        List<AggregateResourceItem> result = resourceActionsMap.entrySet().stream()
                .map(entry -> {
                    AggregateResourceItem item = new AggregateResourceItem();
                    item.setUserId(id);
                    item.setResourceId(entry.getKey());
                    item.setActions(new ArrayList<>(entry.getValue()));
                    item.setActionsJson(formatActionsJson(entry.getValue()));
                    return item;
                })
                .collect(Collectors.toList());

        return ApiResponse.success(result);
    }

    /**
     * 解析 actions JSON 字符串
     */
    private Set<String> parseActions(String actionsJson) {
        if (actionsJson == null || actionsJson.isBlank()) {
            return new HashSet<>();
        }
        try {
            // 尝试解析 JSON 数组
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return new HashSet<>(Arrays.asList(mapper.readValue(actionsJson, String[].class)));
        } catch (Exception e) {
            // 兼容旧格式：逗号分隔或 [*]
            String trimmed = actionsJson.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            return Arrays.stream(trimmed.split("[,\\s]+"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
    }

    /**
     * 格式化 actions 为 JSON 字符串
     */
    private String formatActionsJson(Set<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(new ArrayList<>(actions));
        } catch (Exception e) {
            return actions.toString();
        }
    }

    /**
     * 聚合权限项 VO
     */
    @Data
    public static class AggregateResourceItem {
        private Long userId;
        private Long resourceId;
        private List<String> actions;
        private String actionsJson;
    }

    // 用户授权：不直接生效，提交审批
    @PutMapping("/users/{id}/resources")
    public ApiResponse<Map<String, Object>> updateUserResources(@PathVariable Long id, @Valid @RequestBody UserGrantRequest req) {
        try {
            // 系统管理员直接应用，无需审批
            try {
                String uname = org.springframework.security.core.context.SecurityContextHolder.getContext() != null &&
                        org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null
                        ? org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName() : null;
                if (uname != null) {
                    com.yiyundao.compensation.modules.user.entity.SysUser op = sysUserService.findByUsername(uname);
                    if (op != null && op.getRoles() != null && op.getRoles().contains("ROLE_ADMIN")) {
                        userResourceMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.yiyundao.compensation.modules.rbac.entity.SysUserResource>()
                                .eq(com.yiyundao.compensation.modules.rbac.entity.SysUserResource::getUserId, id));
                        if (req.getResourceIds() != null) {
                            for (Long rid : req.getResourceIds()) {
                                com.yiyundao.compensation.modules.rbac.entity.SysUserResource ur = new com.yiyundao.compensation.modules.rbac.entity.SysUserResource();
                                ur.setUserId(id);
                                ur.setResourceId(rid);
                                if (req.getActions() != null) {
                                    java.util.List<String> act = req.getActions().get(rid);
                                    if (act != null && !act.isEmpty()) ur.setActionsJson(act.toString());
                                }
                                userResourceMapper.insert(ur);
                            }
                        }
                        // bump user permission version + evict cache
                        com.yiyundao.compensation.modules.user.entity.SysUser target = sysUserService.getById(id);
                        if (target != null) {
                            Integer v = (target.getPermissionVersion() == null) ? 0 : target.getPermissionVersion();
                            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.yiyundao.compensation.modules.user.entity.SysUser> uw = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
                            uw.eq(com.yiyundao.compensation.modules.user.entity.SysUser::getId, id)
                              .set(com.yiyundao.compensation.modules.user.entity.SysUser::getPermissionVersion, v + 1);
                            sysUserService.update(uw);
                            try { resourceCacheService.evictByUserId(id); } catch (Exception ignored) {}
                        }
                        java.util.Map<String,Object> resp = new java.util.HashMap<>();
                        resp.put("workflowId", null);
                        return ApiResponse.<java.util.Map<String, Object>>success("已直接生效(管理员)", resp);
                    }
                }
            } catch (Exception ignored) {}

            // 检查是增加权限还是减少权限
            List<SysUserResource> prev = userResourceMapper.selectList(new LambdaQueryWrapper<SysUserResource>()
                    .eq(SysUserResource::getUserId, id));

            // 获取之前的资源ID集合
            Set<Long> prevResourceIds = prev.stream()
                    .map(SysUserResource::getResourceId)
                    .collect(Collectors.toSet());

            // 获取当前的资源ID集合
            Set<Long> currResourceIds = req.getResourceIds() != null
                    ? new HashSet<>(req.getResourceIds())
                    : new HashSet<>();

            // 判断是否是纯减少权限（当前集合是之前集合的子集）
            boolean isOnlyReducing = prevResourceIds.containsAll(currResourceIds);

            // 如果是纯减少权限，直接生效无需审批
            if (isOnlyReducing) {
                userResourceMapper.delete(new LambdaQueryWrapper<SysUserResource>()
                        .eq(SysUserResource::getUserId, id));
                for (Long rid : currResourceIds) {
                    SysUserResource ur = new SysUserResource();
                    ur.setUserId(id);
                    ur.setResourceId(rid);
                    if (req.getActions() != null && req.getActions().get(rid) != null) {
                        List<String> act = req.getActions().get(rid);
                        if (!act.isEmpty()) {
                            try {
                                ur.setActionsJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(act));
                            } catch (Exception e) {
                                ur.setActionsJson(act.toString());
                            }
                        }
                    }
                    userResourceMapper.insert(ur);
                }
                // 清除缓存
                com.yiyundao.compensation.modules.user.entity.SysUser target = sysUserService.getById(id);
                if (target != null) {
                    Integer v = (target.getPermissionVersion() == null) ? 0 : target.getPermissionVersion();
                    com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.yiyundao.compensation.modules.user.entity.SysUser> uw = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
                    uw.eq(com.yiyundao.compensation.modules.user.entity.SysUser::getId, id)
                      .set(com.yiyundao.compensation.modules.user.entity.SysUser::getPermissionVersion, v + 1);
                    sysUserService.update(uw);
                    try { resourceCacheService.evictByUserId(id); } catch (Exception ignored) {}
                }
                Map<String, Object> resp = new HashMap<>();
                resp.put("workflowId", null);
                return ApiResponse.<Map<String, Object>>success("权限已取消", resp);
            }

            // 增加权限需要审批
            Map<String, Object> data = new HashMap<>();
            data.put("mode", "USER");
            data.put("userId", id);
            data.put("resourceIds", req.getResourceIds());
            data.put("actions", req.getActions());
            data.put("snapshotPrev", prev);
            Long wfId = approvalEngine.startWorkflow(
                    com.yiyundao.compensation.enums.WorkflowType.PERMISSION,
                    "USER-" + id,
                    "USER_RESOURCE_GRANT",
                    1L,
                    data
            );
            Map<String, Object> resp = new HashMap<>();
            resp.put("workflowId", wfId);
            return ApiResponse.<Map<String, Object>>success("已提交审批", resp);
        } catch (Exception e) {
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "提交审批失败: " + e.getMessage());
        }
    }

    @Data
    public static class UserGrantRequest {
        private List<Long> resourceIds;
        private Map<Long, List<String>> actions; // resourceId -> [actionCode]
    }
}
