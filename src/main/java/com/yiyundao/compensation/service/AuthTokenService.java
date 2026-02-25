package com.yiyundao.compensation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AuthTokenService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_REFRESH_PREFIX = "auth:refresh:";
    private static final String KEY_BLACKLIST_PREFIX = "auth:blacklist:";
    private static final String KEY_OAUTH_STATE_PREFIX = "oauth:state:"; // oauth:state:{platform}:{state}

    public AuthTokenService(@Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void storeRefreshToken(String username, String refreshToken, long ttlMillis) {
        if (redisTemplate == null) {
            return;
        }
        String key = KEY_REFRESH_PREFIX + refreshToken;
        try {
            redisTemplate.opsForValue().set(key, username, ttlMillis, TimeUnit.MILLISECONDS);
        } catch (DataAccessException e) {
            log.warn("storeRefreshToken redis error: {}", e.getMessage());
        }
    }

    public String getRefreshOwner(String refreshToken) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            Object v = redisTemplate.opsForValue().get(KEY_REFRESH_PREFIX + refreshToken);
            return v == null ? null : String.valueOf(v);
        } catch (DataAccessException e) {
            log.warn("getRefreshOwner redis error: {}", e.getMessage());
            return null;
        }
    }

    public void deleteRefreshToken(String refreshToken) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(KEY_REFRESH_PREFIX + refreshToken);
        } catch (DataAccessException e) {
            log.warn("deleteRefreshToken redis error: {}", e.getMessage());
        }
    }

    public void blacklistToken(String token, long ttlMillis) {
        if (redisTemplate == null) {
            return;
        }
        if (ttlMillis <= 0) ttlMillis = Duration.ofMinutes(5).toMillis();
        try {
            redisTemplate.opsForValue().set(KEY_BLACKLIST_PREFIX + token, 1, ttlMillis, TimeUnit.MILLISECONDS);
        } catch (DataAccessException e) {
            log.warn("blacklistToken redis error: {}", e.getMessage());
        }
    }

    public boolean isBlacklisted(String token) {
        if (redisTemplate == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_BLACKLIST_PREFIX + token));
        } catch (DataAccessException e) {
            log.warn("isBlacklisted redis error: {}", e.getMessage());
            return false;
        }
    }

    public void storeOAuthState(String platform, String state, long ttlSeconds) {
        if (redisTemplate == null) {
            return;
        }
        String key = KEY_OAUTH_STATE_PREFIX + platform + ":" + state;
        try {
            redisTemplate.opsForValue().set(key, 1, Math.max(1, ttlSeconds), TimeUnit.SECONDS);
        } catch (DataAccessException e) {
            log.warn("storeOAuthState redis error: {}", e.getMessage());
        }
    }

    public boolean consumeOAuthState(String platform, String state) {
        if (redisTemplate == null) {
            return false;
        }
        String key = KEY_OAUTH_STATE_PREFIX + platform + ":" + state;
        try {
            Boolean exists = redisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                redisTemplate.delete(key);
                return true;
            }
        } catch (DataAccessException e) {
            log.warn("consumeOAuthState redis error: {}", e.getMessage());
        }
        return false;
    }
}
