package com.yiyundao.compensation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_REFRESH_PREFIX = "auth:refresh:";
    private static final String KEY_BLACKLIST_PREFIX = "auth:blacklist:";
    private static final String KEY_OAUTH_STATE_PREFIX = "oauth:state:"; // oauth:state:{platform}:{state}

    public void storeRefreshToken(String username, String refreshToken, long ttlMillis) {
        String key = KEY_REFRESH_PREFIX + refreshToken;
        redisTemplate.opsForValue().set(key, username, ttlMillis, TimeUnit.MILLISECONDS);
    }

    public String getRefreshOwner(String refreshToken) {
        Object v = redisTemplate.opsForValue().get(KEY_REFRESH_PREFIX + refreshToken);
        return v == null ? null : String.valueOf(v);
    }

    public void deleteRefreshToken(String refreshToken) {
        redisTemplate.delete(KEY_REFRESH_PREFIX + refreshToken);
    }

    public void blacklistToken(String token, long ttlMillis) {
        if (ttlMillis <= 0) ttlMillis = Duration.ofMinutes(5).toMillis();
        redisTemplate.opsForValue().set(KEY_BLACKLIST_PREFIX + token, 1, ttlMillis, TimeUnit.MILLISECONDS);
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_BLACKLIST_PREFIX + token));
    }

    public void storeOAuthState(String platform, String state, long ttlSeconds) {
        String key = KEY_OAUTH_STATE_PREFIX + platform + ":" + state;
        redisTemplate.opsForValue().set(key, 1, Math.max(1, ttlSeconds), TimeUnit.SECONDS);
    }

    public boolean consumeOAuthState(String platform, String state) {
        String key = KEY_OAUTH_STATE_PREFIX + platform + ":" + state;
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }
}
