package com.yiyundao.compensation.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置类
 * 提供 HTTP 客户端用于外部 API 调用（钉钉、飞书等）
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 创建 RestTemplate Bean
     * 用于发送 HTTP 请求到外部服务（钉钉 API、飞书 API 等）
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
