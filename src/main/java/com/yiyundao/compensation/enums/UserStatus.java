package com.yiyundao.compensation.enums;

import lombok.Getter;

@Getter
public enum UserStatus {
    ACTIVE("active", "激活"),
    INACTIVE("inactive", "禁用");

    private final String code;
    private final String name;

    UserStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static UserStatus fromCode(String code) {
        for (UserStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown user status: " + code);
    }
}