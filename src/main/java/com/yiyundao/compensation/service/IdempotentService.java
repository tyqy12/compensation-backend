package com.yiyundao.compensation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * 幂等性服务
 * <p>
 * 基于 Redis 的幂等性锁服务实现。
 * </p>
 * <p>
 * 注意：此组件仅在 Redis 可用时加载。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(StringRedisTemplate.class)
public class IdempotentService {

    private final StringRedisTemplate redisTemplate;
    private final ExpressionParser parser = new SpelExpressionParser();

    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:";

    public boolean tryLock(String key, int expireSeconds, long waitTime) {
        String fullKey = IDEMPOTENT_KEY_PREFIX + key;

        if (waitTime > 0) {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < waitTime) {
                Boolean result = redisTemplate.opsForValue()
                        .setIfAbsent(fullKey, "1", Duration.ofSeconds(expireSeconds));
                if (Boolean.TRUE.equals(result)) {
                    return true;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false;
        }

        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(fullKey, "1", Duration.ofSeconds(expireSeconds));
        return Boolean.TRUE.equals(result);
    }

    public void unlock(String key) {
        String fullKey = IDEMPOTENT_KEY_PREFIX + key;
        redisTemplate.delete(fullKey);
        log.debug("释放幂等锁: key={}", fullKey);
    }

    public String generateKey(String spelExpression, Map<String, Object> variables) {
        if (spelExpression == null || spelExpression.isEmpty()) {
            return generateDefaultKey(variables);
        }

        EvaluationContext context = new StandardEvaluationContext();
        variables.forEach(context::setVariable);

        return parser.parseExpression(spelExpression).getValue(context, String.class);
    }

    public String generateKey(String spelExpression, Object... args) {
        if (args == null || args.length == 0) {
            return spelExpression;
        }

        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
        }

        return parser.parseExpression(spelExpression).getValue(context, String.class);
    }

    private String generateDefaultKey(Map<String, Object> variables) {
        StringBuilder sb = new StringBuilder();
        variables.forEach((k, v) -> {
            if (v != null) {
                sb.append(k).append("=").append(v.toString()).append(":");
            }
        });
        return sb.toString();
    }
}
