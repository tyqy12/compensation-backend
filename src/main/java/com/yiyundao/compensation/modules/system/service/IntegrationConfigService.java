package com.yiyundao.compensation.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.system.entity.IntegrationConfig;
import com.yiyundao.compensation.interfaces.dto.config.*;

import java.util.Map;

public interface IntegrationConfigService extends IService<IntegrationConfig> {
    IntegrationConfig getRawConfig(String platformType);
    void saveOrUpdate(String platformType, String configJson, boolean enabled);

    WechatConfigDto getWechatConfig();
    DingTalkConfigDto getDingTalkConfig();
    FeishuConfigDto getFeishuConfig();
    AlipayConfigDto getAlipayConfig();
    SmsConfigDto getSmsConfig();
    EmailConfigDto getEmailConfig();
    EncryptionConfigDto getEncryptionConfig();

    /**
     * 获取解密后的配置Map（通用方法）
     */
    Map<String, String> getDecryptedConfig(String platformType);

    /**
     * 检查平台配置是否存在且启用
     */
    boolean isPlatformEnabled(String platformType);

    /**
     * 获取配置值
     */
    String getConfigValue(String platformType, String key);

    /**
     * 获取配置值，带默认值
     */
    String getConfigValue(String platformType, String key, String defaultValue);
}

