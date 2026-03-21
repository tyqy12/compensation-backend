package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum PayrollConfirmationRecordStatus {
    PENDING("pending", "待确认"),
    CONFIRMED("confirmed", "已确认"),
    REJECTED("rejected", "已拒绝"),
    AUTO_CONFIRMED("auto_confirmed", "自动确认");

    @EnumValue
    private final String code;
    private final String name;

    PayrollConfirmationRecordStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static PayrollConfirmationRecordStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PayrollConfirmationRecordStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}
