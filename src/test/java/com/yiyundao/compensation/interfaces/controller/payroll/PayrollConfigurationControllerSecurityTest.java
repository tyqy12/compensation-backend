package com.yiyundao.compensation.interfaces.controller.payroll;

import com.yiyundao.compensation.security.SecurityAnnotations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollConfigurationControllerSecurityTest {

    @Test
    void payCycleControllerShouldRequireFinanceOrAdmin() {
        assertTrue(PayCycleController.class.isAnnotationPresent(SecurityAnnotations.IsFinanceOrAdmin.class));
    }

    @Test
    void salaryTemplateControllerShouldRequireFinanceOrAdmin() {
        assertTrue(SalaryTemplateController.class.isAnnotationPresent(SecurityAnnotations.IsFinanceOrAdmin.class));
    }
}
