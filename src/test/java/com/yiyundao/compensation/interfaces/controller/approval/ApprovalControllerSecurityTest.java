package com.yiyundao.compensation.interfaces.controller.approval;

import com.yiyundao.compensation.security.SecurityAnnotations;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalControllerSecurityTest {

    @Test
    void workflowReadEndpointsShouldAllowAuthenticatedUserAndRelyOnObjectLevelChecks() throws NoSuchMethodException {
        assertThat(ApprovalController.class
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrManagerOrAdmin.class)).isFalse();

        assertThat(method("list", Integer.class, Integer.class, String.class, String.class, String.class,
                String.class, String.class, String.class, String.class)
                .isAnnotationPresent(SecurityAnnotations.IsAuthenticated.class)).isTrue();
        assertThat(method("getPending")
                .isAnnotationPresent(SecurityAnnotations.IsAuthenticated.class)).isTrue();
        assertThat(method("getMy", Integer.class, Integer.class, String.class)
                .isAnnotationPresent(SecurityAnnotations.IsAuthenticated.class)).isTrue();
        assertThat(method("getDetail", Long.class)
                .isAnnotationPresent(SecurityAnnotations.IsAuthenticated.class)).isTrue();
        assertThat(method("getSteps", Long.class)
                .isAnnotationPresent(SecurityAnnotations.IsAuthenticated.class)).isTrue();
    }

    @Test
    void workflowWriteEndpointsShouldAllowAuthenticatedUserAndRelyOnWorkflowOwnershipChecks()
            throws NoSuchMethodException {
        assertThat(method("approve", Long.class, ApprovalController.DecisionRequest.class)
                .isAnnotationPresent(SecurityAnnotations.IsAuthenticated.class)).isTrue();
        assertThat(method("reject", Long.class, ApprovalController.DecisionRequest.class)
                .isAnnotationPresent(SecurityAnnotations.IsAuthenticated.class)).isTrue();
        assertThat(method("cancel", Long.class, ApprovalController.DecisionRequest.class)
                .isAnnotationPresent(SecurityAnnotations.IsAuthenticated.class)).isTrue();

        assertThat(method("approve", Long.class, ApprovalController.DecisionRequest.class)
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrManagerOrAdmin.class)).isFalse();
        assertThat(method("reject", Long.class, ApprovalController.DecisionRequest.class)
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrManagerOrAdmin.class)).isFalse();
        assertThat(method("cancel", Long.class, ApprovalController.DecisionRequest.class)
                .isAnnotationPresent(SecurityAnnotations.IsFinanceOrManagerOrAdmin.class)).isFalse();
    }

    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return ApprovalController.class.getMethod(name, parameterTypes);
    }
}
