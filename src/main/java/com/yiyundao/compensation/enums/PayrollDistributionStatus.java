package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum PayrollDistributionStatus {
    PLANNED("planned", "计划中"),
    SUBMITTING("submitting", "提交中"),
    PROCESSING("processing", "处理中"),
    COMPLETED("completed", "全部完成"),
    PARTIALLY_COMPLETED("partially_completed", "部分完成"),
    FAILED("failed", "发放失败"),
    CANCELLED("cancelled", "已取消"),
    SUPERSEDED("superseded", "已作废");

    @EnumValue
    private final String code;
    private final String name;

    PayrollDistributionStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static PayrollDistributionStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PayrollDistributionStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}
