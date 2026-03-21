package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum PaymentBatchProcessStatus {
    CREATED("created", "已创建"),
    SUBMITTED("submitted", "已提交"),
    PROCESSING("processing", "处理中"),
    SUCCESS("success", "全部成功"),
    PARTIAL_SUCCESS("partial_success", "部分成功"),
    FAILED("failed", "全部失败"),
    CANCELLED("cancelled", "已取消");

    @EnumValue
    private final String code;
    private final String name;

    PaymentBatchProcessStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static PaymentBatchProcessStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PaymentBatchProcessStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}
