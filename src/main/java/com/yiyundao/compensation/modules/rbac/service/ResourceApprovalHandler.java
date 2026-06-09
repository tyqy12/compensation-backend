package com.yiyundao.compensation.modules.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 资源授权审批处理器
 * <p>
 * 监听审批完成事件，处理审批通过后的资源授权应用。
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceApprovalHandler {

    private final RoleService roleService;
    private final UserResourceService userResourceService;
    private final ObjectMapper objectMapper;
    private final SysConfigService sysConfigService;

    /**
     * 监听审批完成事件
     * <p>
     * 处理资源授权审批，审批通过后应用授权配置。
     * 使用同步事件监听，确保授权应用失败时审批事务回滚，避免审批已通过但权限未生效。
     * </p>
     *
     * @param event 审批完成事件
     */
    @EventListener
    @SuppressWarnings("unchecked")
    public void onApprovalCompleted(ApprovalCompletedEvent event) {
        ApprovalWorkflow workflow = event.getWorkflow();
        ApprovalStatus status = event.getFinalStatus();

        if (workflow == null) {
            log.warn("ResourceApprovalHandler: workflow is null, skip processing");
            return;
        }

        boolean isRoleGrant = "RESOURCE_GRANT".equalsIgnoreCase(workflow.getBusinessType());
        boolean isUserGrant = "USER_RESOURCE_GRANT".equalsIgnoreCase(workflow.getBusinessType());

        if (!isRoleGrant && !isUserGrant) return;

        if (status != ApprovalStatus.APPROVED) {
            log.info("资源授权审批未通过，workflowId={}", workflow.getId());
            return;
        }

        try {
            Map<String, Object> data = objectMapper.readValue(workflow.getWorkflowData(), Map.class);
            String mode = String.valueOf(data.get("mode"));

            if (isRoleGrant && "ROLE".equalsIgnoreCase(mode)) {
                handleRoleResourceGrant(data);
            } else if (isUserGrant && "USER".equalsIgnoreCase(mode)) {
                handleUserResourceGrant(data);
            } else {
                throw new IllegalArgumentException("资源授权审批数据缺少有效授权模式");
            }

            log.info("资源授权审批处理成功: workflowId={}", workflow.getId());
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 业务异常向上抛出，触发事务回滚
            log.error("资源授权审批回调失败（业务异常）: workflowId={}", workflow.getId(), e);
            throw e;
        } catch (Exception e) {
            // 其他异常也向上抛出
            log.error("资源授权审批回调失败（系统异常）: workflowId={}", workflow.getId(), e);
            throw new RuntimeException("资源授权审批处理失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRoleResourceGrant(Map<String, Object> data) {
        Long roleId = toLong(data.get("roleId"));
        List<Long> rids = toLongList(data.get("resourceIds"));
        Map<String, List<String>> actions = (Map<String, List<String>>) data.get("actions");

        if (roleId == null) {
            throw new IllegalArgumentException("角色资源授权审批数据缺失 roleId");
        }

        // 构建分配请求，调用 RoleService 的方法
        if (rids.isEmpty()) {
            throw new IllegalArgumentException("角色资源授权审批数据缺少 resourceIds");
        }
        // 构建 RoleResourceAssignRequest
        List<com.yiyundao.compensation.interfaces.dto.role.RoleResourceAssignRequest.ResourceAssignment> assignments =
                rids.stream().map(rid -> {
                    com.yiyundao.compensation.interfaces.dto.role.RoleResourceAssignRequest.ResourceAssignment a =
                            new com.yiyundao.compensation.interfaces.dto.role.RoleResourceAssignRequest.ResourceAssignment();
                    a.setResourceId(rid);
                    a.setActions(actions != null ? actions.get(String.valueOf(rid)) : null);
                    return a;
                }).toList();

        com.yiyundao.compensation.interfaces.dto.role.RoleResourceAssignRequest request =
                new com.yiyundao.compensation.interfaces.dto.role.RoleResourceAssignRequest();
        request.setResources(assignments);
        request.setReplaceExisting(true); // 替换模式

        roleService.assignResources(roleId, request, systemOperatorId());
        log.info("审批通过后应用角色资源授权: roleId={}, resourceCount={}", roleId, rids.size());
    }

    @SuppressWarnings("unchecked")
    private void handleUserResourceGrant(Map<String, Object> data) {
        Long userId = toLong(data.get("userId"));
        List<Long> rids = toLongList(data.get("resourceIds"));

        if (userId == null) {
            throw new IllegalArgumentException("用户资源授权审批数据缺失 userId");
        }

        if (rids.isEmpty()) {
            throw new IllegalArgumentException("用户资源授权审批数据缺少 resourceIds");
        }
        Map<Long, List<String>> actionsMap = toLongActionsMap(data.get("actions"));

        ensureUserResourceSnapshotUnchanged(userId, data.get("snapshotPrev"));

        userResourceService.assignResources(userId, rids, actionsMap, systemOperatorId());
        log.info("审批通过后应用用户资源授权: userId={}, resourceCount={}", userId, rids.size());
    }

    private Long systemOperatorId() {
        return sysConfigService.getLong("system.admin_user_id", 1L);
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private List<Long> toLongList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .map(this::toLong)
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<Long, List<String>> toLongActionsMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<Long, List<String>> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            Long resourceId = toLong(entry.getKey());
            if (resourceId == null) {
                continue;
            }
            result.put(resourceId, toStringList(entry.getValue()));
        }
        return result;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .map(String::valueOf)
                .filter(item -> item != null && !item.isBlank())
                .toList();
    }

    private void ensureUserResourceSnapshotUnchanged(Long userId, Object snapshotValue) {
        Map<Long, Set<String>> snapshot = normalizeSnapshot(snapshotValue);
        Map<Long, Set<String>> current = normalizeCurrentUserResources(userResourceService.getUserResources(userId));
        if (!snapshot.equals(current)) {
            throw new IllegalStateException("用户资源授权审批已过期，请刷新权限配置后重新提交");
        }
    }

    private Map<Long, Set<String>> normalizeSnapshot(Object snapshotValue) {
        if (!(snapshotValue instanceof Collection<?> collection)) {
            return Map.of();
        }
        Map<Long, Set<String>> result = new HashMap<>();
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Long resourceId = toLong(row.get("resourceId"));
            if (resourceId == null) {
                continue;
            }
            Object actionsJson = row.get("actionsJson");
            result.computeIfAbsent(resourceId, ignored -> new HashSet<>())
                    .addAll(parseActions(actionsJson == null ? "" : String.valueOf(actionsJson)));
        }
        return normalizeActionMap(result);
    }

    private Map<Long, Set<String>> normalizeCurrentUserResources(Map<Long, Set<String>> current) {
        if (current == null || current.isEmpty()) {
            return Map.of();
        }
        return normalizeActionMap(current);
    }

    private Map<Long, Set<String>> normalizeActionMap(Map<Long, Set<String>> source) {
        Map<Long, Set<String>> result = new TreeMap<>();
        source.forEach((resourceId, actions) -> {
            if (resourceId != null) {
                result.put(resourceId, actions == null ? Set.of() : new TreeSet<>(actions));
            }
        });
        return result;
    }

    private Set<String> parseActions(String actionsJson) {
        if (actionsJson == null || actionsJson.isBlank() || "null".equalsIgnoreCase(actionsJson)) {
            return Set.of();
        }
        try {
            return new TreeSet<>(Arrays.asList(objectMapper.readValue(actionsJson, String[].class)));
        } catch (Exception ignored) {
            String trimmed = actionsJson.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            if (trimmed.isBlank()) {
                return Set.of();
            }
            Set<String> actions = new TreeSet<>();
            for (String item : trimmed.split("[,\\s]+")) {
                String action = item.trim().replaceAll("^[\"']|[\"']$", "");
                if (!action.isBlank()) {
                    actions.add(action);
                }
            }
            return actions;
        }
    }
}
