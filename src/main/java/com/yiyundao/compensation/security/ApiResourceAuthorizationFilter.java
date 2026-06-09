package com.yiyundao.compensation.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.UserStatus;
import com.yiyundao.compensation.infrastructure.dao.SysResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.service.ResourceService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.rbac.service.impl.ResourceChangeListener.ResourceChangeEvent;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API 资源授权过滤器
 * <p>
 * 基于数据库资源定义进行动态权限校验
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Slf4j
@RequiredArgsConstructor
public class ApiResourceAuthorizationFilter extends OncePerRequestFilter {

    private final SysResourceMapper sysResourceMapper;
    private final SysUserMapper sysUserMapper;
    private final ResourceService resourceService;
    private final UserRoleService userRoleService;
    private final ObjectMapper objectMapper;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final Set<String> FAIL_CLOSED_PREFIXES = Set.of(
            "/admin",
            "/v1/admin",
            "/approval",
            "/dashboard",
            "/employee",
            "/employees",
            "/payment",
            "/payroll",
            "/settlement",
            "/system"
    );

    private static final Set<String> UNMATCHED_ALLOW_PREFIXES = Set.of(
            "/auth",
            "/actuator",
            "/openapi",
            "/v1/oauth",
            "/v1/payroll",
            "/v1/payslips",
            "/v1/ping",
            "/v1/settlement/callback",
            "/alipay/notify",
            "/favicon.ico",
            "/v3/api-docs",
            "/swagger-ui",
            "/webjars"
    );

    private volatile List<SysResource> apiResourcesCache;
    private final AtomicLong lastLoadTs = new AtomicLong(0);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleResourceChange(ResourceChangeEvent event) {
        evictApiResourcesCache();
    }

