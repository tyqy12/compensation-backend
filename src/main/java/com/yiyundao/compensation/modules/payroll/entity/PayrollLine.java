package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_line")
public class PayrollLine extends BaseEntity {
    @TableField("batch_id")
    private Long batchId;
    @TableField("employee_id")
    private Long employeeId;
    @TableField("employment_type")
    private String employmentType;
    @TableField("template_id")
    private Long templateId;
    @TableField("items_snapshot_json")
    private String itemsSnapshotJson;
    @TableField("gross_amount")
    private java.math.BigDecimal grossAmount;
    @TableField("tax_amount")
    private java.math.BigDecimal taxAmount;
    @TableField("social_amount")
    private java.math.BigDecimal socialAmount;
    @TableField("net_amount")
    private java.math.BigDecimal netAmount;
    private String currency;
    private String status;
    private String note;
    @TableField("warning")
    private String warning;
}

