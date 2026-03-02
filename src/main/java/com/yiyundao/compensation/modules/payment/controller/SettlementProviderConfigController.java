package com.yiyundao.compensation.modules.payment.controller;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.modules.payment.dto.SettlementProviderConfigDto;
import com.yiyundao.compensation.modules.payment.entity.SettlementProviderConfig;
import com.yiyundao.compensation.modules.payment.service.SettlementProviderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 结算渠道配置管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/settlement/provider-config")
@RequiredArgsConstructor
public class SettlementProviderConfigController {

    private final SettlementProviderConfigService configService;

    /**
     * 创建渠道配置
     */
    @PostMapping
    public ApiResponse<SettlementProviderConfig> createConfig(@Valid @RequestBody SettlementProviderConfigDto dto) {
        SettlementProviderConfig config = new SettlementProviderConfig();
        BeanUtils.copyProperties(dto, config);
        
        if (config.getPriority() == null) {
            config.setPriority(100);
        }
        if (config.getEnabled() == null) {
            config.setEnabled(true);
        }
        
        SettlementProviderConfig created = configService.createConfig(config);
        return ApiResponse.success(created);
    }

    /**
     * 更新渠道配置
     */
    @PutMapping("/{id}")
    public ApiResponse<SettlementProviderConfig> updateConfig(
            @PathVariable Long id,
            @Valid @RequestBody SettlementProviderConfigDto dto) {
        SettlementProviderConfig config = new SettlementProviderConfig();
        BeanUtils.copyProperties(dto, config);
        
        SettlementProviderConfig updated = configService.updateConfig(id, config);
        return ApiResponse.success(updated);
    }

    /**
     * 删除渠道配置
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConfig(@PathVariable Long id) {
        configService.deleteConfig(id);
        return ApiResponse.success(null);
    }

    /**
     * 获取渠道配置详情
     */
    @GetMapping("/{id}")
    public ApiResponse<SettlementProviderConfig> getConfig(@PathVariable Long id) {
        SettlementProviderConfig config = configService.getConfigById(id);
        if (config == null) {
            return ApiResponse.error("渠道配置不存在");
        }
        return ApiResponse.success(config);
    }

    /**
     * 根据代码获取渠道配置
     */
    @GetMapping("/code/{providerCode}")
    public ApiResponse<SettlementProviderConfig> getConfigByCode(@PathVariable String providerCode) {
        SettlementProviderConfig config = configService.getConfigByCode(providerCode);
        if (config == null) {
            return ApiResponse.error("渠道配置不存在");
        }
        return ApiResponse.success(config);
    }

    /**
     * 获取所有启用的渠道配置
     */
    @GetMapping("/enabled")
    public ApiResponse<List<SettlementProviderConfig>> getEnabledConfigs() {
        List<SettlementProviderConfig> configs = configService.getEnabledConfigs();
        return ApiResponse.success(configs);
    }

    /**
     * 获取所有渠道配置
     */
    @GetMapping
    public ApiResponse<List<SettlementProviderConfig>> getAllConfigs() {
        List<SettlementProviderConfig> configs = configService.getAllConfigs();
        return ApiResponse.success(configs);
    }

    /**
     * 启用/禁用渠道
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<Void> toggleStatus(
            @PathVariable Long id,
            @RequestParam Boolean enabled) {
        configService.toggleStatus(id, enabled);
        return ApiResponse.success(null);
    }
}
