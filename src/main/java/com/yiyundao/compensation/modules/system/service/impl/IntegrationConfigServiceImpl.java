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
          .eq(IntegrationConfig::getEnabled, true)
          .last("limit 1");
        return getOne(qw);
    }

    @Override
    public void saveOrUpdate(String platformType, String configJson, boolean enabled) {
        LambdaQueryWrapper<IntegrationConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(IntegrationConfig::getPlatformType, platformType).last("limit 1");
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
            IntegrationConfig cfg = getRawConfig("wechat");
            if (cfg == null || cfg.getConfigJson() == null) {
                log.warn("数据库中未找到微信配置");
                return null;
            }
            String cipher = cfg.getConfigJson().trim();
            log.debug("原始配置内容: {}, 长度: {}", cipher, cipher.length());
            
            // 先尝试解密
            try {
                String plain = configDecryptionService.decrypt(cipher);
                log.debug("配置解密成功");
                return objectMapper.readValue(plain, WechatConfigDto.class);
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
            IntegrationConfig cfg = getRawConfig("dingtalk");
            if (cfg == null || cfg.getConfigJson() == null) return null;
            String cipher = cfg.getConfigJson();
            try {
                String plain = configDecryptionService.decrypt(cipher);
                return objectMapper.readValue(plain, DingTalkConfigDto.class);
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
            IntegrationConfig cfg = getRawConfig("feishu");
            if (cfg == null || cfg.getConfigJson() == null) return null;
            String cipher = cfg.getConfigJson();
            try {
                String plain = configDecryptionService.decrypt(cipher);
                return objectMapper.readValue(plain, FeishuConfigDto.class);
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
            IntegrationConfig cfg = getRawConfig("alipay");
            if (cfg == null || cfg.getConfigJson() == null) return null;
            String cipher = cfg.getConfigJson();
            try {
                String plain = configDecryptionService.decrypt(cipher);
                return objectMapper.readValue(plain, AlipayConfigDto.class);
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
    public Map<String, String> getDecryptedConfig(String platformType) {
        try {
            IntegrationConfig cfg = getRawConfig(platformType);
            if (cfg == null || cfg.getConfigJson() == null) {
                return new HashMap<>();
            }
            String cipher = cfg.getConfigJson();
            try {
                String plain = configDecryptionService.decrypt(cipher);
                return objectMapper.readValue(plain, new TypeReference<Map<String, String>>() {});
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
            IntegrationConfig cfg = getRawConfig("sms");
            if (cfg == null || cfg.getConfigJson() == null) return null;
            String cipher = cfg.getConfigJson();
            try {
                String plain = configDecryptionService.decrypt(cipher);
                return objectMapper.readValue(plain, SmsConfigDto.class);
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
            IntegrationConfig cfg = getRawConfig("email");
            if (cfg == null || cfg.getConfigJson() == null) return null;
            String cipher = cfg.getConfigJson();
            try {
                String plain = configDecryptionService.decrypt(cipher);
                return objectMapper.readValue(plain, EmailConfigDto.class);
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
            IntegrationConfig cfg = getRawConfig("encryption");
            if (cfg == null || cfg.getConfigJson() == null) return null;
            String cipher = cfg.getConfigJson();
            try {
                String plain = configDecryptionService.decrypt(cipher);
                return objectMapper.readValue(plain, EncryptionConfigDto.class);
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
}
