package com.yiyundao.compensation.interfaces.controller.openapi;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;
import com.yiyundao.compensation.modules.app.service.AppRateLimitService;
import com.yiyundao.compensation.modules.app.service.AppRegistryService;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.payroll.service.ExternalPayrollQueryService;
import com.yiyundao.compensation.security.ExternalApiTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiScopeSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExternalApiTokenService externalApiTokenService;

    @MockBean
    private AppRegistryService appRegistryService;

    @MockBean
    private AppRateLimitService appRateLimitService;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private ExternalPayrollQueryService externalPayrollQueryService;

    private AppRegistry app;

    @BeforeEach
    void setUp() {
        app = new AppRegistry();
        app.setId(100L);
        app.setClientId("client-scope-test");
        app.setAppName("Scope Test App");
        app.setStatus("enabled");

        when(appRegistryService.getById(100L)).thenReturn(app);
        when(appRegistryService.isIpAllowed(eq(app), anyString())).thenReturn(true);
        when(appRegistryService.resolveScopes(app)).thenReturn(List.of("payroll:read", "payslip:read"));
    }

    @Test
    void payrollEndpointShouldRejectTokenWithoutPayrollReadScope() throws Exception {
        String token = tokenWithScopes("payslip:read");

        mockMvc.perform(get("/v1/payroll/batches")
                        .header("Authorization", "Bearer " + token)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isForbidden());
    }

    @Test
    void payslipEndpointShouldRejectTokenWithoutPayslipReadScope() throws Exception {
        String token = tokenWithScopes("payroll:read");

        mockMvc.perform(get("/v1/payslips")
                        .param("employeeRef", "emp:E01")
                        .param("period", "2026-01")
                        .header("Authorization", "Bearer " + token)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isForbidden());
    }

    @Test
    void payrollEndpointShouldAllowPayrollReadScope() throws Exception {
        when(externalPayrollQueryService.pagePtBatches(null, null, 1, 20))
                .thenReturn(new Page<>(1, 20, 0));
        String token = tokenWithScopes("payroll:read");

        mockMvc.perform(get("/v1/payroll/batches")
                        .header("Authorization", "Bearer " + token)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void pingEndpointShouldAllowAnyAuthenticatedExternalApp() throws Exception {
        String token = tokenWithScopes("payslip:read");

        mockMvc.perform(get("/v1/ping")
                        .header("Authorization", "Bearer " + token)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientId").value("client-scope-test"));
    }

    private String tokenWithScopes(String... scopes) {
        return externalApiTokenService.generateToken(app, List.of(scopes)).accessToken();
    }
}
