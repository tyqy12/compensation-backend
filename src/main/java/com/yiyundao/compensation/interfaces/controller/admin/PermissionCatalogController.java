package com.yiyundao.compensation.interfaces.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.interfaces.dto.admin.PermissionActionRequest;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.DatabasePermissionCatalogService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 权限操作目录管理 API。接口自身也作为 API 资源进入数据库权限目录，
 * 不依赖角色名称或代码旁路。
 */
@RestController
@RequestMapping("/admin/permission-actions")
@RequiredArgsConstructor
@SecurityAnnotations.IsAuthenticated
public class PermissionCatalogController {

    private final DatabasePermissionCatalogService catalogService;
    private final SysUserService sysUserService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ApiResponse<List<DatabasePermissionCatalogService.ActionView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(catalogService.listActions(status, keyword));
    }

    @PostMapping
    public ApiResponse<DatabasePermissionCatalogService.ActionView> create(
            @Valid @RequestBody PermissionActionRequest request) {
        return ApiResponse.success(catalogService.create(toCommand(request), currentUserId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<DatabasePermissionCatalogService.ActionView> update(
            @PathVariable Long id,
            @Valid @RequestBody PermissionActionRequest request) {
        return ApiResponse.success(catalogService.update(id, toCommand(request), currentUserId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        catalogService.delete(id, currentUserId());
        return ApiResponse.success(null);
    }

    @GetMapping("/resources/{resourceId}")
    public ApiResponse<List<DatabasePermissionCatalogService.ActionView>> resourceActions(
            @PathVariable Long resourceId) {
        return ApiResponse.success(catalogService.listResourceActions(resourceId));
    }

    @PutMapping("/resources/{resourceId}")
    public ApiResponse<List<DatabasePermissionCatalogService.ActionView>> replaceResourceActions(
            @PathVariable Long resourceId,
            @RequestBody ResourceActionRequest request) {
        if (request == null) {
            request = new ResourceActionRequest();
        }
        return ApiResponse.success(catalogService.replaceResourceActions(
                resourceId, request.getActionCodes(), request.getActionIds(), currentUserId()));
    }

    private DatabasePermissionCatalogService.ActionCommand toCommand(PermissionActionRequest request) {
        DatabasePermissionCatalogService.ActionCommand command = new DatabasePermissionCatalogService.ActionCommand();
        command.setCode(request.getCode());
        command.setName(request.getName());
        command.setDescription(request.getDescription());
        command.setHttpMethods(request.getHttpMethods());
        command.setAuthority(request.getAuthority());
        command.setStatus(request.getStatus());
        command.setOrderNum(request.getOrderNum());
        if (request.getProps() != null && !request.getProps().isEmpty()) {
            try {
                command.setPropsJson(objectMapper.writeValueAsString(request.getProps()));
            } catch (Exception e) {
                throw new IllegalArgumentException("操作扩展配置无法序列化", e);
            }
        }
        return command;
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !StringUtils.hasText(authentication.getName())
                || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            throw new AccessDeniedException("未登录或登录已过期");
        }
        SysUser user = sysUserService.findByUsername(authentication.getName());
        if (user == null || user.getId() == null) {
            throw new AccessDeniedException("当前用户不存在");
        }
        return user.getId();
    }

    @Data
    public static class ResourceActionRequest {
        private List<String> actionCodes;
        private List<Long> actionIds;
    }
}
