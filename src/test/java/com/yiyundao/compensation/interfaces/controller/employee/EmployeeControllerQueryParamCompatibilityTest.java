package com.yiyundao.compensation.interfaces.controller.employee;

import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeListItemVO;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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
}
