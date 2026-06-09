package com.yiyundao.compensation.interfaces.controller.payment;

import com.yiyundao.compensation.common.idempotent.Idempotent;
import com.yiyundao.compensation.security.SecurityAnnotations;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentBatchControllerSecurityTest {

    @Test
    void paymentBatchControllerShouldRequireFinanceOrAdmin() {
        assertTrue(PaymentBatchController.class.isAnnotationPresent(SecurityAnnotations.IsFinanceOrAdmin.class));
    }

    @Test
    void startTransferShouldRemainIdempotentBehindFinanceBoundary() throws NoSuchMethodException {
        Method method = PaymentBatchController.class.getMethod("start", String.class);

        assertTrue(PaymentBatchController.class.isAnnotationPresent(SecurityAnnotations.IsFinanceOrAdmin.class));
        assertTrue(method.isAnnotationPresent(Idempotent.class));
    }
}
