package com.yiyundao.compensation.common.idempotent;

import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentAspectTest {

    @Test
    void aroundShouldUseParsedKeyWithoutDoublePrefix() throws Throwable {
        RecordingRedisLockService redisLockService = new RecordingRedisLockService(true);
        IdempotentAspect aspect = new IdempotentAspect(
                new IdempotentLockService(redisLockService),
                mock(RedisTemplate.class)
        );
        ProceedingJoinPoint joinPoint = joinPoint("startPayment", 42L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint);

        assertThat(result).isEqualTo("ok");
        assertThat(redisLockService.lockKey).isEqualTo("idempotent:payment:batch:start:42");
        assertThat(redisLockService.expireSeconds).isEqualTo(600);
        assertThat(redisLockService.unlockCount).isZero();
        verify(joinPoint).proceed();
    }

    @Test
    void aroundShouldThrowBusinessExceptionWhenLockExists() throws Throwable {
        RecordingRedisLockService redisLockService = new RecordingRedisLockService(false);
        IdempotentAspect aspect = new IdempotentAspect(
                new IdempotentLockService(redisLockService),
                mock(RedisTemplate.class)
        );
        ProceedingJoinPoint joinPoint = joinPoint("startPayment", 42L);

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(BusinessException.class)
                .hasMessage("批量转账正在处理中，请勿重复提交");
        assertThat(redisLockService.lockKey).isEqualTo("idempotent:payment:batch:start:42");
        verify(joinPoint, never()).proceed();
    }

    @Test
    void aroundShouldUnlockWhenDeleteOnErrorAndBusinessExceptionThrown() throws Throwable {
        RecordingRedisLockService redisLockService = new RecordingRedisLockService(true);
        IdempotentAspect aspect = new IdempotentAspect(
                new IdempotentLockService(redisLockService),
                mock(RedisTemplate.class)
        );
        ProceedingJoinPoint joinPoint = joinPoint("submitApproval", 42L);
        when(joinPoint.proceed()).thenThrow(new BusinessException(ErrorCode.INVALID_STATUS, "状态不可提交"));

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(BusinessException.class)
                .hasMessage("状态不可提交");
        assertThat(redisLockService.unlockKey).isEqualTo("idempotent:payroll:batch:submit-approval:42");
        assertThat(redisLockService.unlockCount).isOne();
    }

    @Test
    void aroundShouldUnlockWhenDeleteOnErrorAndApiResponseIsError() throws Throwable {
        RecordingRedisLockService redisLockService = new RecordingRedisLockService(true);
        IdempotentAspect aspect = new IdempotentAspect(
                new IdempotentLockService(redisLockService),
                mock(RedisTemplate.class)
        );
        ProceedingJoinPoint joinPoint = joinPoint("startPaymentWithErrorResponse", 42L);
        ApiResponse<String> response = ApiResponse.error(ErrorCode.BUSINESS_ERROR, "批次校验未通过");
        when(joinPoint.proceed()).thenReturn(response);

        Object result = aspect.around(joinPoint);

        assertThat(result).isSameAs(response);
        assertThat(redisLockService.unlockKey).isEqualTo("idempotent:payment:batch:start:42");
        assertThat(redisLockService.unlockCount).isOne();
    }

    @Test
    void aroundShouldRejectInvalidKeyExpressionWithoutCallingTarget() throws Throwable {
        RecordingRedisLockService redisLockService = new RecordingRedisLockService(true);
        IdempotentAspect aspect = new IdempotentAspect(
                new IdempotentLockService(redisLockService),
                mock(RedisTemplate.class)
        );
        ProceedingJoinPoint joinPoint = joinPoint("invalidKeyExpression", 42L);

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(BusinessException.class)
                .hasMessage("幂等键配置错误，请联系管理员");
        assertThat(redisLockService.lockKey).isNull();
        verify(joinPoint, never()).proceed();
    }

    private ProceedingJoinPoint joinPoint(String methodName, Object... args) throws NoSuchMethodException {
        Method method = TestTarget.class.getDeclaredMethod(methodName, Long.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        return joinPoint;
    }

    private static class TestTarget {

        @Idempotent(
                key = "'payment:batch:start:' + #p0",
                expireSeconds = 600,
                message = "批量转账正在处理中，请勿重复提交",
                throwOnLockFail = true
        )
        public String startPayment(Long batchId) {
            return batchId.toString();
        }

        @Idempotent(
                key = "'payroll:batch:submit-approval:' + #p0",
                expireSeconds = 300,
                message = "薪资批次正在提交审批，请勿重复提交",
                throwOnLockFail = true,
                deleteOnError = true
        )
        public String submitApproval(Long batchId) {
            return batchId.toString();
        }

        @Idempotent(
                key = "'payment:batch:start:' + #p0",
                expireSeconds = 600,
                message = "批量转账正在处理中，请勿重复提交",
                throwOnLockFail = true,
                deleteOnError = true
        )
        public ApiResponse<String> startPaymentWithErrorResponse(Long batchId) {
            return ApiResponse.error("批次校验未通过");
        }

        @Idempotent(
                key = "'payment:batch:start:' + #missing.id",
                expireSeconds = 600,
                message = "批量转账正在处理中，请勿重复提交",
                throwOnLockFail = true
        )
        public String invalidKeyExpression(Long batchId) {
            return batchId.toString();
        }
    }

    private static class RecordingRedisLockService implements IdempotentLockService.RedisLockService {

        private final boolean locked;
        private String lockKey;
        private String unlockKey;
        private int expireSeconds;
        private int unlockCount;

        private RecordingRedisLockService(boolean locked) {
            this.locked = locked;
        }

        @Override
        public boolean tryLock(String key, int expireSeconds) {
            this.lockKey = key;
            this.expireSeconds = expireSeconds;
            return locked;
        }

        @Override
        public void unlock(String key) {
            this.unlockKey = key;
            this.unlockCount++;
        }

        @Override
        public void forceUnlock(String key) {
            this.lockKey = key;
        }
    }
}
