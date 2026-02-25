package com.yiyundao.compensation.interfaces.dto.app;

import lombok.Data;

import java.util.List;

@Data
public class AppRegistryUpdateRequest {

    private String appName;
    private List<String> scopes;
    private List<String> ipWhitelist;
    private String webhookUrl;
    private String status;
}

