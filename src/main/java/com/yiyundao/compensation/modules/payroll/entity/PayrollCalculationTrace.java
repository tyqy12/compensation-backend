package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_calculation_trace")
public class PayrollCalculationTrace extends BaseEntity {
    @TableField("payroll_batch_id")
    private Long payrollBatchId;
    @TableField("payroll_line_id")
    private Long payrollLineId;
    @TableField("employee_id")
    private Long employeeId;
    private Integer sequence;
    @TableField("step_code")
    private String stepCode;
    @TableField("item_code")
    private String itemCode;
    @TableField("input_json")
    private String inputJson;
    @TableField("output_value")
    private BigDecimal outputValue;
    private String formula;
    @TableField("rule_version")
    private String ruleVersion;
    @TableField("source_ref")
    private String sourceRef;
    @TableField("rounding_mode")
    private String roundingMode;
    private String checksum;
}
