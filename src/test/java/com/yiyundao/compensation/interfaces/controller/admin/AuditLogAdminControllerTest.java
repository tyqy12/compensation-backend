package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.interfaces.dto.admin.AuditLogResponseDto;
import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import com.yiyundao.compensation.modules.audit.listener.AuditMetricsListener;
import com.yiyundao.compensation.modules.audit.listener.LoginFailureListener;
import com.yiyundao.compensation.modules.audit.service.AuditLogQueryService;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogAdminControllerTest {

    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AuditLogQueryService auditLogQueryService;
    @Mock
    private AuditMetricsListener auditMetricsListener;
    @Mock
    private LoginFailureListener loginFailureListener;

    private AuditLogAdminController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        controller = new AuditLogAdminController(
                auditLogService,
                auditLogQueryService,
                auditMetricsListener,
                loginFailureListener
        );
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void pageShouldSanitizeHistoricalSecretFieldsInResponse() throws Exception {
        Page<AuditLog> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(auditLog()));
        when(auditLogService.page(any(Page.class), any())).thenReturn(page);

        ApiResponse<Map<String, Object>> response = controller.page(
                1, 10, null, null, null, null, null, null, null, null
        );

        assertSanitizedJson(response);
    }

    @Test
    void detailShouldSanitizeHistoricalSecretFieldsInResponse() throws Exception {
        when(auditLogService.getById(1L)).thenReturn(auditLog());

        assertSanitizedJson(controller.detail(1L));
    }

    @Test
    void detailShouldReturnNotFoundWhenAuditLogMissing() {
        when(auditLogService.getById(404L)).thenReturn(null);

        ApiResponse<AuditLogResponseDto> response = controller.detail(404L);

        assertThat(response.getCode()).isEqualTo(1002);
        assertThat(response.getMessage()).contains("审计日志不存在");
    }

    @Test
    void recentOperationsShouldSanitizeHistoricalSecretFieldsInResponse() throws Exception {
        when(auditLogQueryService.findRecentByUsername("admin", 50)).thenReturn(List.of(auditLog()));

        assertSanitizedJson(controller.getUserRecentOperations("admin", 100));
    }

    @Test
    void pageShouldClampPageAndSizeBeforeQuerying() {
        Page<AuditLog> page = new Page<>(1, 200, 0);
        page.setRecords(List.of());
        when(auditLogService.page(any(Page.class), any())).thenReturn(page);

        controller.page(-1, 1000, null, null, null, null, null, null, null, null);

        ArgumentCaptor<Page<AuditLog>> captor = ArgumentCaptor.forClass(Page.class);
        verify(auditLogService).page(captor.capture(), any());
        assertThat(captor.getValue().getCurrent()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(200);
    }

    @Test
    void timeRangeShouldSanitizeHistoricalSecretFieldsInResponse() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 2, 0, 0);
        when(auditLogQueryService.findByTimeRange(eq(start), eq(end))).thenReturn(List.of(auditLog()));

        assertSanitizedJson(controller.getByTimeRange(start, end));
    }

    private void assertSanitizedJson(Object response) throws Exception {
        String json = objectMapper.writeValueAsString(response);

        assertThat(json)
                .contains("\\\"apiKey\\\":\\\"***\\\"")
                .contains("access_token=***")
                .contains("clientSecret=***")
                .doesNotContain("raw-api-key")
                .doesNotContain("raw-token")
                .doesNotContain("raw-client-secret")
                .doesNotContain("deleted")
                .doesNotContain("version");
    }

    private AuditLog auditLog() {
        AuditLog log = new AuditLog();
        log.setId(1L);
        log.setUsername("admin");
        log.setOperation("CALL_EXTERNAL_API");
        log.setMethod("POST");
        log.setRequestUrl("/admin/integration-configs");
        log.setRequestIp("127.0.0.1");
        log.setUserAgent("JUnit");
        log.setRequestParams("{\"apiKey\":\"raw-api-key\"}");
        log.setResponseResult("FAILED access_token=raw-token");
        log.setErrorMsg("upstream rejected clientSecret=raw-client-secret");
        log.setExecutionTime(12L);
        log.setBusinessType("INTEGRATION");
        log.setBusinessKey("wechat");
        log.setCreateTime(LocalDateTime.of(2026, 6, 2, 12, 0));
        log.setUpdateTime(LocalDateTime.of(2026, 6, 2, 12, 1));
        log.setDeleted(0);
        log.setVersion(7);
        return log;
    }
}
