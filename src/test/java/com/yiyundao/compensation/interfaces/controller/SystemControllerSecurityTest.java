package com.yiyundao.compensation.interfaces.controller;

import com.yiyundao.compensation.security.SecurityAnnotations;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SystemControllerSecurityTest {

    @Test
    void systemInfoShouldRequireAdmin() throws NoSuchMethodException {
        assertThat(method("info").isAnnotationPresent(SecurityAnnotations.IsAdmin.class)).isTrue();
    }

    @Test
    void systemHealthShouldNotRequireAdmin() throws NoSuchMethodException {
        assertThat(method("health").isAnnotationPresent(SecurityAnnotations.IsAdmin.class)).isFalse();
    }

    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return SystemController.class.getMethod(name, parameterTypes);
    }
}
