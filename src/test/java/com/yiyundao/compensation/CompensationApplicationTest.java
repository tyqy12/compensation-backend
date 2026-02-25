package com.yiyundao.compensation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Spring Boot 应用上下文测试")
class CompensationApplicationTest {

    @Test
    @DisplayName("应用上下文加载测试")
    void testContextLoads() {
        // 验证 Spring 应用上下文能够正确加载
    }
}
