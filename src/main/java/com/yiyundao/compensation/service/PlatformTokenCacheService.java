package com.yiyundao.compensation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PlatformTokenCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_OAUTH_TOKEN_PREFIX = "oauth:token:"; // oauth:token:{platform}

    public String getToken(String platform) {
        Object v = redisTemplate.opsForValue().get(KEY_OAUTH_TOKEN_PREFIX + platform);
        return v == null ? null : String.valueOf(v);
    }

    public void setToken(String platform, String token, long ttlSeconds) {
        long ttl = Math.max(1, ttlSeconds);
        redisTemplate.opsForValue().set(KEY_OAUTH_TOKEN_PREFIX + platform, token, ttl, TimeUnit.SECONDS);
    }
}

