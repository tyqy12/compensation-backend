package com.yiyundao.compensation.modules.payment.support;

import com.yiyundao.compensation.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("支付记录状态转换规则测试")
class PaymentRecordStatusTransitionsTest {

    @Test
    @DisplayName("成功状态不可被迟到回调降级")
    void isAllowed_shouldRejectDowngradeFromSuccess() {
        assertFalse(PaymentRecordStatusTransitions.isAllowed(PaymentStatus.SUCCESS, PaymentStatus.FAILED));
        assertFalse(PaymentRecordStatusTransitions.isAllowed(PaymentStatus.SUCCESS, PaymentStatus.CANCELLED));
        assertFalse(PaymentRecordStatusTransitions.isAllowed(PaymentStatus.SUCCESS, PaymentStatus.PROCESSING));
    }

    @Test
    @DisplayName("失败或取消可被渠道成功状态纠正")
    void isAllowed_shouldAllowSuccessCorrectionFromFailureTerminal() {
        assertTrue(PaymentRecordStatusTransitions.isAllowed(PaymentStatus.FAILED, PaymentStatus.SUCCESS));
        assertTrue(PaymentRecordStatusTransitions.isAllowed(PaymentStatus.CANCELLED, PaymentStatus.SUCCESS));
    }

    @Test
    @DisplayName("待处理和处理中可继续向目标状态收敛")
    void isAllowed_shouldAllowProgressFromNonTerminal() {
        assertTrue(PaymentRecordStatusTransitions.isAllowed(PaymentStatus.PENDING, PaymentStatus.PROCESSING));
        assertTrue(PaymentRecordStatusTransitions.isAllowed(PaymentStatus.PROCESSING, PaymentStatus.SUCCESS));
        assertTrue(PaymentRecordStatusTransitions.isAllowed(PaymentStatus.PROCESSING, PaymentStatus.FAILED));
    }
}
