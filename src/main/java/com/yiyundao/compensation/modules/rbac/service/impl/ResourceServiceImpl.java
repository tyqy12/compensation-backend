package com.yiyundao.compensation.modules.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.SysResourceMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.service.ResourceService;
import com.yiyundao.compensation.security.DatabasePermissionService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl extends ServiceImpl<SysResourceMapper, SysResource> implements ResourceService {

    private final DatabasePermissionService databasePermissionService;

    @Override
    public List<SysResource> getResourceTree(String type) {
        List<SysResource> all = list(new LambdaQueryWrapper<SysResource>()
                .eq(type != null, SysResource::getType, type)
                .eq(SysResource::getStatus, "enabled")
                .orderByAsc(SysResource::getOrderNum));

        if (all.isEmpty()) {
            return List.of();
        }

        log.debug("getResourceTree: 找到 {} 个资源", all.size());
        return buildResourceTree(all, null);
    }

    /**
     * 构建资源树（嵌套结构）
     * 返回的资源在 propsJson 中包含 _children 字段（子资源ID列表）
     *
     * @param allResources 所有资源列表
     * @param parentId     父资源ID（递归时使用）
     * @return 嵌套的资源树
     */
    private List<SysResource> buildResourceTree(List<SysResource> allResources, Long parentId) {
        List<SysResource> children = allResources.stream()
                .filter(r -> Objects.equals(r.getParentId(), parentId))
                .collect(java.util.stream.Collectors.toList());

        if (children.isEmpty()) {
            return List.of();
        }

        // 为每个子节点添加子资源信息到 meta
        for (SysResource child : children) {
            List<SysResource> grandChildren = buildResourceTree(allResources, child.getId());
            if (!grandChildren.isEmpty()) {
                addChildrenToResource(child, grandChildren);
            }
        }

        return children;
    }

    /**
     * 为资源添加子资源信息到 propsJson 的 meta 中
     */
    private void addChildrenToResource(SysResource parent, List<SysResource> children) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode meta;
            if (StringUtils.hasText(parent.getPropsJson())) {
                meta = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(parent.getPropsJson());
            } else {
                meta = mapper.createObjectNode();
            }
            // 添加子资源ID列表到meta中
            meta.putPOJO("_children", children.stream()
                    .map(SysResource::getId)
                    .collect(java.util.stream.Collectors.toList()));
            parent.setPropsJson(mapper.writeValueAsString(meta));
        } catch (Exception e) {
            log.warn("为资源添加子资源信息失败: resourceId={}", parent.getId(), e);
        }
    }

    @Override
    public List<SysResource> getUserResources(Long userId) {
        return databasePermissionService.getUserBundle(userId).resources();
    }

    @Override
    public Map<Long, List<String>> getUserActions(Long userId) {
        return databasePermissionService.getUserBundle(userId).actions();
    }

    @Override
    public void batchUpdateOrderNum(List<SortItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        // 转换为 Mapper DTO
        List<SysResourceMapper.SortItemDto> dtos = items.stream()
                .filter(item -> item.id() != null && item.orderNum() != null)
                .map(item -> new SysResourceMapper.SortItemDto(item.id(), item.orderNum()))
                .toList();

        if (dtos.isEmpty()) {
            log.warn("批量更新排序号时没有有效的数据");
            return;
        }

        int updated = baseMapper.batchUpdateOrderNum(dtos);
        log.info("批量更新排序号完成: count={}", updated);
    }
}
