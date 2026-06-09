package com.yiyundao.compensation.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAnnotationsTest {

    @Test
    void orgSyncPermissionShouldNotGrantWriteAccessByManagerRoleAlone() {
        PreAuthorize preAuthorize = SecurityAnnotations.HasOrgSyncPermission.class
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize.value()).contains("hasRole('ADMIN')");
        assertThat(preAuthorize.value()).contains("hasAuthority('org:sync')");
        assertThat(preAuthorize.value()).doesNotContain("MANAGER");
    }

    @Test
    void orgReadPermissionMayStillGrantReadAccessToManagers() {
        PreAuthorize preAuthorize = SecurityAnnotations.HasOrgReadPermission.class
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize.value()).contains("MANAGER");
    }

    @Test
    void orgAdminReadPermissionShouldNotGrantAccessByManagerRoleAlone() {
        PreAuthorize preAuthorize = SecurityAnnotations.HasOrgAdminReadPermission.class
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize.value()).contains("hasRole('ADMIN')");
        assertThat(preAuthorize.value()).contains("hasAuthority('org:read')");
        assertThat(preAuthorize.value()).doesNotContain("MANAGER");
    }
}
