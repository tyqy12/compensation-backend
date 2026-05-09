package com.yiyundao.compensation.interfaces.controller;

import com.yiyundao.compensation.controller.FileController;
import com.yiyundao.compensation.controller.TaskScheduleController;
import com.yiyundao.compensation.modules.payment.controller.SettlementProviderConfigController;
import com.yiyundao.compensation.modules.payment.controller.SettlementProviderRoutingController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ControllerRouteMappingTest {

    @Test
    void versionedControllersShouldNotRepeatGlobalApiContextPath() {
        assertMapping(FileController.class, "/v1/files");
        assertMapping(TaskScheduleController.class, "/v1/admin/tasks");
    }

    @Test
    void settlementControllersShouldNotRepeatGlobalApiContextPath() {
        assertMapping(SettlementProviderConfigController.class, "/settlement/provider-config");
        assertMapping(SettlementProviderRoutingController.class, "/settlement/routing");
    }

    private void assertMapping(Class<?> controllerClass, String expectedPath) {
        RequestMapping mapping = controllerClass.getAnnotation(RequestMapping.class);

        assertArrayEquals(new String[]{expectedPath}, mapping.value());
    }
}
