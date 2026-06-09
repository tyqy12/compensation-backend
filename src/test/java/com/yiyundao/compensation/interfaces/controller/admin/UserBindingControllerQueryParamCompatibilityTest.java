package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.dto.UserPlatformBindingResult;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.LegacyPlatformFieldPolicy;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.modules.user.service.UserBindingService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBindingControllerQueryParamCompatibilityTest {

    @Mock
    private SysUserService sysUserService;
    @Mock
    private UserBindingService userBindingService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private ExternalIdentityService externalIdentityService;
    @Mock
    private LegacyPlatformFieldPolicy legacyPlatformFieldPolicy;
    @Mock
    private HttpServletRequest request;

    private UserBindingController controller;

    @BeforeEach
    void setUp() {
        controller = new UserBindingController(
                sysUserService,
                userBindingService,
                employeeService,
                externalIdentityService,
                legacyPlatformFieldPolicy
        );
        Page<SysUser> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        lenient().when(sysUserService.page(
                any(Page.class),
                any(LambdaQueryWrapper.class)
        )).thenReturn(page);
    }

    @Test
    void shouldIgnoreLegacyPlatformTypeWhenProviderMissing() {
        when(request.getParameter("platformType")).thenReturn("wechat");
        ApiResponse<Map<String, Object>> response = controller.userBindings(1, 10, null, null, null, null, request);

        verify(legacyPlatformFieldPolicy).handleLegacyInput(
                eq("admin_user_bindings_query"),
                eq("wechat"),
                isNull()
        );
        verify(sysUserService).page(any(Page.class), any(LambdaQueryWrapper.class));
        verify(externalIdentityService, never()).list(any(LambdaQueryWrapper.class));

        assertNotNull(response.getData());
        assertEquals(0L, response.getData().get("total"));
    }

    @Test
    void shouldClampPageAndSizeBeforeQuerying() {
        when(request.getParameter("platformType")).thenReturn(null);

        controller.userBindings(-1, 1000, null, null, null, null, request);

        ArgumentCaptor<Page<SysUser>> captor = ArgumentCaptor.forClass(Page.class);
        verify(sysUserService).page(captor.capture(), any(LambdaQueryWrapper.class));
        assertEquals(1, captor.getValue().getCurrent());
        assertEquals(200, captor.getValue().getSize());
    }

    @Test
    void bindShouldReturnWorkflowIdWhenApprovalIsStarted() {
        SysUser user = new SysUser();
        user.setId(10L);
        when(sysUserService.getById(10L)).thenReturn(user);
        when(userBindingService.bindPlatform(10L, "wechat", "wx-conflict"))
                .thenReturn(UserPlatformBindingResult.pendingApproval(7104L, "平台账号冲突，已发起审批"));

        UserBindingController.BindingForm form = new UserBindingController.BindingForm();
        form.setProvider("wechat");
        form.setSubjectId("wx-conflict");

        ApiResponse<Map<String, Object>> response = controller.bind(10L, form);

        assertEquals(ErrorCode.SUCCESS.getCode(), response.getCode());
        assertEquals("平台账号冲突，已发起审批", response.getMessage());
        assertEquals("PENDING_APPROVAL", response.getData().get("status"));
        assertEquals(7104L, response.getData().get("workflowId"));
    }
}
