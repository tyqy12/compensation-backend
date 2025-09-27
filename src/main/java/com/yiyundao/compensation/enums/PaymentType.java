package com.yiyundao.compensation.enums;

import lombok.Getter;

@Getter
public enum PaymentType {
    SALARY("salary", "工资"),
    BONUS("bonus", "奖金"),
    REIMBURSEMENT("reimbursement", "报销");

    private final String code;
    private final String name;

    PaymentType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static PaymentType fromCode(String code) {
        for (PaymentType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown payment type: " + code);
    }
}