package com.yiyundao.compensation.modules.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.SysResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysRoleMapper;
import com.yiyundao.compensation.infrastructure.dao.SysRoleResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.interfaces.dto.role.CreateRoleRequest;
import com.yiyundao.compensation.interfaces.dto.role.RoleResourceAssignRequest;
import com.yiyundao.compensation.interfaces.dto.role.UpdateRoleRequest;
import com.yiyundao.compensation.interfaces.vo.role.RoleDetailVO;
import com.yiyundao.compensation.interfaces.vo.role.RoleVO;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.entity.SysRole;
import com.yiyundao.compensation.modules.rbac.entity.SysRoleResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserRole;
import com.yiyundao.compensation.modules.rbac.service.RoleService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.DatabasePermissionAssignmentService;
import com.yiyundao.compensation.security.DatabasePermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 角色服务实现
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements RoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleResourceMapper roleResourceMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysResourceMapper resourceMapper;
    private final SysUserService sysUserService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    private final DatabasePermissionAssignmentService databasePermissionAssignmentService;
    private final DatabasePermissionService databasePermissionService;

    // ==================== 角色 CRUD ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysRole createRole(CreateRoleRequest request, Long operatorId) {
        Assert.notNull(request, "创建请求不能为空");
        Assert.hasText(request.getCode(), "角色编码不能为空");
        Assert.hasText(request.getName(), "角色名称不能为空");

        // 检查编码唯一性
        checkCodeUnique(request.getCode(), null);

        SysRole role = new SysRole();
        role.setCode(request.getCode().trim().toUpperCase());
        role.setName(request.getName().trim());
        role.setDescription(trimToNull(request.getDescription()));
        role.setRoleType(request.getRoleType() != null ? request.getRoleType() : "BUSINESS");
        role.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        role.setIcon(trimToNull(request.getIcon()));
        role.setRemarks(trimToNull(request.getRemarks()));
        role.setIsEditable(true);
        role.setStatus(SysRole.Status.ENABLED.getCode());
        role.setCreateBy(String.valueOf(operatorId));
        role.setUpdateBy(String.valueOf(operatorId));

        save(role);

        // 记录审计日志
        logAudit("ROLE_CREATE", role.getId(), role.getCode(), operatorId,
                "创建角色: code=" + role.getCode() + ", name=" + role.getName());

        log.info("创建角色成功: code={}, name={}, operatorId={}", role.getCode(), role.getName(), operatorId);

        // 如果有初始资源分配
        if (CollectionUtils.isNotEmpty(request.getResourceIds())) {
            assignResourcesInternal(role.getId(), request.getResourceIds(), request.getActions(), operatorId);
        }

        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysRole updateRole(Long id, UpdateRoleRequest request, Long operatorId) {
        Assert.notNull(id, "角色ID不能为空");
        Assert.notNull(request, "更新请求不能为空");

        SysRole role = getById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }

        if (role.isProtected()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统保护角色不可修改");
        }

        boolean changed = false;

        // 更新名称
        if (StringUtils.hasText(request.getName()) && !request.getName().equals(role.getName())) {
            role.setName(request.getName().trim());
            changed = true;
        }

        // 更新描述
        String newDesc = trimToNull(request.getDescription());
        if (!Objects.equals(newDesc, role.getDescription())) {
            role.setDescription(newDesc);
            changed = true;
        }

        // 更新图标
        String newIcon = trimToNull(request.getIcon());
        if (!Objects.equals(newIcon, role.getIcon())) {
            role.setIcon(newIcon);
            changed = true;
        }

        // 更新备注
        String newRemarks = trimToNull(request.getRemarks());
        if (!Objects.equals(newRemarks, role.getRemarks())) {
            role.setRemarks(newRemarks);
            changed = true;
        }

        // 更新排序
        if (request.getSortOrder() != null && !request.getSortOrder().equals(role.getSortOrder())) {
            role.setSortOrder(request.getSortOrder());
            changed = true;
        }

        // 更新状态
        if (StringUtils.hasText(request.getStatus()) && !request.getStatus().equals(role.getStatus())) {
            role.setStatus(request.getStatus());
            changed = true;
        }

        // 更新可编辑状态（仅非系统角色可修改）
        if (request.getIsEditable() != null && !request.getIsEditable().equals(role.getIsEditable())) {
            role.setIsEditable(request.getIsEditable());
            changed = true;
        }

        if (changed) {
            role.setUpdateBy(String.valueOf(operatorId));
            role.setUpdateTime(LocalDateTime.now());
            updateById(role);

            // 记录审计日志
            logAudit("ROLE_UPDATE", role.getId(), role.getCode(), operatorId,
                    "更新角色: code=" + role.getCode());

            log.info("更新角色成功: id={}, operatorId={}", id, operatorId);
        }

        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id, Long operatorId) {
        Assert.notNull(id, "角色ID不能为空");

        SysRole role = getById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }

        if (role.isProtected()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统保护角色不可删除");
        }

        // 检查是否有用户关联
        Long userCount = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id));
        if (userCount > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该角色仍有用户关联，请先解除关联");
        }

        // 删除角色资源关联
        roleResourceMapper.delete(new LambdaQueryWrapper<SysRoleResource>()
                .eq(SysRoleResource::getRoleId, id));
        databasePermissionAssignmentService.revokeRolePermissions(id, null, operatorId);

        // 删除角色
        removeById(id);

        // 记录审计日志
        logAudit("ROLE_DELETE", id, role.getCode(), operatorId,
                "删除角色: code=" + role.getCode());

        log.info("删除角色成功: id={}, code={}, operatorId={}", id, role.getCode(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableRole(Long id, Long operatorId) {
        SysRole role = getByIdOrThrow(id);
        if (role.isProtected()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统保护角色不可禁用");
        }
        updateRoleStatus(role, SysRole.Status.DISABLED.getCode(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableRole(Long id, Long operatorId) {
        SysRole role = getByIdOrThrow(id);
        updateRoleStatus(role, SysRole.Status.ENABLED.getCode(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysRole copyRole(Long sourceRoleId, String newCode, String newName, Long operatorId) {
        Assert.notNull(sourceRoleId, "源角色ID不能为空");
        Assert.hasText(newCode, "新角色编码不能为空");
        Assert.hasText(newName, "新角色名称不能为空");

        SysRole source = getByIdOrThrow(sourceRoleId);
        checkCodeUnique(newCode.trim().toUpperCase(), null);

        // 创建新角色
        SysRole copy = new SysRole();
        copy.setCode(newCode.trim().toUpperCase());
        copy.setName(newName.trim());
        copy.setDescription(source.getDescription());
        copy.setRoleType("CUSTOM"); // 复制的角色为自定义类型
        copy.setSortOrder(source.getSortOrder() != null ? source.getSortOrder() + 1 : 0);
        copy.setIcon(source.getIcon());
        copy.setRemarks("复制自角色: " + source.getName());
        copy.setIsEditable(true);
        copy.setStatus(SysRole.Status.ENABLED.getCode());
        copy.setCreateBy(String.valueOf(operatorId));
        copy.setUpdateBy(String.valueOf(operatorId));

        save(copy);

        // 复制资源权限
        List<SysRoleResource> sourceResources = roleResourceMapper.selectList(
                new LambdaQueryWrapper<SysRoleResource>().eq(SysRoleResource::getRoleId, sourceRoleId));
        if (CollectionUtils.isNotEmpty(sourceResources)) {
            for (SysRoleResource sr : sourceResources) {
                SysRoleResource newSr = new SysRoleResource();
                newSr.setRoleId(copy.getId());
                newSr.setResourceId(sr.getResourceId());
                newSr.setActionsJson(sr.getActionsJson());
                roleResourceMapper.insert(newSr);
            }
        }

        Map<Long, List<String>> sourcePermissions = databasePermissionService.getRoleActionCodes(sourceRoleId)
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new ArrayList<>(entry.getValue()),
                        (left, right) -> right, LinkedHashMap::new));
        databasePermissionAssignmentService.replaceRolePermissions(
                copy.getId(), sourcePermissions.keySet(), sourcePermissions, operatorId);

        log.info("复制角色成功: sourceId={}, newCode={}, newName={}, operatorId={}",
                sourceRoleId, newCode, newName, operatorId);
        return copy;
    }

    // ==================== 角色查询 ====================

    @Override
    public RoleDetailVO getRoleDetail(Long id) {
        SysRole role = getByIdOrThrow(id);
        RoleDetailVO vo = (RoleDetailVO) RoleVO.fromEntity(role);

        // 统计用户数
        Long userCount = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id));
        vo.setUserCount(userCount.intValue());

        // 统计运行时真正生效的资源数，避免旧授权表与新权限表显示不一致。
        int resourceCount = databasePermissionService.countRoleResources(Set.of(id))
                .getOrDefault(id, 0L)
                .intValue();
        vo.setResourceCount(resourceCount);

        return vo;
    }

    @Override
    public SysRole getByCode(String code) {
        Assert.hasText(code, "角色编码不能为空");
        return roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, code.toUpperCase().trim()));
    }

    @Override
    public List<SysRole> listEnabledRoles() {
        return list(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getStatus, SysRole.Status.ENABLED.getCode())
                .orderByAsc(SysRole::getSortOrder));
    }

    @Override
    public List<SysRole> listRoles(String keyword, String roleType, String status) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();

        // 关键词搜索
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim() + "%";
            wrapper.and(w -> w.like(SysRole::getCode, kw)
                    .or().like(SysRole::getName, kw)
                    .or().like(SysRole::getDescription, kw));
        }

        // 类型筛选
        if (StringUtils.hasText(roleType)) {
            wrapper.eq(SysRole::getRoleType, roleType.toUpperCase());
        }

        // 状态筛选
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysRole::getStatus, status);
        }

        wrapper.orderByAsc(SysRole::getSortOrder);
        List<SysRole> roles = list(wrapper);

        // 批量查询统计信息，避免 N+1 查询问题
        if (!roles.isEmpty()) {
            enrichRolesWithStats(roles);
        }

        return roles;
    }

    /**
     * 批量填充角色的统计信息（用户数、资源数）
     */
    private void enrichRolesWithStats(List<SysRole> roles) {
        Set<Long> roleIds = roles.stream()
                .map(SysRole::getId)
                .collect(Collectors.toSet());

        // 批量查询每个角色的用户数
        Map<Long, Long> userCountMap = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>()
                                .in(SysUserRole::getRoleId, roleIds))
                .stream()
                .collect(Collectors.groupingBy(
                        SysUserRole::getRoleId,
                        Collectors.counting()));

        // 运行时只认数据库驱动的新权限表，统计也必须使用同一口径。
        Map<Long, Long> resourceCountMap = databasePermissionService.countRoleResources(roleIds);

        // 填充统计信息到角色对象
        for (SysRole role : roles) {
            Long userCount = userCountMap.getOrDefault(role.getId(), 0L);
            Long resourceCount = resourceCountMap.getOrDefault(role.getId(), 0L);
            role.setUserCount(userCount.intValue());
            role.setResourceCount(resourceCount.intValue());
        }
    }

    // ==================== 权限分配 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignResources(Long roleId, RoleResourceAssignRequest request, Long operatorId) {
        SysRole role = getByIdOrThrow(roleId);
        if (role.isProtected()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统保护角色的权限不可修改");
        }

        if (request == null || CollectionUtils.isEmpty(request.getResources())) {
            // 清空所有资源
            revokeResources(roleId, null, operatorId);
            return;
        }

        List<Long> resourceIds = request.getResources().stream()
                .map(RoleResourceAssignRequest.ResourceAssignment::getResourceId)
                .toList();

        // 如果是替换模式，先清空
        if (Boolean.TRUE.equals(request.getReplaceExisting())) {
            revokeResources(roleId, null, operatorId);
        }

        Map<Long, List<String>> requestedPermissions = request.getResources().stream()
                .collect(Collectors.toMap(
                        RoleResourceAssignRequest.ResourceAssignment::getResourceId,
                        RoleResourceAssignRequest.ResourceAssignment::getActions,
                        (left, right) -> right,
                        LinkedHashMap::new));
        if (Boolean.TRUE.equals(request.getReplaceExisting())) {
            databasePermissionAssignmentService.replaceRolePermissions(
                    roleId, requestedPermissions.keySet(), requestedPermissions, operatorId);
        } else {
            databasePermissionAssignmentService.upsertRolePermissions(roleId, requestedPermissions, operatorId);
        }

        // 批量分配
        for (RoleResourceAssignRequest.ResourceAssignment assignment : request.getResources()) {
            List<String> actions = assignment.getActions();
            String actionsJson = actionsToJson(actions);
            assignResourcesInternal(roleId, assignment.getResourceId(), actionsJson, operatorId);
        }

        // 清除关联用户的权限缓存
        invalidateUserCaches(roleId);

        // 记录审计日志
        logAudit("ROLE_PERM_ASSIGN", roleId, role.getCode(), operatorId,
                "分配角色资源: code=" + role.getCode() + ", count=" + resourceIds.size());

        log.info("分配角色资源成功: roleId={}, resourceCount={}, operatorId={}",
                roleId, resourceIds.size(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeResources(Long roleId, List<Long> resourceIds, Long operatorId) {
        Assert.notNull(roleId, "角色ID不能为空");

        SysRole role = getByIdOrThrow(roleId);
        if (role.isProtected()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统保护角色的权限不可修改");
        }

        LambdaQueryWrapper<SysRoleResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleResource::getRoleId, roleId);

        if (CollectionUtils.isNotEmpty(resourceIds)) {
            wrapper.in(SysRoleResource::getResourceId, resourceIds);
        }

        roleResourceMapper.delete(wrapper);
        databasePermissionAssignmentService.revokeRolePermissions(roleId, resourceIds, operatorId);

        // 清除关联用户的权限缓存
        invalidateUserCaches(roleId);

        // 记录审计日志
        logAudit("ROLE_PERM_REVOKE", roleId, role.getCode(), operatorId,
                "撤销角色资源: code=" + role.getCode() + ", count=" + (resourceIds != null ? resourceIds.size() : "all"));

        log.info("撤销角色资源成功: roleId={}, resourceCount={}, operatorId={}",
                roleId, resourceIds != null ? resourceIds.size() : "all", operatorId);
    }

    @Override
    public Map<SysResource, Set<String>> getRoleResources(Long roleId) {
        Assert.notNull(roleId, "角色ID不能为空");
        getByIdOrThrow(roleId);

        Map<Long, Set<String>> actionMap = databasePermissionService.getRoleActionCodes(roleId);
        if (actionMap.isEmpty()) {
            return Map.of();
        }
        List<SysResource> resources = resourceMapper.selectBatchIds(actionMap.keySet());
        Map<Long, SysResource> resourceMap = resources.stream()
                .collect(Collectors.toMap(SysResource::getId, r -> r));
        Map<SysResource, Set<String>> result = new LinkedHashMap<>();
        actionMap.forEach((resourceId, actions) -> {
            SysResource resource = resourceMap.get(resourceId);
            if (resource != null && "enabled".equalsIgnoreCase(resource.getStatus())) {
                result.put(resource, actions);
            }
        });
        return result;
    }

    @Override
    public boolean hasResource(Long roleId, Long resourceId, String action) {
        Assert.notNull(roleId, "角色ID不能为空");
        Assert.notNull(resourceId, "资源ID不能为空");

        return databasePermissionService.hasRolePermission(roleId, resourceId, action);
    }

    // ==================== 私有辅助方法 ====================

    private void checkCodeUnique(String code, Long excludeId) {
        SysRole existing = getByCode(code);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException(ErrorCode.RESOURCE_EXISTS, "角色编码已存在: " + code);
        }
    }

    private SysRole getByIdOrThrow(Long id) {
        SysRole role = getById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private void updateRoleStatus(SysRole role, String status, Long operatorId) {
        if (status.equals(role.getStatus())) {
            return;
        }
        role.setStatus(status);
        role.setUpdateBy(String.valueOf(operatorId));
        role.setUpdateTime(LocalDateTime.now());
        updateById(role);

        // 清除关联用户的权限缓存
        invalidateUserCaches(role.getId());

        // 记录审计日志
        String operation = "DISABLED".equals(status) ? "ROLE_DISABLE" : "ROLE_ENABLE";
        logAudit(operation, role.getId(), role.getCode(), operatorId,
                "角色状态变更: code=" + role.getCode() + ", status=" + status);

        log.info("更新角色状态: id={}, status={}, operatorId={}", role.getId(), status, operatorId);
    }

    private void assignResourcesInternal(Long roleId, Long resourceId, String actionsJson, Long operatorId) {
        // 检查是否已存在
        SysRoleResource existing = roleResourceMapper.selectOne(
                new LambdaQueryWrapper<SysRoleResource>()
                        .eq(SysRoleResource::getRoleId, roleId)
                        .eq(SysRoleResource::getResourceId, resourceId));

        if (existing != null) {
            // 更新
            existing.setActionsJson(actionsJson);
            existing.setUpdateBy(String.valueOf(operatorId));
            existing.setUpdateTime(LocalDateTime.now());
            roleResourceMapper.updateById(existing);
        } else {
            // 新增
            SysRoleResource assignment = new SysRoleResource();
            assignment.setRoleId(roleId);
            assignment.setResourceId(resourceId);
            assignment.setActionsJson(actionsJson);
            assignment.setCreateBy(String.valueOf(operatorId));
            roleResourceMapper.insert(assignment);
        }
    }

    private void assignResourcesInternal(Long roleId, List<Long> resourceIds, List<String> actions, Long operatorId) {
        String actionsJson = actionsToJson(actions);
        for (Long resourceId : resourceIds) {
            assignResourcesInternal(roleId, resourceId, actionsJson, operatorId);
        }
        Map<Long, List<String>> permissions = resourceIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(resourceId -> resourceId, resourceId -> actions,
                        (left, right) -> right, LinkedHashMap::new));
        databasePermissionAssignmentService.replaceRolePermissions(
                roleId, permissions.keySet(), permissions, operatorId);
    }

    private void invalidateUserCaches(Long roleId) {
        // 获取该角色下的所有用户
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId));

        if (userRoles.isEmpty()) {
            log.debug("角色没有关联用户，无需清除缓存: roleId={}", roleId);
            return;
        }

        Set<Long> userIds = userRoles.stream()
                .map(SysUserRole::getUserId)
                .collect(Collectors.toSet());

        // 批量清除缓存（通过递增权限版本）
        sysUserService.batchIncrementPermissionVersion(userIds);

        log.info("已清除 {} 个用户的权限缓存", userIds.size());
    }

    private String actionsToJson(List<String> actions) {
        if (CollectionUtils.isEmpty(actions)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(actions);
        } catch (JsonProcessingException e) {
            log.warn("序列化操作权限失败: {}", e.getMessage());
            return null;
        }
    }

    private Set<String> parseActions(String actionsJson) {
        if (!StringUtils.hasText(actionsJson)) {
            return Set.of();
        }
        try {
            return objectMapper.readValue(actionsJson, new TypeReference<Set<String>>() {});
        } catch (JsonProcessingException e) {
            // 兼容旧格式：逗号分隔
            return Arrays.stream(actionsJson.split("[,\\s]+"))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 记录审计日志
     *
     * @param operation 操作类型
     * @param businessKey 业务键
     * @param businessType 业务类型
     * @param operatorId 操作人ID
     * @param detail 详情描述
     */
    private void logAudit(String operation, Long businessKey, String businessType, Long operatorId, String detail) {
        try {
            auditLogService.record(
                    operation,
                    "SYSTEM",
                    "RoleService",
                    "127.0.0.1",
                    "System",
                    businessType,
                    String.valueOf(businessKey),
                    String.valueOf(operatorId),
                    null,
                    detail,
                    null,
                    null
            );
        } catch (Exception e) {
            log.warn("记录审计日志失败: operation={}, businessKey={}", operation, businessKey, e);
        }
    }
}
