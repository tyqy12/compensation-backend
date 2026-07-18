package com.yiyundao.compensation.interfaces.controller.payroll;

import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchCreateRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchUpdateRequest;
import com.yiyundao.compensation.modules.payroll.service.PayrollCalculationService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import com.yiyundao.compensation.security.DatabasePermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollBatchControllerSecurityTest {

    @Test
    void createShouldRequireFinanceOrAdmin() throws NoSuchMethodException {
        Method method = PayrollBatchController.class.getMethod("create", PayrollBatchCreateRequest.class);

        assertTrue(method.isAnnotationPresent(SecurityAnnotations.IsFinanceOrAdmin.class));
        assertFalse(method.isAnnotationPresent(SecurityAnnotations.IsFinanceOrHrOrAdmin.class));
    }

    @Test
    void updateShouldRequireFinanceOrAdmin() throws NoSuchMethodException {
        Method method = PayrollBatchController.class.getMethod("update", Long.class, PayrollBatchUpdateRequest.class);

        assertTrue(method.isAnnotationPresent(SecurityAnnotations.IsFinanceOrAdmin.class));
        assertFalse(method.isAnnotationPresent(SecurityAnnotations.IsFinanceOrHrOrAdmin.class));
    }

    @Test
    void payrollAmountQueryEndpointsShouldRequireFinanceOrAdmin() throws NoSuchMethodException {
        assertFinanceOrAdminOnly(PayrollBatchController.class.getMethod(
                "dryRun", Long.class));
        assertFinanceOrAdminOnly(PayrollBatchController.class.getMethod(
                "list", int.class, int.class, String.class, String.class, String.class));
        assertFinanceOrAdminOnly(PayrollBatchController.class.getMethod(
                "get", Long.class));
    }

    @Test
    void managerReviewShouldKeepManagerAccess() throws NoSuchMethodException {
        Method method = PayrollBatchController.class.getMethod(
                "managerReview", Long.class, String.class, Long.class, String.class);

        assertTrue(method.isAnnotationPresent(SecurityAnnotations.IsFinanceOrManagerOrAdmin.class));
        assertFalse(method.isAnnotationPresent(SecurityAnnotations.IsFinanceOrAdmin.class));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managerReviewShouldForceManagerScopeToCurrentEmployee() {
        PayrollCalculationService calculationService = mock(PayrollCalculationService.class);
        SysUserService sysUserService = mock(SysUserService.class);
        DatabasePermissionService permissionService = mock(DatabasePermissionService.class);
        PayrollBatchController controller = controller(calculationService, sysUserService, permissionService);
        SysUser manager = user("manager", 10L, 100L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", "n/a"));
        when(sysUserService.findByUsername("manager")).thenReturn(manager);

        controller.managerReview(7L, "Engineering", 999L, "alice");

        verify(calculationService).managerReview(7L, "Engineering", 100L, "alice");
    }

    @Test
    void managerReviewShouldKeepRequestedScopeForFinanceOrAdmin() {
        PayrollCalculationService calculationService = mock(PayrollCalculationService.class);
        SysUserService sysUserService = mock(SysUserService.class);
        DatabasePermissionService permissionService = mock(DatabasePermissionService.class);
        PayrollBatchController controller = controller(calculationService, sysUserService, permissionService);
        SysUser finance = user("finance", 11L, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("finance", "n/a"));
        when(sysUserService.findByUsername("finance")).thenReturn(finance);
        when(permissionService.hasCurrentRequestScope(11L, "ALL")).thenReturn(true);

        controller.managerReview(8L, null, 999L, null);

        verify(calculationService).managerReview(8L, null, 999L, null);
    }

    @Test
    void managerReviewShouldRejectManagerWithoutEmployeeBinding() {
        PayrollCalculationService calculationService = mock(PayrollCalculationService.class);
        SysUserService sysUserService = mock(SysUserService.class);
        DatabasePermissionService permissionService = mock(DatabasePermissionService.class);
        PayrollBatchController controller = controller(calculationService, sysUserService, permissionService);
        SysUser manager = user("manager", 12L, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", "n/a"));
        when(sysUserService.findByUsername("manager")).thenReturn(manager);

        assertThatThrownBy(() -> controller.managerReview(9L, null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    private static void assertFinanceOrAdminOnly(Method method) {
        assertTrue(method.isAnnotationPresent(SecurityAnnotations.IsFinanceOrAdmin.class));
        assertFalse(method.isAnnotationPresent(SecurityAnnotations.IsFinanceOrManagerOrAdmin.class));
        assertFalse(method.isAnnotationPresent(SecurityAnnotations.IsFinanceOrHrOrManagerOrAdmin.class));
    }

    private static PayrollBatchController controller(PayrollCalculationService calculationService,
                                                     SysUserService sysUserService,
                                                     DatabasePermissionService permissionService) {
        PayrollBatchController controller = new PayrollBatchController(
                null,
                calculationService,
                null,
                null,
                null,
                sysUserService,
                permissionService,
                null
        );
        return controller;
    }

    private static SysUser user(String username, Long id, Long employeeId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setEmployeeId(employeeId);
        return user;
    }
}
