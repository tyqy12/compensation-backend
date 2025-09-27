package com.yiyundao.compensation.interfaces.vo.approval;

import com.yiyundao.compensation.modules.approval.entity.ApprovalStep;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApprovalStepVO {
    private Long id;
    private Integer stepNo;
    private String stepName;
    private Long approverId;
    private String approverName;
    private String status;
    private String statusName;
    private String approveComment;
    private String rejectReason;
    private Integer timeoutHours;
    private LocalDateTime approveTime;

    public static ApprovalStepVO from(ApprovalStep s) {
        ApprovalStepVO vo = new ApprovalStepVO();
        vo.setId(s.getId());
        vo.setStepNo(s.getStepNo());
        vo.setStepName(s.getStepName());
        vo.setApproverId(s.getApproverId());
        vo.setApproverName(s.getApproverName());
        if (s.getStatus() != null) {
            vo.setStatus(s.getStatus().getCode());
            vo.setStatusName(s.getStatus().getName());
        }
        vo.setApproveComment(s.getApproveComment());
        vo.setRejectReason(s.getRejectReason());
        vo.setTimeoutHours(s.getTimeoutHours());
        vo.setApproveTime(s.getApproveTime());
        return vo;
    }
}

