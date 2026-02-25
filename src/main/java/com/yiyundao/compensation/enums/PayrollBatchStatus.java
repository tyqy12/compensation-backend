package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum PayrollBatchStatus {
    DRAFT("draft", "草稿"),
    LOCKED("locked", "已锁定"),
    SUBMITTED("submitted", "已提交审批"),
    APPROVED("approved", "已审批"),
    REJECTED("rejected", "已拒绝"),
    PAY_PROCESSING("pay_processing", "支付处理中"),
    PAID("paid", "已支付"),
    ARCHIVED("archived", "已归档");

    @EnumValue
    private final String code;
    private final String name;

    PayrollBatchStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static PayrollBatchStatus fromCode(String code) {
        if (code == null) return null;
        for (PayrollBatchStatus status : values()) {
            if (status.getCode().equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean canEdit() {
        return this == DRAFT;
    }

    public boolean canLock() {
        return this == DRAFT;
    }

    public boolean canSubmitApproval() {
        return this == LOCKED;
    }

    public boolean canCompute() {
        return this == LOCKED || this == APPROVED;
    }
}
