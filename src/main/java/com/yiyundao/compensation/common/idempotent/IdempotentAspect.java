package com.yiyundao.compensation.common.idempotent;

import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性拦截器
 * <p>
 * 通过 AOP 拦截带有 @Idempotent 注解的方法，实现幂等性保护。
 * </p>
 * <p>
 * 注意：此组件仅在 Redis 和 IdempotentLockService 可用时加载。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Slf4j
@Aspect
@Component
@Order(100)
@ConditionalOnClass(IdempotentLockService.class)
@ConditionalOnBean({IdempotentLockService.class, RedisTemplate.class})
public class IdempotentAspect {

    private final IdempotentLockService lockService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public IdempotentAspect(@Autowired(required = false) IdempotentLockService lockService,
                            @Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        this.lockService = lockService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 环绕通知：处理幂等性注解
     */
    @Around("@annotation(com.yiyundao.compensation.common.idempotent.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        if (idempotent == null) {
            return joinPoint.proceed();
        }

        // 如果锁服务不可用，跳过幂等性检查
        if (lockService == null || redisTemplate == null) {
            log.debug("幂等性服务不可用，跳过幂等性检查: method={}", method.getName());
            return joinPoint.proceed();
        }

        // 解析幂等键
        Object[] args = joinPoint.getArgs();
        String idempotentKey = lockService.parseKey(idempotent.key(), args, null);

        if (!StringUtils.hasText(idempotentKey)) {
            log.warn("幂等键解析失败: method={}, keyExpression={}",
                    method.getName(), idempotent.key());
            return joinPoint.proceed();
        }

        String lockKey = buildLockKey(idempotentKey, idempotent.lockPrefix());

        try {
            // 尝试获取锁
            boolean locked = lockService.tryLock(lockKey, idempotent.expireSeconds(), idempotent.lockPrefix());

            if (!locked) {
                log.info("幂等锁获取失败: key={}, method={}", idempotentKey, method.getName());

                if (idempotent.throwOnLockFail()) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, idempotent.message());
                }

                // 获取已缓存的响应（如果有）
                Object cachedResponse = getCachedResponse(lockKey);
                if (cachedResponse != null) {
                    log.debug("返回缓存的响应: key={}", idempotentKey);
                    return cachedResponse;
                }

                throw new BusinessException(ErrorCode.BUSINESS_ERROR, idempotent.message());
            }

            log.debug("幂等锁获取成功: key={}, method={}", idempotentKey, method.getName());

            // 执行方法
            Object result = joinPoint.proceed();

            return result;

        } catch (Exception e) {
            // 如果配置了删除锁
            if (idempotent.deleteOnError() && !(e instanceof BusinessException)) {
                lockService.unlock(lockKey, idempotent.lockPrefix());
            }
            throw e;
        } finally {
            // 默认不自动释放锁，等待过期
            // 如果需要立即释放，可以在这里调用 unlock
        }
    }

    /**
     * 构建锁键
     */
    private String buildLockKey(String key, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "idempotent:lock:" + key;
        }
        return prefix + "lock:" + key;
    }

    /**
     * 获取缓存的响应
     */
    private Object getCachedResponse(String lockKey) {
        try {
            Object cached = redisTemplate.opsForValue().get(lockKey + ":response");
            return cached;
        } catch (Exception e) {
            log.debug("获取缓存响应失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 缓存响应（用于重复请求返回相同响应）
     */
    public void cacheResponse(String key, Object response, int expireSeconds) {
        try {
            String lockKey = "idempotent:lock:" + key;
            redisTemplate.opsForValue().set(lockKey + ":response", response, expireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("缓存响应失败: {}", e.getMessage());
        }
    }
}
