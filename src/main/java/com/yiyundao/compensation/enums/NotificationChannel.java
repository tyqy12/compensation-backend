package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum NotificationChannel {
    WECHAT("wechat", "企业微信"),
    DINGTALK("dingtalk", "钉钉"),
    FEISHU("feishu", "飞书"),
    SMS("sms", "短信"),
    EMAIL("email", "邮件"),
    SYSTEM("system", "站内信");

    @EnumValue
    private final String code;
    private final String name;

    NotificationChannel(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static NotificationChannel fromCode(String code) {
        for (NotificationChannel channel : values()) {
            if (channel.getCode().equals(code)) {
                return channel;
            }
        }
        throw new IllegalArgumentException("Unknown notification channel: " + code);
    }
}