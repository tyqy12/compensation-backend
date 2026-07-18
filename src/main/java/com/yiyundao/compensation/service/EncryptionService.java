package com.yiyundao.compensation.service;

import com.yiyundao.compensation.interfaces.dto.config.EncryptionConfigDto;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EncryptionService {

    // 保留作为fallback，当动态配置不可用时使用
    @Value("${encryption.sm4.key:default_sm4_key_32_chars_long_here}")
    private String fallbackSm4Key;

    @Value("${encryption.aes.key:default_aes_key_32_chars_long_here}")
    private String fallbackAesKey;

    @Value("${encryption.aes.key-id:env-current}")
    private String fallbackAesKeyId;

    @Value("${encryption.aes.keyring:}")
    private String fallbackAesKeyring;

    @Value("${encryption.sm4.keyring:}")
    private String fallbackSm4Keyring;

    private static final String SM4_ALGORITHM = "SM4/CBC/PKCS5Padding";
    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final String ENVELOPE_VERSION = "v2";
    private static final int IV_LENGTH = 16; // 16字节IV
    private static final int GCM_NONCE_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Environment environment;
    @Lazy
    private final IntegrationConfigService integrationConfigService;

    // Cached key material (refreshed when needed)
    private volatile byte[] cachedSm4KeyBytes; // 16 bytes
    private volatile Map<String, byte[]> cachedSm4Keyring = Map.of();
    private volatile byte[] cachedAesKeyBytes; // 32 bytes (AES-256)
    private volatile String cachedAesKeyId;
    private volatile Map<String, byte[]> cachedAesKeyring = Map.of();
    private volatile long lastKeyRefresh = 0;
    private static final long KEY_REFRESH_INTERVAL = 300_000; // 5分钟刷新一次

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

        EncryptionConfigDto config = loadEncryptionConfig();
        String sm4Key = resolveSm4Key(config);
        String aesKey = resolveAesKey(config);

        // 生产/预发布严格校验：不得使用默认占位符，且长度不少于16字符
        if (isProdLike) {
            if (isPlaceholderKey(sm4Key) || isPlaceholderKey(aesKey)) {
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

        // 初始化密钥缓存
        refreshKeys(config);

        log.info("Encryption service initialized (profiles: {}), keys derived: SM4={}, AES={} bits",
                String.join(",", environment.getActiveProfiles()),
                cachedSm4KeyBytes.length * 8,
                cachedAesKeyBytes.length * 8);
    }

    private boolean isPlaceholderKey(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("default")
                || normalized.contains("change-me")
                || normalized.contains("your_")
                || normalized.contains("replace-with");
    }

    /**
     * 获取动态加密配置。配置中心或数据库不可用时返回null，由调用方使用fallback配置。
     */
    private EncryptionConfigDto loadEncryptionConfig() {
        try {
            return integrationConfigService.getEncryptionConfig();
        } catch (Exception e) {
            log.warn("获取动态加密配置失败，使用fallback配置", e);
            return null;
        }
    }

    /**
     * 获取当前SM4密钥（优先从动态配置获取）
     */
    private String resolveSm4Key(EncryptionConfigDto config) {
        if (config != null && config.getSm4Key() != null && !config.getSm4Key().trim().isEmpty()
                && (!isProdLikeProfile() || !isPlaceholderKey(config.getSm4Key()))) {
            log.debug("使用动态配置的SM4密钥");
            return config.getSm4Key();
        }
        log.debug("使用fallback SM4密钥");
        return fallbackSm4Key;
    }

    /**
     * 获取当前AES密钥（优先从动态配置获取）
     */
    private String resolveAesKey(EncryptionConfigDto config) {
        if (config != null && config.getAesKey() != null && !config.getAesKey().trim().isEmpty()
                && (!isProdLikeProfile() || !isPlaceholderKey(config.getAesKey()))) {
            log.debug("使用动态配置的AES密钥");
            return config.getAesKey();
        }
        log.debug("使用fallback AES密钥");
        return fallbackAesKey;
    }

    private boolean isProdLikeProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod")
                        || profile.equalsIgnoreCase("production")
                        || profile.equalsIgnoreCase("staging"));
    }

    private String resolveAesKeyId(EncryptionConfigDto config) {
        if (config != null && config.getAesKeyId() != null && !config.getAesKeyId().trim().isEmpty()) {
            return config.getAesKeyId().trim();
        }
        return fallbackAesKeyId == null || fallbackAesKeyId.isBlank() ? "env-current" : fallbackAesKeyId.trim();
    }

    private Map<String, String> resolveAesKeyring(EncryptionConfigDto config, String currentKeyId, String currentKey) {
        Map<String, String> result = parseKeyring(fallbackAesKeyring);
        if (config != null && config.getAesKeyring() != null) {
            config.getAesKeyring().forEach((keyId, key) -> {
                if (keyId != null && !keyId.isBlank() && key != null && !key.isBlank()) {
                    result.put(keyId.trim(), key);
                }
            });
        }
        result.put(currentKeyId, currentKey);
        return result;
    }

    private Map<String, String> resolveSm4Keyring(EncryptionConfigDto config, String currentKeyId, String currentKey) {
        Map<String, String> result = parseKeyring(fallbackSm4Keyring);
        if (config != null && config.getSm4Keyring() != null) {
            config.getSm4Keyring().forEach((keyId, key) -> {
                if (keyId != null && !keyId.isBlank() && key != null && !key.isBlank()) {
                    result.put(keyId.trim(), key);
                }
            });
        }
        result.put(currentKeyId, currentKey);
        return result;
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

    /**
     * 刷新加密密钥缓存
     */
    private synchronized void refreshKeys() {
        refreshKeys(loadEncryptionConfig());
    }

    /**
     * 基于已加载配置刷新加密密钥缓存，避免同一次刷新重复查库。
     */
    private synchronized void refreshKeys(EncryptionConfigDto config) {
        String sm4Key = resolveSm4Key(config);
        String aesKey = resolveAesKey(config);
        String sm4KeyId = config != null && config.getSm4KeyId() != null && !config.getSm4KeyId().isBlank()
                ? config.getSm4KeyId().trim() : "legacy-current";
        String aesKeyId = resolveAesKeyId(config);

        if (aesKeyId.contains(":")) {
            throw new IllegalStateException("Encryption AES key id must not contain ':'");
        }

        // 派生密钥字节：
        // - SM4 需要 16 字节密钥
        // - AES 使用 32 字节（AES-256）；通过 SHA-256 从配置字符串派生
        Map<String, byte[]> nextKeyring = new LinkedHashMap<>();
        resolveAesKeyring(config, aesKeyId, aesKey)
                .forEach((keyId, key) -> nextKeyring.put(keyId, deriveKey(key, 32)));
        // 同一进程轮换时保留上一版，即使配置中心短暂删除了旧 keyring 条目。
        if (cachedAesKeyId != null && cachedAesKeyBytes != null) {
            nextKeyring.putIfAbsent(cachedAesKeyId, cachedAesKeyBytes);
        }
        Map<String, byte[]> nextSm4Keyring = new LinkedHashMap<>();
        resolveSm4Keyring(config, sm4KeyId, sm4Key)
                .forEach((keyId, key) -> nextSm4Keyring.put(keyId, deriveKey(key, 16)));
        if (cachedSm4KeyBytes != null) {
            nextSm4Keyring.putIfAbsent("legacy-previous", cachedSm4KeyBytes);
        }
        this.cachedSm4KeyBytes = nextSm4Keyring.get(sm4KeyId);
        this.cachedSm4Keyring = Map.copyOf(nextSm4Keyring);
        this.cachedAesKeyBytes = nextKeyring.get(aesKeyId);
        this.cachedAesKeyId = aesKeyId;
        this.cachedAesKeyring = Map.copyOf(nextKeyring);
        this.lastKeyRefresh = System.currentTimeMillis();

        log.debug("加密密钥已刷新");
    }

    /**
     * 获取SM4密钥字节（带缓存刷新）
     */
    private byte[] getSm4KeyBytes() {
        if (System.currentTimeMillis() - lastKeyRefresh > KEY_REFRESH_INTERVAL) {
            refreshKeys();
        }
        return cachedSm4KeyBytes;
    }

    /**
     * 获取AES密钥字节（带缓存刷新）
     */
    private byte[] getAesKeyBytes() {
        if (System.currentTimeMillis() - lastKeyRefresh > KEY_REFRESH_INTERVAL) {
            refreshKeys();
        }
        return cachedAesKeyBytes;
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
            // 新数据统一使用带认证标签的 AES-GCM；旧 SM4/CBC 密文仍由 decryptIdCard 兼容读取。
            return gcmEncrypt(idCard);
        } catch (Exception e) {
            log.error("身份证号加密失败", e);
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * SM4解密身份证号
     */
    public String decryptIdCard(String encryptedIdCard) {
        try {
            if (isVersion2Envelope(encryptedIdCard)) {
                return gcmDecrypt(encryptedIdCard);
            }
            log.debug("读取兼容格式身份证号密文");
            return decryptLegacySm4WithKeyring(encryptedIdCard);
        } catch (Exception e) {
            log.debug("兼容格式 SM4 解密失败，尝试 AES", e);
            try {
                return decryptLegacyAesWithKeyring(encryptedIdCard);
            } catch (Exception legacyAesException) {
                log.error("身份证号解密失败", legacyAesException);
                throw new RuntimeException("解密失败", legacyAesException);
            }
        }
    }

    /**
     * AES加密通用数据
     */
    public String encrypt(String data) {
        try {
            return gcmEncrypt(data);
        } catch (Exception e) {
            log.error("数据加密失败", e);
            throw new RuntimeException("数据加密失败", e);
        }
    }

    /**
     * AES解密通用数据
     */
    public String decrypt(String encryptedData) {
        try {
            return isVersion2Envelope(encryptedData)
                    ? gcmDecrypt(encryptedData)
                    : decryptLegacyAesWithKeyring(encryptedData);
        } catch (Exception e) {
            log.error("数据解密失败", e);
            throw new RuntimeException("数据解密失败", e);
        }
    }

    /**
     * SM4加密实现
     */
    private String sm4Encrypt(String data) throws Exception {
        byte[] iv = generateRandomIV();
        SecretKeySpec keySpec = new SecretKeySpec(getSm4KeyBytes(), "SM4");
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
        return sm4Decrypt(encryptedData, getSm4KeyBytes());
    }

    private String sm4Decrypt(String encryptedData, byte[] keyBytes) throws Exception {
        byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedData);
        if (encryptedWithIv.length <= IV_LENGTH) {
            throw new IllegalArgumentException("Invalid legacy encryption payload");
        }

        // Extract IV from the beginning
        byte[] iv = new byte[IV_LENGTH];
        byte[] encrypted = new byte[encryptedWithIv.length - IV_LENGTH];
        System.arraycopy(encryptedWithIv, 0, iv, 0, IV_LENGTH);
        System.arraycopy(encryptedWithIv, IV_LENGTH, encrypted, 0, encrypted.length);

        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "SM4");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(SM4_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private String decryptLegacySm4WithKeyring(String encryptedData) throws Exception {
        Exception lastFailure = null;
        for (byte[] key : cachedSm4Keyring.values()) {
            try {
                return sm4Decrypt(encryptedData, key);
            } catch (Exception e) {
                lastFailure = e;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        return sm4Decrypt(encryptedData, getSm4KeyBytes());
    }

    /**
     * v2 密文格式：v2:keyId:base64url(nonce || ciphertext || authenticationTag)。
     * keyId 和版本参与 AAD，避免密文被换绑到另一把密钥。
     */
    private String gcmEncrypt(String data) throws Exception {
        byte[] nonce = new byte[GCM_NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(getAesKeyBytes(), "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
        cipher.updateAAD(envelopeAad(cachedAesKeyId));
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        byte[] payload = new byte[nonce.length + encrypted.length];
        System.arraycopy(nonce, 0, payload, 0, nonce.length);
        System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
        return ENVELOPE_VERSION + ":" + cachedAesKeyId + ":"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    }

    private String gcmDecrypt(String encryptedData) throws Exception {
        String[] parts = encryptedData.split(":", 3);
        if (parts.length != 3 || !ENVELOPE_VERSION.equals(parts[0]) || parts[1].isBlank()) {
            throw new IllegalArgumentException("Unsupported encryption envelope");
        }
        byte[] key = cachedAesKeyring.get(parts[1]);
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
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, "AES"),
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

    /**
     * AES加密实现
     */
    private String legacyAesEncrypt(String data) throws Exception {
        byte[] iv = generateRandomIV();
        SecretKeySpec keySpec = new SecretKeySpec(getAesKeyBytes(), "AES");
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
    private String legacyAesDecrypt(String encryptedData, byte[] keyBytes) throws Exception {
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

    private String decryptLegacyAesWithKeyring(String encryptedData) throws Exception {
        Exception lastFailure = null;
        for (byte[] key : cachedAesKeyring.values()) {
            try {
                return legacyAesDecrypt(encryptedData, key);
            } catch (Exception e) {
                lastFailure = e;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        return legacyAesDecrypt(encryptedData, getAesKeyBytes());
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

    /**
     * 验证加密配置
     */
    public boolean checkEncryptionConfig() {
        try {
            // 检查是否启用加密配置
            if (!integrationConfigService.isPlatformEnabled("encryption")) {
                log.warn("加密配置未启用，使用fallback配置");
                return true; // fallback配置仍然可用
            }

            // 检查动态配置
            EncryptionConfigDto config = integrationConfigService.getEncryptionConfig();
            if (config == null) {
                log.warn("加密配置不存在，使用fallback配置");
                return true; // fallback配置仍然可用
            }

            // 检查密钥配置
            boolean hasValidSm4Key = config.getSm4Key() != null &&
                                   !config.getSm4Key().trim().isEmpty() &&
                                   config.getSm4Key().length() >= 16;

            boolean hasValidAesKey = config.getAesKey() != null &&
                                   !config.getAesKey().trim().isEmpty() &&
                                   config.getAesKey().length() >= 16;

            if (!hasValidSm4Key && !hasValidAesKey) {
                log.warn("动态加密配置中的密钥无效，使用fallback配置");
                return true; // fallback配置仍然可用
            }

            // 尝试测试加密解密
            String testData = "test_encryption_" + System.currentTimeMillis();
            String encrypted = encrypt(testData);
            String decrypted = decrypt(encrypted);

            boolean testPassed = testData.equals(decrypted);
            if (testPassed) {
                log.info("加密配置验证通过");
            } else {
                log.error("加密配置验证失败：测试数据不匹配");
            }

            return testPassed;

        } catch (Exception e) {
            log.error("加密配置验证异常", e);
            return false;
        }
    }

    /**
     * 强制刷新加密密钥缓存
     */
    public void forceRefreshKeys() {
        log.info("强制刷新加密密钥缓存");
        refreshKeys();
    }
}
