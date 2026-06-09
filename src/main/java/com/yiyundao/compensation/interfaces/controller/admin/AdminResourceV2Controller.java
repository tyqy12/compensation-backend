package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.dto.rbac.ResourceDto;
import com.yiyundao.compensation.infrastructure.dao.SysRoleResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.interfaces.dto.admin.ResourceResponseDto;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.entity.SysRoleResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserRole;
import com.yiyundao.compensation.modules.rbac.service.ResourceService;
import com.yiyundao.compensation.modules.rbac.service.impl.ResourceChangeListener.ResourceChangeEvent;
import com.yiyundao.compensation.security.SecurityAnnotations;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 资源管理控制器（V2版本）
 * <p>
 * 提供菜单资源、API资源的增删改查功能，支持树形结构和批量操作
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@RestController
@RequestMapping("/admin/resources/v2")
@RequiredArgsConstructor
@SecurityAnnotations.IsAdmin
public class AdminResourceV2Controller {

    private final ResourceService resourceService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SysRoleResourceMapper roleResourceMapper;
    private final SysUserResourceMapper userResourceMapper;
    private final SysUserRoleMapper userRoleMapper;

    /**
     * 资源列表（按 type 过滤）
     */
    @GetMapping("/list")
    public ApiResponse<List<ResourceDto>> list(@RequestParam(required = false) String type) {
        List<SysResource> list = resourceService.list(new LambdaQueryWrapper<SysResource>()
                .eq(type != null, SysResource::getType, type)
                .orderByAsc(SysResource::getOrderNum));
        return ApiResponse.success(list.stream().map(r -> ResourceDto.from(r, objectMapper)).toList());
    }

    /**
     * 资源树（按 type 过滤）
     */
    @GetMapping("/tree")
    public ApiResponse<List<ResourceResponseDto>> tree(@RequestParam(required = false) String type) {
        List<SysResource> tree = resourceService.getResourceTree(type);
        return ApiResponse.success(tree.stream()
                .map(resource -> ResourceResponseDto.from(resource, objectMapper))
                .toList());
    }

    /**
     * 新增资源
     */
    @PostMapping
    public ApiResponse<ResourceDto> create(@Valid @RequestBody ResourceDto req) {
        SysResource entity = req.toEntity(objectMapper);
        entity.setId(null);
        if (entity.getOrderNum() == null) entity.setOrderNum(0);
        if (entity.getStatus() == null) entity.setStatus("enabled");
        resourceService.save(entity);

        // 发布资源变更事件
        eventPublisher.publishEvent(new ResourceChangeEvent(
                ResourceChangeEvent.ChangeType.CREATE, entity.getId()));

        return ApiResponse.success(ResourceDto.from(entity, objectMapper));
    }

    /**
     * 更新资源
     */
    @PutMapping("/{id}")
    public ApiResponse<ResourceDto> update(@PathVariable Long id, @Valid @RequestBody ResourceDto req) {
        if (resourceService.getById(id) == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "资源不存在");
        }
        SysResource entity = req.toEntity(objectMapper);
        entity.setId(id);
        resourceService.updateById(entity);

        // 发布资源变更事件
        eventPublisher.publishEvent(new ResourceChangeEvent(
                ResourceChangeEvent.ChangeType.UPDATE, id));

