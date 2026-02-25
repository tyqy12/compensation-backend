package com.yiyundao.compensation.common.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 加密工具类
 * <p>
 * 支持 AES-256-GCM 加密，用于敏感配置加密和敏感数据加密。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Slf4j
@Component
public class EncryptionUtils {

    /**
     * AES-256-GCM 算法
     */
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    /**
     * GCM 认证标签长度（128位）
     */
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * GCM IV 长度（12字节）
     */
    private static final int GCM_IV_LENGTH = 12;

    /**
     * 默认加密密钥（通过环境变量覆盖）
     */
    @Value("${encryption.default-key:}")
    private String defaultKey;

    private SecretKeySpec secretKey;

    @PostConstruct
    public void init() {
        if (defaultKey != null && !defaultKey.isEmpty()) {
            this.secretKey = deriveKey(defaultKey);
            log.info("加密工具初始化完成，使用默认密钥");
        } else {
            log.warn("未配置加密密钥，部分加密功能不可用");
        }
    }

    /**
     * 加密文本
     *
     * @param plainText 明文
     * @return Base64 编码的密文（包含 IV）
     */
    public String encrypt(String plainText) {
        return encrypt(plainText, null);
    }

    /**
     * 加密文本（使用指定密钥）
     *
     * @param plainText 明文
     * @param key       密钥（可选）
     * @return Base64 编码的密文（包含 IV）
     */
    public String encrypt(String plainText, String key) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }

        try {
            SecretKeySpec keySpec = getKey(key);
            byte[] iv = generateIV();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 组合 IV + 密文
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("加密失败: {}", e.getMessage());
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 解密文本
     *
     * @param cipherText Base64 编码的密文
     * @return 明文
     */
    public String decrypt(String cipherText) {
        return decrypt(cipherText, null);
    }

    /**
     * 解密文本（使用指定密钥）
     *
     * @param cipherText Base64 编码的密文
     * @param key        密钥（可选）
     * @return 明文
     */
    public String decrypt(String cipherText, String key) {
        if (cipherText == null || cipherText.isEmpty()) {
            return null;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);

            // 分离 IV 和密文
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherTextBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherTextBytes);

            SecretKeySpec keySpec = getKey(key);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherTextBytes);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("解密失败: {}", e.getMessage());
            throw new RuntimeException("解密失败", e);
        }
    }

    /**
     * 解码加密配置值
     * <p>
     * 支持多种格式：
     * - 直接返回（普通值）
     * - ENC(xxx) - 加密值
     * - PLAIN(xxx) - 明文值
     * </p>
     *
     * @param configValue 配置值
     * @return 解码后的值
     */
    public String decodeConfigValue(String configValue) {
        if (configValue == null || configValue.isEmpty()) {
            return configValue;
        }

        String trimmed = configValue.trim();

        // 检查是否加密格式
        if (trimmed.startsWith("ENC(") && trimmed.endsWith(")")) {
            String encrypted = trimmed.substring(4, trimmed.length() - 1);
            return decrypt(encrypted);
        }

        // 检查是否明文格式
        if (trimmed.startsWith("PLAIN(") && trimmed.endsWith(")")) {
            return trimmed.substring(6, trimmed.length() - 1);
        }

        // 直接返回
        return trimmed;
    }

    /**
     * 编码配置值
     *
     * @param value 值
     * @return 编码后的值
     */
    public String encodeConfigValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return "ENC(" + encrypt(value) + ")";
    }

    /**
     * 生成随机密钥
     *
     * @return Base64 编码的随机密钥
     */
    public String generateKey() {
        byte[] keyBytes = new byte[32]; // 256 位
        new SecureRandom().nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    /**
     * 获取密钥
     */
    private SecretKeySpec getKey(String key) {
        if (key != null && !key.isEmpty()) {
            return deriveKey(key);
        }
        if (secretKey != null) {
            return secretKey;
        }
        throw new RuntimeException("未配置加密密钥");
    }

    /**
     * 从字符串派生密钥
     */
    private SecretKeySpec deriveKey(String key) {
        try {
            // 使用 SHA-256 派生密钥
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("密钥派生失败", e);
        }
    }

    /**
     * 生成 IV
     */
    private byte[] generateIV() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * 检查是否加密格式
     *
     * @param value 值
     * @return 是否加密格式
     */
    public boolean isEncrypted(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.trim().startsWith("ENC(") && value.trim().endsWith(")");
    }
}
