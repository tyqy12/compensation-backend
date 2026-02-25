package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum NotificationType {
    PAYMENT_SUCCESS("payment_success", "支付成功通知"),
    PAYMENT_FAILED("payment_failed", "支付失败通知"),
    BATCH_COMPLETE("batch_complete", "批次完成通知"),
    APPROVAL_PENDING("approval_pending", "待审批通知"),
    APPROVAL_APPROVED("approval_approved", "审批通过通知"),
    APPROVAL_REJECTED("approval_rejected", "审批拒绝通知"),
    SYSTEM_ALERT("system_alert", "系统告警通知");

    @EnumValue
    private final String code;
    private final String name;

    NotificationType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static NotificationType fromCode(String code) {
        for (NotificationType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown notification type: " + code);
    }
}