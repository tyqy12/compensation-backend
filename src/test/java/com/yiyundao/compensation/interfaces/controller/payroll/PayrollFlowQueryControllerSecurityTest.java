package com.yiyundao.compensation.interfaces.controller.payroll;

import com.yiyundao.compensation.security.SecurityAnnotations;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollFlowQueryControllerSecurityTest {

    @Test
    void distributionQueriesShouldRequireFinanceOrAdmin() {
        assertThat(PayrollDistributionController.class
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrAdmin.class)).isTrue();
        assertThat(PayrollDistributionController.class
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrHrOrManagerOrAdmin.class)).isFalse();
    }

    @Test
    void reconciliationQueriesShouldRequireFinanceOrAdmin() {
        assertThat(PayrollReconciliationController.class
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrAdmin.class)).isTrue();
        assertThat(PayrollReconciliationController.class
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrHrOrManagerOrAdmin.class)).isFalse();
    }

    @Test
    void payslipQueriesShouldNotGrantManagerRoleAccess() throws NoSuchMethodException {
        assertThat(PayslipController.class.getMethod("list", int.class, int.class, Long.class)
                .isAnnotationPresent(SecurityAnnotations.IsEmployeeOrFinanceOrAdmin.class)).isTrue();
        assertThat(PayslipController.class.getMethod("detail", Long.class)
                .isAnnotationPresent(SecurityAnnotations.IsEmployeeOrFinanceOrAdmin.class)).isTrue();
        assertThat(PayslipController.class.getMethod("download", Long.class)
                .isAnnotationPresent(SecurityAnnotations.IsEmployeeOrFinanceOrAdmin.class)).isTrue();
    }
}
