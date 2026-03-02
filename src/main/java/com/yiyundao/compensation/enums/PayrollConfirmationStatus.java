package com.yiyundao.compensation.enums;

import lombok.Getter;

@Getter
public enum PayrollConfirmationStatus {
    PENDING("pending", "待确认"),
    CONFIRMED("confirmed", "已确认"),
    OBJECTED("objected", "已发起异议"),
    OBJECTED_APPROVED("objected_approved", "异议通过"),
    OBJECTED_REJECTED("objected_rejected", "异议驳回");

    private final String code;
    private final String name;

    PayrollConfirmationStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static PayrollConfirmationStatus fromCode(String code) {
        if (code == null) {
            return PENDING;
        }
        for (PayrollConfirmationStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return PENDING;
    }

    public boolean isFinalForPayment() {
        return this == CONFIRMED || this == OBJECTED_APPROVED;
    }
}
