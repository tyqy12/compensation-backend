package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_contribution_record")
public class PayrollContributionRecord extends BaseEntity {
    @TableField("payroll_batch_id")
    private Long payrollBatchId;
    @TableField("payroll_line_id")
    private Long payrollLineId;
    @TableField("employee_id")
    private Long employeeId;
    @TableField("contribution_type")
    private String contributionType;
    @TableField("region_code")
    private String regionCode;
    @TableField("policy_id")
    private Long policyId;
    @TableField("declared_wage")
    private BigDecimal declaredWage;
    @TableField("contribution_base")
    private BigDecimal contributionBase;
    @TableField("employer_rate")
    private BigDecimal employerRate;
    @TableField("employee_rate")
    private BigDecimal employeeRate;
    @TableField("employer_amount")
    private BigDecimal employerAmount;
    @TableField("employee_amount")
    private BigDecimal employeeAmount;
    @TableField("adjustment_of_id")
    private Long adjustmentOfId;
    private String status;
    @TableField("calculation_hash")
    private String calculationHash;
}
