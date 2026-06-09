package com.yiyundao.compensation.common.idempotent;

import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ApiResponse;
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
import org.springframework.expression.ExpressionException;
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
        String idempotentKey;
        try {
            idempotentKey = lockService.parseKey(idempotent.key(), args, null);
        } catch (ExpressionException | IllegalArgumentException e) {
            log.warn("幂等键解析失败: method={}, keyExpression={}, error={}",
                    method.getName(), idempotent.key(), e.getMessage());
            throw new BusinessException(ErrorCode.PARAM_INVALID, "幂等键配置错误，请联系管理员");
        }

        if (!StringUtils.hasText(idempotentKey)) {
            log.warn("幂等键解析失败: method={}, keyExpression={}",
                    method.getName(), idempotent.key());
            throw new BusinessException(ErrorCode.PARAM_INVALID, "幂等键配置错误，请联系管理员");
        }

        String lockKey = lockService.buildLockKey(idempotentKey, idempotent.lockPrefix());

        boolean lockAcquired = false;
        try {
            // 尝试获取锁
            boolean locked = lockService.tryLock(idempotentKey, idempotent.expireSeconds(), idempotent.lockPrefix());

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

            lockAcquired = true;
            log.debug("幂等锁获取成功: key={}, method={}", idempotentKey, method.getName());

            // 执行方法
            Object result = joinPoint.proceed();
            if (idempotent.deleteOnError() && isErrorResponse(result)) {
                unlockAfterFailure(idempotentKey, idempotent.lockPrefix());
            }

            return result;

        } catch (Throwable e) {
            // 如果业务执行失败且配置了删除锁，释放当前线程持有的锁，允许修正后立即重试。
            if (lockAcquired && idempotent.deleteOnError()) {
                unlockAfterFailure(idempotentKey, idempotent.lockPrefix());
            }
            throw e;
        } finally {
            // 默认不自动释放锁，等待过期
            // 如果需要立即释放，可以在这里调用 unlock
        }
    }

    private boolean isErrorResponse(Object result) {
        return result instanceof ApiResponse<?> response && !response.isSuccess();
    }

    private void unlockAfterFailure(String key, String lockPrefix) {
        try {
            lockService.unlock(key, lockPrefix);
        } catch (Exception unlockError) {
            log.warn("业务失败后释放幂等锁失败: key={}, error={}", key, unlockError.getMessage());
        }
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
            String lockKey = lockService.buildLockKey(key, IdempotentLockService.DEFAULT_LOCK_PREFIX);
            redisTemplate.opsForValue().set(lockKey + ":response", response, expireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("缓存响应失败: {}", e.getMessage());
        }
    }
}
