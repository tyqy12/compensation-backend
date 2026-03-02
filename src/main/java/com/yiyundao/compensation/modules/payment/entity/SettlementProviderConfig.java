package com.yiyundao.compensation.modules.payment.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import com.yiyundao.compensation.enums.SettlementProviderType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 结算渠道配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("settlement_provider_config")
public class SettlementProviderConfig extends BaseEntity {

    /**
     * 渠道编码：alipay/yunzhanghu/wechat
     */
    @TableField("provider_code")
    private String providerCode;

    /**
     * 渠道名称
     */
    @TableField("provider_name")
    private String providerName;

    /**
     * 渠道类型
     */
    @TableField("provider_type")
    private SettlementProviderType providerType;

    /**
     * API端点
     */
    @TableField("api_endpoint")
    private String apiEndpoint;

    /**
     * API密钥
     */
    @TableField("api_key")
    private String apiKey;

    /**
     * API密钥
     */
    @TableField("api_secret")
    private String apiSecret;

    /**
     * 商户ID
     */
    @TableField("merchant_id")
    private String merchantId;

    /**
     * 回调URL
     */
    @TableField("notify_url")
    private String notifyUrl;

    /**
     * 返回URL
     */
    @TableField("return_url")
    private String returnUrl;

    /**
     * 优先级（数字越小优先级越高）
     */
    @TableField("priority")
    private Integer priority;

    /**
     * 是否启用：1-启用，0-禁用
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;
}
