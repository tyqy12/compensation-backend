package com.yiyundao.compensation.modules.employee.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import com.yiyundao.compensation.modules.employee.dto.EmployeeProfileChangePayload;
import com.yiyundao.compensation.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeProfileChangeApprovalHandler {

    private final EmployeeService employeeService;
    private final ObjectMapper objectMapper;
    private final EncryptionService encryptionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public void onApprovalCompleted(ApprovalCompletedEvent event) {
        ApprovalWorkflow workflow = event.getWorkflow();
        ApprovalStatus finalStatus = event.getFinalStatus();

        if (workflow == null) {
            log.warn("EmployeeProfileChangeApprovalHandler: workflow is null, skip");
            return;
        }
        if (!EmployeeService.BUSINESS_TYPE_EMPLOYEE_PROFILE_CHANGE.equalsIgnoreCase(workflow.getBusinessType())) {
            return;
        }

        if (finalStatus != ApprovalStatus.APPROVED) {
            log.info("员工资料变更审批未通过，workflowId={}, status={}", workflow.getId(), finalStatus);
            return;
        }

        try {
            if (!StringUtils.hasText(workflow.getWorkflowData())) {
                throw new IllegalStateException("审批流程数据为空");
            }
            Map<String, Object> data = objectMapper.readValue(workflow.getWorkflowData(), Map.class);
            Long employeeId = readEmployeeId(workflow, data);
            String payloadCipher = toText(data.get("changePayloadCipher"));
            if (employeeId == null) {
                throw new IllegalArgumentException("审批数据缺少employeeId");
            }
            if (!StringUtils.hasText(payloadCipher)) {
                throw new IllegalArgumentException("审批数据缺少changePayloadCipher");
            }

            String payloadJson = encryptionService.decrypt(payloadCipher);
            EmployeeProfileChangePayload payload = objectMapper.readValue(payloadJson, EmployeeProfileChangePayload.class);
            employeeService.applyApprovedProfileChange(workflow.getId(), employeeId, payload);
            log.info("员工资料变更审批处理成功: workflowId={}, employeeId={}", workflow.getId(), employeeId);
        } catch (Exception e) {
            log.error("员工资料变更审批处理失败: workflowId={}", workflow.getId(), e);
            throw new RuntimeException("员工资料变更审批处理失败: " + e.getMessage(), e);
        }
    }

    private Long readEmployeeId(ApprovalWorkflow workflow, Map<String, Object> data) {
        Long employeeId = toLong(data.get("employeeId"));
        if (employeeId != null) {
            return employeeId;
        }
        return workflow.getEmployeeId();
    }

    private Long toLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toText(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }
}
