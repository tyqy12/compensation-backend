package com.yiyundao.compensation.modules.payment.service;

import com.yiyundao.compensation.modules.payment.entity.SettlementProviderConfig;
import java.util.List;

/**
 * 结算渠道配置服务
 */
public interface SettlementProviderConfigService {

    /**
     * 创建渠道配置
     */
    SettlementProviderConfig createConfig(SettlementProviderConfig config);

    /**
     * 更新渠道配置
     */
    SettlementProviderConfig updateConfig(Long id, SettlementProviderConfig config);

    /**
     * 删除渠道配置
     */
    void deleteConfig(Long id);

    /**
     * 根据ID获取配置
     */
    SettlementProviderConfig getConfigById(Long id);

    /**
     * 根据渠道代码获取配置
     */
    SettlementProviderConfig getConfigByCode(String providerCode);

    /**
     * 获取所有启用的渠道配置
     */
    List<SettlementProviderConfig> getEnabledConfigs();

    /**
     * 获取所有渠道配置
     */
    List<SettlementProviderConfig> getAllConfigs();

    /**
     * 启用/禁用渠道
     */
    void toggleStatus(Long id, Boolean enabled);
}
