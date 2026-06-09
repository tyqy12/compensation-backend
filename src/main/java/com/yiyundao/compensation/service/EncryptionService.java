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

    // 保留作为fallback，当动态配置不可用时使用
    @Value("${encryption.sm4.key:default_sm4_key_32_chars_long_here}")
    private String fallbackSm4Key;

    @Value("${encryption.aes.key:default_aes_key_32_chars_long_here}")
    private String fallbackAesKey;

    private static final String SM4_ALGORITHM = "SM4/CBC/PKCS5Padding";
    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16; // 16字节IV

    private final SecureRandom secureRandom = new SecureRandom();
    private final Environment environment;
    @Lazy
    private final IntegrationConfigService integrationConfigService;

    // Cached key material (refreshed when needed)
    private volatile byte[] cachedSm4KeyBytes; // 16 bytes
    private volatile byte[] cachedAesKeyBytes; // 32 bytes (AES-256)
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

        // 初始化密钥缓存
        refreshKeys(config);

        log.info("Encryption service initialized (profiles: {}), keys derived: SM4={}, AES={} bits",
                String.join(",", environment.getActiveProfiles()),
                cachedSm4KeyBytes.length * 8,
                cachedAesKeyBytes.length * 8);
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
        if (config != null && config.getSm4Key() != null && !config.getSm4Key().trim().isEmpty()) {
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
        if (config != null && config.getAesKey() != null && !config.getAesKey().trim().isEmpty()) {
            log.debug("使用动态配置的AES密钥");
            return config.getAesKey();
        }
        log.debug("使用fallback AES密钥");
        return fallbackAesKey;
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
    private void refreshKeys(EncryptionConfigDto config) {
        String sm4Key = resolveSm4Key(config);
        String aesKey = resolveAesKey(config);

        // 派生密钥字节：
        // - SM4 需要 16 字节密钥
        // - AES 使用 32 字节（AES-256）；通过 SHA-256 从配置字符串派生
        this.cachedSm4KeyBytes = deriveKey(sm4Key, 16);
        this.cachedAesKeyBytes = deriveKey(aesKey, 32);
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
        byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedData);

        // Extract IV from the beginning
        byte[] iv = new byte[IV_LENGTH];
        byte[] encrypted = new byte[encryptedWithIv.length - IV_LENGTH];
        System.arraycopy(encryptedWithIv, 0, iv, 0, IV_LENGTH);
        System.arraycopy(encryptedWithIv, IV_LENGTH, encrypted, 0, encrypted.length);

        SecretKeySpec keySpec = new SecretKeySpec(getSm4KeyBytes(), "SM4");
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
    private String aesDecrypt(String encryptedData) throws Exception {
        byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedData);

        // Extract IV from the beginning
        byte[] iv = new byte[IV_LENGTH];
        byte[] encrypted = new byte[encryptedWithIv.length - IV_LENGTH];
        System.arraycopy(encryptedWithIv, 0, iv, 0, IV_LENGTH);
        System.arraycopy(encryptedWithIv, IV_LENGTH, encrypted, 0, encrypted.length);

        SecretKeySpec keySpec = new SecretKeySpec(getAesKeyBytes(), "AES");
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
