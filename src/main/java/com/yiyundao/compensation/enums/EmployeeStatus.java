package com.yiyundao.compensation.enums;

import lombok.Getter;

@Getter
public enum EmployeeStatus {
    ACTIVE("active", "在职"),
    INACTIVE("inactive", "离职"),
    SUSPENDED("suspended", "停职");

    private final String code;
    private final String name;

    EmployeeStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static EmployeeStatus fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Unknown employee status: null");
        }
        String normalizedCode = code.trim();
        for (EmployeeStatus status : values()) {
            if (status.getCode().equalsIgnoreCase(normalizedCode)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown employee status: " + code);
    }
}
