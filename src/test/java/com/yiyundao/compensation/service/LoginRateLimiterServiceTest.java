package com.yiyundao.compensation.service;

import com.yiyundao.compensation.modules.system.service.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginRateLimiterServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SysConfigService sysConfigService;

    private LoginRateLimiterService service;

    @BeforeEach
    void setUp() {
        service = new LoginRateLimiterService(redisTemplate, sysConfigService);
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("redis down"));
        when(sysConfigService.getLong(anyString(), anyLong())).thenAnswer(invocation -> invocation.getArgument(1));
        when(sysConfigService.getInt(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return key.contains("user.threshold") ? 2 : 30;
        });
    }

    @Test
    void usesLocalLimiterWhenRedisUnavailable() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        assertThat(service.isLocked("admin", "127.0.0.1")).isFalse();

        service.onFail("admin", "127.0.0.1");
        assertThat(service.isLocked("admin", "127.0.0.1")).isFalse();

        service.onFail("admin", "127.0.0.1");
        assertThat(service.isLocked("admin", "127.0.0.1")).isTrue();
    }

    @Test
    void onSuccessDoesNotThrowWhenRedisUnavailable() {
        service.onFail("admin", "127.0.0.1");
        assertThatNoException().isThrownBy(() -> service.onSuccess("admin", "127.0.0.1"));
    }
}