        SysResource latest = resourceService.getById(id);
        return ApiResponse.success(ResourceDto.from(latest, objectMapper));
    }

    /**
     * 删除资源
     */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (resourceService.getById(id) == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "资源不存在");
        }
        boolean hasChildren = resourceService.count(new LambdaQueryWrapper<SysResource>().eq(SysResource::getParentId, id)) > 0;
        if (hasChildren) return ApiResponse.error(ErrorCode.BUSINESS_ERROR, "请先删除子资源");

        Set<Long> affectedUserIds = collectAffectedUserIdsBeforeDelete(id);

        // 清理角色-资源关联
        roleResourceMapper.delete(new LambdaQueryWrapper<SysRoleResource>()
                .eq(SysRoleResource::getResourceId, id));

        // 清理用户-资源关联
        userResourceMapper.delete(new LambdaQueryWrapper<SysUserResource>()
                .eq(SysUserResource::getResourceId, id));

        // 发布删除事件
        eventPublisher.publishEvent(new ResourceChangeEvent(
                ResourceChangeEvent.ChangeType.DELETE, id, affectedUserIds));

        resourceService.removeById(id);
        return ApiResponse.success(null);
    }

    /**
     * 批量排序（使用批量更新 SQL 优化性能）
     */
    @PostMapping("/sort")
    public ApiResponse<Void> sort(@RequestBody List<SortItem> items) {
        if (CollectionUtils.isEmpty(items)) return ApiResponse.success(null);

        // 转换为 ResourceService.SortItem
        List<ResourceService.SortItem> serviceItems = items.stream()
                .map(item -> new ResourceService.SortItem(item.getId(), item.getOrderNum()))
                .toList();

        // 收集所有被修改的资源ID
        List<Long> affectedIds = serviceItems.stream()
                .map(ResourceService.SortItem::id)
                .filter(Objects::nonNull)
                .toList();

        // 使用批量更新优化性能
        resourceService.batchUpdateOrderNum(serviceItems);

        // 发布资源变更事件
        for (Long id : affectedIds) {
            eventPublisher.publishEvent(new ResourceChangeEvent(
                    ResourceChangeEvent.ChangeType.UPDATE, id));
        }

        return ApiResponse.success(null);
    }

    /**
     * 导入资源（按 code 幂等；支持 parentCode 引用父资源）
     */
    @PostMapping("/import")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> importJson(@RequestBody List<ResourceDto> list) {
        if (CollectionUtils.isEmpty(list)) {
            return ApiResponse.success(Map.of("created", 0, "updated", 0, "errors", List.of()));
        }

        int created = 0, updated = 0;
        validateImportRequest(list);

        // 第一步：建立 code -> existingId 的映射（处理已存在的资源）
        Map<String, Long> existingCodeMap = new HashMap<>();
        List<String> allCodes = list.stream()
                .map(ResourceDto::getCode)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .collect(Collectors.toList());

        if (!allCodes.isEmpty()) {
            List<SysResource> existingResources = resourceService.list(
                    new LambdaQueryWrapper<SysResource>().in(SysResource::getCode, allCodes));
            for (SysResource r : existingResources) {
                existingCodeMap.put(r.getCode(), r.getId());
            }
        }

        // 构建 parentCode -> id 的映射（优先使用已有的，然后是本次创建的）
        Map<String, Long> parentCodeMap = new HashMap<>(existingCodeMap);

        List<ResourceDto> sortedList = resolveImportOrder(list, parentCodeMap.keySet());

        // 收集所有被修改的资源ID
        Set<Long> allAffectedIds = new HashSet<>();

        for (ResourceDto d : sortedList) {
            SysResource entity = d.toEntity(objectMapper);
            Long existingId = existingCodeMap.get(d.getCode());

            Long parentId = d.resolveParentId(parentCodeMap);
            if (StringUtils.hasText(d.getParentCode()) && parentId == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "父资源不存在: " + d.getParentCode());
            }
            entity.setParentId(parentId);

            if (existingId == null) {
                entity.setId(null);
                if (entity.getOrderNum() == null) entity.setOrderNum(0);
                if (entity.getStatus() == null) entity.setStatus("enabled");
                if (!resourceService.save(entity)) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, "创建资源失败: " + d.getCode());
                }
                created++;
                allAffectedIds.add(entity.getId());

                if (StringUtils.hasText(d.getCode())) {
                    parentCodeMap.put(d.getCode(), entity.getId());
                }
            } else {
                entity.setId(existingId);
                if (!resourceService.updateById(entity)) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, "更新资源失败: " + d.getCode());
                }
                updated++;
                allAffectedIds.add(existingId);
            }
        }

        // 发布资源变更事件
        for (Long id : allAffectedIds) {
            eventPublisher.publishEvent(new ResourceChangeEvent(
                    ResourceChangeEvent.ChangeType.UPDATE, id));
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("created", created);
        resp.put("updated", updated);
        resp.put("errors", List.of());
        return ApiResponse.success(resp);
    }

    /**
     * 导出资源
     */
    @GetMapping("/export")
    public ApiResponse<List<ResourceDto>> exportJson() {
        List<SysResource> list = resourceService.list();
        return ApiResponse.success(list.stream().map(r -> ResourceDto.from(r, objectMapper)).collect(Collectors.toList()));
    }

    /**
     * 排序项（前端请求 DTO）
     */
    @Data
    public static class SortItem {
        private Long id;
        private Integer orderNum;
    }

    private Set<Long> collectAffectedUserIdsBeforeDelete(Long resourceId) {
        Set<Long> userIds = new HashSet<>();

        List<SysUserResource> userResources = userResourceMapper.selectList(new LambdaQueryWrapper<SysUserResource>()
                .eq(SysUserResource::getResourceId, resourceId));
        userResources.stream()
                .map(SysUserResource::getUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);

        List<SysRoleResource> roleResources = roleResourceMapper.selectList(new LambdaQueryWrapper<SysRoleResource>()
                .eq(SysRoleResource::getResourceId, resourceId));
        Set<Long> roleIds = roleResources.stream()
                .map(SysRoleResource::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!roleIds.isEmpty()) {
            List<SysUserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                    .in(SysUserRole::getRoleId, roleIds));
            userRoles.stream()
                    .map(SysUserRole::getUserId)
                    .filter(Objects::nonNull)
                    .forEach(userIds::add);
        }

        return userIds;
    }

    private void validateImportRequest(List<ResourceDto> list) {
        Set<String> codes = new HashSet<>();
        for (ResourceDto dto : list) {
            if (dto == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "资源不能为空");
            }
            if (!StringUtils.hasText(dto.getCode())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "资源编码不能为空");
            }
            if (!StringUtils.hasText(dto.getType())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "资源类型不能为空: " + dto.getCode());
            }
            if (!StringUtils.hasText(dto.getName())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "资源名称不能为空: " + dto.getCode());
            }
            if (!codes.add(dto.getCode())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "资源编码重复: " + dto.getCode());
            }
        }
    }

    private List<ResourceDto> resolveImportOrder(List<ResourceDto> list, Set<String> existingCodes) {
        List<ResourceDto> pending = new ArrayList<>(list);
        List<ResourceDto> ordered = new ArrayList<>(list.size());
        Set<String> resolvedCodes = new HashSet<>(existingCodes);

        while (!pending.isEmpty()) {
            int before = pending.size();
            Iterator<ResourceDto> iterator = pending.iterator();
            while (iterator.hasNext()) {
                ResourceDto dto = iterator.next();
                if (!StringUtils.hasText(dto.getParentCode()) || resolvedCodes.contains(dto.getParentCode())) {
                    ordered.add(dto);
                    resolvedCodes.add(dto.getCode());
                    iterator.remove();
                }
            }
            if (pending.size() == before) {
                String unresolved = pending.stream()
                        .map(ResourceDto::getParentCode)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .sorted()
                        .collect(Collectors.joining(","));
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "父资源不存在或存在循环引用: " + unresolved);
            }
        }

        return ordered;
    }
}
