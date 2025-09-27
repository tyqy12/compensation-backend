package com.yiyundao.compensation.modules.approval.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import com.yiyundao.compensation.enums.ApprovalStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

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

    @TableField("approve_comment")
    private String approveComment;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("timeout_hours")
    private Integer timeoutHours;

    @TableField("approve_time")
    private java.time.LocalDateTime approveTime;
}

