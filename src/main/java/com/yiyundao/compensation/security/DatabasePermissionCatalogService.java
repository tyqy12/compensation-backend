package com.yiyundao.compensation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 权限操作目录和资源-操作绑定的数据库管理服务。
 *
 * <p>这里维护的是授权模型本身，而不是某个业务角色的快捷配置。所有变更都直接作用于
 * sys_permission_action / sys_resource_action，运行时决策服务只读取这两张表及授权关系表。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabasePermissionCatalogService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:-]{0,99}");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public List<ActionView> listActions(String status, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT id,code,name,description,http_methods,authority,status,order_num,props_json " +
                "FROM sys_permission_action WHERE deleted=0");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(status)) {
            sql.append(" AND status=?");
            args.add(normalizeStatus(status));
        }
        if (StringUtils.hasText(keyword)) {
            sql.append(" AND (code LIKE ? OR name LIKE ? OR description LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value);
            args.add(value);
            args.add(value);
        }
        sql.append(" ORDER BY order_num,id");
        List<ActionRow> rows = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new ActionRow(
                rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("description"), rs.getString("http_methods"), rs.getString("authority"),
                rs.getString("status"), rs.getInt("order_num"), rs.getString("props_json")
        ), args.toArray());

        Map<Long, List<Long>> resourceIdsByAction = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT action_id,resource_id FROM sys_resource_action " +
                        "WHERE status='enabled' AND deleted=0 ORDER BY resource_id",
                (RowCallbackHandler) rs -> resourceIdsByAction.computeIfAbsent(rs.getLong("action_id"), ignored -> new ArrayList<>())
                        .add(rs.getLong("resource_id")));
        return rows.stream().map(row -> toView(row, resourceIdsByAction.getOrDefault(row.id(), List.of()))).toList();
    }

    public ActionView getAction(Long actionId) {
        if (actionId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "操作ID不能为空");
        }
        List<ActionView> actions = listActions(null, null).stream()
                .filter(action -> Objects.equals(action.id(), actionId))
                .toList();
        if (actions.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "权限操作不存在");
        }
        return actions.get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public ActionView create(ActionCommand command, Long operatorId) {
        validate(command, null);
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_permission_action WHERE code=?", Integer.class, command.code());
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_EXISTS, "权限操作编码已存在: " + command.code());
        }
        jdbcTemplate.update("INSERT INTO sys_permission_action " +
                        "(code,name,description,http_methods,authority,status,order_num,props_json,create_time,update_time,create_by,update_by,deleted,version) " +
                        "VALUES (?,?,?,?,?,?,?,?,NOW(),NOW(),?,?,0,0)",
                command.code(), command.name(), command.description(), command.httpMethods(), command.authority(),
                normalizeStatus(command.status()), command.orderNum(), command.propsJson(), operator(operatorId), operator(operatorId));
        ActionView result = getActionByCode(command.code());
        audit("RBAC_ACTION_CREATE", result.code(), operatorId, command.name());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public ActionView update(Long actionId, ActionCommand command, Long operatorId) {
        ActionView existing = getAction(actionId);
        validate(command, existing);
        jdbcTemplate.update("UPDATE sys_permission_action SET name=?,description=?,http_methods=?,authority=?,status=?," +
                        "order_num=?,props_json=?,update_time=NOW(),update_by=?,version=version+1 WHERE id=? AND deleted=0",
                command.name(), command.description(), command.httpMethods(), command.authority(), normalizeStatus(command.status()),
                command.orderNum(), command.propsJson(), operator(operatorId), actionId);
        if ("disabled".equalsIgnoreCase(command.status())) {
            jdbcTemplate.update("UPDATE sys_resource_action SET status='disabled',deleted=1,update_time=NOW(),update_by=? " +
                    "WHERE action_id=? AND deleted=0", operator(operatorId), actionId);
        }
        ActionView result = getAction(actionId);
        audit("RBAC_ACTION_UPDATE", result.code(), operatorId, result.name());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long actionId, Long operatorId) {
        ActionView existing = getAction(actionId);
        jdbcTemplate.update("UPDATE sys_permission_action SET status='disabled',deleted=1,update_time=NOW(),update_by=? " +
                "WHERE id=? AND deleted=0", operator(operatorId), actionId);
        jdbcTemplate.update("UPDATE sys_resource_action SET status='disabled',deleted=1,update_time=NOW(),update_by=? " +
                "WHERE action_id=? AND deleted=0", operator(operatorId), actionId);
        jdbcTemplate.update("UPDATE sys_role_permission SET status='disabled',deleted=1,update_time=NOW(),update_by=? " +
                "WHERE action_id=? AND deleted=0", operator(operatorId), actionId);
        jdbcTemplate.update("UPDATE sys_user_permission SET status='disabled',deleted=1,update_time=NOW(),update_by=? " +
                "WHERE action_id=? AND deleted=0", operator(operatorId), actionId);
        audit("RBAC_ACTION_DELETE", existing.code(), operatorId, existing.name());
    }

    public List<ActionView> listResourceActions(Long resourceId) {
        requireResource(resourceId);
        return jdbcTemplate.query("SELECT a.id,a.code,a.name,a.description,a.http_methods,a.authority,a.status,a.order_num,a.props_json " +
                        "FROM sys_resource_action ra JOIN sys_permission_action a ON a.id=ra.action_id " +
                        "WHERE ra.resource_id=? AND ra.status='enabled' AND ra.deleted=0 AND a.status='enabled' AND a.deleted=0 " +
                        "ORDER BY a.order_num,a.id",
                (rs, rowNum) -> new ActionView(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("description"), rs.getString("http_methods"), rs.getString("authority"),
                        rs.getString("status"), rs.getInt("order_num"), rs.getString("props_json"), List.of()),
                resourceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ActionView> replaceResourceActions(Long resourceId, Collection<String> actionCodes,
                                                    Collection<Long> actionIds, Long operatorId) {
        requireResource(resourceId);
        Set<Long> selectedIds = resolveActionIds(actionCodes, actionIds);
        jdbcTemplate.update("UPDATE sys_resource_action SET status='disabled',deleted=1,update_time=NOW(),update_by=? " +
                "WHERE resource_id=? AND deleted=0", operator(operatorId), resourceId);
        for (Long actionId : selectedIds) {
            int restored = jdbcTemplate.update("UPDATE sys_resource_action SET status='enabled',deleted=0,update_time=NOW(),update_by=? " +
                    "WHERE resource_id=? AND action_id=?", operator(operatorId), resourceId, actionId);
            if (restored == 0) {
                jdbcTemplate.update("INSERT INTO sys_resource_action " +
                                "(resource_id,action_id,status,create_time,update_time,create_by,update_by,deleted,version) " +
                                "VALUES (?,?,'enabled',NOW(),NOW(),?,?,0,0)",
                        resourceId, actionId, operator(operatorId), operator(operatorId));
            }
        }
        bumpPermissionVersions(resourceId);
        audit("RBAC_RESOURCE_ACTION_REPLACE", String.valueOf(resourceId), operatorId,
                "actions=" + selectedIds);
        return listResourceActions(resourceId);
    }

    private Set<Long> resolveActionIds(Collection<String> actionCodes, Collection<Long> actionIds) {
        Set<Long> selected = new LinkedHashSet<>();
        if (actionIds != null) {
            selected.addAll(actionIds.stream().filter(Objects::nonNull).toList());
        }
        if (actionCodes != null) {
            for (String code : actionCodes) {
                if (!StringUtils.hasText(code)) {
                    continue;
                }
                Long id = jdbcTemplate.query("SELECT id FROM sys_permission_action WHERE code=? AND status='enabled' AND deleted=0",
                        (rs, rowNum) -> rs.getLong(1), code.trim()).stream().findFirst().orElse(null);
                if (id == null) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "权限操作不存在或已停用: " + code);
                }
                selected.add(id);
            }
        }
        if (selected.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(selected.size(), "?"));
        List<Long> existing = jdbcTemplate.query("SELECT id FROM sys_permission_action WHERE id IN (" + placeholders + ") " +
                        "AND status='enabled' AND deleted=0", (rs, rowNum) -> rs.getLong(1), selected.toArray());
        if (existing.size() != selected.size()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "包含不存在或已停用的权限操作");
        }
        return selected;
    }

    private void requireResource(Long resourceId) {
        if (resourceId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "资源ID不能为空");
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_resource WHERE id=? AND deleted=0", Integer.class, resourceId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "资源不存在");
        }
    }

    private ActionView getActionByCode(String code) {
        return listActions(null, code).stream().filter(action -> code.equals(action.code())).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "权限操作不存在"));
    }

    private ActionView toView(ActionRow row, List<Long> resourceIds) {
        return new ActionView(row.id(), row.code(), row.name(), row.description(), row.httpMethods(), row.authority(),
                row.status(), row.orderNum(), row.propsJson(), resourceIds);
    }

    private void validate(ActionCommand command, ActionView existing) {
        if (command == null || !StringUtils.hasText(command.code()) || !StringUtils.hasText(command.name())) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "权限操作编码和名称不能为空");
        }
        String code = command.code().trim();
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "权限操作编码格式无效");
        }
        command.setCode(code);
        command.setName(command.name().trim());
        command.setStatus(normalizeStatus(command.status()));
        command.setOrderNum(command.orderNum() == null ? 0 : command.orderNum());
        if (existing != null && !existing.code().equals(code)) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "权限操作编码不可修改，请新建操作");
        }
        if (StringUtils.hasText(command.httpMethods())) {
            command.setHttpMethods(command.httpMethods().trim().toUpperCase());
        }
        if (StringUtils.hasText(command.authority())) {
            command.setAuthority(command.authority().trim());
        }
        if (StringUtils.hasText(command.propsJson())) {
            try {
                objectMapper.readTree(command.propsJson());
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "操作扩展配置必须是合法 JSON");
            }
        }
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "enabled";
        }
        String normalized = status.trim().toLowerCase();
        if (!"enabled".equals(normalized) && !"disabled".equals(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "状态仅支持 enabled/disabled");
        }
        return normalized;
    }

    private String operator(Long operatorId) {
        return operatorId == null ? "system" : String.valueOf(operatorId);
    }

    private void bumpPermissionVersions(Long resourceId) {
        try {
            jdbcTemplate.update("UPDATE sys_user SET permission_version=COALESCE(permission_version,0)+1, " +
                    "update_time=NOW() WHERE status='active' AND id IN (" +
                    "SELECT user_id FROM sys_user_permission WHERE resource_id=? AND deleted=0 " +
                    "UNION SELECT ur.user_id FROM sys_user_role ur JOIN sys_role_permission rp ON rp.role_id=ur.role_id " +
                    "WHERE rp.resource_id=? AND ur.deleted=0 AND rp.deleted=0)", resourceId, resourceId);
        } catch (Exception e) {
            log.warn("权限版本号更新失败，运行时无缓存不影响即时决策: resourceId={}", resourceId, e);
        }
    }

    private void audit(String operation, String key, Long operatorId, String detail) {
        try {
            auditLogService.record(operation, "SYSTEM", "DatabasePermissionCatalog", "127.0.0.1", "SYSTEM",
                    "RBAC", key, operator(operatorId), null, detail, null, null);
        } catch (Exception e) {
            log.warn("权限目录审计写入失败: operation={}, key={}", operation, key, e);
        }
    }

    public static class ActionCommand {
        private String code;
        private String name;
        private String description;
        private String httpMethods;
        private String authority;
        private String status;
        private Integer orderNum;
        private String propsJson;

        public String code() { return code; }
        public void setCode(String code) { this.code = code; }
        public String name() { return name; }
        public void setName(String name) { this.name = name; }
        public String description() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String httpMethods() { return httpMethods; }
        public void setHttpMethods(String httpMethods) { this.httpMethods = httpMethods; }
        public String authority() { return authority; }
        public void setAuthority(String authority) { this.authority = authority; }
        public String status() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer orderNum() { return orderNum; }
        public void setOrderNum(Integer orderNum) { this.orderNum = orderNum; }
        public String propsJson() { return propsJson; }
        public void setPropsJson(String propsJson) { this.propsJson = propsJson; }
    }

    public record ActionView(Long id, String code, String name, String description, String httpMethods,
                             String authority, String status, Integer orderNum, String propsJson,
                             List<Long> resourceIds) {
    }

    private record ActionRow(Long id, String code, String name, String description, String httpMethods,
                             String authority, String status, Integer orderNum, String propsJson) {
    }
}
