package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_payment_failure")
public class PayrollPaymentFailure extends BaseEntity {

    @TableField("workflow_id")
    private Long workflowId;

    @TableField("payroll_batch_id")
    private Long payrollBatchId;

    @TableField("business_key")
    private String businessKey;

    @TableField("error_message")
    private String errorMessage;

    private String status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("last_failed_time")
    private LocalDateTime lastFailedTime;

    @TableField("last_retry_time")
    private LocalDateTime lastRetryTime;

    @TableField("resolved_time")
    private LocalDateTime resolvedTime;

    @TableField("payment_batch_no")
    private String paymentBatchNo;

    public boolean isResolved() {
        return "resolved".equalsIgnoreCase(status);
    }
}
