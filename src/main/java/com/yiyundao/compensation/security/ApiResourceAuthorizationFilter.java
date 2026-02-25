package com.yiyundao.compensation.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.infrastructure.dao.SysResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.service.ResourceService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    private volatile List<SysResource> apiResourcesCache;
    private final AtomicLong lastLoadTs = new AtomicLong(0);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String uri = request.getRequestURI();           // e.g. /api/payment/batch/123/start
        String servletPath = request.getServletPath();   // e.g. /payment/batch/123/start

        log.debug("ApiResourceAuthorizationFilter: method={}, uri={}, servletPath={}", method, uri, servletPath);

        SysResource matched = matchApiResource(method, uri, servletPath);
        log.debug("ApiResourceAuthorizationFilter: matched={}", matched != null ? matched.getCode() : "null");

        if (matched == null) {
            log.debug("ApiResourceAuthorizationFilter: No API resource matched, passing through");
            filterChain.doFilter(request, response);
            return;
        }

        // 已匹配到 API 资源，进行用户授权校验
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        log.debug("ApiResourceAuthorizationFilter: auth={}, isAuthenticated={}", auth, auth != null ? auth.isAuthenticated() : "null");

        if (auth == null || !auth.isAuthenticated()) {
            writeJson(response, 401, "未登录");
            return;
        }
        String username = auth.getName();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).last("limit 1"));
        if (user == null) {
            writeJson(response, 401, "未登录");
            return;
        }

        // 管理员放行（使用双重检查策略确保数据一致性）
        if (isAdminUser(user.getId(), user)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 检查用户是否拥有该资源的权限
        boolean hasResource = resourceService.getUserResources(user.getId())
                .stream().anyMatch(r -> r.getId().equals(matched.getId()));
        if (!hasResource) {
            writeJson(response, 403, "无权限访问该接口");
            return;
        }

        // 检查操作权限（HTTP 方法）
        List<String> actions = resourceService.getUserActions(user.getId()).get(matched.getId());
        if (actions == null || actions.isEmpty()) {
            // 没有定义具体操作权限，允许访问
            filterChain.doFilter(request, response);
            return;
        }

        // 将 HTTP 方法映射为权限（GET -> read, POST -> write, PUT -> write, DELETE -> delete, PATCH -> write）
        String requiredAction = mapHttpMethodToAction(method);
        boolean hasPermission = actions.contains("*") || actions.contains(requiredAction) || actions.contains(method.toUpperCase());
        if (!hasPermission) {
            writeJson(response, 403, "无权限执行该操作（需要 " + requiredAction + " 权限）");
            return;
        }

        filterChain.doFilter(request, response);
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

    /**
     * 匹配 API 资源
     */
    private SysResource matchApiResource(String method, String uri, String servletPath) {
        long now = System.currentTimeMillis();
        // 使用常量消除硬编码
        if (apiResourcesCache == null || (now - lastLoadTs.get()) > SecurityConstants.API_RESOURCE_CACHE_TTL_MS) {
            apiResourcesCache = sysResourceMapper.selectList(new LambdaQueryWrapper<SysResource>()
                    .eq(SysResource::getType, "API")
                    .eq(SysResource::getStatus, "enabled"));
            lastLoadTs.set(now);
        }

        for (SysResource r : apiResourcesCache) {
            if (!StringUtils.hasText(r.getPath())) continue;
            String pattern = r.getPath();

            // 方法匹配（propsJson.method）
            String requiredMethod = extractMethod(r.getPropsJson());
            if (requiredMethod != null && !requiredMethod.equalsIgnoreCase(method)) continue;

            if (pathMatcher.match(pattern, uri)) {
                return r;
            }

            // 兼容资源路径未包含 context-path (/api) 的情况
            if (!pattern.startsWith("/api")) {
                if (pathMatcher.match(pattern, servletPath)) {
                    return r;
                }
                String normalized = pattern.startsWith("/") ? "/api" + pattern : "/api/" + pattern;
                if (pathMatcher.match(normalized, uri)) {
                    return r;
                }
            } else {
                String withoutPrefix = pattern.substring(4);
                if (!withoutPrefix.startsWith("/")) {
                    withoutPrefix = "/" + withoutPrefix;
                }
                if (pathMatcher.match(withoutPrefix, servletPath)) {
                    return r;
                }
            }
        }
        return null;
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
     * 写入 JSON 响应
     */
    private void writeJson(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(200); // 与全局响应格式保持一致
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        String body = objectMapper.writeValueAsString(ApiResponse.error(code, message));
        response.getWriter().write(body);
    }
}
