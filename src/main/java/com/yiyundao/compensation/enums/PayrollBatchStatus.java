package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum PayrollBatchStatus {
    DRAFT("draft", "草稿"),
    LOCKED("locked", "已锁定"),
    CONFIRMING("confirming", "待员工确认"),
    DISPUTE_PROCESSING("dispute_processing", "异议处理中"),
    CONFIRMED("confirmed", "确认完成"),
    SUBMITTED("submitted", "已提交审批"),
    APPROVED("approved", "已审批"),
    REJECTED("rejected", "已拒绝"),
    PAY_PROCESSING("pay_processing", "支付处理中"),
    PAY_FAILED("pay_failed", "支付失败"),
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
        return this == CONFIRMED;
    }

    public boolean canCompute() {
        return this == LOCKED
                || this == CONFIRMING
                || this == DISPUTE_PROCESSING
                || this == REJECTED;
    }

    /**
     * 判断批次是否允许从当前状态进入目标状态。
     * <p>
     * 同状态转移用于幂等调用；支付失败重新发起支付属于显式的恢复路径。
     * </p>
     */
    public boolean canTransitionTo(PayrollBatchStatus target) {
        if (target == null || target == this) {
            return target == this;
        }
        return switch (this) {
            case DRAFT -> target == LOCKED;
            case LOCKED -> target == CONFIRMING || target == CONFIRMED || target == SUBMITTED;
            case CONFIRMING -> target == DISPUTE_PROCESSING || target == CONFIRMED;
            case DISPUTE_PROCESSING -> target == CONFIRMING || target == CONFIRMED;
            case CONFIRMED -> target == SUBMITTED || target == CONFIRMING;
            case SUBMITTED -> target == APPROVED || target == REJECTED || target == CONFIRMED;
            case APPROVED -> target == PAY_PROCESSING || target == PAY_FAILED;
            case REJECTED -> target == LOCKED || target == CONFIRMING;
            case PAY_PROCESSING -> target == PAY_FAILED || target == PAID;
            case PAY_FAILED -> target == PAY_PROCESSING;
            case PAID -> target == ARCHIVED;
            case ARCHIVED -> false;
        };
    }
}
