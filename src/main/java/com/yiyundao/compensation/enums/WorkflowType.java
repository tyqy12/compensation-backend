package com.yiyundao.compensation.enums;

import lombok.Getter;

@Getter
public enum WorkflowType {
    BATCH("BATCH", "批量支付"),
    ADHOC("ADHOC", "临时支付"),
    OFFLINE("OFFLINE", "离线员工");

    private final String code;
    private final String name;

    WorkflowType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static WorkflowType fromCode(String code) {
        for (WorkflowType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown workflow type: " + code);
    }
}