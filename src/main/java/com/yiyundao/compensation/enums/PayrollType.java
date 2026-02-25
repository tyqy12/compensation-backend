package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum PayrollType {
    FULL_TIME("full_time", "全职薪资"),
    PART_TIME("part_time", "兼职薪资"),
    BONUS("bonus", "奖金"),
    REIMBURSEMENT("reimbursement", "报销");

    @EnumValue
    private final String code;
    private final String name;

    PayrollType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static PayrollType fromCode(String code) {
        if (code == null) return null;
        for (PayrollType type : values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
