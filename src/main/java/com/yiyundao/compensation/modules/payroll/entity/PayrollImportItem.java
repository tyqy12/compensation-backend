package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_import_item")
public class PayrollImportItem extends BaseEntity {
    @TableField("batch_id")
    private Long batchId;
    @TableField("employee_id")
    private Long employeeId;
    @TableField("item_code")
    private String itemCode;
    private BigDecimal amount;
    private String note;
    @TableField("source_name")
    private String sourceName;
    @TableField("row_no")
    private Integer rowNo;
    private String status; // valid/invalid
    @TableField("error_msg")
    private String errorMsg;
}

