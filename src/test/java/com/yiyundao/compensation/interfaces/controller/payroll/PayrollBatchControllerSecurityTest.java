package com.yiyundao.compensation.interfaces.controller.payroll;

import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchCreateRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchUpdateRequest;
import com.yiyundao.compensation.security.SecurityAnnotations;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollBatchControllerSecurityTest {

    @Test
    void createShouldRequireFinanceOrAdmin() throws NoSuchMethodException {
        Method method = PayrollBatchController.class.getMethod("create", PayrollBatchCreateRequest.class);

        assertTrue(method.isAnnotationPresent(SecurityAnnotations.IsFinanceOrAdmin.class));
        assertFalse(method.isAnnotationPresent(SecurityAnnotations.IsFinanceOrHrOrAdmin.class));
    }

    @Test
    void updateShouldRequireFinanceOrAdmin() throws NoSuchMethodException {
        Method method = PayrollBatchController.class.getMethod("update", Long.class, PayrollBatchUpdateRequest.class);

        assertTrue(method.isAnnotationPresent(SecurityAnnotations.IsFinanceOrAdmin.class));
        assertFalse(method.isAnnotationPresent(SecurityAnnotations.IsFinanceOrHrOrAdmin.class));
    }
}
