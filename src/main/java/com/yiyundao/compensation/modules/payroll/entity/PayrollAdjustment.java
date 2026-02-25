package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_adjustment")
public class PayrollAdjustment extends BaseEntity {
    @TableField("line_id")
    private Long lineId;
    @TableField("item_code")
    private String itemCode;
    private BigDecimal amount;
    private String reason;
    @TableField("approver_id")
    private Long approverId;
}

