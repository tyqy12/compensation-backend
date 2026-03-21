package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum PayrollCalculationStatus {
    DRAFT("draft", "草稿"),
    LOCKED("locked", "已锁定"),
    CALCULATING("calculating", "计算中"),
    CALCULATED("calculated", "计算完成"),
    FAILED("failed", "计算失败");

    @EnumValue
    private final String code;
    private final String name;

    PayrollCalculationStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static PayrollCalculationStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PayrollCalculationStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}
