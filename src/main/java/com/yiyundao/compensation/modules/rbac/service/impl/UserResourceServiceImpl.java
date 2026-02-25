package com.yiyundao.compensation.modules.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.SysUserResourceMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysUserResource;
import com.yiyundao.compensation.modules.rbac.service.ResourceCacheService;
import com.yiyundao.compensation.modules.rbac.service.UserResourceService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 用户资源服务实现
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserResourceServiceImpl extends ServiceImpl<SysUserResourceMapper, SysUserResource>
        implements UserResourceService {

    private final SysUserResourceMapper userResourceMapper;
    private final SysUserService sysUserService;
    private final ResourceCacheService resourceCacheService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignResources(Long userId, List<Long> resourceIds, Map<Long, List<String>> actionsMap, Long operatorId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "用户ID不能为空");
        }

        // 如果资源列表为空，清空用户所有资源
        if (CollectionUtils.isEmpty(resourceIds)) {
            revokeResources(userId, null, operatorId);
            return;
        }

        // 先删除用户的所有资源
        userResourceMapper.delete(new LambdaQueryWrapper<SysUserResource>()
                .eq(SysUserResource::getUserId, userId));

        // 批量插入新资源
        for (Long resourceId : resourceIds) {
            SysUserResource ur = new SysUserResource();
            ur.setUserId(userId);
            ur.setResourceId(resourceId);
            if (actionsMap != null) {
                List<String> actions = actionsMap.get(resourceId);
                if (CollectionUtils.isNotEmpty(actions)) {
                    ur.setActionsJson(actionsToJson(actions));
                }
            }
            ur.setCreateBy(String.valueOf(operatorId));
            ur.setUpdateBy(String.valueOf(operatorId));
            userResourceMapper.insert(ur);
        }

        // 清除用户缓存
        clearUserCache(userId);
        log.info("用户资源分配成功: userId={}, resourceCount={}, operatorId={}",
                userId, resourceIds.size(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addResources(Long userId, List<Long> resourceIds, Map<Long, List<String>> actionsMap, Long operatorId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "用户ID不能为空");
        }

        if (CollectionUtils.isEmpty(resourceIds)) {
            return;
        }

        // 获取已存在的资源
        Set<Long> existingResourceIds = getExistingResourceIds(userId);

        // 批量插入不存在的资源
        for (Long resourceId : resourceIds) {
            if (existingResourceIds.contains(resourceId)) {
                continue; // 已存在的资源跳过
            }
            SysUserResource ur = new SysUserResource();
            ur.setUserId(userId);
            ur.setResourceId(resourceId);
            if (actionsMap != null) {
                List<String> actions = actionsMap.get(resourceId);
                if (CollectionUtils.isNotEmpty(actions)) {
                    ur.setActionsJson(actionsToJson(actions));
                }
            }
            ur.setCreateBy(String.valueOf(operatorId));
            ur.setUpdateBy(String.valueOf(operatorId));
            userResourceMapper.insert(ur);
        }

        // 清除用户缓存
        clearUserCache(userId);
        log.info("用户资源追加成功: userId={}, resourceCount={}, operatorId={}",
                userId, resourceIds.size(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeResources(Long userId, List<Long> resourceIds, Long operatorId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "用户ID不能为空");
        }

        LambdaQueryWrapper<SysUserResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserResource::getUserId, userId);

        if (CollectionUtils.isNotEmpty(resourceIds)) {
            wrapper.in(SysUserResource::getResourceId, resourceIds);
        }

        userResourceMapper.delete(wrapper);

        // 清除用户缓存
        clearUserCache(userId);
        log.info("用户资源撤销成功: userId={}, resourceCount={}, operatorId={}",
                userId, resourceIds != null ? resourceIds.size() : "all", operatorId);
    }

    @Override
    public Map<Long, Set<String>> getUserResources(Long userId) {
        if (userId == null) {
            return Map.of();
        }

        List<SysUserResource> resources = userResourceMapper.selectList(
                new LambdaQueryWrapper<SysUserResource>()
                        .eq(SysUserResource::getUserId, userId));

        Map<Long, Set<String>> result = new HashMap<>();
        for (SysUserResource ur : resources) {
            Set<String> actions = parseActions(ur.getActionsJson());
            result.put(ur.getResourceId(), actions);
        }

        return result;
    }

    @Override
    public void clearUserCache(Long userId) {
        if (userId == null) {
            return;
        }

        // 递增用户权限版本
        sysUserService.incrementPermissionVersion(userId);

        // 清除缓存
        try {
            resourceCacheService.evictByUserId(userId);
        } catch (Exception e) {
            log.warn("清除用户资源缓存失败: userId={}", userId, e);
        }
    }

    private Set<Long> getExistingResourceIds(Long userId) {
        List<SysUserResource> resources = userResourceMapper.selectList(
                new LambdaQueryWrapper<SysUserResource>()
                        .eq(SysUserResource::getUserId, userId)
                        .select(SysUserResource::getResourceId));
        Set<Long> ids = new HashSet<>();
        for (SysUserResource ur : resources) {
            ids.add(ur.getResourceId());
        }
        return ids;
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
                    .collect(java.util.stream.Collectors.toSet());
        }
    }
}
