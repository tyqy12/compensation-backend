package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.infrastructure.dao.SysRoleResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.interfaces.dto.admin.UserResourceResponseDto;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.rbac.entity.SysRoleResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserRole;
import com.yiyundao.compensation.modules.rbac.service.UserResourceService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.security.SecurityAnnotations;
import com.yiyundao.compensation.security.SecurityConstants;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminUserAuthorizationController {

    private static final String BUSINESS_TYPE_USER_RESOURCE_GRANT = "USER_RESOURCE_GRANT";

    private final SysUserResourceMapper userResourceMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleResourceMapper roleResourceMapper;
    private final ApprovalWorkflowMapper approvalWorkflowMapper;
    private final ApprovalEngine approvalEngine;
    private final com.yiyundao.compensation.modules.user.service.SysUserService sysUserService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final UserRoleService userRoleService;
    private final UserResourceService userResourceService;

    // 用户已授权资源（个性化权限）
    @GetMapping("/users/{id}/resources")
    @SecurityAnnotations.IsAdmin
    public ApiResponse<List<UserResourceResponseDto>> userResources(@PathVariable Long id) {
        List<SysUserResource> list = userResourceMapper.selectList(new LambdaQueryWrapper<SysUserResource>()
                .eq(SysUserResource::getUserId, id));
        return ApiResponse.success(list.stream()
                .map(resource -> UserResourceResponseDto.from(resource, objectMapper))
                .toList());
    }

    /**
     * 获取用户聚合权限（角色权限 + 个性化权限）
     * <p>
     * 合并用户的所有角色权限和个性化配置权限，去重后返回
     * 个性化权限可以覆盖角色权限中的 actions 配置
     */
    @GetMapping("/users/{id}/aggregate-resources")
    @SecurityAnnotations.IsAdmin
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
    @SecurityAnnotations.IsManagerOrAdmin
    public ApiResponse<Map<String, Object>> updateUserResources(@PathVariable Long id, @Valid @RequestBody UserGrantRequest req) {
        try {
            if (req == null) {
                return ApiResponse.error(ErrorCode.PARAM_MISSING, "用户授权请求不能为空");
            }

            List<Long> requestedResourceIds = normalizeResourceIds(req.getResourceIds());

            // 系统管理员直接应用，无需审批
            String uname = currentUsername();
            if (uname != null) {
                SysUser op = sysUserService.findByUsername(uname);
                if (op != null && op.getId() != null && userRoleService.hasRole(op.getId(), SecurityConstants.ROLE_ADMIN)) {
                    userResourceService.assignResources(id, requestedResourceIds, req.getActions(), op.getId());
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("workflowId", null);
                    return ApiResponse.<Map<String, Object>>success("已直接生效(管理员)", resp);
                }
            }

            // 检查是增加权限还是减少权限
            List<SysUserResource> prev = userResourceMapper.selectList(new LambdaQueryWrapper<SysUserResource>()
                    .eq(SysUserResource::getUserId, id));

            // 获取当前的资源ID集合
            Set<Long> currResourceIds = new HashSet<>(requestedResourceIds);

            // 判断是否是纯减少权限：资源不能新增，同一资源的 action 也不能扩权。
            boolean isOnlyReducing = isOnlyReducingUserResources(prev, currResourceIds, req.getActions());

            // 如果是纯减少权限，直接生效无需审批
            if (isOnlyReducing) {
                userResourceService.assignResources(id, requestedResourceIds, req.getActions(), requireCurrentUserId());
                Map<String, Object> resp = new HashMap<>();
                resp.put("workflowId", null);
                return ApiResponse.<Map<String, Object>>success("权限已取消", resp);
            }

            // 增加权限需要审批
            if (hasPendingUserResourceGrant(id)) {
                return ApiResponse.error(ErrorCode.REQUEST_CONFLICT, "已有待审批的用户资源授权申请，请等待处理完成后再提交");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("mode", "USER");
            data.put("userId", id);
            data.put("resourceIds", requestedResourceIds);
            data.put("actions", req.getActions());
            data.put("snapshotPrev", prev);
            Long wfId = approvalEngine.startWorkflow(
                    com.yiyundao.compensation.enums.WorkflowType.PERMISSION,
                    buildUserResourceGrantBusinessKey(id),
                    BUSINESS_TYPE_USER_RESOURCE_GRANT,
                    requireCurrentUserId(),
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

    private List<Long> normalizeResourceIds(List<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return List.of();
        }
        return resourceIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new
                ));
    }

    private boolean isOnlyReducingUserResources(List<SysUserResource> previous,
                                                Set<Long> currentResourceIds,
                                                Map<Long, List<String>> requestedActions) {
        Map<Long, Set<String>> previousActions = previous.stream()
                .collect(Collectors.toMap(
                        SysUserResource::getResourceId,
                        resource -> parseActions(resource.getActionsJson()),
                        (left, right) -> {
                            Set<String> merged = new HashSet<>(left);
                            merged.addAll(right);
                            return merged;
                        }
                ));

        if (!previousActions.keySet().containsAll(currentResourceIds)) {
            return false;
        }

        for (Long resourceId : currentResourceIds) {
            Set<String> prevActions = previousActions.getOrDefault(resourceId, Set.of());
            Set<String> currActions = requestedActions == null
                    ? Set.of()
                    : new HashSet<>(requestedActions.getOrDefault(resourceId, List.of()));
            if (!actionSubset(prevActions, currActions)) {
                return false;
            }
        }
        return true;
    }

    private boolean actionSubset(Set<String> previousActions, Set<String> currentActions) {
        if (previousActions == null || previousActions.isEmpty()) {
            return true;
        }
        if (previousActions.contains("*")) {
            return true;
        }
        if (currentActions == null || currentActions.isEmpty()) {
            return false;
        }
        return previousActions.containsAll(currentActions);
    }

    private boolean hasPendingUserResourceGrant(Long userId) {
        String baseBusinessKey = baseUserResourceGrantBusinessKey(userId);
        Long count = approvalWorkflowMapper.selectCount(new LambdaQueryWrapper<ApprovalWorkflow>()
                .eq(ApprovalWorkflow::getBusinessType, BUSINESS_TYPE_USER_RESOURCE_GRANT)
                .and(w -> w
                        .eq(ApprovalWorkflow::getBusinessKey, baseBusinessKey)
                        .or()
                        .likeRight(ApprovalWorkflow::getBusinessKey, baseBusinessKey + "-"))
                .eq(ApprovalWorkflow::getStatus, ApprovalStatus.PENDING));
        return count != null && count > 0;
    }

    private String buildUserResourceGrantBusinessKey(Long userId) {
        return baseUserResourceGrantBusinessKey(userId) + "-" + System.currentTimeMillis();
    }

    private String baseUserResourceGrantBusinessKey(Long userId) {
        return "USER-" + userId;
    }

    private Long requireCurrentUserId() {
        try {
            String username = currentUsername();
            if (username != null) {
                SysUser user = sysUserService.findByUsername(username);
                if (user != null && user.getId() != null) {
                    return user.getId();
                }
            }
        } catch (Exception ignored) {
        }
        throw new IllegalStateException("未识别到当前登录用户");
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext() != null
                && SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
    }
}
