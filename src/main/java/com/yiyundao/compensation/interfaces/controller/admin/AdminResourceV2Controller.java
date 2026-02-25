package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.dto.rbac.ResourceDto;
import com.yiyundao.compensation.infrastructure.dao.SysRoleResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserResourceMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.entity.SysRoleResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserResource;
import com.yiyundao.compensation.modules.rbac.service.ResourceService;
import com.yiyundao.compensation.modules.rbac.service.impl.ResourceChangeListener.ResourceChangeEvent;
import com.yiyundao.compensation.security.SecurityAnnotations;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
     * 直接返回 SysResource，避免 ResourceDto 转换时的序列化问题
     */
    @GetMapping("/tree")
    public ApiResponse<List<SysResource>> tree(@RequestParam(required = false) String type) {
        List<SysResource> tree = resourceService.getResourceTree(type);
        return ApiResponse.success(tree);
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
    public ApiResponse<Void> delete(@PathVariable Long id) {
        boolean hasChildren = resourceService.count(new LambdaQueryWrapper<SysResource>().eq(SysResource::getParentId, id)) > 0;
        if (hasChildren) return ApiResponse.error(ErrorCode.BUSINESS_ERROR, "请先删除子资源");

        // 清理角色-资源关联
        roleResourceMapper.delete(new LambdaQueryWrapper<SysRoleResource>()
                .eq(SysRoleResource::getResourceId, id));

        // 清理用户-资源关联
        userResourceMapper.delete(new LambdaQueryWrapper<SysUserResource>()
                .eq(SysUserResource::getResourceId, id));

        // 发布删除事件
        eventPublisher.publishEvent(new ResourceChangeEvent(
                ResourceChangeEvent.ChangeType.DELETE, id));

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
    public ApiResponse<Map<String, Object>> importJson(@RequestBody List<ResourceDto> list) {
        if (CollectionUtils.isEmpty(list)) {
            return ApiResponse.success(Map.of("created", 0, "updated", 0, "errors", List.of()));
        }

        int created = 0, updated = 0;
        List<String> errors = new ArrayList<>();

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

        // 第三步：按顺序处理资源（先处理父资源，再处理子资源）
        List<ResourceDto> sortedList = list.stream()
                .sorted((a, b) -> {
                    boolean aHasParent = a.getParentId() != null || StringUtils.hasText(a.getParentCode());
                    boolean bHasParent = b.getParentId() != null || StringUtils.hasText(b.getParentCode());
                    if (!aHasParent && bHasParent) return -1;
                    if (aHasParent && !bHasParent) return 1;
                    return Integer.compare(
                            a.getOrderNum() != null ? a.getOrderNum() : 0,
                            b.getOrderNum() != null ? b.getOrderNum() : 0);
                })
                .collect(Collectors.toList());

        // 收集所有被修改的资源ID
        Set<Long> allAffectedIds = new HashSet<>();

        // 处理每个资源
        for (ResourceDto d : sortedList) {
            try {
                SysResource entity = d.toEntity(objectMapper);
                Long existingId = existingCodeMap.get(d.getCode());

                // 解析 parentId（优先使用 parentId，其次根据 parentCode 查询）
                Long parentId = d.resolveParentId(parentCodeMap);
                entity.setParentId(parentId);

                if (existingId == null) {
                    entity.setId(null);
                    if (entity.getOrderNum() == null) entity.setOrderNum(0);
                    if (entity.getStatus() == null) entity.setStatus("enabled");
                    resourceService.save(entity);
                    created++;
                    allAffectedIds.add(entity.getId());

                    if (StringUtils.hasText(d.getCode())) {
                        parentCodeMap.put(d.getCode(), entity.getId());
                    }
                } else {
                    entity.setId(existingId);
                    resourceService.updateById(entity);
                    updated++;
                    allAffectedIds.add(existingId);
                }
            } catch (Exception e) {
                String errorMsg = "导入资源失败: code=" + d.getCode() + ", error=" + e.getMessage();
                errors.add(errorMsg);
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
        resp.put("errors", errors);
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
}
