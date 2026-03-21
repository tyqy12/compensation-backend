package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_reconciliation_task")
public class PayrollReconciliationTask extends BaseEntity {

    @TableField("distribution_id")
    private Long distributionId;

    @TableField("task_status")
    private String taskStatus;

    @TableField("expected_amount")
    private BigDecimal expectedAmount;

    @TableField("actual_amount")
    private BigDecimal actualAmount;

    private BigDecimal difference;

    private String result;

    @TableField("difference_detail")
    private String differenceDetail;
}
