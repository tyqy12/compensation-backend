package com.yiyundao.compensation.interfaces.controller.openapi;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;
import com.yiyundao.compensation.modules.app.service.AppRateLimitService;
import com.yiyundao.compensation.modules.app.service.AppRegistryService;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.payroll.service.ExternalPayrollQueryService;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.infrastructure.dao.SysResourceMapper;
import com.yiyundao.compensation.security.ExternalApiTokenService;
import com.yiyundao.compensation.security.DatabasePermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Autowired
    private DatabasePermissionService databasePermissionService;

    @Autowired
    private SysResourceMapper sysResourceMapper;

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
    void databaseResourceMatcherShouldClassifyPayrollEndpointAsExternal() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payroll/batches");
        SysResource resource = sysResourceMapper.selectList(null).stream()
                .filter(item -> "rbac.external.payroll".equals(item.getCode()))
                .findFirst()
                .orElseThrow();

        org.assertj.core.api.Assertions.assertThat(resource.getPath()).isEqualTo("/v1/payroll/**");
        Object matchingPattern = ReflectionTestUtils.invokeMethod(databasePermissionService, "matchingPattern", resource,
                request.getRequestURI(), request.getServletPath(), request.getContextPath());
        org.assertj.core.api.Assertions.assertThat(matchingPattern == null ? null : matchingPattern.toString())
                .isEqualTo("/v1/payroll/**");
        org.assertj.core.api.Assertions.assertThat(databasePermissionService.matchesExternalResource(request))
                .isTrue();
    }

    @Test
    void databaseResourceMatcherShouldKeepContextPrefixedApiPath() {
        SysResource resource = new SysResource();
        resource.setPath("/api/employee");

        Object matchingPattern = ReflectionTestUtils.invokeMethod(databasePermissionService, "matchingPattern", resource,
                "/api/employee", "/employee", "/api");

        org.assertj.core.api.Assertions.assertThat(matchingPattern == null ? null : matchingPattern.toString())
                .isEqualTo("/api/employee");
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
