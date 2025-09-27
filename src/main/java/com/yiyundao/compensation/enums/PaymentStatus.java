package com.yiyundao.compensation.enums;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    PENDING("pending", "待处理"),
    PROCESSING("processing", "处理中"),
    SUCCESS("success", "成功"),
    FAILED("failed", "失败"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String name;

    PaymentStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static PaymentStatus fromCode(String code) {
        for (PaymentStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown payment status: " + code);
    }
}