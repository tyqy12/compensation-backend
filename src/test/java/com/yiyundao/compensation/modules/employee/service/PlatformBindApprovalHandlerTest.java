package com.yiyundao.compensation.modules.employee.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import com.yiyundao.compensation.modules.employee.dto.BindPlatformResult;
import com.yiyundao.compensation.modules.user.service.LegacyPlatformFieldPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformBindApprovalHandlerTest {

    @Mock
    private EmployeeService employeeService;

    private PlatformBindApprovalHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PlatformBindApprovalHandler(
                employeeService,
                new ObjectMapper(),
                new LegacyPlatformFieldPolicy("warn", "warn")
        );
    }

    @Test
    void shouldUseProviderAndSubjectIdWhenPresent() {
        ApprovalCompletedEvent event = approvedEvent(
                1001L,
                """
                        {
                          "employeeId": 2001,
                          "provider": "wechat",
                          "subjectId": "wx_user_2001"
                        }
                        """
        );
        when(employeeService.executeApprovedBinding(1001L, 2001L, "wechat", "wx_user_2001"))
                .thenReturn(BindPlatformResult.success(2001L, "EMP2001", "张三", "wechat", "wx_user_2001", 3001L));

        handler.onApprovalCompleted(event);

        verify(employeeService).executeApprovedBinding(1001L, 2001L, "wechat", "wx_user_2001");
    }

    @Test
    void shouldFallbackToLegacyPlatformFieldsWhenNewFieldsMissing() {
        ApprovalCompletedEvent event = approvedEvent(
                1002L,
                """
                        {
                          "employeeId": 2002,
                          "platformType": "dingtalk",
                          "platformUserId": "ding_user_2002"
                        }
                        """
        );
        when(employeeService.executeApprovedBinding(1002L, 2002L, "dingtalk", "ding_user_2002"))
                .thenReturn(BindPlatformResult.success(2002L, "EMP2002", "李四", "dingtalk", "ding_user_2002", 3002L));

        handler.onApprovalCompleted(event);

        verify(employeeService).executeApprovedBinding(1002L, 2002L, "dingtalk", "ding_user_2002");
    }

    @Test
    void shouldRejectLegacyPlatformFieldsWhenWorkflowModeIsReject() {
        PlatformBindApprovalHandler strictHandler = new PlatformBindApprovalHandler(
                employeeService,
                new ObjectMapper(),
                new LegacyPlatformFieldPolicy("warn", "reject")
        );
        ApprovalCompletedEvent event = approvedEvent(
                1003L,
                """
                        {
                          "employeeId": 2003,
                          "platformType": "wechat",
                          "platformUserId": "wx_user_2003"
                        }
                        """
        );

        assertThrows(IllegalArgumentException.class, () -> strictHandler.onApprovalCompleted(event));
        verify(employeeService, never()).executeApprovedBinding(1003L, 2003L, "wechat", "wx_user_2003");
    }

    private ApprovalCompletedEvent approvedEvent(Long workflowId, String workflowData) {
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(workflowId);
        workflow.setBusinessType("PLATFORM_BIND");
        workflow.setWorkflowData(workflowData);
        return new ApprovalCompletedEvent(this, workflow, ApprovalStatus.APPROVED, 9001L);
    }
}
