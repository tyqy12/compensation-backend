package com.yiyundao.compensation.enums;

import lombok.Getter;

@Getter
public enum ApprovalStatus {
    PENDING("pending", "待审批"),
    APPROVED("approved", "已通过"),
    REJECTED("rejected", "已拒绝"),
    CANCELLED("cancelled", "已取消"),
    SKIPPED("skipped", "已跳过");

    private final String code;
    private final String name;

    ApprovalStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static ApprovalStatus fromCode(String code) {
        for (ApprovalStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown approval status: " + code);
    }
}