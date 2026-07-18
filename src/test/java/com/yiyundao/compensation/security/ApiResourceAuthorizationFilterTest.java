package com.yiyundao.compensation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yiyundao.compensation.common.response.ErrorCode;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiResourceAuthorizationFilterTest {

    private DatabasePermissionService permissionService;
    private ApiResourceAuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        permissionService = mock(DatabasePermissionService.class);
        filter = new ApiResourceAuthorizationFilter(permissionService,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void shouldAllowWhenDatabaseDecisionAllows() throws Exception {
        when(permissionService.decide(any(), any())).thenReturn(
                new DatabasePermissionService.PermissionDecision(
                        true, ErrorCode.SUCCESS, null, 1L, "resource.employee", "read", "{\"mode\":\"ALL\"}"
                ));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/employees");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturnDatabaseErrorWithoutRoleFallback() throws Exception {
        when(permissionService.decide(any(), any())).thenReturn(
                new DatabasePermissionService.PermissionDecision(
                        false, ErrorCode.FORBIDDEN, "接口未配置访问权限", null, null, null, null
                ));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/unconfigured");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("接口未配置访问权限");
    }

    @Test
    void shouldNeverBypassOptionsDecisionWithAdminAuthority() throws Exception {
        when(permissionService.decide(any(), any())).thenReturn(
                new DatabasePermissionService.PermissionDecision(
                        false, ErrorCode.FORBIDDEN, "拒绝", null, null, null, null
                ));
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/employees");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
