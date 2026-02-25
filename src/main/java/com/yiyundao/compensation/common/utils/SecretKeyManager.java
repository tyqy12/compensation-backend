package com.yiyundao.compensation.common.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 密钥管理服务
 * <p>
 * 功能：
 * 1. 管理多版本密钥
 * 2. 支持密钥轮换
 * 3. 支持密钥热更新
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Slf4j
@Service
public class SecretKeyManager {

    /**
     * 密钥版本分隔符
     */
    private static final String VERSION_SEPARATOR = ":";

    /**
     * 当前密钥版本
     */
    private volatile int currentVersion = 1;

    /**
     * 密钥缓存
     */
    private final Map<Integer, String> keyCache = new ConcurrentHashMap<>();

    /**
     * 加密工具
     */
    private final EncryptionUtils encryptionUtils;

    /**
     * 主密钥（用于加密其他密钥）
     */
    @Value("${secret.master-key:}")
    private String masterKey;

    /**
     * 是否允许从环境变量读取密钥
     */
    @Value("${secret.allow-env-read:true}")
    private boolean allowEnvRead;

    /**
     * 环境变量密钥前缀
     */
    private static final String ENV_KEY_PREFIX = "SECRET_KEY_V";

    public SecretKeyManager(EncryptionUtils encryptionUtils) {
        this.encryptionUtils = encryptionUtils;
    }

    @PostConstruct
    public void init() {
        // 加载配置的主密钥
        if (masterKey != null && !masterKey.isEmpty()) {
            log.info("主密钥已配置");
        } else if (allowEnvRead) {
            // 尝试从环境变量加载
            loadKeysFromEnv();
        }
    }

    /**
     * 获取当前版本的密钥
     *
     * @return 密钥
     */
    public String getCurrentKey() {
        return getKey(currentVersion);
    }

    /**
     * 获取指定版本的密钥
     *
     * @param version 版本号
     * @return 密钥
     */
    public String getKey(int version) {
        return keyCache.get(version);
    }

    /**
     * 添加新密钥
     *
     * @param key 密钥
     * @return 新版本号
     */
    public int addKey(String key) {
        int newVersion = currentVersion + 1;
        keyCache.put(newVersion, key);
        currentVersion = newVersion;
        log.info("新增密钥版本: {}", newVersion);
        return newVersion;
    }

    /**
     * 添加新密钥（带版本号）
     *
     * @param version 版本号
     * @param key     密钥
     */
    public void addKey(int version, String key) {
        keyCache.put(version, key);
        if (version > currentVersion) {
            currentVersion = version;
        }
        log.info("添加密钥版本: {}", version);
    }

    /**
     * 设置当前版本
     *
     * @param version 版本号
     */
    public void setCurrentVersion(int version) {
        if (!keyCache.containsKey(version)) {
            throw new IllegalArgumentException("版本不存在: " + version);
        }
        currentVersion = version;
        log.info("切换到密钥版本: {}", version);
    }

    /**
     * 获取当前版本号
     *
     * @return 版本号
     */
    public int getCurrentVersion() {
        return currentVersion;
    }

    /**
     * 解密配置值
     *
     * @param encryptedValue 加密的配置值
     * @return 解密后的值
     */
    public String decryptConfigValue(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return encryptedValue;
        }

        String trimmed = encryptedValue.trim();

        // 格式: ENC(version:ciphertext)
        if (trimmed.startsWith("ENC(") && trimmed.endsWith(")")) {
            String content = trimmed.substring(4, trimmed.length() - 1);
            return decryptWithVersion(content);
        }

        return encryptionUtils.decodeConfigValue(encryptedValue);
    }

    /**
     * 使用指定版本解密
     *
     * @param content 内容（version:ciphertext）
     * @return 解密后的值
     */
    private String decryptWithVersion(String content) {
        int separatorIndex = content.indexOf(VERSION_SEPARATOR);
        if (separatorIndex > 0) {
            try {
                int version = Integer.parseInt(content.substring(0, separatorIndex));
                String cipherText = content.substring(separatorIndex + 1);
                String key = keyCache.get(version);
                if (key != null) {
                    return encryptionUtils.decrypt(cipherText, key);
                } else {
                    log.warn("密钥版本不存在: {}", version);
                }
            } catch (NumberFormatException e) {
                log.warn("版本号解析失败: {}", content);
            }
        }

        // 降级到默认解密
        return encryptionUtils.decrypt(content);
    }

    /**
     * 加密配置值
     *
     * @param value 值
     * @return 加密的配置值
     */
    public String encryptConfigValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String cipherText = encryptionUtils.encrypt(value, getCurrentKey());
        return "ENC(" + currentVersion + VERSION_SEPARATOR + cipherText + ")";
    }

    /**
     * 从环境变量加载密钥
     */
    private void loadKeysFromEnv() {
        // 尝试读取主密钥
        String envMasterKey = System.getenv("MASTER_KEY");
        if (envMasterKey != null && !envMasterKey.isEmpty()) {
            this.masterKey = envMasterKey;
            log.info("从环境变量加载主密钥成功");
        }

        // 尝试读取各版本密钥
        for (int i = 1; i <= 10; i++) { // 最多支持 10 个版本
            String envKey = System.getenv(ENV_KEY_PREFIX + i);
            if (envKey != null && !envKey.isEmpty()) {
                keyCache.put(i, envKey);
                log.info("从环境变量加载密钥版本: {}", i);
            }
        }

        if (!keyCache.isEmpty()) {
            // 设置最高版本为当前版本
            currentVersion = keyCache.keySet().stream().max(Integer::compareTo).orElse(1);
            log.info("当前密钥版本: {}", currentVersion);
        }
    }

    /**
     * 生成新的密钥
     *
     * @return 密钥
     */
    public String generateNewKey() {
        return encryptionUtils.generateKey();
    }

    /**
     * 轮换密钥
     *
     * @return 新版本号
     */
    public int rotateKey() {
        String newKey = generateNewKey();
        return addKey(newKey);
    }

    /**
     * 检查密钥是否已配置
     *
     * @return 是否已配置
     */
    public boolean isConfigured() {
        return !keyCache.isEmpty() || encryptionUtils.isEncrypted("");
    }

    /**
     * 获取密钥版本列表
     *
     * @return 版本号列表
     */
    public java.util.Set<Integer> getVersions() {
        return keyCache.keySet();
    }
}
