package com.yiyundao.compensation.common.idempotent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StringRedisLockService implements IdempotentLockService.RedisLockService {

    private static final RedisScript<Long> UNLOCK_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else return 0 end",
            Long.class);

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ThreadLocal<Map<String, String>> lockTokens = ThreadLocal.withInitial(HashMap::new);

    @Override
    public boolean tryLock(String key, int expireSeconds) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            log.warn("StringRedisTemplate 不可用，幂等锁获取失败: key={}", key);
            return false;
        }
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(key, token, Duration.ofSeconds(Math.max(1, expireSeconds)));
        if (Boolean.TRUE.equals(locked)) {
            lockTokens.get().put(key, token);
            return true;
        }
        lockTokens.get().remove(key);
        return false;
    }

    @Override
    public void unlock(String key) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        String token = lockTokens.get().remove(key);
        if (token == null) {
            log.debug("当前线程未持有幂等锁，跳过释放: key={}", key);
            cleanupThreadLocalIfEmpty();
            return;
        }
        redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
        cleanupThreadLocalIfEmpty();
    }

    @Override
    public void forceUnlock(String key) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        lockTokens.get().remove(key);
        cleanupThreadLocalIfEmpty();
        redisTemplate.delete(key);
    }

    private void cleanupThreadLocalIfEmpty() {
        if (lockTokens.get().isEmpty()) {
            lockTokens.remove();
        }
    }
}
