package com.yiyundao.compensation.modules.payment.dto;

import com.yiyundao.compensation.enums.SettlementProviderType;
import com.yiyundao.compensation.modules.payment.entity.SettlementProviderConfig;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 结算渠道配置响应。不要在外部响应中暴露 apiKey/apiSecret。
 */
@Data
@Builder
public class SettlementProviderConfigResponse {

    private Long id;
    private String providerCode;
    private String providerName;
    private SettlementProviderType providerType;
    private String apiEndpoint;
    private String merchantId;
    private String notifyUrl;
    private String returnUrl;
    private Integer priority;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static SettlementProviderConfigResponse from(SettlementProviderConfig config) {
        if (config == null) {
            return null;
        }
        return SettlementProviderConfigResponse.builder()
                .id(config.getId())
                .providerCode(config.getProviderCode())
                .providerName(config.getProviderName())
                .providerType(config.getProviderType())
                .apiEndpoint(config.getApiEndpoint())
                .merchantId(config.getMerchantId())
                .notifyUrl(config.getNotifyUrl())
                .returnUrl(config.getReturnUrl())
                .priority(config.getPriority())
                .enabled(config.getEnabled())
                .remark(config.getRemark())
                .createTime(config.getCreateTime())
                .updateTime(config.getUpdateTime())
                .build();
    }
}
