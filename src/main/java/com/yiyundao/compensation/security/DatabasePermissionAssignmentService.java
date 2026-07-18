package com.yiyundao.compensation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 角色/用户授权写入端口。授权页面可以继续提交 resourceId + actionCode，最终落库为
 * 独立的 resource-action 和 subject-resource-action 关系，不再把操作集合塞进 JSON 字段。
 */
@Service
@RequiredArgsConstructor
public class DatabasePermissionAssignmentService {

    private static final String SCOPE_ALL = "{\"mode\":\"ALL\"}";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void replaceRolePermissions(Long roleId, Collection<Long> resourceIds,
                                       Map<Long, List<String>> actionsByResource, Long operatorId) {
        if (roleId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "角色ID不能为空");
        }
        disablePermissions("sys_role_permission", "role_id", roleId, operatorId);
        for (Long resourceId : normalizeIds(resourceIds)) {
            insertPermissions("sys_role_permission", "role_id", roleId, resourceId,
                    actionsByResource == null ? null : actionsByResource.get(resourceId), operatorId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceUserPermissions(Long userId, Collection<Long> resourceIds,
                                       Map<Long, List<String>> actionsByResource, Long operatorId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "用户ID不能为空");
        }
        disablePermissions("sys_user_permission", "user_id", userId, operatorId);
        for (Long resourceId : normalizeIds(resourceIds)) {
            insertPermissions("sys_user_permission", "user_id", userId, resourceId,
                    actionsByResource == null ? null : actionsByResource.get(resourceId), operatorId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void upsertRolePermissions(Long roleId, Map<Long, List<String>> actionsByResource, Long operatorId) {
        if (roleId == null || actionsByResource == null) {
            return;
        }
        for (Map.Entry<Long, List<String>> entry : actionsByResource.entrySet()) {
            if (entry.getKey() != null) {
                insertPermissions("sys_role_permission", "role_id", roleId, entry.getKey(),
                        entry.getValue(), operatorId);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void upsertUserPermissions(Long userId, Map<Long, List<String>> actionsByResource, Long operatorId) {
        if (userId == null || actionsByResource == null) {
            return;
        }
        for (Map.Entry<Long, List<String>> entry : actionsByResource.entrySet()) {
            if (entry.getKey() != null) {
                insertPermissions("sys_user_permission", "user_id", userId, entry.getKey(),
                        entry.getValue(), operatorId);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeRolePermissions(Long roleId, Collection<Long> resourceIds, Long operatorId) {
        disablePermissions("sys_role_permission", "role_id", roleId, operatorId, resourceIds);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeUserPermissions(Long userId, Collection<Long> resourceIds, Long operatorId) {
        disablePermissions("sys_user_permission", "user_id", userId, operatorId, resourceIds);
    }

    private void disablePermissions(String table, String subjectColumn, Long subjectId, Long operatorId) {
        jdbcTemplate.update("UPDATE " + table + " SET status='disabled', deleted=1, update_by=?, update_time=NOW() " +
                        "WHERE " + subjectColumn + "=? AND deleted=0", String.valueOf(operatorId), subjectId);
    }

    private void disablePermissions(String table, String subjectColumn, Long subjectId,
                                    Long operatorId, Collection<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            disablePermissions(table, subjectColumn, subjectId, operatorId);
            return;
        }
        List<Long> ids = normalizeIds(resourceIds);
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(String.valueOf(operatorId));
        args.add(subjectId);
        args.addAll(ids);
        jdbcTemplate.update("UPDATE " + table + " SET status='disabled', deleted=1, update_by=?, update_time=NOW() " +
                        "WHERE " + subjectColumn + "=? AND resource_id IN (" + placeholders + ") AND deleted=0",
                args.toArray());
    }

    private void insertPermissions(String table, String subjectColumn, Long subjectId, Long resourceId,
                                   List<String> requestedActions, Long operatorId) {
        List<ActionRow> actions = resolveActions(resourceId, requestedActions);
        if (actions.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "资源未配置可授权操作: resourceId=" + resourceId);
        }
        for (ActionRow action : actions) {
            jdbcTemplate.update("INSERT INTO " + table + " (" + subjectColumn + ",resource_id,action_id,effect,scope_json,status,create_time,update_time,create_by,update_by,deleted,version) " +
                            "VALUES (?,?,?,?,?,'enabled',NOW(),NOW(),?,?,0,0) " +
                            "ON DUPLICATE KEY UPDATE effect=VALUES(effect),scope_json=VALUES(scope_json),status='enabled',deleted=0,update_by=VALUES(update_by),update_time=NOW()",
                    subjectId, resourceId, action.id(), "ALLOW", SCOPE_ALL,
                    String.valueOf(operatorId), String.valueOf(operatorId));
        }
    }

    private List<ActionRow> resolveActions(Long resourceId, List<String> requestedActions) {
        List<ActionRow> available = jdbcTemplate.query(
                "SELECT a.id,a.code FROM sys_resource_action ra JOIN sys_permission_action a ON a.id=ra.action_id " +
                        "WHERE ra.resource_id=? AND ra.status='enabled' AND ra.deleted=0 " +
                        "AND a.status='enabled' AND a.deleted=0 ORDER BY a.order_num,a.id",
                (rs, rowNum) -> new ActionRow(rs.getLong("id"), rs.getString("code")), resourceId);
        if (requestedActions == null || requestedActions.isEmpty()
                || requestedActions.stream().anyMatch(item -> "*".equals(item))) {
            return available;
        }
        Map<String, ActionRow> byCode = new LinkedHashMap<>();
        for (ActionRow action : available) {
            byCode.put(action.code().toLowerCase(), action);
        }
        List<ActionRow> selected = new ArrayList<>();
        for (String requested : new LinkedHashSet<>(requestedActions)) {
            if (!StringUtils.hasText(requested)) {
                continue;
            }
            ActionRow action = byCode.get(requested.trim().toLowerCase());
            if (action == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "资源未配置该操作: resourceId=" + resourceId + ", action=" + requested);
            }
            selected.add(action);
        }
        return selected;
    }

    private List<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private record ActionRow(Long id, String code) {
    }
}
