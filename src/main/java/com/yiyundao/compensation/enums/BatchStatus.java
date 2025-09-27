package com.yiyundao.compensation.enums;

import lombok.Getter;

@Getter
public enum BatchStatus {
    DRAFT("draft", "草稿"),
    SUBMITTED("submitted", "已提交"),
    APPROVED("approved", "已审批"),
    PROCESSING("processing", "处理中"),
    COMPLETED("completed", "已完成"),
    FAILED("failed", "失败");

    private final String code;
    private final String name;

    BatchStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static BatchStatus fromCode(String code) {
        for (BatchStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown batch status: " + code);
    }
}