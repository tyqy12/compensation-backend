package com.yiyundao.compensation.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "external-api")
public class ExternalApiAuthProperties {

    private Jwt jwt = new Jwt();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Jwt {
        private String secret;
        private long expirationSeconds = 1800;
    }

    @Data
    public static class RateLimit {
        private int perMinute = 600;
        private long alertCooldownSeconds = 300;
    }
}

