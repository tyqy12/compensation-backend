package com.yiyundao.compensation.interfaces.dto.config;

import lombok.Data;

/**
 * 短信服务配置DTO
 */
@Data
public class SmsConfigDto {
    private String provider; // 服务商: aliyun/tencent/huawei/mock

    // 阿里云配置
    private String accessKeyId;
    private String accessKeySecret;
    private String signName; // 短信签名
    private String templateCode; // 默认模板

    // 腾讯云配置
    private String secretId;
    private String secretKey;
    private String appId;
    private String sdkAppId;

    // 华为云配置
    private String appKey;
    private String appSecret;
    private String sender;
    private String templateId;

    // 通用配置
    private String endpoint; // 服务端点
    private String region; // 地域
    private Integer dailyLimit; // 每日发送限制
    private Integer rateLimitPerMinute; // 每分钟限流
    private Boolean enabled; // 是否启用
}