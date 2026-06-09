package com.yiyundao.compensation.modules.approval.listener;

import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import com.yiyundao.compensation.modules.audit.event.AuditLogSavedEvent;
import com.yiyundao.compensation.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ApprovalStatusChangeListenerTest {

    @Test
    void failedApprovalAttemptShouldNotSendRejectedNotification() {
        NotificationService notificationService = mock(NotificationService.class);
        ApprovalStatusChangeListener listener = new ApprovalStatusChangeListener(notificationService);

        listener.onAuditLogSaved(event("审批通过", "FAILED", "无权限审批该流程"));

        verify(notificationService, never()).sendSystemAlert(anyString(), anyString(), anyString());
    }

    @Test
    void successfulRejectShouldSendRejectedNotification() {
        NotificationService notificationService = mock(NotificationService.class);
        ApprovalStatusChangeListener listener = new ApprovalStatusChangeListener(notificationService);

        listener.onAuditLogSaved(event("审批拒绝", "OK", null));

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> businessKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendSystemAlert(
                titleCaptor.capture(),
                contentCaptor.capture(),
                businessKeyCaptor.capture()
        );
        assertThat(titleCaptor.getValue()).isEqualTo("审批被拒绝");
        assertThat(contentCaptor.getValue())
                .contains("审批流程ID：1001")
                .contains("操作人：manager")
                .contains("原因：未说明原因");
        assertThat(businessKeyCaptor.getValue()).isEqualTo("APPROVAL_REJECTED_1001");
    }

    private static AuditLogSavedEvent event(String operation, String responseResult, String errorMsg) {
        AuditLog auditLog = new AuditLog();
        auditLog.setOperation(operation);
        auditLog.setBusinessKey("1001");
        auditLog.setUsername("manager");
        auditLog.setResponseResult(responseResult);
        auditLog.setErrorMsg(errorMsg);
        return new AuditLogSavedEvent(new Object(), auditLog);
    }
}
