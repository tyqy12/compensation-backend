package com.yiyundao.compensation.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAnnotationsTest {

    @Test
    void legacyAnnotationNamesShouldUseTheDatabaseDecisionEvaluator() {
        PreAuthorize preAuthorize = SecurityAnnotations.HasOrgSyncPermission.class
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize.value()).isEqualTo("@databaseMethodAuthorizationEvaluator.check(authentication)");
    }

    @Test
    void allLegacyAnnotationNamesShouldUseTheSameEvaluator() {
        PreAuthorize preAuthorize = SecurityAnnotations.HasOrgReadPermission.class
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize.value()).isEqualTo("@databaseMethodAuthorizationEvaluator.check(authentication)");
    }

    @Test
    void adminReadAnnotationShouldNotContainAStaticRoleRule() {
        PreAuthorize preAuthorize = SecurityAnnotations.HasOrgAdminReadPermission.class
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize.value()).isEqualTo("@databaseMethodAuthorizationEvaluator.check(authentication)");
    }
}
