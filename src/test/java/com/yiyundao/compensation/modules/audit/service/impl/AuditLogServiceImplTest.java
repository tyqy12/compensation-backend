package com.yiyundao.compensation.modules.audit.service.impl;

import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import com.yiyundao.compensation.modules.audit.event.AuditLogSavedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLogServiceImplTest {

    @Test
    void recordShouldSanitizeSensitiveTextBeforePersistAndPublishEvent() {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        CapturingAuditLogService service = new CapturingAuditLogService(eventPublisher);

        service.record(
                "CALL_EXTERNAL_API",
                "POST",
                "/api/admin/integration-configs",
                "127.0.0.1",
                "JUnit",
                "INTEGRATION",
                "wechat",
                "admin",
                "{\"apiKey\":\"raw-api-key\",\"password\":\"raw-password\",\"des3Key\":\"raw-des3\"}",
                "FAILED access_token=response-token",
                "upstream rejected appSecret=raw-app-secret accessKeySecret=raw-access-secret",
                15L
        );

        AuditLog saved = service.saved;
        assertThat(saved).isNotNull();
        assertThat(saved.getRequestParams())
                .contains("\"apiKey\":\"***\"")
                .contains("\"password\":\"***\"")
                .contains("\"des3Key\":\"***\"")
                .doesNotContain("raw-api-key", "raw-password", "raw-des3");
        assertThat(saved.getResponseResult())
                .isEqualTo("FAILED access_token=***")
                .doesNotContain("response-token");
        assertThat(saved.getErrorMsg())
                .contains("appSecret=***")
                .contains("accessKeySecret=***")
                .doesNotContain("raw-app-secret", "raw-access-secret");

        ArgumentCaptor<ApplicationEvent> eventCaptor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(AuditLogSavedEvent.class);
        AuditLogSavedEvent event = (AuditLogSavedEvent) eventCaptor.getValue();
        assertThat(event.getAuditLog()).isSameAs(saved);
        assertThat(event.getAuditLog().getErrorMsg()).doesNotContain("raw-app-secret", "raw-access-secret");
    }

    private static final class CapturingAuditLogService extends AuditLogServiceImpl {

        private AuditLog saved;

        private CapturingAuditLogService(ApplicationEventPublisher eventPublisher) {
            super(eventPublisher);
        }

        @Override
        public boolean save(AuditLog entity) {
            this.saved = entity;
            return true;
        }
    }
}
