package com.yiyundao.compensation.enums;

import lombok.Getter;

@Getter
public enum PlatformType {
    WECHAT("wechat", "企业微信"),
    DINGTALK("dingtalk", "钉钉"),
    FEISHU("feishu", "飞书");

    private final String code;
    private final String name;

    PlatformType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static PlatformType fromCode(String code) {
        for (PlatformType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown platform type: " + code);
    }
}