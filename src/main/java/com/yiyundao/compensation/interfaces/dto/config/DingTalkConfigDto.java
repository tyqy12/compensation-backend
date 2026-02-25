package com.yiyundao.compensation.interfaces.dto.config;

import lombok.Data;

@Data
public class DingTalkConfigDto {
    private String appKey;
    private String appSecret;
    private String webhookUrl; // 机器人Webhook地址
    private Boolean enabled;
}

