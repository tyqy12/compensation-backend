package com.yiyundao.compensation.interfaces.controller.dashboard;

import com.yiyundao.compensation.security.SecurityAnnotations;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardControllerSecurityTest {

    private static PreAuthorize preAuthorize;

    @BeforeAll
    static void setUpClass() {
        preAuthorize = DashboardController.class
                .getAnnotation(SecurityAnnotations.IsFinanceOrHrOrAdmin.class)
                .annotationType()
                .getAnnotation(PreAuthorize.class);
    }

    @Test
    void dashboardShouldRequireGlobalOperationalRoles() {
        assertThat(DashboardController.class
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrHrOrAdmin.class)).isTrue();
        assertThat(DashboardController.class
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrHrOrManagerOrAdmin.class)).isFalse();
        assertThat(preAuthorize.value()).contains("FINANCE", "HR", "ADMIN");
        assertThat(preAuthorize.value()).doesNotContain("MANAGER");
    }
}
