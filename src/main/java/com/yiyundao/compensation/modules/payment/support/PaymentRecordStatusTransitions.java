package com.yiyundao.compensation.modules.payment.support;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yiyundao.compensation.enums.PaymentStatus;

/**
 * 支付记录状态只允许向更确定的结果收敛，避免迟到回调覆盖已确认成功的记录。
 */
public final class PaymentRecordStatusTransitions {

    private PaymentRecordStatusTransitions() {
    }

    public static boolean isAllowed(PaymentStatus currentStatus, PaymentStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        if (currentStatus == targetStatus) {
            return true;
        }
        if (currentStatus == PaymentStatus.SUCCESS) {
            return false;
        }
        if (currentStatus == PaymentStatus.FAILED || currentStatus == PaymentStatus.CANCELLED) {
            return targetStatus == PaymentStatus.SUCCESS;
        }
        return true;
    }

    public static void applyAllowedStatusGuard(UpdateWrapper<?> wrapper, PaymentStatus targetStatus) {
        if (wrapper == null || targetStatus == null) {
            throw new IllegalArgumentException("wrapper and targetStatus must not be null");
        }
        wrapper.and(statusGuard -> {
            statusGuard.in("status", PaymentStatus.PENDING.getCode(), PaymentStatus.PROCESSING.getCode())
                    .or()
                    .eq("status", targetStatus.getCode());
            if (targetStatus == PaymentStatus.SUCCESS) {
                statusGuard.or()
                        .in("status", PaymentStatus.FAILED.getCode(), PaymentStatus.CANCELLED.getCode());
            }
        });
    }
}
