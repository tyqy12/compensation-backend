package com.yiyundao.compensation.modules.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.infrastructure.dao.SettlementProviderConfigMapper;
import com.yiyundao.compensation.modules.payment.entity.SettlementProviderConfig;
import com.yiyundao.compensation.modules.payment.service.SettlementProviderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 结算渠道配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementProviderConfigServiceImpl implements SettlementProviderConfigService {

    private final SettlementProviderConfigMapper configMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SettlementProviderConfig createConfig(SettlementProviderConfig config) {
        // 检查渠道代码是否已存在
        SettlementProviderConfig existing = getConfigByCode(config.getProviderCode());
        if (existing != null) {
            throw new IllegalArgumentException("渠道代码已存在: " + config.getProviderCode());
        }

        configMapper.insert(config);
        log.info("创建结算渠道配置: {}", config.getProviderCode());
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SettlementProviderConfig updateConfig(Long id, SettlementProviderConfig config) {
        SettlementProviderConfig existing = getConfigById(id);
        if (existing == null) {
            throw new IllegalArgumentException("渠道配置不存在: " + id);
        }

        // 如果修改了渠道代码，检查新代码是否已被使用
        if (!existing.getProviderCode().equals(config.getProviderCode())) {
            SettlementProviderConfig duplicate = getConfigByCode(config.getProviderCode());
            if (duplicate != null && !duplicate.getId().equals(id)) {
                throw new IllegalArgumentException("渠道代码已存在: " + config.getProviderCode());
            }
        }

        config.setId(id);
        configMapper.updateById(config);
        log.info("更新结算渠道配置: {}", config.getProviderCode());
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(Long id) {
        SettlementProviderConfig config = getConfigById(id);
        if (config == null) {
            throw new IllegalArgumentException("渠道配置不存在: " + id);
        }

        configMapper.deleteById(id);
        log.info("删除结算渠道配置: {}", config.getProviderCode());
    }

    @Override
    public SettlementProviderConfig getConfigById(Long id) {
        return configMapper.selectById(id);
    }

    @Override
    public SettlementProviderConfig getConfigByCode(String providerCode) {
        LambdaQueryWrapper<SettlementProviderConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SettlementProviderConfig::getProviderCode, providerCode);
        return configMapper.selectOne(wrapper);
    }

    @Override
    public List<SettlementProviderConfig> getEnabledConfigs() {
        LambdaQueryWrapper<SettlementProviderConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SettlementProviderConfig::getEnabled, true)
               .orderByAsc(SettlementProviderConfig::getPriority);
        return configMapper.selectList(wrapper);
    }

    @Override
    public List<SettlementProviderConfig> getAllConfigs() {
        LambdaQueryWrapper<SettlementProviderConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SettlementProviderConfig::getPriority);
        return configMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id, Boolean enabled) {
        SettlementProviderConfig config = getConfigById(id);
        if (config == null) {
            throw new IllegalArgumentException("渠道配置不存在: " + id);
        }

        config.setEnabled(enabled);
        configMapper.updateById(config);
        log.info("切换结算渠道状态: {} -> {}", config.getProviderCode(), enabled ? "启用" : "禁用");
    }
}
