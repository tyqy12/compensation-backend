package com.yiyundao.compensation.modules.rbac.service;

import com.yiyundao.compensation.modules.rbac.entity.SysResource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ResourceCacheService {
    Optional<UserPermissionBundle> get(Long userId, Integer version);
    void put(Long userId, Integer version, UserPermissionBundle bundle);
    void evictByUserId(Long userId);

    class UserPermissionBundle {
        public final List<SysResource> resources;
        public final Map<Long, List<String>> actions;
        public UserPermissionBundle(List<SysResource> resources, Map<Long, List<String>> actions) {
            this.resources = resources;
            this.actions = actions;
        }
    }
}

