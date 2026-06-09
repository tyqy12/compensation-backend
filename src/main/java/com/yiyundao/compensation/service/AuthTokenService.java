package com.yiyundao.compensation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AuthTokenService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, Long> localBlacklistedTokens = new ConcurrentHashMap<>();

    private static final String KEY_REFRESH_PREFIX = "auth:refresh:";
    private static final String KEY_BLACKLIST_PREFIX = "auth:blacklist:";
    private static final String KEY_OAUTH_STATE_PREFIX = "oauth:state:"; // oauth:state:{platform}:{state}

    public AuthTokenService(@Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean storeRefreshToken(String username, String refreshToken, long ttlMillis) {
        if (!StringUtils.hasText(username)
                || !StringUtils.hasText(refreshToken)
                || ttlMillis <= 0
                || redisTemplate == null) {
            return false;
        }
        String key = KEY_REFRESH_PREFIX + refreshToken;
        try {
            redisTemplate.opsForValue().set(key, username, ttlMillis, TimeUnit.MILLISECONDS);
            return true;
        } catch (DataAccessException e) {
            log.warn("storeRefreshToken redis error: {}", e.getMessage());
            return false;
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

    public String consumeRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken) || redisTemplate == null) {
            return null;
        }
        try {
            Object v = redisTemplate.opsForValue().getAndDelete(KEY_REFRESH_PREFIX + refreshToken);
            return v == null ? null : String.valueOf(v);
        } catch (DataAccessException e) {
            log.warn("consumeRefreshToken redis error: {}", e.getMessage());
            return null;
        }
    }

    public boolean deleteRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken) || redisTemplate == null) {
            return false;
        }
        try {
            redisTemplate.delete(KEY_REFRESH_PREFIX + refreshToken);
            return true;
        } catch (DataAccessException e) {
            log.warn("deleteRefreshToken redis error: {}", e.getMessage());
            return false;
        }
    }

    public boolean blacklistToken(String token, long ttlMillis) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        if (ttlMillis <= 0) {
            ttlMillis = Duration.ofMinutes(5).toMillis();
        }
        rememberLocalBlacklist(token, ttlMillis);
        if (redisTemplate == null) {
            return false;
        }
        try {
            redisTemplate.opsForValue().set(KEY_BLACKLIST_PREFIX + token, 1, ttlMillis, TimeUnit.MILLISECONDS);
            return true;
        } catch (DataAccessException e) {
            log.warn("blacklistToken redis error: {}", e.getMessage());
            return false;
        }
    }

    public boolean isBlacklisted(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        if (isLocallyBlacklisted(token, System.currentTimeMillis())) {
            return true;
        }
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

    private void rememberLocalBlacklist(String token, long ttlMillis) {
        long now = System.currentTimeMillis();
        localBlacklistedTokens.entrySet().removeIf(entry -> entry.getValue() <= now);
        localBlacklistedTokens.put(token, now + ttlMillis);
    }

    private boolean isLocallyBlacklisted(String token, long now) {
        Long expiresAt = localBlacklistedTokens.get(token);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= now) {
            localBlacklistedTokens.remove(token, expiresAt);
            return false;
        }
        return true;
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
        if (!StringUtils.hasText(platform) || !StringUtils.hasText(state) || redisTemplate == null) {
            return false;
        }
        String key = KEY_OAUTH_STATE_PREFIX + platform + ":" + state;
        try {
            return redisTemplate.opsForValue().getAndDelete(key) != null;
        } catch (DataAccessException e) {
            log.warn("consumeOAuthState redis error: {}", e.getMessage());
        }
        return false;
    }
}
