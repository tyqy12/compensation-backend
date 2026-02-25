package com.yiyundao.compensation.interfaces.controller.admin;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.interfaces.dto.role.CreateRoleRequest;
import com.yiyundao.compensation.interfaces.dto.role.RoleResourceAssignRequest;
import com.yiyundao.compensation.interfaces.dto.role.UpdateRoleRequest;
import com.yiyundao.compensation.interfaces.vo.role.RoleDetailVO;
import com.yiyundao.compensation.interfaces.vo.role.RoleVO;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.entity.SysRole;
import com.yiyundao.compensation.modules.rbac.service.RoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色管理控制器
 * <p>
 * 提供角色的增删改查及权限分配功能
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@RestController
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "角色的增删改查及权限分配")
@SecurityRequirement(name = "Bearer")
@SecurityAnnotations.IsAdmin
public class RoleController {

    private final RoleService roleService;
    private final SysUserService sysUserService;

    // ==================== 角色 CRUD ====================

    @PostMapping
    @Operation(summary = "创建角色", description = "创建新角色，支持初始资源分配")
    public ApiResponse<RoleVO> create(@Valid @RequestBody CreateRoleRequest request) {
        Long operatorId = getCurrentUserId();
        SysRole role = roleService.createRole(request, operatorId);
        return ApiResponse.success(RoleVO.fromEntity(role));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取角色详情", description = "获取角色详细信息，包含用户数和资源数")
    public ApiResponse<RoleDetailVO> getDetail(@PathVariable Long id) {
        return ApiResponse.success(roleService.getRoleDetail(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新角色", description = "更新角色信息，系统保护角色不可修改")
    public ApiResponse<RoleVO> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        Long operatorId = getCurrentUserId();
        SysRole role = roleService.updateRole(id, request, operatorId);
        return ApiResponse.success(RoleVO.fromEntity(role));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色", description = "删除角色，系统保护角色和有关联用户的角色不可删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long operatorId = getCurrentUserId();
        roleService.deleteRole(id, operatorId);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/disable")
    @Operation(summary = "禁用角色", description = "禁用角色，系统保护角色不可禁用")
    public ApiResponse<RoleVO> disable(@PathVariable Long id) {
        Long operatorId = getCurrentUserId();
        roleService.disableRole(id, operatorId);
        return ApiResponse.success(RoleVO.fromEntity(roleService.getById(id)));
    }

    @PutMapping("/{id}/enable")
    @Operation(summary = "启用角色", description = "启用角色")
    public ApiResponse<RoleVO> enable(@PathVariable Long id) {
        Long operatorId = getCurrentUserId();
        roleService.enableRole(id, operatorId);
        return ApiResponse.success(RoleVO.fromEntity(roleService.getById(id)));
    }

    @PostMapping("/{id}/copy")
    @Operation(summary = "复制角色", description = "复制角色的配置生成新角色")
    public ApiResponse<RoleVO> copy(
            @PathVariable Long id,
            @RequestParam String newCode,
            @RequestParam String newName) {
        Long operatorId = getCurrentUserId();
        SysRole role = roleService.copyRole(id, newCode, newName, operatorId);
        return ApiResponse.success(RoleVO.fromEntity(role));
    }

    // ==================== 角色列表 ====================

    @GetMapping
    @Operation(summary = "角色列表", description = "获取角色列表，支持关键词和状态筛选")
    public ApiResponse<List<RoleVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleType,
            @RequestParam(required = false) String status) {
        List<SysRole> roles = roleService.listRoles(keyword, roleType, status);
        return ApiResponse.success(RoleVO.fromEntities(roles));
    }

    @GetMapping("/enabled")
    @Operation(summary = "启用的角色列表", description = "获取所有启用的角色列表")
    public ApiResponse<List<RoleVO>> listEnabled() {
        List<SysRole> roles = roleService.listEnabledRoles();
        return ApiResponse.success(RoleVO.fromEntities(roles));
    }

    // ==================== 权限分配 ====================

    @GetMapping("/{id}/permissions")
    @Operation(summary = "获取角色资源权限", description = "获取角色的资源权限列表")
    public ApiResponse<List<RoleDetailVO.ResourceBriefVO>> getResources(@PathVariable Long id) {
        Map<SysResource, Set<String>> resources = roleService.getRoleResources(id);
        List<RoleDetailVO.ResourceBriefVO> result = resources.entrySet().stream()
                .map(e -> {
                    RoleDetailVO.ResourceBriefVO vo = new RoleDetailVO.ResourceBriefVO();
                    vo.setId(e.getKey().getId());
                    vo.setCode(e.getKey().getCode());
                    vo.setName(e.getKey().getName());
                    vo.setType(e.getKey().getType());
                    vo.setActions(e.getValue().stream().toList());
                    return vo;
                })
                .toList();
        return ApiResponse.success(result);
    }

    @PutMapping("/{id}/permissions")
    @Operation(summary = "分配角色资源权限", description = "为角色分配资源权限，支持替换或追加模式")
    public ApiResponse<Void> assignResources(
            @PathVariable Long id,
            @Valid @RequestBody RoleResourceAssignRequest request) {
        Long operatorId = getCurrentUserId();
        roleService.assignResources(id, request, operatorId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}/permissions")
    @Operation(summary = "撤销角色资源权限", description = "撤销角色的资源权限，resourceIds为空时撤销所有")
    public ApiResponse<Void> revokeResources(
            @PathVariable Long id,
            @RequestParam(required = false) List<Long> resourceIds) {
        Long operatorId = getCurrentUserId();
        roleService.revokeResources(id, resourceIds, operatorId);
        return ApiResponse.success(null);
    }

    // ==================== 兼容 /resources 路径（与数据库资源配置保持一致） ====================

    @PutMapping("/{id}/resources")
    @Operation(summary = "分配角色资源权限", description = "为角色分配资源权限，支持替换或追加模式")
    public ApiResponse<Void> assignResourcesCompat(
            @PathVariable Long id,
            @Valid @RequestBody RoleResourceAssignRequest request) {
        Long operatorId = getCurrentUserId();
        roleService.assignResources(id, request, operatorId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/resources")
    @Operation(summary = "获取角色资源权限", description = "获取角色的资源权限列表")
    public ApiResponse<List<RoleDetailVO.ResourceBriefVO>> getResourcesCompat(@PathVariable Long id) {
        Map<SysResource, Set<String>> resources = roleService.getRoleResources(id);
        List<RoleDetailVO.ResourceBriefVO> result = resources.entrySet().stream()
                .map(e -> {
                    RoleDetailVO.ResourceBriefVO vo = new RoleDetailVO.ResourceBriefVO();
                    vo.setId(e.getKey().getId());
                    vo.setCode(e.getKey().getCode());
                    vo.setName(e.getKey().getName());
                    vo.setType(e.getKey().getType());
                    vo.setActions(e.getValue().stream().toList());
                    return vo;
                })
                .toList();
        return ApiResponse.success(result);
    }

    @DeleteMapping("/{id}/resources")
    @Operation(summary = "撤销角色资源权限", description = "撤销角色的资源权限，resourceIds为空时撤销所有")
    public ApiResponse<Void> revokeResourcesCompat(
            @PathVariable Long id,
            @RequestParam(required = false) List<Long> resourceIds) {
        Long operatorId = getCurrentUserId();
        roleService.revokeResources(id, resourceIds, operatorId);
        return ApiResponse.success(null);
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取当前登录用户ID
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new org.springframework.security.access.AccessDeniedException("未登录或登录已过期");
        }

        String username = auth.getName();
        if (username == null || username.isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("无法获取当前用户信息");
        }

        // 从数据库查询用户
        SysUser user = sysUserService.findByUsername(username);
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("用户不存在: " + username);
        }

        return user.getId();
    }
}
