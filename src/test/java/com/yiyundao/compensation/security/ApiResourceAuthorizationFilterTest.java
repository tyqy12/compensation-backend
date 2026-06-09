package com.yiyundao.compensation.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.UserStatus;
import com.yiyundao.compensation.infrastructure.dao.SysResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.service.ResourceService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.rbac.service.impl.ResourceChangeListener.ResourceChangeEvent;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiResourceAuthorizationFilterTest {

    @Test
    void matchedResourceShouldReturnUnauthorizedWhenAuthenticationMissing() throws Exception {
        SysResource resource = new SysResource();
        resource.setId(9L);
        resource.setType("API");
        resource.setCode("api.employee.self");
        resource.setPath("/api/employee/self");
        resource.setStatus("enabled");
        resource.setPropsJson("{\"method\":\"GET\"}");

        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of(resource));

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/employee/self");
        request.setServletPath("/employee/self");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "未登录");
        verify(userMapper, never()).selectOne(any());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void resourceRolesShouldDenyUserWithoutRequiredRoleBeforeResourceGrant() throws Exception {
        SysResource resource = new SysResource();
        resource.setId(10L);
        resource.setType("API");
        resource.setCode("api.employee.decrypt-id-card");
        resource.setPath("/api/employee/{id}/id-card");
        resource.setStatus("enabled");
        resource.setPropsJson("{\"method\":\"GET\",\"roles\":[\"ADMIN\"]}");

        SysUser user = new SysUser();
        user.setId(100L);
        user.setUsername("hr-user");
        user.setStatus(UserStatus.ACTIVE);

        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of(resource));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleService.hasRole(100L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(userRoleService.hasAnyRole(100L, "ADMIN")).thenReturn(false);

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("hr-user", "N/A", List.of())
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/employee/1/id-card");
            request.setServletPath("/employee/1/id-card");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN, "角色不匹配");
            verify(resourceService, never()).getUserResources(100L);
            verify(chain, never()).doFilter(any(), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void matchedResourceShouldRejectInactiveUserEvenWhenAuthenticationExists() throws Exception {
        SysResource resource = new SysResource();
        resource.setId(13L);
        resource.setType("API");
        resource.setCode("api.dashboard.metrics");
        resource.setPath("/api/dashboard/metrics");
        resource.setStatus("enabled");
        resource.setPropsJson("{\"method\":\"GET\"}");

        SysUser user = new SysUser();
        user.setId(103L);
        user.setUsername("disabled-user");
        user.setStatus(UserStatus.INACTIVE);

        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of(resource));
        when(userMapper.selectOne(any())).thenReturn(user);

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("disabled-user", "N/A", List.of())
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/metrics");
            request.setServletPath("/dashboard/metrics");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "账号已禁用");
            verify(userRoleService, never()).hasRole(any(), any());
            verify(resourceService, never()).getUserResources(any());
            verify(chain, never()).doFilter(any(), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void resourceRolesShouldAllowMatchingRoleAndContinueToResourceGrant() throws Exception {
        SysResource resource = new SysResource();
        resource.setId(11L);
        resource.setPath("/api/payroll/batches/*");
        resource.setPropsJson("{\"method\":\"GET\",\"roles\":[\"FINANCE\"]}");

        SysUser user = new SysUser();
        user.setId(101L);
        user.setUsername("finance-user");
        user.setStatus(UserStatus.ACTIVE);

        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of(resource));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleService.hasRole(101L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(userRoleService.hasAnyRole(101L, "FINANCE")).thenReturn(true);
        when(resourceService.getUserResources(101L)).thenReturn(List.of(resource));
        when(resourceService.getUserActions(101L)).thenReturn(Map.of(resource.getId(), List.of("read")));

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance-user", "N/A", List.of())
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payroll/batches/1");
            request.setServletPath("/payroll/batches/1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void resourceActionShouldReturnForbiddenWhenUserLacksRequiredAction() throws Exception {
        SysResource resource = new SysResource();
        resource.setId(12L);
        resource.setPath("/api/payroll/batches/*");
        resource.setPropsJson("{\"method\":\"POST\",\"roles\":[\"FINANCE\"]}");

        SysUser user = new SysUser();
        user.setId(102L);
        user.setUsername("finance-user");
        user.setStatus(UserStatus.ACTIVE);

        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of(resource));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleService.hasRole(102L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(userRoleService.hasAnyRole(102L, "FINANCE")).thenReturn(true);
        when(resourceService.getUserResources(102L)).thenReturn(List.of(resource));
        when(resourceService.getUserActions(102L)).thenReturn(Map.of(resource.getId(), List.of("read")));

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance-user", "N/A", List.of())
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payroll/batches/1");
            request.setServletPath("/payroll/batches/1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN, "需要 write 权限");
            verify(chain, never()).doFilter(any(), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void unmatchedProtectedBusinessApiShouldReturnForbidden() throws Exception {
        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of());

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance-user", "N/A", List.of())
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payroll/import/commit");
            request.setServletPath("/payroll/import/commit");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN, "接口未配置访问权限");
            verify(chain, never()).doFilter(any(), any());
            verify(userMapper, never()).selectOne(any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void unmatchedVersionedAdminApiShouldReturnForbidden() throws Exception {
        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of());

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance-user", "N/A", List.of())
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/tasks/10/trigger");
            request.setServletPath("/v1/admin/tasks/10/trigger");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN, "接口未配置访问权限");
            verify(chain, never()).doFilter(any(), any());
            verify(userMapper, never()).selectOne(any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void unmatchedProtectedBusinessApiShouldPassThroughForAdmin() throws Exception {
        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of());

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin",
                        "N/A",
                        List.of(new SimpleGrantedAuthority(SecurityConstants.ROLE_ADMIN))
                )
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payroll/import/salary-items");
            request.setServletPath("/payroll/import/salary-items");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
            verify(userMapper, never()).selectOne(any());
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void unmatchedAuthSelfApiShouldPassThrough() throws Exception {
        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of());

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "N/A", List.of())
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
            request.setServletPath("/auth/me");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
            verify(userMapper, never()).selectOne(any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void payrollConfirmationWildcardResourceShouldMatchEmployeeConfirmRequest() throws Exception {
        SysResource resource = new SysResource();
        resource.setId(77L);
        resource.setType("API");
        resource.setCode("api.payroll.confirmations.confirm");
        resource.setPath("/api/payroll/confirmations/payslips/*/confirm");
        resource.setStatus("enabled");
        resource.setPropsJson("{\"method\":\"POST\"}");

        SysUser user = new SysUser();
        user.setId(301L);
        user.setUsername("employee-user");
        user.setStatus(UserStatus.ACTIVE);

        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of(resource));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleService.hasRole(301L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(resourceService.getUserResources(301L)).thenReturn(List.of(resource));
        when(resourceService.getUserActions(301L)).thenReturn(Map.of(resource.getId(), List.of("write")));

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("employee-user", "N/A", List.of())
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "POST",
                    "/api/payroll/confirmations/payslips/123/confirm"
            );
            request.setServletPath("/payroll/confirmations/payslips/123/confirm");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void moreSpecificPayrollResourceShouldWinOverWildcardResource() throws Exception {
        SysResource detail = new SysResource();
        detail.setId(81L);
        detail.setType("API");
        detail.setCode("api.payroll.cycles.detail");
        detail.setPath("/api/payroll/cycles/*");
        detail.setStatus("enabled");
        detail.setPropsJson("{\"method\":\"GET\"}");

        SysResource open = new SysResource();
        open.setId(82L);
        open.setType("API");
        open.setCode("api.payroll.cycles.open");
        open.setPath("/api/payroll/cycles/open");
        open.setStatus("enabled");
        open.setPropsJson("{\"method\":\"GET\"}");

        SysUser user = new SysUser();
        user.setId(302L);
        user.setUsername("finance-user");
        user.setStatus(UserStatus.ACTIVE);

        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of(detail, open));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleService.hasRole(302L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(resourceService.getUserResources(302L)).thenReturn(List.of(open));
        when(resourceService.getUserActions(302L)).thenReturn(Map.of(open.getId(), List.of("read")));

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance-user", "N/A", List.of())
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payroll/cycles/open");
            request.setServletPath("/payroll/cycles/open");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void payrollImportItemResourcesShouldMatchByHttpMethodOnSamePath() throws Exception {
        SysResource update = new SysResource();
        update.setId(91L);
        update.setType("API");
        update.setCode("api.payroll.import.item.update");
        update.setPath("/api/payroll/import/batches/*/items/*");
        update.setStatus("enabled");
        update.setPropsJson("{\"method\":\"PUT\"}");

        SysResource delete = new SysResource();
        delete.setId(92L);
        delete.setType("API");
        delete.setCode("api.payroll.import.item.delete");
        delete.setPath("/api/payroll/import/batches/*/items/*");
        delete.setStatus("enabled");
        delete.setPropsJson("{\"method\":\"DELETE\"}");

        SysUser user = new SysUser();
        user.setId(303L);
        user.setUsername("finance-user");
        user.setStatus(UserStatus.ACTIVE);

        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of(update, delete));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleService.hasRole(303L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(resourceService.getUserResources(303L)).thenReturn(List.of(update));
        when(resourceService.getUserActions(303L)).thenReturn(Map.of(update.getId(), List.of("write")));

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance-user", "N/A", List.of())
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "PUT",
                    "/api/payroll/import/batches/1001/items/88"
            );
            request.setServletPath("/payroll/import/batches/1001/items/88");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void duplicateSamePathResourcesShouldAllowAnyGrantedTopCandidate() throws Exception {
        SysResource legacy = new SysResource();
        legacy.setId(101L);
        legacy.setType("API");
        legacy.setCode("api.payroll.batches.submit");
        legacy.setPath("/api/payroll/batches/*/submit-approval");
        legacy.setStatus("enabled");
        legacy.setPropsJson("{\"method\":\"POST\"}");

        SysResource current = new SysResource();
        current.setId(102L);
        current.setType("API");
        current.setCode("api.payroll.batches.submit-approval");
        current.setPath("/api/payroll/batches/*/submit-approval");
        current.setStatus("enabled");
        current.setPropsJson("{\"method\":\"POST\"}");

        SysUser user = new SysUser();
        user.setId(304L);
        user.setUsername("finance-user");
        user.setStatus(UserStatus.ACTIVE);

        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of(legacy, current));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleService.hasRole(304L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(resourceService.getUserResources(304L)).thenReturn(List.of(current));
        when(resourceService.getUserActions(304L)).thenReturn(Map.of(current.getId(), List.of("write")));

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance-user", "N/A", List.of())
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "POST",
                    "/api/payroll/batches/1001/submit-approval"
            );
            request.setServletPath("/payroll/batches/1001/submit-approval");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void topCandidateGroupShouldNotFallBackToLessSpecificGrantedResource() throws Exception {
        SysResource wildcard = new SysResource();
        wildcard.setId(111L);
        wildcard.setType("API");
        wildcard.setCode("api.payroll.cycles.detail");
        wildcard.setPath("/api/payroll/cycles/*");
        wildcard.setStatus("enabled");
        wildcard.setPropsJson("{\"method\":\"GET\"}");

        SysResource open = new SysResource();
        open.setId(112L);
        open.setType("API");
        open.setCode("api.payroll.cycles.open");
        open.setPath("/api/payroll/cycles/open");
        open.setStatus("enabled");
        open.setPropsJson("{\"method\":\"GET\"}");

        SysUser user = new SysUser();
        user.setId(305L);
        user.setUsername("hr-user");
        user.setStatus(UserStatus.ACTIVE);

        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of(wildcard, open));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleService.hasRole(305L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(resourceService.getUserResources(305L)).thenReturn(List.of(wildcard));

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("hr-user", "N/A", List.of())
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payroll/cycles/open");
            request.setServletPath("/payroll/cycles/open");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN, "无权限访问该接口");
            verify(chain, never()).doFilter(any(), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void nullUserActionsShouldBehaveLikeNoActionRestrictions() throws Exception {
        SysResource resource = new SysResource();
        resource.setId(121L);
        resource.setType("API");
        resource.setCode("api.dashboard.metrics");
        resource.setPath("/api/dashboard/metrics");
        resource.setStatus("enabled");
        resource.setPropsJson("{\"method\":\"GET\"}");

        SysUser user = new SysUser();
        user.setId(306L);
        user.setUsername("dashboard-user");
        user.setStatus(UserStatus.ACTIVE);

        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of(resource));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleService.hasRole(306L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(resourceService.getUserResources(306L)).thenReturn(List.of(resource));
        when(resourceService.getUserActions(306L)).thenReturn(null);

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("dashboard-user", "N/A", List.of())
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/metrics");
            request.setServletPath("/dashboard/metrics");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void nullUserResourcesShouldReturnForbiddenWithoutThrowing() throws Exception {
        SysResource resource = new SysResource();
        resource.setId(122L);
        resource.setType("API");
        resource.setCode("api.dashboard.metrics");
        resource.setPath("/api/dashboard/metrics");
        resource.setStatus("enabled");
        resource.setPropsJson("{\"method\":\"GET\"}");

        SysUser user = new SysUser();
        user.setId(307L);
        user.setUsername("dashboard-user");
        user.setStatus(UserStatus.ACTIVE);

        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of(resource));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleService.hasRole(307L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(resourceService.getUserResources(307L)).thenReturn(null);

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("dashboard-user", "N/A", List.of())
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/metrics");
            request.setServletPath("/dashboard/metrics");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN, "无权限访问该接口");
            verify(chain, never()).doFilter(any(), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void resourceActionShouldMatchCaseInsensitively() throws Exception {
        SysResource resource = new SysResource();
        resource.setId(123L);
        resource.setType("API");
        resource.setCode("api.dashboard.metrics");
        resource.setPath("/api/dashboard/metrics");
        resource.setStatus("enabled");
        resource.setPropsJson("{\"method\":\"GET\"}");

        SysUser user = new SysUser();
        user.setId(309L);
        user.setUsername("dashboard-user");
        user.setStatus(UserStatus.ACTIVE);

        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of(resource));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleService.hasRole(309L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(resourceService.getUserResources(309L)).thenReturn(List.of(resource));
        when(resourceService.getUserActions(309L)).thenReturn(Map.of(resource.getId(), List.of("READ")));

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("dashboard-user", "N/A", List.of())
        );
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/metrics");
            request.setServletPath("/dashboard/metrics");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void resourceChangeEventShouldEvictApiResourceCatalogCache() throws Exception {
        SysResource oldResource = new SysResource();
        oldResource.setId(131L);
        oldResource.setType("API");
        oldResource.setCode("api.payroll.old");
        oldResource.setPath("/api/payroll/cache-test");
        oldResource.setStatus("enabled");
        oldResource.setPropsJson("{\"method\":\"GET\"}");

        SysResource newResource = new SysResource();
        newResource.setId(132L);
        newResource.setType("API");
        newResource.setCode("api.payroll.new");
        newResource.setPath("/api/payroll/cache-test");
        newResource.setStatus("enabled");
        newResource.setPropsJson("{\"method\":\"GET\"}");

        SysUser user = new SysUser();
        user.setId(308L);
        user.setUsername("finance-user");
        user.setStatus(UserStatus.ACTIVE);

        SysResourceMapper resourceMapper = mock(SysResourceMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        ResourceService resourceService = mock(ResourceService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        FilterChain chain = mock(FilterChain.class);

        when(resourceMapper.selectList(any())).thenReturn(List.of(oldResource), List.of(newResource));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleService.hasRole(308L, SecurityConstants.ROLE_ADMIN)).thenReturn(false);
        when(resourceService.getUserResources(308L)).thenReturn(List.of(oldResource), List.of(newResource));
        when(resourceService.getUserActions(308L)).thenReturn(
                Map.of(oldResource.getId(), List.of("read")),
                Map.of(newResource.getId(), List.of("read"))
        );

        ApiResourceAuthorizationFilter filter = new ApiResourceAuthorizationFilter(
                resourceMapper, userMapper, resourceService, userRoleService, objectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance-user", "N/A", List.of())
        );
        try {
            MockHttpServletRequest firstRequest = new MockHttpServletRequest("GET", "/api/payroll/cache-test");
            firstRequest.setServletPath("/payroll/cache-test");
            MockHttpServletResponse firstResponse = new MockHttpServletResponse();

            filter.doFilter(firstRequest, firstResponse, chain);

            filter.handleResourceChange(new ResourceChangeEvent(ResourceChangeEvent.ChangeType.UPDATE, oldResource.getId()));

            MockHttpServletRequest secondRequest = new MockHttpServletRequest("GET", "/api/payroll/cache-test");
            secondRequest.setServletPath("/payroll/cache-test");
            MockHttpServletResponse secondResponse = new MockHttpServletResponse();

            filter.doFilter(secondRequest, secondResponse, chain);

            verify(resourceMapper, times(2)).selectList(any());
            verify(chain, times(2)).doFilter(any(), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private void assertErrorResponse(MockHttpServletResponse response,
                                     int httpStatus,
        ErrorCode errorCode,
        String messagePart) throws Exception {
        assertThat(response.getStatus()).isEqualTo(httpStatus);
        JsonNode body = objectMapper().readTree(response.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(errorCode.getCode());
        assertThat(body.get("message").asText()).contains(messagePart);
    }
}
