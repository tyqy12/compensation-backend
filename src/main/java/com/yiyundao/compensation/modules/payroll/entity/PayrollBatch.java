package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_batch")
public class PayrollBatch extends BaseEntity {
    @TableField("pay_cycle_id")
    private Long payCycleId;
    private String periodLabel;
    private String type; // full_time/part_time
    @TableField("scope_json")
    private String scopeJson;
    private String currency;
    private PayrollBatchStatus status; // 使用枚举类型，MyBatis-Plus会自动处理与数据库的转换
    @TableField("approval_workflow_id")
    private Long approvalWorkflowId;
    @TableField("payment_batch_no")
    private String paymentBatchNo;
    private String remark;
}

