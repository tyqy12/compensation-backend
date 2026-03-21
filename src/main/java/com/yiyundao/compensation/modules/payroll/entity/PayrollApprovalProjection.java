package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_approval_projection")
public class PayrollApprovalProjection extends BaseEntity {

    @TableField("batch_id")
    private Long batchId;

    @TableField("batch_revision")
    private Integer batchRevision;

    @TableField("distribution_id")
    private Long distributionId;

    @TableField("workflow_id")
    private Long workflowId;

    @TableField("business_status")
    private String businessStatus;

    @TableField("submitter_id")
    private Long submitterId;

    @TableField("submitted_at")
    private LocalDateTime submittedAt;

    @TableField("current_approver_id")
    private Long currentApproverId;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    private String result;
}
