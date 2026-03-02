package com.yiyundao.compensation.interfaces.vo.employee;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EmployeeApprovalRecordVO {
    private Long id;
    private Long employeeId;
    private String workflowName;
    private String workflowType;
    private String workflowTypeName;
    private String businessType;
    private String businessKey;
    private Integer currentStep;
    private Integer totalSteps;
    private String status;
    private String statusName;
    private Long initiatorId;
    private String initiatorName;
    private Long currentApproverId;
    private String currentApproverName;
    private LocalDateTime submitTime;
    private LocalDateTime completeTime;
}
