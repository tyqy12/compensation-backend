package com.yiyundao.compensation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

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

    private final com.yiyundao.compensation.modules.system.service.SysConfigService sysConfigService;

    public boolean isLocked(String username, String ip) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(KEY_LOCK_USER + username))) return true;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(KEY_LOCK_IP + ip))) return true;
        return false;
    }

    public void onFail(String username, String ip) {
        Long userIncr = redisTemplate.opsForValue().increment(KEY_FAIL_USER + username, 1L);
        Long ipIncr = redisTemplate.opsForValue().increment(KEY_FAIL_IP + ip, 1L);
        long userFails = userIncr == null ? 0L : userIncr;
        long ipFails = ipIncr == null ? 0L : ipIncr;
        // ensure TTL on counters
        long window = sysConfigService.getLong("auth.login.rate.window.seconds", WINDOW_SECONDS_DEFAULT);
        long lock = sysConfigService.getLong("auth.login.rate.lock.seconds", LOCK_SECONDS_DEFAULT);
        redisTemplate.expire(KEY_FAIL_USER + username, window, TimeUnit.SECONDS);
        redisTemplate.expire(KEY_FAIL_IP + ip, window, TimeUnit.SECONDS);
        int userThreshold = sysConfigService.getInt("auth.login.rate.user.threshold", USER_THRESHOLD_DEFAULT);
        int ipThreshold = sysConfigService.getInt("auth.login.rate.ip.threshold", IP_THRESHOLD_DEFAULT);
        if (userFails >= userThreshold) {
            redisTemplate.opsForValue().set(KEY_LOCK_USER + username, 1, lock, TimeUnit.SECONDS);
        }
        if (ipFails >= ipThreshold) {
            redisTemplate.opsForValue().set(KEY_LOCK_IP + ip, 1, lock, TimeUnit.SECONDS);
        }
    }

    public void onSuccess(String username, String ip) {
        redisTemplate.delete(KEY_FAIL_USER + username);
        // 不自动清 IP 失败计数，避免短时间绕过；也可选择清除
    }
}
