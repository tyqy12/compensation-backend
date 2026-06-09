package com.yiyundao.compensation.common.idempotent;

import org.junit.jupiter.api.Test;
import org.springframework.expression.ExpressionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotentLockServiceTest {

    @Test
    void parseKeyShouldEvaluateLiteralConcatenationExpression() {
        IdempotentLockService service = new IdempotentLockService(new RecordingRedisLockService(true));

        String key = service.parseKey("'payment:batch:start:' + #p0", new Object[]{42L}, null);

        assertThat(key).isEqualTo("payment:batch:start:42");
    }

    @Test
    void parseKeyShouldEvaluateMethodCallExpression() {
        IdempotentLockService service = new IdempotentLockService(new RecordingRedisLockService(true));
        ImportRequest request = new ImportRequest("batch-a");

        String key = service.parseKey("'employee:batch-import:' + #p0.hashCode()", new Object[]{request}, null);

        assertThat(key).isEqualTo("employee:batch-import:" + request.hashCode());
    }

    @Test
    void parseKeyShouldKeepPlainLiteralKey() {
        IdempotentLockService service = new IdempotentLockService(new RecordingRedisLockService(true));

        String key = service.parseKey("fixed:operation:key", new Object[]{42L}, null);

        assertThat(key).isEqualTo("fixed:operation:key");
    }

    @Test
    void parseKeyShouldRejectBlankKey() {
        IdempotentLockService service = new IdempotentLockService(new RecordingRedisLockService(true));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.parseKey(" ", new Object[0], null))
                .withMessage("幂等键不能为空");
    }

    @Test
    void parseKeyShouldFailClosedWhenSpelCannotBeEvaluated() {
        IdempotentLockService service = new IdempotentLockService(new RecordingRedisLockService(true));

        assertThatThrownBy(() -> service.parseKey("'payment:batch:start:' + #missing.id", new Object[]{42L}, null))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("解析幂等键表达式失败");
    }

    @Test
    void tryLockShouldApplyPrefixOnlyOnce() {
        RecordingRedisLockService redisLockService = new RecordingRedisLockService(true);
        IdempotentLockService service = new IdempotentLockService(redisLockService);

        boolean locked = service.tryLock("payment:batch:start:42", 600, "idempotent:");

        assertThat(locked).isTrue();
        assertThat(redisLockService.key).isEqualTo("idempotent:payment:batch:start:42");
        assertThat(redisLockService.expireSeconds).isEqualTo(600);
    }

    private record ImportRequest(String batchNo) {
    }

    private static class RecordingRedisLockService implements IdempotentLockService.RedisLockService {

        private final boolean locked;
        private String key;
        private int expireSeconds;

        private RecordingRedisLockService(boolean locked) {
            this.locked = locked;
        }

        @Override
        public boolean tryLock(String key, int expireSeconds) {
            this.key = key;
            this.expireSeconds = expireSeconds;
            return locked;
        }

        @Override
        public void unlock(String key) {
            this.key = key;
        }

        @Override
        public void forceUnlock(String key) {
            this.key = key;
        }
    }
}
