package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import com.yiyundao.compensation.enums.PayrollConfirmationSheetStatus;
import com.yiyundao.compensation.enums.PayrollConfirmationTimeoutStrategy;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_confirmation")
public class PayrollConfirmation extends BaseEntity {

    @TableField("confirmation_no")
    private String confirmationNo;

    @TableField("batch_id")
    private Long batchId;

    @TableField("batch_revision")
    private Integer batchRevision;

    @TableField("require_confirmation")
    private Boolean requireConfirmation;

    private LocalDateTime deadline;

    @TableField("timeout_strategy")
    private PayrollConfirmationTimeoutStrategy timeoutStrategy;

    @TableField("confirmation_status")
    private PayrollConfirmationSheetStatus confirmationStatus;

    @TableField("total_employees")
    private Integer totalEmployees;

    @TableField("confirmed_count")
    private Integer confirmedCount;

    @TableField("rejected_count")
    private Integer rejectedCount;

    @TableField("policy_id")
    private String policyId;
}
