package com.yiyundao.compensation.modules.payment.listener;

import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import com.yiyundao.compensation.modules.audit.event.AuditLogSavedEvent;
import com.yiyundao.compensation.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PaymentStatusChangeListenerTest {

    @Test
    void shouldBoundTrackedPaymentFailureBatches() {
        PaymentStatusChangeListener listener = new PaymentStatusChangeListener(mock(NotificationService.class));
        Map<String, Integer> paymentFailureCount = paymentFailureCount(listener);
        for (int i = 0; i < 1_000; i++) {
            paymentFailureCount.put("PAY-BATCH-" + i, 1);
        }

        listener.onAuditLogSaved(failedPaymentEvent("PAY-BATCH-NEW"));

        assertThat(paymentFailureCount(listener)).hasSizeLessThanOrEqualTo(1_000);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> paymentFailureCount(PaymentStatusChangeListener listener) {
        try {
            Field field = PaymentStatusChangeListener.class.getDeclaredField("paymentFailureCount");
            field.setAccessible(true);
            return (Map<String, Integer>) field.get(listener);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("读取支付失败计数失败", e);
        }
    }

    private AuditLogSavedEvent failedPaymentEvent(String batchNo) {
        AuditLog auditLog = new AuditLog();
        auditLog.setOperation("启动批量转账");
        auditLog.setBusinessType("PAYMENT");
        auditLog.setBusinessKey(batchNo);
        auditLog.setResponseResult("FAILED");
        auditLog.setErrorMsg("provider down");
        return new AuditLogSavedEvent(this, auditLog);
    }
}
