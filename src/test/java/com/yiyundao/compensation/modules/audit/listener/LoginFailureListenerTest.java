package com.yiyundao.compensation.modules.audit.listener;

import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import com.yiyundao.compensation.modules.audit.event.AuditLogSavedEvent;
import com.yiyundao.compensation.service.NotificationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LoginFailureListenerTest {

    @Test
    void shouldBoundTrackedFailureUsers() {
        LoginFailureListener listener = new LoginFailureListener(mock(NotificationService.class));

        for (int i = 0; i < 1_050; i++) {
            listener.onAuditLogSaved(failedLoginEvent("user-" + i));
        }

        assertThat(listener.getLoginFailureCount()).hasSizeLessThanOrEqualTo(1_000);
    }

    private AuditLogSavedEvent failedLoginEvent(String username) {
        AuditLog auditLog = new AuditLog();
        auditLog.setOperation("用户登录");
        auditLog.setUsername(username);
        auditLog.setResponseResult("FAILED");
        auditLog.setErrorMsg("bad credentials");
        return new AuditLogSavedEvent(this, auditLog);
    }
}
