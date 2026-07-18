package com.yiyundao.compensation.service;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/**
 * 独立的配置解密服务，专门用于解密集成配置数据
 * 不依赖于 IntegrationConfigService，避免循环依赖
 */
@Slf4j
@Service
public class ConfigDecryptionService {

    private final String fallbackAesKey;
    private final Environment environment;

    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final String ENVELOPE_VERSION = "v2";
    private static final int IV_LENGTH = 16; // 16字节IV
    private static final int GCM_NONCE_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String DEFAULT_AES_KEY = "default_aes_key_32_chars_long_here";
    private static final int MIN_AES_KEY_LENGTH = 16;

    // Cached key material
    private byte[] aesKeyBytes; // 32 bytes (AES-256)
    private String aesKeyId;
    private Map<String, byte[]> aesKeyring = Map.of();
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${encryption.aes.key-id:env-current}")
    private String configuredAesKeyId;

    @Value("${encryption.aes.keyring:}")
    private String configuredAesKeyring;

    static {
        // 添加BouncyCastle提供者以支持SM4
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Autowired
    public ConfigDecryptionService(
            @Value("${encryption.aes.key:" + DEFAULT_AES_KEY + "}") String fallbackAesKey,
            Environment environment) {
        this.fallbackAesKey = fallbackAesKey;
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        validateKeyForEnvironment();
        this.aesKeyBytes = deriveKey(fallbackAesKey, 32);
        this.aesKeyId = configuredAesKeyId == null || configuredAesKeyId.isBlank()
                ? "env-current" : configuredAesKeyId.trim();
        Map<String, byte[]> keyring = new LinkedHashMap<>();
        parseKeyring(configuredAesKeyring).forEach((keyId, secret) -> keyring.put(keyId, deriveKey(secret, 32)));
        keyring.put(aesKeyId, aesKeyBytes);
        this.aesKeyring = Map.copyOf(keyring);
        log.info("配置解密服务初始化完成");
    }

    private void validateKeyForEnvironment() {
        if (isBlank(fallbackAesKey) || fallbackAesKey.length() < MIN_AES_KEY_LENGTH) {
            throw new IllegalStateException("encryption.aes.key must be at least 16 characters");
        }
        if (isProdLikeProfile() && isPlaceholderKey(fallbackAesKey)) {
            throw new IllegalStateException("Production deployment cannot use default encryption.aes.key");
        }
        if (isDefaultAesKey(fallbackAesKey)) {
            log.warn("Using default/dev config encryption key. Do NOT use in production.");
        }
    }

    private boolean isProdLikeProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("prod")
                        || profile.equals("production")
                        || profile.equals("staging"));
    }

    private boolean isDefaultAesKey(String key) {
        return DEFAULT_AES_KEY.equals(key) || key.toLowerCase(Locale.ROOT).contains("default");
    }

    private boolean isPlaceholderKey(String key) {
        if (isBlank(key)) {
            return true;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return isDefaultAesKey(key) || normalized.contains("change-me")
                || normalized.contains("your_") || normalized.contains("replace-with");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 解密配置数据
     */
    public String decrypt(String encryptedData) {
        try {
            if (isVersion2Envelope(encryptedData)) {
                return gcmDecrypt(encryptedData);
            }
            return legacyDecrypt(encryptedData);
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
            return gcmEncrypt(data);
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
    private byte[] generateRandomNonce() {
        byte[] nonce = new byte[GCM_NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        return nonce;
    }

    /**
     * AES加密实现
     */
    private String gcmEncrypt(String data) throws Exception {
        byte[] nonce = generateRandomNonce();
        Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKeyBytes, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
        cipher.updateAAD(envelopeAad(aesKeyId));
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        byte[] payload = new byte[nonce.length + encrypted.length];
        System.arraycopy(nonce, 0, payload, 0, nonce.length);
        System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
        return ENVELOPE_VERSION + ":" + aesKeyId + ":"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    }

    /**
     * AES解密实现
     */
    private String legacyDecrypt(String encryptedData) throws Exception {
        Exception lastFailure = null;
        for (byte[] key : aesKeyring.values()) {
            try {
                return legacyDecryptWithKey(encryptedData, key);
            } catch (Exception e) {
                lastFailure = e;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        return legacyDecryptWithKey(encryptedData, aesKeyBytes);
    }

    private String legacyDecryptWithKey(String encryptedData, byte[] keyBytes) throws Exception {
        byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedData);
        if (encryptedWithIv.length <= IV_LENGTH) {
            throw new IllegalArgumentException("Invalid legacy encryption payload");
        }

        // Extract IV from the beginning
        byte[] iv = new byte[IV_LENGTH];
        byte[] encrypted = new byte[encryptedWithIv.length - IV_LENGTH];
        System.arraycopy(encryptedWithIv, 0, iv, 0, IV_LENGTH);
        System.arraycopy(encryptedWithIv, IV_LENGTH, encrypted, 0, encrypted.length);

        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private String gcmDecrypt(String encryptedData) throws Exception {
        String[] parts = encryptedData.split(":", 3);
        if (parts.length != 3 || !ENVELOPE_VERSION.equals(parts[0]) || parts[1].isBlank()) {
            throw new IllegalArgumentException("Unsupported encryption envelope");
        }
        byte[] key = aesKeyring.get(parts[1]);
        if (key == null) {
            throw new IllegalStateException("Encryption key version is not available: " + parts[1]);
        }
        byte[] payload = Base64.getUrlDecoder().decode(parts[2]);
        if (payload.length <= GCM_NONCE_LENGTH + (GCM_TAG_LENGTH / 8)) {
            throw new IllegalArgumentException("Invalid encryption envelope payload");
        }
        byte[] nonce = Arrays.copyOf(payload, GCM_NONCE_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(payload, GCM_NONCE_LENGTH, payload.length);
        Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
        cipher.updateAAD(envelopeAad(parts[1]));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private byte[] envelopeAad(String keyId) {
        return (ENVELOPE_VERSION + ":" + keyId).getBytes(StandardCharsets.UTF_8);
    }

    private boolean isVersion2Envelope(String value) {
        return value != null && value.startsWith(ENVELOPE_VERSION + ":");
    }

    private Map<String, String> parseKeyring(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String entry : raw.split(",")) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                continue;
            }
            String keyId = entry.substring(0, separator).trim();
            String key = entry.substring(separator + 1).trim();
            if (!keyId.isEmpty() && !key.isEmpty()) {
                result.put(keyId, key);
            }
        }
        return result;
    }
}
