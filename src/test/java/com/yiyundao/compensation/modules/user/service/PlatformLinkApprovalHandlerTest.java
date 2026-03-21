package com.yiyundao.compensation.modules.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PlatformLinkApprovalHandlerTest {

    @Mock
    private UserBindingService userBindingService;

    private PlatformLinkApprovalHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PlatformLinkApprovalHandler(
                userBindingService,
                new ObjectMapper(),
                new LegacyPlatformFieldPolicy("warn", "warn")
        );
    }

    @Test
    void shouldBindUsingProposedProviderAndSubjectId() {
        ApprovalCompletedEvent event = approvedEvent(
                1101L,
                """
                        {
                          "userId": 5001,
                          "employeeId": 6001,
                          "proposedProvider": "wechat",
                          "proposedSubjectId": "wx_user_5001"
                        }
                        """
        );

        handler.onApprovalCompleted(event);

        verify(userBindingService).bindPlatform(5001L, "wechat", "wx_user_5001");
        verify(userBindingService).bindEmployee(5001L, 6001L);
    }

    @Test
    void shouldFallbackToLegacyProposedPlatformFields() {
        ApprovalCompletedEvent event = approvedEvent(
                1102L,
                """
                        {
                          "userId": 5002,
                          "employeeId": 6002,
                          "proposedPlatformType": "feishu",
                          "proposedPlatformUserId": "fs_user_5002"
                        }
                        """
        );

        handler.onApprovalCompleted(event);

        verify(userBindingService).bindPlatform(5002L, "feishu", "fs_user_5002");
        verify(userBindingService).bindEmployee(5002L, 6002L);
    }

    @Test
    void shouldRejectLegacyProposedPlatformFieldsWhenWorkflowModeIsReject() {
        PlatformLinkApprovalHandler strictHandler = new PlatformLinkApprovalHandler(
                userBindingService,
                new ObjectMapper(),
                new LegacyPlatformFieldPolicy("warn", "reject")
        );
        ApprovalCompletedEvent event = approvedEvent(
                1103L,
                """
                        {
                          "userId": 5003,
                          "employeeId": 6003,
                          "proposedPlatformType": "wechat",
                          "proposedPlatformUserId": "wx_user_5003"
                        }
                        """
        );

        assertThrows(IllegalArgumentException.class, () -> strictHandler.onApprovalCompleted(event));
        verify(userBindingService, never()).bindPlatform(5003L, "wechat", "wx_user_5003");
        verify(userBindingService, never()).bindEmployee(5003L, 6003L);
    }

    private ApprovalCompletedEvent approvedEvent(Long workflowId, String workflowData) {
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(workflowId);
        workflow.setBusinessType("PLATFORM_LINK");
        workflow.setWorkflowData(workflowData);
        return new ApprovalCompletedEvent(this, workflow, ApprovalStatus.APPROVED, 9002L);
    }
}
