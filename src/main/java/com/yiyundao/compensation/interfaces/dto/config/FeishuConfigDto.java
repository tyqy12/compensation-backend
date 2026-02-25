package com.yiyundao.compensation.interfaces.dto.config;

import lombok.Data;

@Data
public class FeishuConfigDto {
    private String appId;
    private String appSecret;
    private String appToken; // 应用Token
    private Boolean enabled;
}

