package com.yiyundao.compensation.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.infrastructure.dao.IntegrationConfigMapper;
import com.yiyundao.compensation.interfaces.dto.config.AlipayConfigDto;
import com.yiyundao.compensation.interfaces.dto.config.DingTalkConfigDto;
import com.yiyundao.compensation.interfaces.dto.config.FeishuConfigDto;
import com.yiyundao.compensation.interfaces.dto.config.WechatConfigDto;
import com.yiyundao.compensation.modules.system.entity.IntegrationConfig;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationConfigServiceImpl extends ServiceImpl<IntegrationConfigMapper, IntegrationConfig>
        implements IntegrationConfigService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final com.yiyundao.compensation.service.EncryptionService encryptionService;

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
            encrypted = encryptionService.encrypt(configJson);
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
            if (cfg == null || cfg.getConfigJson() == null) return null;
            String plain = encryptionService.decrypt(cfg.getConfigJson());
            return objectMapper.readValue(plain, WechatConfigDto.class);
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
            String plain = encryptionService.decrypt(cfg.getConfigJson());
            return objectMapper.readValue(plain, DingTalkConfigDto.class);
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
            String plain = encryptionService.decrypt(cfg.getConfigJson());
            return objectMapper.readValue(plain, FeishuConfigDto.class);
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
            String plain = encryptionService.decrypt(cfg.getConfigJson());
            return objectMapper.readValue(plain, AlipayConfigDto.class);
        } catch (Exception e) {
            log.error("解析支付宝配置失败", e);
            return null;
        }
    }
}
