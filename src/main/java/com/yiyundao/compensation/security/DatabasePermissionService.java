package com.yiyundao.compensation.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.UserStatus;
import com.yiyundao.compensation.infrastructure.dao.SysResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 唯一的数据库驱动权限决策入口。
 *
 * <p>角色名称、URL 前缀、HTTP 方法到角色的映射以及管理员旁路都不在这里定义。
 * 资源、操作、资源-操作绑定、角色授权和用户授权全部来自数据库。旧的
 * {@code sys_role_resource}/{@code sys_user_resource} 仅由迁移器读取，运行时不再读取。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabasePermissionService {

    private static final String RESOURCE_TYPE_API = "API";
    private static final String RESOURCE_MODE_PUBLIC = "PUBLIC";
    private static final String RESOURCE_MODE_EXTERNAL = "EXTERNAL";
    private static final String EFFECT_ALLOW = "ALLOW";
    private static final String EFFECT_DENY = "DENY";

    private final JdbcTemplate jdbcTemplate;
    private final SysResourceMapper sysResourceMapper;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;
    private final ExternalApiContext externalApiContext;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 对一次 HTTP 请求做完整的资源和操作决策。
     */
    public PermissionDecision decide(HttpServletRequest request, Authentication authentication) {
        if (request == null) {
            return denied(ErrorCode.FORBIDDEN, "缺少权限请求上下文");
        }
        String method = request.getMethod();
        List<SysResource> resources = findMatchingApiResources(
                method,
                request.getRequestURI(),
                request.getServletPath(),
                request.getContextPath()
        );
        if (resources.isEmpty()) {
            return denied(ErrorCode.FORBIDDEN, "接口未配置访问权限");
        }

        boolean hasUnauthenticatedMatch = false;
        boolean hasExternalMatch = false;
        boolean hasUserMatch = false;
        for (SysResource resource : resources) {
            String accessMode = normalizeMode(resource.getAccessMode());
            List<ActionDefinition> actions = findActions(resource.getId(), method);
            for (ActionDefinition action : actions) {
                if (RESOURCE_MODE_PUBLIC.equals(accessMode)) {
                    return allowed(resource, action, null);
                }
                if (RESOURCE_MODE_EXTERNAL.equals(accessMode)) {
                    hasExternalMatch = true;
                    if (isExternalAuthentication(authentication) && authorityMatches(authentication, action.authority())) {
                        return allowed(resource, action, null);
                    }
                    continue;
                }

                hasUserMatch = true;
                SysUser user = findActiveUser(authentication);
                if (user == null) {
                    hasUnauthenticatedMatch = true;
                    continue;
                }
                GrantDecision grant = evaluateUserGrant(user.getId(), resource.getId(), action.id());
                if (grant.denied()) {
                    return denied(ErrorCode.FORBIDDEN, "权限策略明确拒绝该操作", resource, action);
                }
                if (grant.allowed()) {
                    return allowed(resource, action, grant.scopeJson());
                }
            }
        }

        if (hasExternalMatch && !isExternalAuthentication(authentication)) {
            return denied(ErrorCode.UNAUTHORIZED, "该接口需要外部应用认证");
        }
        if (hasUserMatch && hasUnauthenticatedMatch) {
            return denied(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return denied(ErrorCode.FORBIDDEN, "无权限执行该操作");
    }

    /**
     * 由外部认证过滤器调用。是否为外部接口只由资源表 access_mode 决定。
     */
    public boolean matchesExternalResource(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        return findMatchingResources(
                RESOURCE_TYPE_API,
                null,
                request.getRequestURI(),
                request.getServletPath(),
                request.getContextPath()
        ).stream().anyMatch(resource -> RESOURCE_MODE_EXTERNAL.equals(normalizeMode(resource.getAccessMode())));
    }

    /**
     * 公开接口和外部接口可以由数据库控制是否需要令牌端点旁路。
     */
    public boolean isPublicResource(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        return findMatchingResources(
                RESOURCE_TYPE_API,
                null,
                request.getRequestURI(),
                request.getServletPath(),
                request.getContextPath()
        ).stream().anyMatch(resource -> RESOURCE_MODE_PUBLIC.equals(normalizeMode(resource.getAccessMode())));
    }

    /**
     * 返回当前用户可见的菜单/页面和对应操作。结果不使用角色名称兜底。
     */
    public PermissionBundle getUserBundle(Long userId) {
        if (userId == null) {
            return new PermissionBundle(List.of(), Map.of());
        }
        List<PermissionGrant> grants = loadUserGrants(userId);
        Set<PermissionKey> denied = grants.stream()
                .filter(grant -> EFFECT_DENY.equals(grant.effect()))
                .map(grant -> new PermissionKey(grant.resourceId(), grant.actionCode()))
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, Set<String>> actionCodes = new LinkedHashMap<>();
        for (PermissionGrant grant : grants) {
            if (!EFFECT_ALLOW.equals(grant.effect())) {
                continue;
            }
            PermissionKey key = new PermissionKey(grant.resourceId(), grant.actionCode());
            if (denied.contains(key)) {
                continue;
            }
            actionCodes.computeIfAbsent(grant.resourceId(), ignored -> new LinkedHashSet<>())
                    .add(grant.actionCode());
        }

        List<SysResource> all = sysResourceMapper.selectList(new LambdaQueryWrapper<SysResource>()
                .in(SysResource::getType, List.of("MENU", "VIEW"))
                .eq(SysResource::getStatus, "enabled")
                .orderByAsc(SysResource::getOrderNum));
        List<SysResource> visible = all.stream()
                .filter(resource -> actionCodes.containsKey(resource.getId()))
                .toList();
        Map<Long, List<String>> actions = new LinkedHashMap<>();
        for (Map.Entry<Long, Set<String>> entry : actionCodes.entrySet()) {
            actions.put(entry.getKey(), entry.getValue().stream().sorted().toList());
        }
        return new PermissionBundle(visible, actions);
    }

    public boolean hasPermission(Long userId, String resourceCode, String actionCode) {
        if (userId == null || !StringUtils.hasText(resourceCode) || !StringUtils.hasText(actionCode)) {
            return false;
        }
        List<SysResource> resources = sysResourceMapper.selectList(new LambdaQueryWrapper<SysResource>()
                .eq(SysResource::getCode, resourceCode)
                .eq(SysResource::getStatus, "enabled"));
        for (SysResource resource : resources) {
            Long actionId = findActionId(resource.getId(), actionCode);
            if (actionId == null) {
                continue;
            }
            GrantDecision decision = evaluateUserGrant(userId, resource.getId(), actionId);
            if (decision.denied()) {
                return false;
            }
            if (decision.allowed()) {
                return true;
            }
        }
        return false;
    }

    /** 返回角色当前生效的资源-操作授权，供权限管理页面读取真实授权表。 */
    public Map<Long, Set<String>> getRoleActionCodes(Long roleId) {
        if (roleId == null) {
            return Map.of();
        }
        return loadSubjectActionCodes(
                "SELECT rp.resource_id,a.code,rp.effect FROM sys_role_permission rp " +
                        "JOIN sys_permission_action a ON a.id=rp.action_id AND a.status='enabled' AND a.deleted=0 " +
                        "JOIN sys_resource_action ra ON ra.resource_id=rp.resource_id AND ra.action_id=rp.action_id " +
                        "AND ra.status='enabled' AND ra.deleted=0 " +
                        "WHERE rp.role_id=? AND rp.status='enabled' AND rp.deleted=0",
                roleId);
    }

    /** 返回用户直接授权的资源-操作，角色继承由 getUserBundle 统一合并。 */
    public Map<Long, Set<String>> getUserDirectActionCodes(Long userId) {
        if (userId == null) {
            return Map.of();
        }
        return loadSubjectActionCodes(
                "SELECT up.resource_id,a.code,up.effect FROM sys_user_permission up " +
                        "JOIN sys_permission_action a ON a.id=up.action_id AND a.status='enabled' AND a.deleted=0 " +
                        "JOIN sys_resource_action ra ON ra.resource_id=up.resource_id AND ra.action_id=up.action_id " +
                        "AND ra.status='enabled' AND ra.deleted=0 " +
                        "WHERE up.user_id=? AND up.status='enabled' AND up.deleted=0",
                userId);
    }

    public boolean hasRolePermission(Long roleId, Long resourceId, String actionCode) {
        if (roleId == null || resourceId == null) {
            return false;
        }
        if (!StringUtils.hasText(actionCode)) {
            return getRoleActionCodes(roleId).containsKey(resourceId);
        }
        Map<Long, Set<String>> assignments = getRoleActionCodes(roleId);
        Set<String> actions = assignments.get(resourceId);
        return actions != null && (actions.contains(actionCode) || actions.contains("*"));
    }

    public boolean hasScope(Long userId, String resourceCode, String actionCode, String scopeMode) {
        if (!hasPermission(userId, resourceCode, actionCode)) {
            return false;
        }
        List<SysResource> resources = sysResourceMapper.selectList(new LambdaQueryWrapper<SysResource>()
                .eq(SysResource::getCode, resourceCode)
                .eq(SysResource::getStatus, "enabled"));
        for (SysResource resource : resources) {
            Long actionId = findActionId(resource.getId(), actionCode);
            if (actionId == null) {
                continue;
            }
            GrantDecision grant = evaluateUserGrant(userId, resource.getId(), actionId);
            if (grant.allowed() && scopeMatches(grant.scopeJson(), scopeMode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断一组外部 scope 是否对应需要对象数据范围的操作。哪些操作需要数据范围由操作目录
     * 的 authority 字段定义，不按业务名称在代码中列白名单。
     */
    public boolean requiresDataGrant(Collection<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return false;
        }
        List<String> authorities = scopes.stream()
                .filter(StringUtils::hasText)
                .map(scope -> "SCOPE_" + scope.trim())
                .toList();
        if (authorities.isEmpty()) {
            return false;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(authorities.size(), "?"));
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_permission_action WHERE authority IN (" + placeholders + ") " +
                        "AND status='enabled' AND deleted=0",
                Integer.class, authorities.toArray());
        return count != null && count > 0;
    }

    public boolean hasCurrentRequestScope(Long userId, String scopeMode) {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (authentication == null || attributes == null) {
            return false;
        }
        PermissionDecision decision = decide(attributes.getRequest(), authentication);
        SysUser user = findActiveUser(authentication);
        return decision.allowed() && scopeMatches(decision.scopeJson(), scopeMode)
                && user != null && Objects.equals(userId, user.getId());
    }

    private GrantDecision evaluateUserGrant(Long userId, Long resourceId, Long actionId) {
        try {
            List<PermissionGrant> grants = new ArrayList<>();
            grants.addAll(jdbcTemplate.query(
                    "SELECT up.resource_id, up.action_id, a.code, up.effect, up.scope_json " +
                            "FROM sys_user_permission up " +
                            "JOIN sys_permission_action a ON a.id=up.action_id AND a.status='enabled' AND a.deleted=0 " +
                            "JOIN sys_resource_action ra ON ra.resource_id=up.resource_id AND ra.action_id=up.action_id " +
                            "AND ra.status='enabled' AND ra.deleted=0 " +
                            "WHERE up.user_id=? AND up.resource_id=? AND up.action_id=? " +
                            "AND up.status='enabled' AND up.deleted=0",
                    (rs, rowNum) -> mapGrant(rs.getLong("resource_id"), rs.getLong("action_id"),
                            rs.getString("code"), rs.getString("effect"), rs.getString("scope_json")),
                    userId, resourceId, actionId));
            grants.addAll(jdbcTemplate.query(
                    "SELECT rp.resource_id, rp.action_id, a.code, rp.effect, rp.scope_json " +
                            "FROM sys_role_permission rp " +
                            "JOIN sys_permission_action a ON a.id=rp.action_id AND a.status='enabled' AND a.deleted=0 " +
                            "JOIN sys_resource_action ra ON ra.resource_id=rp.resource_id AND ra.action_id=rp.action_id " +
                            "AND ra.status='enabled' AND ra.deleted=0 " +
                            "JOIN sys_user_role ur ON ur.role_id=rp.role_id AND ur.user_id=? " +
                            "AND ur.deleted=0 AND (ur.expires_at IS NULL OR ur.expires_at > NOW()) " +
                            "JOIN sys_role r ON r.id=ur.role_id AND r.status='enabled' AND r.deleted=0 " +
                            "WHERE rp.resource_id=? AND rp.action_id=? AND rp.status='enabled' AND rp.deleted=0",
                    (rs, rowNum) -> mapGrant(rs.getLong("resource_id"), rs.getLong("action_id"),
                            rs.getString("code"), rs.getString("effect"), rs.getString("scope_json")),
                    userId, resourceId, actionId));
            return grantDecision(grants);
        } catch (DataAccessException e) {
            log.error("数据库权限策略读取失败: userId={}, resourceId={}, actionId={}", userId, resourceId, actionId, e);
            return GrantDecision.none();
        }
    }

    private List<PermissionGrant> loadUserGrants(Long userId) {
        try {
            String sql = "SELECT up.resource_id, up.action_id, a.code, up.effect, up.scope_json " +
                    "FROM sys_user_permission up " +
                    "JOIN sys_permission_action a ON a.id=up.action_id AND a.status='enabled' AND a.deleted=0 " +
                    "JOIN sys_resource_action ra ON ra.resource_id=up.resource_id AND ra.action_id=up.action_id " +
                    "AND ra.status='enabled' AND ra.deleted=0 " +
                    "WHERE up.user_id=? AND up.status='enabled' AND up.deleted=0 " +
                    "UNION ALL " +
                    "SELECT rp.resource_id, rp.action_id, a.code, rp.effect, rp.scope_json " +
                    "FROM sys_role_permission rp " +
                    "JOIN sys_permission_action a ON a.id=rp.action_id AND a.status='enabled' AND a.deleted=0 " +
                    "JOIN sys_resource_action ra ON ra.resource_id=rp.resource_id AND ra.action_id=rp.action_id " +
                    "AND ra.status='enabled' AND ra.deleted=0 " +
                    "JOIN sys_user_role ur ON ur.role_id=rp.role_id AND ur.user_id=? " +
                    "AND ur.deleted=0 AND (ur.expires_at IS NULL OR ur.expires_at > NOW()) " +
                    "JOIN sys_role r ON r.id=ur.role_id AND r.status='enabled' AND r.deleted=0 " +
                    "WHERE rp.status='enabled' AND rp.deleted=0";
            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> mapGrant(rs.getLong("resource_id"), rs.getLong("action_id"),
                            rs.getString("code"), rs.getString("effect"), rs.getString("scope_json")),
                    userId, userId);
        } catch (DataAccessException e) {
            log.error("用户权限策略读取失败: userId={}", userId, e);
            return List.of();
        }
    }

    private Map<Long, Set<String>> loadSubjectActionCodes(String sql, Long subjectId) {
        try {
            Map<Long, Set<String>> result = new LinkedHashMap<>();
            Set<PermissionKey> denied = new HashSet<>();
            jdbcTemplate.query(sql, (rs, rowNum) -> {
                Long resourceId = rs.getLong("resource_id");
                String code = rs.getString("code");
                String effect = rs.getString("effect");
                if (EFFECT_ALLOW.equalsIgnoreCase(effect)) {
                    result.computeIfAbsent(resourceId, ignored -> new LinkedHashSet<>()).add(code);
                } else if (EFFECT_DENY.equalsIgnoreCase(effect)) {
                    denied.add(new PermissionKey(resourceId, code));
                }
                return null;
            }, subjectId);
            denied.forEach(key -> {
                Set<String> actions = result.get(key.resourceId());
                if (actions != null) {
                    actions.remove(key.actionCode());
                    if (actions.isEmpty()) {
                        result.remove(key.resourceId());
                    }
                }
            });
            return result;
        } catch (DataAccessException e) {
            log.error("主体权限读取失败: subjectId={}", subjectId, e);
            return Map.of();
        }
    }

    private GrantDecision grantDecision(List<PermissionGrant> grants) {
        if (grants == null || grants.isEmpty()) {
            return GrantDecision.none();
        }
        if (grants.stream().anyMatch(grant -> EFFECT_DENY.equals(grant.effect()))) {
            return new GrantDecision(false, true, null);
        }
        return grants.stream()
                .filter(grant -> EFFECT_ALLOW.equals(grant.effect()))
                .findFirst()
                .map(grant -> new GrantDecision(true, false, grant.scopeJson()))
                .orElse(GrantDecision.none());
    }

    private List<SysResource> findMatchingApiResources(String method, String uri, String servletPath, String contextPath) {
        return findMatchingResources(RESOURCE_TYPE_API, method, uri, servletPath, contextPath);
    }

    private List<SysResource> findMatchingResources(String type, String method, String uri,
                                                    String servletPath, String contextPath) {
        List<SysResource> all = sysResourceMapper.selectList(new LambdaQueryWrapper<SysResource>()
                .eq(SysResource::getType, type)
                .eq(SysResource::getStatus, "enabled")
                .orderByAsc(SysResource::getOrderNum));
        List<MatchedResource> matched = all.stream()
                .filter(resource -> methodMatches(resource, method))
                .map(resource -> new MatchedResource(resource, matchingPattern(resource, uri, servletPath, contextPath)))
                .filter(item -> item.pattern() != null)
                .sorted(Comparator.comparingInt((MatchedResource item) -> specificity(item.pattern())).reversed())
                .toList();
        if (matched.isEmpty()) {
            return List.of();
        }
        int best = specificity(matched.get(0).pattern());
        return matched.stream()
                .filter(item -> specificity(item.pattern()) == best)
                .map(MatchedResource::resource)
                .toList();
    }

    private boolean methodMatches(SysResource resource, String method) {
        if (!StringUtils.hasText(method)) {
            return true;
        }
        String configuredMethod = jsonText(resource.getPropsJson(), "method");
        return !StringUtils.hasText(configuredMethod) || configuredMethod.equalsIgnoreCase(method);
    }

    private boolean methodMatches(String configuredMethods, String method) {
        if (!StringUtils.hasText(configuredMethods) || "*".equals(configuredMethods.trim())) {
            return true;
        }
        return java.util.Arrays.stream(configuredMethods.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(item -> item.equalsIgnoreCase(method));
    }

    private List<ActionDefinition> findActions(Long resourceId, String method) {
        if (resourceId == null) {
            return List.of();
        }
        String sql = "SELECT a.id, a.code, a.http_methods, a.authority " +
                "FROM sys_resource_action ra JOIN sys_permission_action a ON a.id=ra.action_id " +
                "WHERE ra.resource_id=? AND ra.status='enabled' AND ra.deleted=0 " +
                "AND a.status='enabled' AND a.deleted=0 ORDER BY a.order_num, a.id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ActionDefinition(
                rs.getLong("id"), rs.getString("code"), rs.getString("http_methods"), rs.getString("authority")
        ), resourceId).stream().filter(action -> methodMatches(action.httpMethods(), method)).toList();
    }

    private Long findActionId(Long resourceId, String actionCode) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT a.id FROM sys_resource_action ra JOIN sys_permission_action a ON a.id=ra.action_id " +
                        "WHERE ra.resource_id=? AND a.code=? AND ra.status='enabled' AND ra.deleted=0 " +
                        "AND a.status='enabled' AND a.deleted=0",
                (rs, rowNum) -> rs.getLong(1), resourceId, actionCode);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private SysUser findActiveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !StringUtils.hasText(authentication.getName())
                || "anonymousUser".equalsIgnoreCase(authentication.getName())
                || isExternalAuthentication(authentication)) {
            return null;
        }
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, authentication.getName())
                .eq(SysUser::getStatus, UserStatus.ACTIVE)
                .last("limit 1"));
    }

    private boolean isExternalAuthentication(Authentication authentication) {
        return externalApiContext.current() != null
                && authentication != null
                && authentication.isAuthenticated();
    }

    private boolean authorityMatches(Authentication authentication, String authority) {
        if (!StringUtils.hasText(authority)) {
            return true;
        }
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(item -> authority.equals(item.getAuthority()));
    }

    private boolean scopeMatches(String scopeJson, String expectedMode) {
        if (!StringUtils.hasText(expectedMode)) {
            return true;
        }
        if (!StringUtils.hasText(scopeJson)) {
            return false;
        }
        String mode = jsonText(scopeJson, "mode");
        return expectedMode.equalsIgnoreCase(mode);
    }

    private String matchingPattern(SysResource resource, String uri, String servletPath, String contextPath) {
        String configured = resource.getPath();
        if (!StringUtils.hasText(configured)) {
            return null;
        }
        Set<String> candidates = new LinkedHashSet<>();
        addPathCandidate(candidates, uri, contextPath);
        addPathCandidate(candidates, servletPath, contextPath);
        for (String candidate : candidates) {
            if (pathMatcher.match(configured, candidate)) {
                return configured;
            }
        }
        return null;
    }

    private void addPathCandidate(Set<String> candidates, String path, String contextPath) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        String rawValue = path.trim();
        addNormalizedPathCandidate(candidates, rawValue);

        // Servlet requests expose both the context-prefixed URI and the servlet-relative path
        // depending on the container stage. Keep both forms so DB resources remain portable
        // when the application context path is changed.
        if (StringUtils.hasText(contextPath) && rawValue.startsWith(contextPath + "/")) {
            addNormalizedPathCandidate(candidates, rawValue.substring(contextPath.length()));
        }
    }

    private void addNormalizedPathCandidate(Set<String> candidates, String path) {
        String value = path.startsWith("/") ? path : "/" + path;
        candidates.add(value);
        if (value.startsWith("/api/")) {
            candidates.add(value.substring(4));
        } else if ("/api".equals(value)) {
            candidates.add("/");
        }
    }

    private int specificity(String pattern) {
        if (!StringUtils.hasText(pattern)) {
            return Integer.MIN_VALUE;
        }
        int score = 0;
        for (String segment : pattern.split("/")) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            if (segment.contains("*")) {
                score -= 10;
            } else if (segment.startsWith("{") && segment.endsWith("}")) {
                score += 1;
            } else {
                score += 100;
            }
        }
        return score + pattern.length();
    }

    private String normalizeMode(String accessMode) {
        return StringUtils.hasText(accessMode) ? accessMode.trim().toUpperCase(Locale.ROOT) : "USER";
    }

    private String jsonText(String json, String field) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json).get(field);
            return node == null || node.isNull() ? null : node.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private PermissionGrant mapGrant(Long resourceId, Long actionId, String actionCode,
                                     String effect, String scopeJson) {
        return new PermissionGrant(resourceId, actionId, actionCode,
                StringUtils.hasText(effect) ? effect.trim().toUpperCase(Locale.ROOT) : EFFECT_ALLOW,
                scopeJson);
    }

    private PermissionDecision allowed(SysResource resource, ActionDefinition action, String scopeJson) {
        return new PermissionDecision(true, ErrorCode.SUCCESS, null,
                resource.getId(), resource.getCode(), action.code(), scopeJson);
    }

    private PermissionDecision denied(ErrorCode errorCode, String message) {
        return new PermissionDecision(false, errorCode, message, null, null, null, null);
    }

    private PermissionDecision denied(ErrorCode errorCode, String message,
                                     SysResource resource, ActionDefinition action) {
        return new PermissionDecision(false, errorCode, message,
                resource == null ? null : resource.getId(),
                resource == null ? null : resource.getCode(),
                action == null ? null : action.code(), null);
    }

    public record PermissionDecision(boolean allowed, ErrorCode errorCode, String message,
                                     Long resourceId, String resourceCode, String actionCode,
                                     String scopeJson) {
    }

    public record PermissionBundle(List<SysResource> resources, Map<Long, List<String>> actions) {
    }

    public record ActionDefinition(Long id, String code, String httpMethods, String authority) {
    }

    private record PermissionGrant(Long resourceId, Long actionId, String actionCode,
                                   String effect, String scopeJson) {
    }

    private record PermissionKey(Long resourceId, String actionCode) {
    }

    private record MatchedResource(SysResource resource, String pattern) {
    }

    private record GrantDecision(boolean allowed, boolean denied, String scopeJson) {
        static GrantDecision none() {
            return new GrantDecision(false, false, null);
        }
    }
}
