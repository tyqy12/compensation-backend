package com.yiyundao.compensation.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.infrastructure.dao.IntegrationConfigMapper;
import com.yiyundao.compensation.interfaces.dto.config.*;
import com.yiyundao.compensation.modules.system.entity.IntegrationConfig;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
            if (cfg == null || cfg.getConfigJson() == null) {
                log.warn("数据库中未找到微信配置");
                return null;
            }
            String cipher = cfg.getConfigJson().trim();
            log.debug("原始配置内容: {}, 长度: {}", cipher, cipher.length());
            
            // 先尝试解密
            try {
                String plain = configDecryptionService.decrypt(cipher);
                if (looksLikeJson(plain)) {
                    log.debug("配置解密成功");
                    return objectMapper.readValue(plain, WechatConfigDto.class);
                }
                String twicePlain = configDecryptionService.decrypt(plain);
                if (looksLikeJson(twicePlain)) {
                    log.warn("检测到微信配置存在历史重复加密，已按兼容逻辑解析");
                    return objectMapper.readValue(twicePlain, WechatConfigDto.class);
                }
                return null;
            } catch (Exception ex) {
                log.debug("解密失败，尝试解析明文JSON: {}", ex.getMessage());
                // 兼容历史：若数据库中存的是明文JSON，直接解析
                if (looksLikeJson(cipher)) {
                    log.debug("检测到明文JSON格式，直接解析");
                    return objectMapper.readValue(cipher, WechatConfigDto.class);
                }
                log.error("解析企微配置失败(密文/明文均解析失败), 配置内容: {}", cipher.substring(0, Math.min(50, cipher.length())));
                return null;
            }
        } catch (Exception e) {
            log.error("解析企微配置失败", e);
            return null;
        }
    }

    @Override
    public DingTalkConfigDto getDingTalkConfig() {
        try {
            IntegrationConfig cfg = getEnabledConfig("dingtalk");
            if (cfg == null || cfg.getConfigJson() == null) return null;
            String cipher = cfg.getConfigJson();
            try {
                String plain = configDecryptionService.decrypt(cipher);
                if (looksLikeJson(plain)) {
                    return objectMapper.readValue(plain, DingTalkConfigDto.class);
                }
                String twicePlain = configDecryptionService.decrypt(plain);
                if (looksLikeJson(twicePlain)) {
                    log.warn("检测到钉钉配置存在历史重复加密，已按兼容逻辑解析");
                    return objectMapper.readValue(twicePlain, DingTalkConfigDto.class);
                }
                return null;
            } catch (Exception ex) {
                if (looksLikeJson(cipher)) {
                    return objectMapper.readValue(cipher, DingTalkConfigDto.class);
                }
                log.error("解析钉钉配置失败(密文/明文均解析失败)", ex);
                return null;
            }
        } catch (Exception e) {
            log.error("解析钉钉配置失败", e);
            return null;
        }
    }

    @Override
    public FeishuConfigDto getFeishuConfig() {
        try {
            IntegrationConfig cfg = getEnabledConfig("feishu");
            if (cfg == null || cfg.getConfigJson() == null) return null;
            String cipher = cfg.getConfigJson();
            try {
                String plain = configDecryptionService.decrypt(cipher);
                if (looksLikeJson(plain)) {
                    return objectMapper.readValue(plain, FeishuConfigDto.class);
                }
                String twicePlain = configDecryptionService.decrypt(plain);
                if (looksLikeJson(twicePlain)) {
                    log.warn("检测到飞书配置存在历史重复加密，已按兼容逻辑解析");
                    return objectMapper.readValue(twicePlain, FeishuConfigDto.class);
                }
                return null;
            } catch (Exception ex) {
                if (looksLikeJson(cipher)) {
                    return objectMapper.readValue(cipher, FeishuConfigDto.class);
                }
                log.error("解析飞书配置失败(密文/明文均解析失败)", ex);
                return null;
            }
        } catch (Exception e) {
            log.error("解析飞书配置失败", e);
            return null;
        }
    }

    @Override
    public AlipayConfigDto getAlipayConfig() {
        try {
            IntegrationConfig cfg = getEnabledConfig("alipay");
            if (cfg == null || cfg.getConfigJson() == null) return null;
            String cipher = cfg.getConfigJson();
            try {
                String plain = configDecryptionService.decrypt(cipher);
                if (looksLikeJson(plain)) {
                    return objectMapper.readValue(plain, AlipayConfigDto.class);
                }
                String twicePlain = configDecryptionService.decrypt(plain);
                if (looksLikeJson(twicePlain)) {
                    log.warn("检测到支付宝配置存在历史重复加密，已按兼容逻辑解析");
                    return objectMapper.readValue(twicePlain, AlipayConfigDto.class);
                }
                return null;
            } catch (Exception ex) {
                if (looksLikeJson(cipher)) {
                    return objectMapper.readValue(cipher, AlipayConfigDto.class);
                }
                log.error("解析支付宝配置失败(密文/明文均解析失败)", ex);
                return null;
            }
        } catch (Exception e) {
            log.error("解析支付宝配置失败", e);
            return null;
        }
    }

    @Override
    public YunzhanghuConfigDto getYunzhanghuConfig() {
        try {
            IntegrationConfig cfg = getEnabledConfig("yunzhanghu");
            if (cfg == null || cfg.getConfigJson() == null) return null;
            String cipher = cfg.getConfigJson();
            try {
                String plain = configDecryptionService.decrypt(cipher);
                if (looksLikeJson(plain)) {
                    return objectMapper.readValue(plain, YunzhanghuConfigDto.class);
                }
                String twicePlain = configDecryptionService.decrypt(plain);
                if (looksLikeJson(twicePlain)) {
                    log.warn("检测到云账户配置存在历史重复加密，已按兼容逻辑解析");
                    return objectMapper.readValue(twicePlain, YunzhanghuConfigDto.class);
                }
                return null;
            } catch (Exception ex) {
                if (looksLikeJson(cipher)) {
                    return objectMapper.readValue(cipher, YunzhanghuConfigDto.class);
                }
                log.error("解析云账户配置失败(密文/明文均解析失败)", ex);
                return null;
            }
        } catch (Exception e) {
            log.error("解析云账户配置失败", e);
            return null;
        }
    }

    @Override
    public Map<String, String> getDecryptedConfig(String platformType) {
        try {
            IntegrationConfig cfg = getEnabledConfig(platformType);
            if (cfg == null || cfg.getConfigJson() == null) {
                return new HashMap<>();
            }
            String cipher = cfg.getConfigJson();
            try {
                String plain = configDecryptionService.decrypt(cipher);
                if (looksLikeJson(plain)) {
                    return objectMapper.readValue(plain, new TypeReference<Map<String, String>>() {});
                }
                String twicePlain = configDecryptionService.decrypt(plain);
                if (looksLikeJson(twicePlain)) {
                    log.warn("检测到{}配置存在历史重复加密，已按兼容逻辑解析", platformType);
                    return objectMapper.readValue(twicePlain, new TypeReference<Map<String, String>>() {});
                }
                return new HashMap<>();
            } catch (Exception ex) {
                if (looksLikeJson(cipher)) {
                    return objectMapper.readValue(cipher, new TypeReference<Map<String, String>>() {});
                }
                log.error("解析{}配置失败(密文/明文均解析失败)", platformType, ex);
                return new HashMap<>();
            }
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
            if (cfg == null || cfg.getConfigJson() == null) return null;
            String cipher = cfg.getConfigJson();
            try {
                String plain = configDecryptionService.decrypt(cipher);
                if (looksLikeJson(plain)) {
                    return objectMapper.readValue(plain, SmsConfigDto.class);
                }
                String twicePlain = configDecryptionService.decrypt(plain);
                if (looksLikeJson(twicePlain)) {
                    log.warn("检测到短信配置存在历史重复加密，已按兼容逻辑解析");
                    return objectMapper.readValue(twicePlain, SmsConfigDto.class);
                }
                return null;
            } catch (Exception ex) {
                if (looksLikeJson(cipher)) {
                    return objectMapper.readValue(cipher, SmsConfigDto.class);
                }
                log.error("解析短信配置失败(密文/明文均解析失败)", ex);
                return null;
            }
        } catch (Exception e) {
            log.error("解析短信配置失败", e);
            return null;
        }
    }

    @Override
    public EmailConfigDto getEmailConfig() {
        try {
            IntegrationConfig cfg = getEnabledConfig("email");
            if (cfg == null || cfg.getConfigJson() == null) return null;
            String cipher = cfg.getConfigJson();
            try {
                String plain = configDecryptionService.decrypt(cipher);
                if (looksLikeJson(plain)) {
                    return objectMapper.readValue(plain, EmailConfigDto.class);
                }
                String twicePlain = configDecryptionService.decrypt(plain);
                if (looksLikeJson(twicePlain)) {
                    log.warn("检测到邮件配置存在历史重复加密，已按兼容逻辑解析");
                    return objectMapper.readValue(twicePlain, EmailConfigDto.class);
                }
                return null;
            } catch (Exception ex) {
                if (looksLikeJson(cipher)) {
                    return objectMapper.readValue(cipher, EmailConfigDto.class);
                }
                log.error("解析邮件配置失败(密文/明文均解析失败)", ex);
                return null;
            }
        } catch (Exception e) {
            log.error("解析邮件配置失败", e);
            return null;
        }
    }

    @Override
    public EncryptionConfigDto getEncryptionConfig() {
        try {
            IntegrationConfig cfg = getEnabledConfig("encryption");
            if (cfg == null || cfg.getConfigJson() == null) return null;
            String cipher = cfg.getConfigJson();
            try {
                String plain = configDecryptionService.decrypt(cipher);
                if (looksLikeJson(plain)) {
                    return objectMapper.readValue(plain, EncryptionConfigDto.class);
                }
                String twicePlain = configDecryptionService.decrypt(plain);
                if (looksLikeJson(twicePlain)) {
                    log.warn("检测到加密配置存在历史重复加密，已按兼容逻辑解析");
                    return objectMapper.readValue(twicePlain, EncryptionConfigDto.class);
                }
                return null;
            } catch (Exception ex) {
                if (looksLikeJson(cipher)) {
                    return objectMapper.readValue(cipher, EncryptionConfigDto.class);
                }
                log.error("解析加密配置失败(密文/明文均解析失败)", ex);
                return null;
            }
        } catch (Exception e) {
            log.error("解析加密配置失败", e);
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
