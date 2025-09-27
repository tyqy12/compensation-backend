package com.yiyundao.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.enums.ApprovalStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("approval_step")
public class ApprovalStep extends BaseEntity {

    @TableField("workflow_id")
    private Long workflowId;

    @TableField("step_no")
    private Integer stepNo;

    @TableField("step_name")
    private String stepName;

    @TableField("approver_id")
    private Long approverId;

    @TableField("approver_name")
    private String approverName;

    private ApprovalStatus status;

    @TableField("approve_time")
    private LocalDateTime approveTime;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("approve_comment")
    private String approveComment;

    @TableField("timeout_hours")
    private Integer timeoutHours;
}