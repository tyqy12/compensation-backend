package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum EmploymentType {
    FULL_TIME("full_time", "全职"),
    PART_TIME("part_time", "兼职"),
    INTERN("intern", "实习"),
    CONTRACT("contract", "合同工");

    @EnumValue
    private final String code;
    private final String name;

    EmploymentType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static EmploymentType fromCode(String code) {
        if (code == null) return null;
        for (EmploymentType type : values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
