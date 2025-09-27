package com.yiyundao.compensation.interfaces.vo.approval;

import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApprovalWorkflowVO {
    private Long id;
    private String workflowName;
    private String workflowType;
    private String workflowTypeName;
    private String businessKey;
    private String businessType;
    private Integer currentStep;
    private Integer totalSteps;
    private String status;
    private String statusName;
    private Long initiatorId;
    private Long currentApproverId;
    private LocalDateTime submitTime;
    private LocalDateTime completeTime;

    public static ApprovalWorkflowVO from(ApprovalWorkflow w) {
        ApprovalWorkflowVO vo = new ApprovalWorkflowVO();
        vo.setId(w.getId());
        vo.setWorkflowName(w.getWorkflowName());
        if (w.getWorkflowType() != null) {
            vo.setWorkflowType(w.getWorkflowType().getCode());
            vo.setWorkflowTypeName(w.getWorkflowType().getName());
        }
        vo.setBusinessKey(w.getBusinessKey());
        vo.setBusinessType(w.getBusinessType());
        vo.setCurrentStep(w.getCurrentStep());
        vo.setTotalSteps(w.getTotalSteps());
        if (w.getStatus() != null) {
            vo.setStatus(w.getStatus().getCode());
            vo.setStatusName(w.getStatus().getName());
        }
        vo.setInitiatorId(w.getInitiatorId());
        vo.setCurrentApproverId(w.getCurrentApproverId());
        vo.setSubmitTime(w.getSubmitTime());
        vo.setCompleteTime(w.getCompleteTime());
        return vo;
    }
}

