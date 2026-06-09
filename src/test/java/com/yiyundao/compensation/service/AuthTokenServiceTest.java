package com.yiyundao.compensation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private AuthTokenService service;

    @BeforeEach
    void setUp() {
        service = new AuthTokenService(redisTemplate);
    }

    @Test
    void consumeRefreshTokenReadsAndDeletesTokenAtomically() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("auth:refresh:refresh-token")).thenReturn("alice");

        String owner = service.consumeRefreshToken("refresh-token");

        assertThat(owner).isEqualTo("alice");
        verify(valueOperations).getAndDelete("auth:refresh:refresh-token");
        verify(redisTemplate, never()).delete("auth:refresh:refresh-token");
    }

    @Test
    void consumeRefreshTokenRejectsBlankTokenWithoutRedisCall() {
        assertThat(service.consumeRefreshToken(" ")).isNull();

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void consumeRefreshTokenFailsClosedWhenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("auth:refresh:refresh-token"))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertThat(service.consumeRefreshToken("refresh-token")).isNull();
    }

    @Test
    void storeRefreshTokenReturnsTrueWhenRedisWriteSucceeds() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        assertThat(service.storeRefreshToken("alice", "refresh-token", 60_000)).isTrue();

        verify(valueOperations).set("auth:refresh:refresh-token", "alice", 60_000,
                java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Test
    void storeRefreshTokenReturnsFalseWhenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOperations)
                .set("auth:refresh:refresh-token", "alice", 60_000, java.util.concurrent.TimeUnit.MILLISECONDS);

        assertThat(service.storeRefreshToken("alice", "refresh-token", 60_000)).isFalse();
    }

    @Test
    void storeRefreshTokenRejectsInvalidInputWithoutRedisCall() {
        assertThat(service.storeRefreshToken("alice", "refresh-token", 0)).isFalse();
        assertThat(service.storeRefreshToken("", "refresh-token", 60_000)).isFalse();
        assertThat(service.storeRefreshToken("alice", "", 60_000)).isFalse();

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void consumeOAuthStateReadsAndDeletesStateAtomically() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("oauth:state:wechat:state-token")).thenReturn(1);

        assertThat(service.consumeOAuthState("wechat", "state-token")).isTrue();

        verify(valueOperations).getAndDelete("oauth:state:wechat:state-token");
        verify(redisTemplate, never()).hasKey("oauth:state:wechat:state-token");
        verify(redisTemplate, never()).delete("oauth:state:wechat:state-token");
    }

    @Test
    void consumeOAuthStateRejectsBlankInputWithoutRedisCall() {
        assertThat(service.consumeOAuthState("wechat", " ")).isFalse();
        assertThat(service.consumeOAuthState(" ", "state-token")).isFalse();

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void consumeOAuthStateFailsClosedWhenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("oauth:state:wechat:state-token"))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertThat(service.consumeOAuthState("wechat", "state-token")).isFalse();
    }

    @Test
    void blacklistTokenStoresRedisAndLocalFallback() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        assertThat(service.blacklistToken("access-token", 60_000)).isTrue();

        verify(valueOperations).set("auth:blacklist:access-token", 1, 60_000, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertThat(service.isBlacklisted("access-token")).isTrue();
        verify(redisTemplate, never()).hasKey("auth:blacklist:access-token");
    }

    @Test
    void blacklistTokenKeepsLocalFallbackWhenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOperations)
                .set("auth:blacklist:access-token", 1, 60_000, java.util.concurrent.TimeUnit.MILLISECONDS);

        assertThat(service.blacklistToken("access-token", 60_000)).isFalse();
        assertThat(service.isBlacklisted("access-token")).isTrue();
    }

    @Test
    void deleteRefreshTokenReturnsFalseWhenRedisUnavailable() {
        when(redisTemplate.delete("auth:refresh:refresh-token"))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertThat(service.deleteRefreshToken("refresh-token")).isFalse();
    }
}
