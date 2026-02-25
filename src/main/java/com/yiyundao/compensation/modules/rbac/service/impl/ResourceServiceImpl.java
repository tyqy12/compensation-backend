package com.yiyundao.compensation.modules.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.SysResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysRoleResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.entity.SysRoleResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserResource;
import com.yiyundao.compensation.modules.rbac.service.ResourceCacheService;
import com.yiyundao.compensation.modules.rbac.service.ResourceService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl extends ServiceImpl<SysResourceMapper, SysResource> implements ResourceService {

    private final SysRoleResourceMapper roleResourceMapper;
    private final SysUserResourceMapper userResourceMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserService sysUserService;
    private final UserRoleService userRoleService;
    private final ResourceCacheService resourceCacheService;

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
        SysUser user = sysUserService.getById(userId);
        if (user == null) return List.of();
        ResourceCacheService.UserPermissionBundle bundle = computeBundleIfAbsent(user);
        return bundle.resources;
    }

    @Override
    public Map<Long, List<String>> getUserActions(Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) return Map.of();
        ResourceCacheService.UserPermissionBundle bundle = computeBundleIfAbsent(user);
        return bundle.actions;
    }

    private ResourceCacheService.UserPermissionBundle computeBundleIfAbsent(SysUser user) {
        Integer version = user.getPermissionVersion();
        return resourceCacheService.get(user.getId(), version).orElseGet(() -> {
            // 管理员直返所有资源
            if (userRoleService.hasRole(user.getId(), "ROLE_ADMIN")) {
                List<SysResource> all = list(new LambdaQueryWrapper<SysResource>()
                        .eq(SysResource::getStatus, "enabled")
                        .orderByAsc(SysResource::getOrderNum));
                // 管理员权限随资源表变化而变化，这里不缓存，避免新增资源后不生效
                return new ResourceCacheService.UserPermissionBundle(all, Map.of());
            }

            // 角色资源
            Set<Long> resourceIds = new HashSet<>();
            Map<Long, Set<String>> tmpActions = new HashMap<>();

            List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<com.yiyundao.compensation.modules.rbac.entity.SysUserRole>()
                    .eq(com.yiyundao.compensation.modules.rbac.entity.SysUserRole::getUserId, user.getId()))
                    .stream().map(com.yiyundao.compensation.modules.rbac.entity.SysUserRole::getRoleId).toList();
            if (!roleIds.isEmpty()) {
                List<SysRoleResource> rrs = roleResourceMapper.selectList(new LambdaQueryWrapper<SysRoleResource>()
                        .in(SysRoleResource::getRoleId, roleIds));
                for (SysRoleResource rr : rrs) {
                    resourceIds.add(rr.getResourceId());
                    if (StringUtils.hasText(rr.getActionsJson())) {
                        List<String> actions = parseActions(rr.getActionsJson());
                        tmpActions.computeIfAbsent(rr.getResourceId(), k -> new HashSet<>()).addAll(actions);
                    }
                }
            }

            // 用户个性
            List<SysUserResource> urs = userResourceMapper.selectList(new LambdaQueryWrapper<SysUserResource>()
                    .eq(SysUserResource::getUserId, user.getId()));
            for (SysUserResource ur : urs) {
                resourceIds.add(ur.getResourceId());
                if (StringUtils.hasText(ur.getActionsJson())) {
                    List<String> actions = parseActions(ur.getActionsJson());
                    tmpActions.computeIfAbsent(ur.getResourceId(), k -> new HashSet<>()).addAll(actions);
                }
            }

            List<SysResource> list;
            if (resourceIds.isEmpty()) list = List.of();
            else {
                list = listByIds(resourceIds);
                list.sort(Comparator.comparing(r -> Optional.ofNullable(r.getOrderNum()).orElse(0)));
            }
            Map<Long, List<String>> actions = new HashMap<>();
            tmpActions.forEach((k, v) -> actions.put(k, v.stream().sorted().toList()));
            ResourceCacheService.UserPermissionBundle b = new ResourceCacheService.UserPermissionBundle(list, actions);
            resourceCacheService.put(user.getId(), version, b);
            return b;
        });
    }

    private List<String> parseActions(String json) {
        try {
            // 简化：解析为逗号分隔或JSON数组皆可
            String t = json.trim();
            if (t.startsWith("[") && t.endsWith("]")) {
                t = t.substring(1, t.length()-1);
            }
            if (t.isBlank()) return List.of();
            return Arrays.stream(t.split("[,\\s]+"))
                    .map(s -> s.replaceAll("^[\"']|[\"']$", ""))
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
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
