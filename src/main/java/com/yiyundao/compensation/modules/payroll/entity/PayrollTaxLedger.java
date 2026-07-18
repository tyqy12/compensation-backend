package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_tax_ledger")
public class PayrollTaxLedger extends BaseEntity {
    @TableField("employee_id")
    private Long employeeId;
    @TableField("withholding_entity_id")
    private Long withholdingEntityId;
    @TableField("tax_year")
    private Integer taxYear;
    @TableField("tax_month")
    private Integer taxMonth;
    @TableField("payroll_batch_id")
    private Long payrollBatchId;
    @TableField("payroll_batch_revision")
    private Integer payrollBatchRevision;
    @TableField("payroll_line_id")
    private Long payrollLineId;
    @TableField("cumulative_income")
    private BigDecimal cumulativeIncome;
    @TableField("cumulative_tax_exempt_income")
    private BigDecimal cumulativeTaxExemptIncome;
    @TableField("cumulative_basic_deduction")
    private BigDecimal cumulativeBasicDeduction;
    @TableField("cumulative_special_deduction")
    private BigDecimal cumulativeSpecialDeduction;
    @TableField("cumulative_special_additional")
    private BigDecimal cumulativeSpecialAdditional;
    @TableField("cumulative_other_deduction")
    private BigDecimal cumulativeOtherDeduction;
    @TableField("cumulative_taxable_income")
    private BigDecimal cumulativeTaxableIncome;
    @TableField("tax_rate")
    private BigDecimal taxRate;
    @TableField("quick_deduction")
    private BigDecimal quickDeduction;
    @TableField("cumulative_tax")
    private BigDecimal cumulativeTax;
    @TableField("cumulative_tax_reduction")
    private BigDecimal cumulativeTaxReduction;
    @TableField("cumulative_withheld_tax")
    private BigDecimal cumulativeWithheldTax;
    @TableField("current_withholding_tax")
    private BigDecimal currentWithholdingTax;
    @TableField("policy_id")
    private Long policyId;
    @TableField("calculation_hash")
    private String calculationHash;
    private String status;
}
