package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 结算渠道类型枚举
 */
@Getter
public enum SettlementProviderType {

    /**
     * 支付宝
     */
    ALIPAY("alipay", "支付宝"),

    /**
     * 云账户
     */
    YUNZHANGHU("yunzhanghu", "云账户"),

    /**
     * 微信支付
     */
    WECHAT("wechat", "微信支付"),

    /**
     * 银行转账
     */
    BANK("bank", "银行转账"),

    /**
     * 其他
     */
    OTHER("other", "其他");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    SettlementProviderType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
