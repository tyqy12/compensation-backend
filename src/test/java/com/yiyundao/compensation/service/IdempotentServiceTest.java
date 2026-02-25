package com.yiyundao.compensation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("幂等性服务测试")
class IdempotentServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private IdempotentService idempotentService;

    @BeforeEach
    void setUp() {
        idempotentService = new IdempotentService(redisTemplate);
    }

    @Test
    @DisplayName("获取锁成功")
    void testTryLock_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        boolean result = idempotentService.tryLock("test-key", 300, 0);

        assertTrue(result);
        verify(valueOperations).setIfAbsent(
                eq("idempotent:test-key"),
                eq("1"),
                eq(Duration.ofSeconds(300))
        );
    }

    @Test
    @DisplayName("获取锁失败 - 锁已存在")
    void testTryLock_Fail_LockExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        boolean result = idempotentService.tryLock("test-key", 300, 0);

        assertFalse(result);
    }

    @Test
    @DisplayName("获取锁 - 等待锁成功")
    void testTryLock_WaitSuccess() throws InterruptedException {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false)
                .thenReturn(true);

        boolean result = idempotentService.tryLock("test-key", 300, 200);

        assertTrue(result);
        verify(valueOperations, times(2)).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("获取锁 - 等待超时")
    void testTryLock_WaitTimeout() throws InterruptedException {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        boolean result = idempotentService.tryLock("test-key", 300, 100);

        assertFalse(result);
    }

    @Test
    @DisplayName("释放锁")
    void testUnlock() {
        when(redisTemplate.delete("idempotent:test-key")).thenReturn(true);

        idempotentService.unlock("test-key");

        verify(redisTemplate).delete("idempotent:test-key");
    }

    @Test
    @DisplayName("释放锁 - 锁不存在")
    void testUnlock_NotExists() {
        when(redisTemplate.delete("idempotent:test-key")).thenReturn(false);

        idempotentService.unlock("test-key");

        verify(redisTemplate).delete("idempotent:test-key");
    }

    @Test
    @DisplayName("生成幂等键 - SpEL 表达式")
    void testGenerateKey_WithExpression() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("batchNo", "BATCH001");
        variables.put("employeeId", 1001L);

        String key = idempotentService.generateKey(
                "#batchNo + ':' + #employeeId",
                variables
        );

        assertEquals("BATCH001:1001", key);
    }

    @Test
    @DisplayName("生成幂等键 - 复杂 SpEL 表达式")
    void testGenerateKey_ComplexExpression() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("request", null);
        variables.put("method", "POST");
        variables.put("uri", "/api/payment");
        variables.put("ip", "192.168.1.1");
        variables.put("batchNo", "PAY20240111001");

        String key = idempotentService.generateKey(
                "#method + ':' + #uri + ':' + #batchNo",
                variables
        );

        assertEquals("POST:/api/payment:PAY20240111001", key);
    }

    @Test
    @DisplayName("生成幂等键 - 默认键")
    void testGenerateKey_Default() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("key1", "value1");
        variables.put("key2", "value2");

        String key = idempotentService.generateKey(null, variables);

        assertTrue(key.contains("key1=value1"));
        assertTrue(key.contains("key2=value2"));
    }

    @Test
    @DisplayName("生成幂等键 - 空变量")
    void testGenerateKey_EmptyVariables() {
        Map<String, Object> variables = new HashMap<>();

        String key = idempotentService.generateKey(null, variables);

        assertEquals("", key);
    }

    @Test
    @DisplayName("生成幂等键 - 变长参数")
    void testGenerateKey_VarArgs() {
        String key = idempotentService.generateKey("#p0 + '-' + #p1", "BATCH001", 1001L);

        assertEquals("BATCH001-1001", key);
    }

    @Test
    @DisplayName("生成幂等键 - 空 SpEL 表达式")
    void testGenerateKey_EmptyExpression() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("key", "value");

        String key = idempotentService.generateKey("", variables);

        assertEquals("key=value:", key);
    }

    @Test
    @DisplayName("幂等键前缀测试")
    void testKeyPrefix() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        idempotentService.tryLock("unique-key", 60, 0);

        verify(valueOperations).setIfAbsent(
                eq("idempotent:unique-key"),
                anyString(),
                any(Duration.class)
        );
    }
}
