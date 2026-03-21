package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum PayrollConfirmationSheetStatus {
    PENDING("pending", "待开始"),
    CONFIRMING("confirming", "确认中"),
    CONFIRMED("confirmed", "确认完成"),
    SKIPPED("skipped", "已跳过"),
    TIMEOUT("timeout", "已超时"),
    REJECTED("rejected", "已拒绝"),
    SUPERSEDED("superseded", "已作废");

    @EnumValue
    private final String code;
    private final String name;

    PayrollConfirmationSheetStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static PayrollConfirmationSheetStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PayrollConfirmationSheetStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}
