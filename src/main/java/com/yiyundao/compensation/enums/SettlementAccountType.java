package com.yiyundao.compensation.enums;

import lombok.Getter;

@Getter
public enum SettlementAccountType {
    BANK_CARD("bank_card", "银行卡"),
    ALIPAY("alipay", "支付宝"),
    WECHAT("wechat", "微信"),
    OTHER("other", "其他");

    private final String code;
    private final String name;

    SettlementAccountType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static SettlementAccountType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SettlementAccountType type : values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
