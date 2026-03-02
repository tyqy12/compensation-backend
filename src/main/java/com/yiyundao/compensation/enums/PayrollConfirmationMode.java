package com.yiyundao.compensation.enums;

import lombok.Getter;

@Getter
public enum PayrollConfirmationMode {
    INDIVIDUAL("individual", "员工本人确认"),
    GROUP("group", "负责人集体确认");

    private final String code;
    private final String name;

    PayrollConfirmationMode(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static PayrollConfirmationMode fromCode(String code) {
        if (code == null) {
            return INDIVIDUAL;
        }
        for (PayrollConfirmationMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {
                return mode;
            }
        }
        return INDIVIDUAL;
    }
}
