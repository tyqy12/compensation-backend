package com.yiyundao.compensation.service;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;

/**
 * 独立的配置解密服务，专门用于解密集成配置数据
 * 不依赖于 IntegrationConfigService，避免循环依赖
 */
@Slf4j
@Service
public class ConfigDecryptionService {

    @Value("${encryption.aes.key:default_aes_key_32_chars_long_here}")
    private String fallbackAesKey;

    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16; // 16字节IV


    // Cached key material
    private byte[] aesKeyBytes; // 32 bytes (AES-256)

    static {
        // 添加BouncyCastle提供者以支持SM4
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @PostConstruct
    public void init() {
        // 使用fallback密钥初始化
        this.aesKeyBytes = deriveKey(fallbackAesKey, 32);
        log.info("配置解密服务初始化完成，使用fallback AES密钥");
    }

    /**
     * 解密配置数据
     */
    public String decrypt(String encryptedData) {
        try {
            return aesDecrypt(encryptedData);
        } catch (Exception e) {
            log.error("配置解密失败", e);
            throw new RuntimeException("配置解密失败", e);
        }
    }

    /**
     * 加密配置数据
     */
    public String encrypt(String data) {
        try {
            return aesEncrypt(data);
        } catch (Exception e) {
            log.error("配置加密失败", e);
            throw new RuntimeException("配置加密失败", e);
        }
    }

    // 使用 SHA-256 从任意长度字符串派生固定长度密钥
    private byte[] deriveKey(String secret, int length) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest((secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8));
            if (length <= digest.length) {
                return Arrays.copyOf(digest, length);
            }
            // 如果需要更长（此处不会发生），则重复哈希拼接
            byte[] out = new byte[length];
            int copied = 0;
            byte[] current = digest;
            while (copied < length) {
                int toCopy = Math.min(current.length, length - copied);
                System.arraycopy(current, 0, out, copied, toCopy);
                copied += toCopy;
                current = sha256.digest(current);
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive key material", e);
        }
    }

    /**
     * 生成随机IV
     */
    private byte[] generateRandomIV() {
        byte[] iv = new byte[IV_LENGTH];
        new java.security.SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * AES加密实现
     */
    private String aesEncrypt(String data) throws Exception {
        byte[] iv = generateRandomIV();
        SecretKeySpec keySpec = new SecretKeySpec(aesKeyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

        // Prepend IV to encrypted data
        byte[] encryptedWithIv = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
        System.arraycopy(encrypted, 0, encryptedWithIv, iv.length, encrypted.length);

        return Base64.getEncoder().encodeToString(encryptedWithIv);
    }

    /**
     * AES解密实现
     */
    private String aesDecrypt(String encryptedData) throws Exception {
        byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedData);

        // Extract IV from the beginning
        byte[] iv = new byte[IV_LENGTH];
        byte[] encrypted = new byte[encryptedWithIv.length - IV_LENGTH];
        System.arraycopy(encryptedWithIv, 0, iv, 0, IV_LENGTH);
        System.arraycopy(encryptedWithIv, IV_LENGTH, encrypted, 0, encrypted.length);

        SecretKeySpec keySpec = new SecretKeySpec(aesKeyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}