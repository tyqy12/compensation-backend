package com.yiyundao.compensation.interfaces.controller.employee;

import com.yiyundao.compensation.security.SecurityAnnotations;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeControllerSecurityTest {

    @Test
    void employeeDirectoryReadEndpointsShouldRequireFinanceHrOrAdmin() throws NoSuchMethodException {
        assertThat(method("detail", String.class)
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrHrOrAdmin.class)).isTrue();
        assertThat(method("approvals", Long.class, int.class, int.class)
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrHrOrAdmin.class)).isTrue();
        assertThat(method("page", int.class, int.class, String.class, String.class, String.class, Boolean.class,
                String.class, Long.class, String.class, String.class, HttpServletRequest.class)
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrHrOrAdmin.class)).isTrue();
        assertThat(method("offlineList", Long.class)
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrHrOrAdmin.class)).isTrue();
        assertThat(method("resignedList", Long.class)
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrHrOrAdmin.class)).isTrue();
    }

    @Test
    void employeePayrollReadEndpointsShouldRequireFinanceOrAdmin() throws NoSuchMethodException {
        assertThat(method("payslips", Long.class, int.class, int.class)
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrAdmin.class)).isTrue();
        assertThat(method("payments", Long.class, int.class, int.class)
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrAdmin.class)).isTrue();
    }

    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return EmployeeController.class.getMethod(name, parameterTypes);
    }
}
