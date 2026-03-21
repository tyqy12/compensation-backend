package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import com.yiyundao.compensation.enums.PayrollDistributionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_distribution")
public class PayrollDistribution extends BaseEntity {

    @TableField("distribution_no")
    private String distributionNo;

    @TableField("batch_id")
    private Long batchId;

    @TableField("batch_revision")
    private Integer batchRevision;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("scheduled_date")
    private LocalDate scheduledDate;

    @TableField("retry_limit")
    private Integer retryLimit;

    @TableField("allow_partial")
    private Boolean allowPartial;

    @TableField("distribution_status")
    private PayrollDistributionStatus distributionStatus;

    @TableField("actual_amount")
    private BigDecimal actualAmount;

    @TableField("success_count")
    private Integer successCount;

    @TableField("failed_count")
    private Integer failedCount;

    @TableField("current_attempt")
    private Integer currentAttempt;

    @TableField("approval_workflow_id")
    private Long approvalWorkflowId;
}
