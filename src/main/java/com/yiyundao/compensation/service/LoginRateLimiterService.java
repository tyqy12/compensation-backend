package com.yiyundao.compensation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginRateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_FAIL_USER = "auth:login:fail:user:"; // + username
    private static final String KEY_FAIL_IP = "auth:login:fail:ip:";     // + ip
    private static final String KEY_LOCK_USER = "auth:login:lock:user:"; // + username
    private static final String KEY_LOCK_IP = "auth:login:lock:ip:";     // + ip

    private static final int USER_THRESHOLD_DEFAULT = 5;
    private static final int IP_THRESHOLD_DEFAULT = 30;
    private static final long WINDOW_SECONDS_DEFAULT = 15 * 60;
    private static final long LOCK_SECONDS_DEFAULT = 15 * 60;

    private final Map<String, LocalCounter> localCounters = new ConcurrentHashMap<>();
    private final Map<String, Long> localLocks = new ConcurrentHashMap<>();

    private final com.yiyundao.compensation.modules.system.service.SysConfigService sysConfigService;

    public boolean isLocked(String username, String ip) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(KEY_LOCK_USER + username))) return true;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(KEY_LOCK_IP + ip))) return true;
            return false;
        } catch (DataAccessException e) {
            log.warn("login rate limiter redis unavailable, using local lock state: {}", e.getMessage());
            return isLocallyLocked(KEY_LOCK_USER + username) || isLocallyLocked(KEY_LOCK_IP + ip);
        }
    }

    public void onFail(String username, String ip) {
        long window = sysConfigService.getLong("auth.login.rate.window.seconds", WINDOW_SECONDS_DEFAULT);
        long lock = sysConfigService.getLong("auth.login.rate.lock.seconds", LOCK_SECONDS_DEFAULT);
        int userThreshold = sysConfigService.getInt("auth.login.rate.user.threshold", USER_THRESHOLD_DEFAULT);
        int ipThreshold = sysConfigService.getInt("auth.login.rate.ip.threshold", IP_THRESHOLD_DEFAULT);

        try {
            Long userIncr = redisTemplate.opsForValue().increment(KEY_FAIL_USER + username, 1L);
            Long ipIncr = redisTemplate.opsForValue().increment(KEY_FAIL_IP + ip, 1L);
            long userFails = userIncr == null ? 0L : userIncr;
            long ipFails = ipIncr == null ? 0L : ipIncr;
            // ensure TTL on counters
            redisTemplate.expire(KEY_FAIL_USER + username, window, TimeUnit.SECONDS);
            redisTemplate.expire(KEY_FAIL_IP + ip, window, TimeUnit.SECONDS);
            if (userFails >= userThreshold) {
                redisTemplate.opsForValue().set(KEY_LOCK_USER + username, 1, lock, TimeUnit.SECONDS);
            }
            if (ipFails >= ipThreshold) {
                redisTemplate.opsForValue().set(KEY_LOCK_IP + ip, 1, lock, TimeUnit.SECONDS);
            }
        } catch (DataAccessException e) {
            log.warn("login rate limiter redis unavailable, using local counters: {}", e.getMessage());
            onLocalFail(KEY_FAIL_USER + username, KEY_LOCK_USER + username, userThreshold, window, lock);
            onLocalFail(KEY_FAIL_IP + ip, KEY_LOCK_IP + ip, ipThreshold, window, lock);
        }
    }

    public void onSuccess(String username, String ip) {
        try {
            redisTemplate.delete(KEY_FAIL_USER + username);
            // 不自动清 IP 失败计数，避免短时间绕过；也可选择清除
        } catch (DataAccessException e) {
            log.warn("login rate limiter redis unavailable, clearing local user counter only: {}", e.getMessage());
        }
        localCounters.remove(KEY_FAIL_USER + username);
    }

    private void onLocalFail(String counterKey, String lockKey, int threshold, long windowSeconds, long lockSeconds) {
        LocalCounter counter = localCounters.computeIfAbsent(counterKey, k -> new LocalCounter());
        if (counter.increment(windowSeconds) >= threshold) {
            localLocks.put(lockKey, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(lockSeconds));
        }
    }

    private boolean isLocallyLocked(String lockKey) {
        Long expireAt = localLocks.get(lockKey);
        if (expireAt == null) {
            return false;
        }
        if (expireAt <= System.currentTimeMillis()) {
            localLocks.remove(lockKey, expireAt);
            return false;
        }
        return true;
    }

    private static class LocalCounter {
        private long expireAt;
        private int count;

        synchronized int increment(long windowSeconds) {
            long now = System.currentTimeMillis();
            if (expireAt <= now) {
                expireAt = now + TimeUnit.SECONDS.toMillis(windowSeconds);
                count = 0;
            }
            return ++count;
        }
    }
}
