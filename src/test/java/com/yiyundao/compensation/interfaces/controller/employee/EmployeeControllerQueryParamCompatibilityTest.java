package com.yiyundao.compensation.interfaces.controller.employee;

import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeListItemVO;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeVO;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.user.service.LegacyPlatformFieldPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeControllerQueryParamCompatibilityTest {

    @Mock
    private EmployeeService employeeService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private LegacyPlatformFieldPolicy legacyPlatformFieldPolicy;
    @Mock
    private HttpServletRequest request;

    private EmployeeController controller;

    @BeforeEach
    void setUp() {
        controller = new EmployeeController(employeeService, auditLogService, legacyPlatformFieldPolicy);
        lenient().when(employeeService.pageEmployees(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(PageResponse.of(List.<EmployeeListItemVO>of(), 1, 10, 0));
    }

    @Test
    void shouldPreferProviderWhenProviderAndPlatformTypeBothPresent() {
        when(request.getParameter("platformType")).thenReturn("wechat");
        controller.page(1, 10, null, null, null, null,
                "feishu", null, "createTime", "desc", request);

        verify(employeeService).pageEmployees(
                eq(1),
                eq(10),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("feishu"),
                isNull(),
                eq("createTime"),
                eq("desc")
        );
    }

    @Test
    void shouldIgnoreLegacyPlatformTypeWhenProviderIsMissing() {
        when(request.getParameter("platformType")).thenReturn("wechat");
        controller.page(1, 10, null, null, null, null,
                null, null, "createTime", "desc", request);

        verify(employeeService).pageEmployees(
                eq(1),
                eq(10),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("createTime"),
                eq("desc")
        );
    }

    @Test
    void shouldRejectLegacyPlatformTypeInRejectMode() {
        when(request.getParameter("platformType")).thenReturn("wechat");
        doThrow(new BusinessException(ErrorCode.PARAM_INVALID, "平台字段(platformType/platformUserId)已下线，请使用统一绑定接口"))
                .when(legacyPlatformFieldPolicy).handleLegacyInput(
                eq("employee_controller_page_query"),
                eq("wechat"),
                isNull()
        );

        assertThrows(BusinessException.class, () ->
                controller.page(1, 10, null, null, null, null,
                        null, null, "createTime", "desc", request));
    }

    @Test
    void detailShouldResolveBusinessEmployeeIdWhenIdentifierIsNotNumeric() {
        Employee employee = new Employee();
        employee.setId(99L);
        employee.setEmployeeId("EMP001");
        EmployeeVO vo = new EmployeeVO();
        vo.setId(99L);
        vo.setEmployeeId("EMP001");

        when(employeeService.getByEmployeeId("EMP001")).thenReturn(employee);
        when(employeeService.getEmployeeVO(99L)).thenReturn(vo);

        controller.detail("EMP001");

        verify(employeeService).getByEmployeeId("EMP001");
        verify(employeeService).getEmployeeVO(99L);
    }

    @Test
    void detailShouldFallbackToBusinessEmployeeIdWhenNumericDatabaseIdIsMissing() {
        Employee employee = new Employee();
        employee.setId(1001L);
        employee.setEmployeeId("1001");
        EmployeeVO vo = new EmployeeVO();
        vo.setId(1001L);
        vo.setEmployeeId("1001");

        when(employeeService.getEmployeeVO(1001L)).thenReturn(null, vo);
        when(employeeService.getByEmployeeId("1001")).thenReturn(employee);

        controller.detail("1001");

        verify(employeeService).getByEmployeeId("1001");
        verify(employeeService, org.mockito.Mockito.times(2)).getEmployeeVO(1001L);
    }

    @Test
    void detailShouldPreferBusinessEmployeeIdWhenNumericIdentifierHasLeadingZero() {
        Employee employee = new Employee();
        employee.setId(24L);
        employee.setEmployeeId("00124");
        EmployeeVO vo = new EmployeeVO();
        vo.setId(24L);
        vo.setEmployeeId("00124");

        when(employeeService.getByEmployeeId("00124")).thenReturn(employee);
        when(employeeService.getEmployeeVO(24L)).thenReturn(vo);

        controller.detail("00124");

        verify(employeeService).getByEmployeeId("00124");
        verify(employeeService).getEmployeeVO(24L);
        verify(employeeService, never()).getEmployeeVO(124L);
    }

    @Test
    void detailShouldTreatOversizedNumericIdentifierAsBusinessEmployeeId() {
        String oversizedEmployeeId = "202601010000000000000001";
        Employee employee = new Employee();
        employee.setId(42L);
        employee.setEmployeeId(oversizedEmployeeId);
        EmployeeVO vo = new EmployeeVO();
        vo.setId(42L);
        vo.setEmployeeId(oversizedEmployeeId);

        when(employeeService.getByEmployeeId(oversizedEmployeeId)).thenReturn(employee);
        when(employeeService.getEmployeeVO(42L)).thenReturn(vo);

        controller.detail(oversizedEmployeeId);

        verify(employeeService).getByEmployeeId(oversizedEmployeeId);
        verify(employeeService).getEmployeeVO(42L);
    }

    @Test
    void detailShouldReturnNotFoundWhenNoIdentifierMatches() {
        var response = controller.detail("EMP404");

        assertThat(response.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
        assertThat(response.getMessage()).contains("员工不存在");
    }
}
