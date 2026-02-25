package com.yiyundao.compensation.common.idempotent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等性锁服务
 * <p>
 * 基于 Redis 的分布式锁实现，用于保证接口幂等性。
 * </p>
 * <p>
 * 注意：此组件仅在 RedisLockService 实现可用时加载。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Slf4j
@Component
@ConditionalOnBean(IdempotentLockService.RedisLockService.class)
public class IdempotentLockService {

    private final RedisLockService redisLockService;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * 解析后的表达式缓存
     */
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public IdempotentLockService(RedisLockService redisLockService) {
        this.redisLockService = redisLockService;
    }

    /**
     * 尝试获取幂等锁
     *
     * @param key           幂等键
     * @param expireSeconds 过期时间（秒）
     * @param lockPrefix    锁前缀
     * @return 是否获取成功
     */
    public boolean tryLock(String key, int expireSeconds, String lockPrefix) {
        String lockKey = buildLockKey(key, lockPrefix);
        return redisLockService.tryLock(lockKey, expireSeconds);
    }

    /**
     * 释放幂等锁
     *
     * @param key        幂等键
     * @param lockPrefix 锁前缀
     */
    public void unlock(String key, String lockPrefix) {
        String lockKey = buildLockKey(key, lockPrefix);
        redisLockService.unlock(lockKey);
    }

    /**
     * 释放锁（强制解锁，不检查持有者）
     *
     * @param key        幂等键
     * @param lockPrefix 锁前缀
     */
    public void forceUnlock(String key, String lockPrefix) {
        String lockKey = buildLockKey(key, lockPrefix);
        redisLockService.forceUnlock(lockKey);
    }

    /**
     * 解析幂等键
     *
     * @param keyExpression 键表达式
     * @param args          方法参数
     * @param result        方法返回值（可选，用于 AFTER 解析）
     * @return 解析后的键
     */
    public String parseKey(String keyExpression, Object[] args, Object result) {
        if (!StringUtils.hasText(keyExpression)) {
            throw new IllegalArgumentException("幂等键不能为空");
        }

        // 检查是否是 SpEL 表达式
        if (keyExpression.startsWith("#")) {
            return parseSpelExpression(keyExpression, args, result);
        }

        return keyExpression;
    }

    /**
     * 解析 SpEL 表达式
     */
    private String parseSpelExpression(String expression, Object[] args, Object result) {
        try {
            // 缓存表达式
            Expression exp = expressionCache.computeIfAbsent(expression,
                    k -> expressionParser.parseExpression(k));

            // 创建上下文
            EvaluationContext context = new StandardEvaluationContext();
            StandardEvaluationContext stdContext = (StandardEvaluationContext) context;

            // 设置方法参数
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    stdContext.setVariable("p" + i, args[i]);
                }
            }

            // 设置方法参数数组
            if (args != null && args.length > 0) {
                stdContext.setVariable("args", args);
            }

            // 设置方法返回值
            if (result != null) {
                stdContext.setVariable("result", result);
            }

            // 解析表达式
            Object value = exp.getValue(context);
            return value != null ? value.toString() : expression;
        } catch (Exception e) {
            log.warn("解析幂等键表达式失败: {}", expression, e);
            return expression;
        }
    }

    /**
     * 构建锁键
     */
    private String buildLockKey(String key, String lockPrefix) {
        if (lockPrefix == null || lockPrefix.isEmpty()) {
            return "idempotent:" + key;
        }
        return lockPrefix + key;
    }

    /**
     * Redis 锁服务（简化接口）
     */
    public interface RedisLockService {

        /**
         * 尝试获取锁
         *
         * @param key          锁键
         * @param expireSeconds 过期时间
         * @return 是否获取成功
         */
        boolean tryLock(String key, int expireSeconds);

        /**
         * 释放锁
         *
         * @param key 锁键
         */
        void unlock(String key);

        /**
         * 强制释放锁
         *
         * @param key 锁键
         */
        void forceUnlock(String key);
    }
}
