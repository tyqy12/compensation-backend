package com.yiyundao.compensation.interfaces.dto.app;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AppRegistrySecretResponse {
    private Long id;
    private String appName;
    private String clientId;
    private String clientSecret;
    private List<String> scopes;
    private List<String> ipWhitelist;
    private String webhookUrl;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

