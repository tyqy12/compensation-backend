package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum NotificationStatus {
    PENDING("pending", "待发送"),
    SENDING("sending", "发送中"),
    SUCCESS("success", "发送成功"),
    FAILED("failed", "发送失败"),
    RETRY("retry", "重试中"),
    CANCELLED("cancelled", "已取消");

    @EnumValue
    private final String code;
    private final String name;

    NotificationStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static NotificationStatus fromCode(String code) {
        for (NotificationStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown notification status: " + code);
    }
}