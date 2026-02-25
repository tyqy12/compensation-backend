package com.yiyundao.compensation.security;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
public class ExternalApiContext {

    private static final ThreadLocal<ExternalApiClient> CONTEXT = new ThreadLocal<>();

    public void set(ExternalApiClient client) {
        CONTEXT.set(client);
    }

    public ExternalApiClient current() {
        return CONTEXT.get();
    }

    public void clear() {
        CONTEXT.remove();
    }

    @Data
    @Builder
    public static class ExternalApiClient {
        private Long appId;
        private String clientId;
        private String appName;
        private String clientIp;
        private java.util.List<String> scopes;
    }
}

