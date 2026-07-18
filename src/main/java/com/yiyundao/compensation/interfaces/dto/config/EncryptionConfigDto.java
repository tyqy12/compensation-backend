package com.yiyundao.compensation.interfaces.dto.config;

import lombok.Data;

import java.util.Map;

/**
 * 加密服务配置DTO
 */
@Data
public class EncryptionConfigDto {
    private String aesKey; // AES密钥
    private String aesKeyId; // 当前AES密钥版本
    private Map<String, String> aesKeyring; // 轮换期间保留的历史密钥，keyId -> secret
    private String sm4Key; // SM4密钥
    private String sm4KeyId; // 历史SM4密钥版本
    private Map<String, String> sm4Keyring; // 历史SM4密钥，供兼容解密
    private String algorithm; // 算法: SM4+AES/AES256
    private String keyDerivation; // 密钥派生方式
    private Integer keyRotationDays; // 密钥轮换天数
    private Boolean enabled; // 是否启用
}
