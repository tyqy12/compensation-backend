package com.yiyundao.compensation.modules.payment.dto;

import com.yiyundao.compensation.enums.SettlementProviderType;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 结算渠道配置 DTO
 */
@Data
public class SettlementProviderConfigDto {

    @NotBlank(message = "渠道代码不能为空")
    private String providerCode;

    @NotBlank(message = "渠道名称不能为空")
    private String providerName;

    @NotNull(message = "渠道类型不能为空")
    private SettlementProviderType providerType;

    private String apiEndpoint;

    private String apiKey;

    private String apiSecret;

    private String merchantId;

    private String notifyUrl;

    private String returnUrl;

    private Integer priority;

    private Boolean enabled;

    private String remark;
}
