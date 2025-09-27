package com.yiyundao.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment_batch")
public class PaymentBatch extends BaseEntity {

    @TableField("batch_no")
    private String batchNo;

    @TableField("batch_name")
    private String batchName;

    @TableField("payment_type")
    private PaymentType paymentType;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("success_count")
    private Integer successCount;

    @TableField("failed_count")
    private Integer failedCount;

    private BatchStatus status;

    @TableField("submit_time")
    private LocalDateTime submitTime;

    @TableField("approve_time")
    private LocalDateTime approveTime;

    @TableField("process_start_time")
    private LocalDateTime processStartTime;

    @TableField("process_end_time")
    private LocalDateTime processEndTime;

    @TableField("approver_id")
    private Long approverId;

    @TableField("processor_id")
    private Long processorId;

    private String remark;
}