package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum PayrollConfirmationTimeoutStrategy {
    AUTO_CONFIRM("auto_confirm", "超时自动确认"),
    AUTO_REJECT("auto_reject", "超时自动拒绝"),
    MANUAL_REVIEW("manual_review", "超时人工审核");

    @EnumValue
    private final String code;
    private final String name;

    PayrollConfirmationTimeoutStrategy(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static PayrollConfirmationTimeoutStrategy fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PayrollConfirmationTimeoutStrategy strategy : values()) {
            if (strategy.code.equalsIgnoreCase(code)) {
                return strategy;
            }
        }
        return null;
    }
}
