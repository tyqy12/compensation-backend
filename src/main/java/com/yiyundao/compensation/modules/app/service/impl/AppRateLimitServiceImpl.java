package com.yiyundao.compensation.modules.app.service.impl;

import com.yiyundao.compensation.common.config.ExternalApiAuthProperties;
import com.yiyundao.compensation.modules.app.service.AppRateLimitAlertNotifier;
import com.yiyundao.compensation.modules.app.service.AppRateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AppRateLimitServiceImpl implements AppRateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ExternalApiAuthProperties properties;
    private final AppRateLimitAlertNotifier alertNotifier;

    private final Map<String, LocalCounter> localCounters = new ConcurrentHashMap<>();
    private final Map<String, Instant> alertWindow = new ConcurrentHashMap<>();
    private static final DateTimeFormatter KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);

    @Autowired
    public AppRateLimitServiceImpl(ExternalApiAuthProperties properties,
                                   AppRateLimitAlertNotifier alertNotifier,
                                   @Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        this.properties = properties;
        this.alertNotifier = alertNotifier;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void checkRate(String clientId, String clientIp) {
        int perMinuteLimit = properties.getRateLimit().getPerMinute();
        if (perMinuteLimit <= 0) {
            return;
        }
        String normalizedIp = normalizeIp(clientIp);
        String keySuffix = KEY_FORMATTER.format(Instant.now());
        String redisKey = "app:rate:" + clientId + ":" + normalizedIp + ":" + keySuffix;
        try {
            if (redisTemplate != null) {
                Long count = redisTemplate.opsForValue().increment(redisKey, 1L);
                if (count != null && count == 1L) {
                    redisTemplate.expire(redisKey, Duration.ofMinutes(1));
                }
                if (count != null && count > perMinuteLimit) {
                    triggerAlert(clientId, normalizedIp, count);
                    throw new RateLimitExceededException(clientId, clientIp);
                }
                return;
            }
            throw new IllegalStateException("RedisTemplate unavailable");
        } catch (DataAccessException | IllegalStateException e) {
            log.warn("Redis 限流不可用，使用内存兜底: {}", e.getMessage());
            LocalCounter counter = localCounters.computeIfAbsent(clientId + "|" + normalizedIp, k -> new LocalCounter());
            if (counter.incrementAndCheck(perMinuteLimit)) {
                triggerAlert(clientId, normalizedIp, counter.getCurrentCount());
                throw new RateLimitExceededException(clientId, clientIp);
            }
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String clientId, String ip) {
            super("Rate limit exceeded for client " + clientId + " from ip " + ip);
        }
    }

    private void triggerAlert(String clientId, String normalizedIp, long currentCount) {
        long cooldown = Math.max(60, properties.getRateLimit().getAlertCooldownSeconds());
        String alertKey = clientId + "|" + normalizedIp;
        Instant now = Instant.now();
        Instant last = alertWindow.get(alertKey);
        if (last != null && now.isBefore(last.plusSeconds(cooldown))) {
            return;
        }
        alertWindow.put(alertKey, now);
        if (alertNotifier != null) {
            alertNotifier.notifyRateLimit(clientId, normalizedIp, currentCount);
        }
    }

    private String normalizeIp(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return "unknown";
        }
        return clientIp.trim();
    }

    private static class LocalCounter {
        private volatile String window;
        private volatile int count;

        synchronized boolean incrementAndCheck(int limit) {
            String currentWindow = KEY_FORMATTER.format(Instant.now());
            if (!currentWindow.equals(window)) {
                window = currentWindow;
                count = 0;
            }
            count++;
            return count > limit;
        }

        int getCurrentCount() {
            return count;
        }
    }
}