    void evictApiResourcesCache() {
        apiResourcesCache = null;
        lastLoadTs.set(0);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String uri = request.getRequestURI();           // e.g. /api/payment/batch/123/start
        String servletPath = request.getServletPath();   // e.g. /payment/batch/123/start

        log.debug("ApiResourceAuthorizationFilter: method={}, uri={}, servletPath={}", method, uri, servletPath);

        List<SysResource> matchedResources = matchApiResources(method, uri, servletPath);
        log.debug("ApiResourceAuthorizationFilter: matched={}",
                matchedResources.isEmpty()
                        ? "null"
                        : matchedResources.stream().map(SysResource::getCode).toList());

        if (matchedResources.isEmpty()) {
            if (shouldDenyUnmatchedResource(uri, servletPath)) {
                if (isAuthenticatedAdmin()) {
                    log.debug("ApiResourceAuthorizationFilter: unmatched protected API resource allowed for admin, method={}, uri={}, servletPath={}",
                            method, uri, servletPath);
                    filterChain.doFilter(request, response);
                    return;
                }
                log.warn("ApiResourceAuthorizationFilter: unmatched protected API resource denied, method={}, uri={}, servletPath={}",
                        method, uri, servletPath);
                writeJson(response, ErrorCode.FORBIDDEN, "接口未配置访问权限");
                return;
            }
            log.debug("ApiResourceAuthorizationFilter: No API resource matched, passing through");
            filterChain.doFilter(request, response);
            return;
        }

        // 已匹配到 API 资源，进行用户授权校验
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        log.debug("ApiResourceAuthorizationFilter: auth={}, isAuthenticated={}", auth, auth != null ? auth.isAuthenticated() : "null");

        if (auth == null || !auth.isAuthenticated()) {
            writeJson(response, ErrorCode.UNAUTHORIZED, "未登录");
            return;
        }
        String username = auth.getName();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).last("limit 1"));
        if (user == null) {
            writeJson(response, ErrorCode.UNAUTHORIZED, "未登录");
            return;
        }
        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            writeJson(response, ErrorCode.UNAUTHORIZED, "账号已禁用");
            return;
        }

        // 管理员放行（使用双重检查策略确保数据一致性）
        if (isAdminUser(user.getId(), user)) {
            filterChain.doFilter(request, response);
            return;
        }

        List<SysResource> roleMatchedResources = matchedResources.stream()
                .filter(resource -> hasRequiredRoles(user.getId(), resource))
                .toList();
        if (roleMatchedResources.isEmpty()) {
            writeJson(response, ErrorCode.FORBIDDEN, "无权限访问该接口（角色不匹配）");
            return;
        }

        Set<Long> grantedResourceIds = new HashSet<>();
        List<SysResource> userResources = resourceService.getUserResources(user.getId());
        if (userResources == null) {
            userResources = List.of();
        }
        userResources.stream()
                .map(SysResource::getId)
                .forEach(grantedResourceIds::add);
        List<SysResource> grantedMatchedResources = roleMatchedResources.stream()
                .filter(resource -> grantedResourceIds.contains(resource.getId()))
                .toList();
        if (grantedMatchedResources.isEmpty()) {
            writeJson(response, ErrorCode.FORBIDDEN, "无权限访问该接口");
            return;
        }

        Map<Long, List<String>> userActions = resourceService.getUserActions(user.getId());
        Map<Long, List<String>> effectiveUserActions = userActions == null ? Map.of() : userActions;
        if (grantedMatchedResources.stream()
                .anyMatch(resource -> hasRequiredAction(method, effectiveUserActions.get(resource.getId())))) {
            filterChain.doFilter(request, response);
            return;
        }

        String requiredAction = mapHttpMethodToAction(method);
        writeJson(response, ErrorCode.FORBIDDEN, "无权限执行该操作（需要 " + requiredAction + " 权限）");
    }

    /**
     * 将 HTTP 方法映射为操作权限
     *
     * @param httpMethod HTTP 方法
     * @return 操作权限
     */
    private String mapHttpMethodToAction(String httpMethod) {
        return switch (httpMethod.toUpperCase()) {
            case "GET" -> "read";
            case "POST" -> "write";
            case "PUT" -> "write";
            case "DELETE" -> "delete";
            case "PATCH" -> "write";
            default -> httpMethod.toLowerCase();
        };
    }

    /**
     * 检查用户是否为管理员
     * <p>
     * 使用 UserRoleService 进行角色检查（带缓存）
     * </p>
     *
     * @param userId 用户ID
     * @param user   用户实体
     * @return 是否为管理员
     */
    private boolean isAdminUser(Long userId, SysUser user) {
        // 使用 UserRoleService 检查角色（带缓存）
        return userRoleService.hasRole(userId, SecurityConstants.ROLE_ADMIN);
    }

    private boolean isAuthenticatedAdmin() {
        Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(authority -> SecurityConstants.ROLE_ADMIN.equals(authority.getAuthority()));
    }

    /**
     * 匹配 API 资源
     */
    private List<SysResource> matchApiResources(String method, String uri, String servletPath) {
        long now = System.currentTimeMillis();
        // 使用常量消除硬编码
        if (apiResourcesCache == null || (now - lastLoadTs.get()) > SecurityConstants.API_RESOURCE_CACHE_TTL_MS) {
            apiResourcesCache = sysResourceMapper.selectList(new LambdaQueryWrapper<SysResource>()
                    .eq(SysResource::getType, "API")
                    .eq(SysResource::getStatus, "enabled"));
            lastLoadTs.set(now);
        }

        List<MatchedResource> matched = apiResourcesCache.stream()
                .filter(resource -> matchesApiResource(resource, method, uri, servletPath))
                .map(resource -> new MatchedResource(
                        resource,
                        resourceMatchPattern(resource, uri, servletPath)
                ))
                .sorted(Comparator
                        .comparingInt((MatchedResource matchedResource) -> patternSpecificityScore(matchedResource.pattern()))
                        .thenComparing(MatchedResource::pattern, Comparator.nullsLast(String::compareTo))
                        .reversed()
                )
                .toList();
        if (matched.isEmpty()) {
            return List.of();
        }

        MatchedResource best = matched.get(0);
        int bestScore = patternSpecificityScore(best.pattern());
        String bestPattern = best.pattern();
        return matched.stream()
                .filter(item -> patternSpecificityScore(item.pattern()) == bestScore
                        && Objects.equals(item.pattern(), bestPattern))
                .map(MatchedResource::resource)
                .toList();
    }

    private boolean matchesApiResource(SysResource resource, String method, String uri, String servletPath) {
        if (resource == null || !StringUtils.hasText(resource.getPath())) {
            return false;
        }

        String requiredMethod = extractMethod(resource.getPropsJson());
        if (requiredMethod != null && !requiredMethod.equalsIgnoreCase(method)) {
            return false;
        }

        String pattern = resourceMatchPattern(resource, uri, servletPath);
        return pattern != null;
    }

    private boolean shouldDenyUnmatchedResource(String uri, String servletPath) {
        String normalizedUri = normalizePath(uri);
        String normalizedServletPath = normalizePath(servletPath);
        if (isAllowedUnmatchedPath(normalizedUri) || isAllowedUnmatchedPath(normalizedServletPath)) {
            return false;
        }
        return isFailClosedPath(normalizedUri) || isFailClosedPath(normalizedServletPath);
    }

    private boolean isAllowedUnmatchedPath(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        return UNMATCHED_ALLOW_PREFIXES.stream().anyMatch(prefix -> matchesPrefix(path, prefix));
    }

    private boolean isFailClosedPath(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        return FAIL_CLOSED_PREFIXES.stream().anyMatch(prefix -> matchesPrefix(path, prefix));
    }

    private boolean matchesPrefix(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String normalized = path.trim();
        if (normalized.startsWith("/api/")) {
            normalized = normalized.substring(4);
        } else if (normalized.equals("/api")) {
            normalized = "/";
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String resourceMatchPattern(SysResource resource, String uri, String servletPath) {
        if (resource == null || !StringUtils.hasText(resource.getPath())) {
            return null;
        }

        String pattern = resource.getPath();
        if (pathMatcher.match(pattern, uri)) {
            return pattern;
        }

        if (!pattern.startsWith("/api")) {
            if (pathMatcher.match(pattern, servletPath)) {
                return pattern;
            }
            String normalized = pattern.startsWith("/") ? "/api" + pattern : "/api/" + pattern;
            if (pathMatcher.match(normalized, uri)) {
                return normalized;
            }
            return null;
        }

        String withoutPrefix = pattern.substring(4);
        if (!withoutPrefix.startsWith("/")) {
            withoutPrefix = "/" + withoutPrefix;
        }
        if (pathMatcher.match(withoutPrefix, servletPath)) {
            return withoutPrefix;
        }
        return null;
    }

    private boolean hasRequiredRoles(Long userId, SysResource resource) {
        List<String> requiredRoles = extractRoles(resource.getPropsJson());
        return requiredRoles.isEmpty() || userRoleService.hasAnyRole(userId, requiredRoles.toArray(String[]::new));
    }

    private boolean hasRequiredAction(String method, List<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return true;
        }
        String requiredAction = mapHttpMethodToAction(method);
        String requiredActionUpper = requiredAction.toUpperCase();
        String methodUpper = method.toUpperCase();
        return actions.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(action -> "*".equals(action)
                        || requiredAction.equalsIgnoreCase(action)
                        || requiredActionUpper.equalsIgnoreCase(action)
                        || methodUpper.equalsIgnoreCase(action));
    }

    private record MatchedResource(SysResource resource, String pattern) {
    }

    private int patternSpecificityScore(String pattern) {
        if (!StringUtils.hasText(pattern)) {
            return Integer.MIN_VALUE;
        }

        int wildcardCount = 0;
        int variableCount = 0;
        int staticSegmentCount = 0;

        for (String rawSegment : pattern.split("/")) {
            if (!StringUtils.hasText(rawSegment)) {
                continue;
            }
            if (rawSegment.contains("*")) {
                wildcardCount++;
                continue;
            }
            if (rawSegment.startsWith("{") && rawSegment.endsWith("}")) {
                variableCount++;
                continue;
            }
            staticSegmentCount++;
        }

        return staticSegmentCount * 1000 - variableCount * 100 - wildcardCount * 10 + pattern.length();
    }

    /**
     * 从 propsJson 提取 HTTP 方法
     */
    private String extractMethod(String propsJson) {
        if (!StringUtils.hasText(propsJson)) return null;
        try {
            JsonNode node = objectMapper.readTree(propsJson);
            JsonNode m = node.get("method");
            return m == null ? null : m.asText();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 从 propsJson 提取角色约束，兼容 ["ADMIN"] 和 ["ROLE_ADMIN"]。
     */
    private List<String> extractRoles(String propsJson) {
        if (!StringUtils.hasText(propsJson)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(propsJson);
            JsonNode rolesNode = node.get("roles");
            if (rolesNode == null || rolesNode.isNull()) {
                return List.of();
            }
            List<String> roles = new ArrayList<>();
            if (rolesNode.isArray()) {
                rolesNode.forEach(role -> addRoleIfPresent(roles, role.asText()));
            } else {
                addRoleIfPresent(roles, rolesNode.asText());
            }
            return roles;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void addRoleIfPresent(List<String> roles, String role) {
        if (!StringUtils.hasText(role)) {
            return;
        }
        roles.add(role.trim());
    }

    /**
     * 写入 JSON 响应
     */
    private void writeJson(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.getHttpStatusCode());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        String body = objectMapper.writeValueAsString(ApiResponse.error(errorCode, message));
        response.getWriter().write(body);
    }
}
