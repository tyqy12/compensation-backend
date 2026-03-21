package com.yiyundao.compensation.modules.user.service;

import com.yiyundao.compensation.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyPlatformFieldPolicyTest {

    @Test
    void shouldRejectLegacyFieldsWhenModeIsReject() {
        LegacyPlatformFieldPolicy policy = new LegacyPlatformFieldPolicy("reject", "warn");
        assertThrows(BusinessException.class, () ->
                policy.handleLegacyInput("test_scene", "wechat", "wx_001"));
    }

    @Test
    void shouldAllowLegacyFieldsWhenModeIsWarn() {
        LegacyPlatformFieldPolicy policy = new LegacyPlatformFieldPolicy("warn", "warn");
        assertDoesNotThrow(() ->
                policy.handleLegacyInput("test_scene", "wechat", "wx_001"));
    }

    @Test
    void shouldAllowWhenLegacyFieldsAreEmptyEvenInRejectMode() {
        LegacyPlatformFieldPolicy policy = new LegacyPlatformFieldPolicy("reject", "warn");
        assertDoesNotThrow(() ->
                policy.handleLegacyInput("test_scene", null, null));
    }

    @Test
    void shouldRejectLegacyWorkflowFallbackWhenWorkflowModeIsReject() {
        LegacyPlatformFieldPolicy policy = new LegacyPlatformFieldPolicy("warn", "reject");
        assertThrows(IllegalArgumentException.class, () ->
                policy.handleLegacyWorkflowFallback(
                        "workflow_scene",
                        1001L,
                        "provider",
                        "platformType",
                        "wechat"
                ));
    }
}
