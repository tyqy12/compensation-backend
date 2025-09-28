package com.yiyundao.compensation.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.system.entity.IntegrationConfig;
import com.yiyundao.compensation.interfaces.dto.config.AlipayConfigDto;
import com.yiyundao.compensation.interfaces.dto.config.DingTalkConfigDto;
import com.yiyundao.compensation.interfaces.dto.config.FeishuConfigDto;
import com.yiyundao.compensation.interfaces.dto.config.WechatConfigDto;

public interface IntegrationConfigService extends IService<IntegrationConfig> {
    IntegrationConfig getRawConfig(String platformType);
    void saveOrUpdate(String platformType, String configJson, boolean enabled);

    WechatConfigDto getWechatConfig();
    DingTalkConfigDto getDingTalkConfig();
    FeishuConfigDto getFeishuConfig();
    AlipayConfigDto getAlipayConfig();
}

