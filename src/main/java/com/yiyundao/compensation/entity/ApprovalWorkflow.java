package com.yiyundao.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("approval_workflow")
public class ApprovalWorkflow extends BaseEntity {

    @TableField("workflow_name")
    private String workflowName;

    @TableField("workflow_type")
    private WorkflowType workflowType;

    @TableField("business_key")
    private String businessKey;

    @TableField("business_type")
    private String businessType;

    @TableField("current_step")
    private Integer currentStep;

    @TableField("total_steps")
    private Integer totalSteps;

    private ApprovalStatus status;

    @TableField("initiator_id")
    private Long initiatorId;

    @TableField("current_approver_id")
    private Long currentApproverId;

    @TableField("submit_time")
    private LocalDateTime submitTime;

    @TableField("complete_time")
    private LocalDateTime completeTime;

    @TableField("workflow_data")
    private String workflowData;
}