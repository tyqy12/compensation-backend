package com.yiyundao.compensation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class EncryptionService {

    @Value("${encryption.sm4.key:default_sm4_key_32_chars_long_here}")
    private String sm4Key;

    @Value("${encryption.aes.key:default_aes_key_32_chars_long_here}")
    private String aesKey;

    private static final String SM4_ALGORITHM = "SM4/CBC/PKCS5Padding";
    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16; // 16字节IV

    private final SecureRandom secureRandom = new SecureRandom();
    private final Environment environment;

    // Derived key material used by ciphers (sizes must match algorithm requirements)
    private byte[] sm4KeyBytes; // 16 bytes
    private byte[] aesKeyBytes; // 32 bytes (AES-256)

    static {
        // 添加BouncyCastle提供者以支持SM4
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @PostConstruct
    public void validateConfiguration() {
        // 环境识别
        boolean isProdLike = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production") || p.equalsIgnoreCase("staging"));

        // 生产/预发布严格校验：不得使用默认占位符，且长度不少于16字符
        if (isProdLike) {
            if (sm4Key == null || aesKey == null || sm4Key.contains("default") || aesKey.contains("default")) {
                throw new IllegalStateException("Production deployment cannot use default or empty encryption keys");
            }
            if (sm4Key.length() < 16 || aesKey.length() < 16) {
                throw new IllegalStateException("Encryption keys must be at least 16 characters in prod");
            }
        } else {
            // 开发/测试环境：允许占位符，但给出警告
            if (sm4Key == null || aesKey == null || sm4Key.contains("default") || aesKey.contains("default")) {
                log.warn("Using default/dev encryption keys. Do NOT use in production.");
            }
        }

        // 派生密钥字节：
        // - SM4 需要 16 字节密钥
        // - AES 使用 32 字节（AES-256）；通过 SHA-256 从配置字符串派生
        this.sm4KeyBytes = deriveKey(sm4Key, 16);
        this.aesKeyBytes = deriveKey(aesKey, 32);

        log.info("Encryption service initialized (profiles: {}), keys derived: SM4={}, AES={} bits",
                String.join(",", environment.getActiveProfiles()),
                sm4KeyBytes.length * 8,
                aesKeyBytes.length * 8);
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
        secureRandom.nextBytes(iv);
        return iv;
    }

    /**
     * SM4加密身份证号 (符合国密标准)
     */
    public String encryptIdCard(String idCard) {
        try {
            log.debug("使用SM4加密身份证号");
            return sm4Encrypt(idCard);
        } catch (Exception e) {
            log.error("SM4加密失败，降级使用AES", e);
            try {
                return aesEncrypt(idCard);
            } catch (Exception aesException) {
                log.error("AES加密也失败", aesException);
                throw new RuntimeException("加密失败", aesException);
            }
        }
    }

    /**
     * SM4解密身份证号
     */
    public String decryptIdCard(String encryptedIdCard) {
        try {
            log.debug("使用SM4解密身份证号");
            return sm4Decrypt(encryptedIdCard);
        } catch (Exception e) {
            log.error("SM4解密失败，尝试AES解密", e);
            try {
                return aesDecrypt(encryptedIdCard);
            } catch (Exception aesException) {
                log.error("AES解密也失败", aesException);
                throw new RuntimeException("解密失败", aesException);
            }
        }
    }

    /**
     * AES加密通用数据
     */
    public String encrypt(String data) {
        try {
            return aesEncrypt(data);
        } catch (Exception e) {
            log.error("AES加密失败", e);
            throw new RuntimeException("数据加密失败", e);
        }
    }

    /**
     * AES解密通用数据
     */
    public String decrypt(String encryptedData) {
        try {
            return aesDecrypt(encryptedData);
        } catch (Exception e) {
            log.error("AES解密失败", e);
            throw new RuntimeException("数据解密失败", e);
        }
    }

    /**
     * SM4加密实现
     */
    private String sm4Encrypt(String data) throws Exception {
        byte[] iv = generateRandomIV();
        SecretKeySpec keySpec = new SecretKeySpec(sm4KeyBytes, "SM4");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(SM4_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

        // Prepend IV to encrypted data
        byte[] encryptedWithIv = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
        System.arraycopy(encrypted, 0, encryptedWithIv, iv.length, encrypted.length);

        return Base64.getEncoder().encodeToString(encryptedWithIv);
    }

    /**
     * SM4解密实现
     */
    private String sm4Decrypt(String encryptedData) throws Exception {
        byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedData);

        // Extract IV from the beginning
        byte[] iv = new byte[IV_LENGTH];
        byte[] encrypted = new byte[encryptedWithIv.length - IV_LENGTH];
        System.arraycopy(encryptedWithIv, 0, iv, 0, IV_LENGTH);
        System.arraycopy(encryptedWithIv, IV_LENGTH, encrypted, 0, encrypted.length);

        SecretKeySpec keySpec = new SecretKeySpec(sm4KeyBytes, "SM4");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(SM4_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
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

    /**
     * 数据脱敏显示 (用于日志和前端展示)
     */
    public String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return "****";
        }
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * 银行卡号脱敏
     */
    public String maskBankAccount(String bankAccount) {
        if (bankAccount == null || bankAccount.length() < 8) {
            return "****";
        }
        return bankAccount.substring(0, 4) + "****" + bankAccount.substring(bankAccount.length() - 4);
    }

    /**
     * 手机号脱敏
     */
    public String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
