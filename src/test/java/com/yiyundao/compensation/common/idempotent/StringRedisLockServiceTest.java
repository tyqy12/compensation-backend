package com.yiyundao.compensation.common.idempotent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StringRedisLockServiceTest {

    @Test
    void tryLockShouldUseStringRedisTemplateSetIfAbsentWithTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("idempotent:key"), any(String.class), eq(Duration.ofSeconds(30))))
                .thenReturn(true);

        StringRedisLockService service = new StringRedisLockService(provider(redisTemplate));

        assertThat(service.tryLock("idempotent:key", 30)).isTrue();
        verify(valueOperations).setIfAbsent(eq("idempotent:key"), any(String.class), eq(Duration.ofSeconds(30)));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void unlockShouldReleaseOnlyOwnedLockWithScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("idempotent:key"), any(String.class), eq(Duration.ofSeconds(30))))
                .thenReturn(true);
        StringRedisLockService service = new StringRedisLockService(provider(redisTemplate));

        assertThat(service.tryLock("idempotent:key", 30)).isTrue();
        service.unlock("idempotent:key");

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("idempotent:key")), any(String.class));
        verify(redisTemplate, never()).delete("idempotent:key");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void unlockShouldSkipWhenCurrentThreadDoesNotOwnLock() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StringRedisLockService service = new StringRedisLockService(provider(redisTemplate));

        assertThatCode(() -> service.unlock("idempotent:key")).doesNotThrowAnyException();

        verify(redisTemplate, never()).execute(any(RedisScript.class), any(List.class), any(String.class));
        verify(redisTemplate, never()).delete("idempotent:key");
    }

    private ObjectProvider<StringRedisTemplate> provider(StringRedisTemplate redisTemplate) {
        return new ObjectProvider<>() {
            @Override
            public StringRedisTemplate getObject(Object... args) {
                return redisTemplate;
            }

            @Override
            public StringRedisTemplate getIfAvailable() {
                return redisTemplate;
            }

            @Override
            public StringRedisTemplate getIfUnique() {
                return redisTemplate;
            }

            @Override
            public StringRedisTemplate getObject() {
                return redisTemplate;
            }
        };
    }
}
