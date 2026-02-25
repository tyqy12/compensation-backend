package com.yiyundao.compensation.interfaces.dto.config;

import lombok.Data;

/**
 * 加密服务配置DTO
 */
@Data
public class EncryptionConfigDto {
    private String aesKey; // AES密钥
    private String sm4Key; // SM4密钥
    private String algorithm; // 算法: SM4+AES/AES256
    private String keyDerivation; // 密钥派生方式
    private Integer keyRotationDays; // 密钥轮换天数
    private Boolean enabled; // 是否启用
}