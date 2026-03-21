package com.yiyundao.compensation.interfaces.controller.auth;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.service.ResourceService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/auth/me")
@RequiredArgsConstructor
public class AuthResourceController {

    private final SysUserService sysUserService;
    private final UserRoleService userRoleService;
    private final ResourceService resourceService;
    private final ObjectMapper objectMapper;

    private Authentication getAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth;
        }
        // Fallback: try to get from request attribute (set by Spring Security)
        return null;
    }

    // 基本用户信息（id、username、roles）
    @GetMapping
    public ApiResponse<Map<String, Object>> me(Authentication authentication) {
        log.debug("AuthResourceController.me: authentication param={}, name={}",
                  authentication != null ? authentication.getName() : "null",
                  authentication != null ? authentication.getName() : "null");

        // Try to get from SecurityContextHolder directly
        Authentication auth = getAuthentication();
        log.debug("AuthResourceController.me: SecurityContextHolder auth={}",
                  auth != null ? auth.getName() : "null");

        SysUser user = currentUser(auth != null ? auth : authentication);
        if (user == null) return ApiResponse.error(ErrorCode.UNAUTHORIZED, "未登录");
        Map<String, Object> data = new HashMap<>();
        data.put("id", String.valueOf(user.getId()));
        data.put("username", user.getUsername());
        data.put("roles", toRoleList(user.getId()));
        data.put("employeeId", user.getEmployeeId());
        data.put("hasEmployeeProfile", user.getEmployeeId() != null);
        return ApiResponse.success(data);
    }

    @GetMapping("/resources")
    public ApiResponse<Map<String, Object>> myResources(Authentication authentication) {
        log.debug("AuthResourceController.myResources: authentication param={}",
                  authentication != null ? authentication.getName() : "null");

        // Try to get from SecurityContextHolder directly
        Authentication auth = getAuthentication();
        log.debug("AuthResourceController.myResources: SecurityContextHolder auth={}",
                  auth != null ? auth.getName() : "null");

        SysUser user = currentUser(auth != null ? auth : authentication);
        if (user == null) return ApiResponse.error(ErrorCode.UNAUTHORIZED, "未登录");
        List<SysResource> list = resourceService.getUserResources(user.getId());
        // 仅输出菜单/页面资源，前端自行组装树
        List<Map<String, Object>> items = list.stream()
                .filter(r -> Objects.equals(r.getType(), "MENU") || Objects.equals(r.getType(), "VIEW"))
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.getId());
                    m.put("type", r.getType());
                    m.put("code", r.getCode());
                    m.put("name", r.getName());
                    m.put("path", r.getPath());
                    m.put("component", r.getComponent());
                    m.put("icon", r.getIcon());
                    m.put("parentId", r.getParentId());
                    m.put("orderNum", r.getOrderNum());
                    m.put("meta", parseJsonOrEmpty(r.getPropsJson()));
                    return m;
                }).collect(Collectors.toList());

        Map<Long, List<String>> actions = resourceService.getUserActions(user.getId());

        Map<String, Object> resp = new HashMap<>();
        resp.put("resources", items);
        resp.put("actions", actions);
        resp.put("permissionVersion", user.getPermissionVersion());
        return ApiResponse.success(resp);
    }

    @GetMapping("/actions")
    public ApiResponse<List<String>> myActions(Authentication authentication) {
        log.debug("AuthResourceController.myActions: authentication param={}",
                  authentication != null ? authentication.getName() : "null");

        // Try to get from SecurityContextHolder directly
        Authentication auth = getAuthentication();
        log.debug("AuthResourceController.myActions: SecurityContextHolder auth={}",
                  auth != null ? auth.getName() : "null");

        SysUser user = currentUser(auth != null ? auth : authentication);
        if (user == null) return ApiResponse.error(ErrorCode.UNAUTHORIZED, "未登录");
        Map<Long, List<String>> actions = resourceService.getUserActions(user.getId());
        List<String> actionCodes = actions.values().stream()
                .flatMap(Collection::stream)
                .filter(StringUtils::hasText)
                .toList();
        List<String> resourceCodes = resourceService.getUserResources(user.getId()).stream()
                .map(SysResource::getCode)
                .filter(StringUtils::hasText)
                .toList();
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        merged.addAll(actionCodes);
        merged.addAll(resourceCodes);
        return ApiResponse.success(new ArrayList<>(merged));
    }

    private SysUser currentUser(Authentication authentication) {
        if (authentication == null) return null;
        String name = authentication.getName();
        return sysUserService.findByUsername(name);
    }

    private List<String> toRoleList(Long userId) {
        java.util.Set<String> roleCodes = userRoleService.getUserRoleCodes(userId);
        if (roleCodes == null || roleCodes.isEmpty()) return List.of("ROLE_USER");
        List<String> out = new ArrayList<>();
        for (String r : roleCodes) {
            if (r != null && !r.isBlank()) {
                String role = r.trim();
                if (!role.startsWith("ROLE_")) role = "ROLE_" + role;
                out.add(role);
            }
        }
        return out.isEmpty() ? List.of("ROLE_USER") : out;
    }

    private Map<String, Object> parseJsonOrEmpty(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>(){});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
