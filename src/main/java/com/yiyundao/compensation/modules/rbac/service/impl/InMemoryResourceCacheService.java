package com.yiyundao.compensation.modules.rbac.service.impl;

import com.yiyundao.compensation.modules.rbac.service.ResourceCacheService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryResourceCacheService implements ResourceCacheService {

    private final ConcurrentHashMap<String, UserPermissionBundle> store = new ConcurrentHashMap<>();

    private String key(Long userId, Integer version) { return userId + ":" + (version == null ? 0 : version); }

    @Override
    public Optional<UserPermissionBundle> get(Long userId, Integer version) {
        return Optional.ofNullable(store.get(key(userId, version)));
    }

    @Override
    public void put(Long userId, Integer version, UserPermissionBundle bundle) {
        store.put(key(userId, version), bundle);
    }

    @Override
    public void evictByUserId(Long userId) {
        String prefix = userId + ":";
        store.keySet().removeIf(k -> k.startsWith(prefix));
    }
}

