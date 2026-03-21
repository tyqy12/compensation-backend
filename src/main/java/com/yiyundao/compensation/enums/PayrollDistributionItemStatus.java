package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum PayrollDistributionItemStatus {
    PENDING("pending", "待发放"),
    SUCCESS("success", "发放成功"),
    FAILED("failed", "发放失败"),
    RETRYING("retrying", "重试中");

    @EnumValue
    private final String code;
    private final String name;

    PayrollDistributionItemStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static PayrollDistributionItemStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PayrollDistributionItemStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}
