package com.yiyundao.compensation.interfaces.controller.system;

import com.yiyundao.compensation.interfaces.dto.org.OrgFetchRequest;
import com.yiyundao.compensation.security.SecurityAnnotations;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationSyncControllerSecurityTest {

    @Test
    void sensitiveOrgReadEndpointsShouldRequireAdminReadPermission() throws NoSuchMethodException {
        assertThat(method("check", String.class, HttpServletRequest.class)
                .isAnnotationPresent(SecurityAnnotations.HasOrgAdminReadPermission.class)).isTrue();
        assertThat(method("fetchTree", String.class, OrgFetchRequest.class)
                .isAnnotationPresent(SecurityAnnotations.HasOrgAdminReadPermission.class)).isTrue();
        assertThat(method("task", String.class)
                .isAnnotationPresent(SecurityAnnotations.HasOrgAdminReadPermission.class)).isTrue();
        assertThat(method("history", int.class, int.class, String.class, String.class)
                .isAnnotationPresent(SecurityAnnotations.HasOrgAdminReadPermission.class)).isTrue();
    }

    @Test
    void localDepartmentTreeShouldKeepOrdinaryOrgReadPermission() throws NoSuchMethodException {
        Method tree = DepartmentController.class.getMethod("tree", String.class);

        assertThat(tree.isAnnotationPresent(SecurityAnnotations.HasOrgReadPermission.class)).isTrue();
        assertThat(tree.isAnnotationPresent(SecurityAnnotations.HasOrgAdminReadPermission.class)).isFalse();
    }

    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return OrganizationSyncController.class.getMethod(name, parameterTypes);
    }
}
