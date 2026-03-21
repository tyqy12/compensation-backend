package com.yiyundao.compensation.modules.employee.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import com.yiyundao.compensation.modules.employee.dto.EmployeeProfileChangePayload;
import com.yiyundao.compensation.service.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeProfileChangeApprovalHandlerTest {

    @Mock
    private EmployeeService employeeService;

    @Mock
    private EncryptionService encryptionService;

    private EmployeeProfileChangeApprovalHandler handler;

    @BeforeEach
    void setUp() {
        handler = new EmployeeProfileChangeApprovalHandler(employeeService, new ObjectMapper(), encryptionService);
    }

    @Test
    void shouldApplyApprovedProfileChangeWhenBusinessTypeMatches() {
        ApprovalCompletedEvent event = approvedEvent(
                1001L,
                """
                        {
                          "employeeId": 2001,
                          "changePayloadCipher": "cipher_text"
                        }
                        """
        );
        when(encryptionService.decrypt("cipher_text")).thenReturn(
                """
                        {
                          "name":"张三",
                          "idCard":"110101199001011234"
                        }
                        """
        );

        handler.onApprovalCompleted(event);

        verify(employeeService).applyApprovedProfileChange(eq(1001L), eq(2001L), any(EmployeeProfileChangePayload.class));
    }

    @Test
    void shouldIgnoreNonApprovedEvent() {
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(1002L);
        workflow.setBusinessType(EmployeeService.BUSINESS_TYPE_EMPLOYEE_PROFILE_CHANGE);
        workflow.setWorkflowData("{\"employeeId\":2002,\"changePayloadCipher\":\"cipher_text\"}");
        ApprovalCompletedEvent event = new ApprovalCompletedEvent(this, workflow, ApprovalStatus.REJECTED, 9001L);

        handler.onApprovalCompleted(event);

        verify(employeeService, never()).applyApprovedProfileChange(any(), any(), any());
        verify(encryptionService, never()).decrypt(any());
    }

    private ApprovalCompletedEvent approvedEvent(Long workflowId, String workflowData) {
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(workflowId);
        workflow.setBusinessType(EmployeeService.BUSINESS_TYPE_EMPLOYEE_PROFILE_CHANGE);
        workflow.setWorkflowData(workflowData);
        return new ApprovalCompletedEvent(this, workflow, ApprovalStatus.APPROVED, 9001L);
    }
}
