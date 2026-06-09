package com.yiyundao.compensation.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.utils.DataAccessExceptionUtils;
import com.yiyundao.compensation.infrastructure.dao.IntegrationConfigMapper;
import com.yiyundao.compensation.interfaces.dto.config.*;
import com.yiyundao.compensation.modules.system.entity.IntegrationConfig;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationConfigServiceImpl extends ServiceImpl<IntegrationConfigMapper, IntegrationConfig>
        implements IntegrationConfigService {

    private final ObjectMapper objectMapper;
    private final com.yiyundao.compensation.service.ConfigDecryptionService configDecryptionService;

    @Override
    public IntegrationConfig getRawConfig(String platformType) {
        LambdaQueryWrapper<IntegrationConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(IntegrationConfig::getPlatformType, platformType)
          .orderByDesc(IntegrationConfig::getUpdateTime)
          .orderByDesc(IntegrationConfig::getId)
          .last("limit 1");
        return getOne(qw);
    }

    @Override
    public void saveOrUpdate(String platformType, String configJson, boolean enabled) {
        LambdaQueryWrapper<IntegrationConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(IntegrationConfig::getPlatformType, platformType)
                .orderByDesc(IntegrationConfig::getUpdateTime)
                .orderByDesc(IntegrationConfig::getId)
                .last("limit 1");
        IntegrationConfig found = getOne(qw);
        String encrypted;
        try {
            encrypted = configDecryptionService.encrypt(configJson);
        } catch (Exception e) {
            // 加密失败时，为避免明文落库，直接抛出异常
            throw new IllegalStateException("配置加密失败", e);
        }

        if (found == null) {
            IntegrationConfig cfg = new IntegrationConfig();
            cfg.setPlatformType(platformType);
            cfg.setConfigJson(encrypted);
            cfg.setEnabled(enabled);
            save(cfg);
        } else {
            found.setConfigJson(encrypted);
            found.setEnabled(enabled);
            updateById(found);
        }
    }

    @Override
    public WechatConfigDto getWechatConfig() {
        try {
            IntegrationConfig cfg = getEnabledConfig("wechat");
            if (cfg == null || !StringUtils.hasText(cfg.getConfigJson())) {
                log.warn("数据库中未找到微信配置");
                return null;
            }
            return parseTypedConfig("wechat", cfg.getConfigJson(), WechatConfigDto.class);
        } catch (Exception e) {
            log.error("解析企微配置失败", e);
            return null;
        }
    }

    @Override
    public DingTalkConfigDto getDingTalkConfig() {
        try {
            IntegrationConfig cfg = getEnabledConfig("dingtalk");
            if (cfg == null || !StringUtils.hasText(cfg.getConfigJson())) return null;
            return parseTypedConfig("dingtalk", cfg.getConfigJson(), DingTalkConfigDto.class);
        } catch (Exception e) {
            log.error("解析钉钉配置失败", e);
            return null;
        }
    }

    @Override
    public FeishuConfigDto getFeishuConfig() {
        try {
            IntegrationConfig cfg = getEnabledConfig("feishu");
            if (cfg == null || !StringUtils.hasText(cfg.getConfigJson())) return null;
            return parseTypedConfig("feishu", cfg.getConfigJson(), FeishuConfigDto.class);
        } catch (Exception e) {
            log.error("解析飞书配置失败", e);
            return null;
        }
    }

    @Override
    public AlipayConfigDto getAlipayConfig() {
        try {
            IntegrationConfig cfg = getEnabledConfig("alipay");
            if (cfg == null || !StringUtils.hasText(cfg.getConfigJson())) return null;
            return parseTypedConfig("alipay", cfg.getConfigJson(), AlipayConfigDto.class);
        } catch (Exception e) {
            log.error("解析支付宝配置失败", e);
            return null;
        }
    }

    @Override
    public YunzhanghuConfigDto getYunzhanghuConfig() {
        try {
            IntegrationConfig cfg = getEnabledConfig("yunzhanghu");
            if (cfg == null || !StringUtils.hasText(cfg.getConfigJson())) return null;
            return parseTypedConfig("yunzhanghu", cfg.getConfigJson(), YunzhanghuConfigDto.class);
        } catch (Exception e) {
            log.error("解析云账户配置失败", e);
            return null;
        }
    }

    @Override
    public Map<String, String> getDecryptedConfig(String platformType) {
        try {
            IntegrationConfig cfg = getEnabledConfig(platformType);
            if (cfg == null || !StringUtils.hasText(cfg.getConfigJson())) {
                return new HashMap<>();
            }
            return parseMapConfig(platformType, cfg.getConfigJson());
        } catch (Exception e) {
            log.error("解析{}配置失败", platformType, e);
            return new HashMap<>();
        }
    }

    @Override
    public boolean isPlatformEnabled(String platformType) {
        IntegrationConfig cfg = getRawConfig(platformType);
        return cfg != null && Boolean.TRUE.equals(cfg.getEnabled());
    }

    @Override
    public String getConfigValue(String platformType, String key) {
        Map<String, String> config = getDecryptedConfig(platformType);
        return config.get(key);
    }

    @Override
    public String getConfigValue(String platformType, String key, String defaultValue) {
        String value = getConfigValue(platformType, key);
        return value != null ? value : defaultValue;
    }

    @Override
    public SmsConfigDto getSmsConfig() {
        try {
            IntegrationConfig cfg = getEnabledConfig("sms");
            if (cfg == null || !StringUtils.hasText(cfg.getConfigJson())) return null;
            return parseTypedConfig("sms", cfg.getConfigJson(), SmsConfigDto.class);
        } catch (Exception e) {
            log.error("解析短信配置失败", e);
            return null;
        }
    }

    @Override
    public EmailConfigDto getEmailConfig() {
        try {
            IntegrationConfig cfg = getEnabledConfig("email");
            if (cfg == null || !StringUtils.hasText(cfg.getConfigJson())) return null;
            return parseTypedConfig("email", cfg.getConfigJson(), EmailConfigDto.class);
        } catch (Exception e) {
            log.error("解析邮件配置失败", e);
            return null;
        }
    }

    @Override
    public EncryptionConfigDto getEncryptionConfig() {
        try {
            IntegrationConfig cfg = getEnabledConfig("encryption");
            if (cfg == null || !StringUtils.hasText(cfg.getConfigJson())) return null;
            return parseTypedConfig("encryption", cfg.getConfigJson(), EncryptionConfigDto.class);
        } catch (Exception e) {
            if (DataAccessExceptionUtils.isResourceFailure(e)) {
                log.warn("动态加密配置暂不可用，无法连接数据库，将使用fallback配置: {}", e.getMessage());
                return null;
            }
            log.error("解析加密配置失败", e);
            return null;
        }
    }

    private <T> T parseTypedConfig(String platformType, String rawConfig, Class<T> configType) {
        String json = resolveConfigJson(platformType, rawConfig);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, configType);
        } catch (Exception e) {
            log.error("解析{}配置失败(JSON反序列化)", platformType, e);
            return null;
        }
    }

    private Map<String, String> parseMapConfig(String platformType, String rawConfig) {
        String json = resolveConfigJson(platformType, rawConfig);
        if (!StringUtils.hasText(json)) {
            return new HashMap<>();
        }
        try {
            Map<String, String> map = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
            return map != null ? map : new HashMap<>();
        } catch (Exception e) {
            log.error("解析{}配置失败(JSON反序列化)", platformType, e);
            return new HashMap<>();
        }
    }

    private String resolveConfigJson(String platformType, String rawConfig) {
        if (!StringUtils.hasText(rawConfig)) {
            return null;
        }
        String source = rawConfig.trim();
        if (looksLikeJson(source)) {
            log.debug("检测到{}配置为明文JSON，按兼容逻辑直接解析", platformType);
            return source;
        }
        try {
            String plain = configDecryptionService.decrypt(source);
            if (looksLikeJson(plain)) {
                return plain;
            }
            String twicePlain = configDecryptionService.decrypt(plain);
            if (looksLikeJson(twicePlain)) {
                log.warn("检测到{}配置存在历史重复加密，已按兼容逻辑解析", platformType);
                return twicePlain;
            }
            log.error("解析{}配置失败(解密结果非JSON)", platformType);
            return null;
        } catch (Exception e) {
            log.error("解析{}配置失败(密文解密失败)", platformType, e);
            return null;
        }
    }

    private static boolean looksLikeJson(String s) {
        if (s == null) return false;
        String t = s.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }

    private IntegrationConfig getEnabledConfig(String platformType) {
        LambdaQueryWrapper<IntegrationConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(IntegrationConfig::getPlatformType, platformType)
                .eq(IntegrationConfig::getEnabled, true)
                .orderByDesc(IntegrationConfig::getUpdateTime)
                .orderByDesc(IntegrationConfig::getId)
                .last("limit 1");
        return getOne(qw);
    }
}
