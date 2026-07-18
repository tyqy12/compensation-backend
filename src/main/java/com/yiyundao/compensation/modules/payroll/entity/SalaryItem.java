package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("salary_item")
public class SalaryItem extends BaseEntity {
    private String code;
    private String name;
    private String type; // earning/deduction
    private Boolean taxable;
    private Boolean showOnPayslip;
    private Integer orderNum;
    private String status; // enabled/disabled
    @TableField("tax_category")
    private String taxCategory;
    @TableField("tax_exempt")
    private Boolean taxExempt;
    @TableField("pension_base")
    private Boolean pensionBase;
    @TableField("medical_base")
    private Boolean medicalBase;
    @TableField("unemployment_base")
    private Boolean unemploymentBase;
    @TableField("work_injury_base")
    private Boolean workInjuryBase;
    @TableField("maternity_base")
    private Boolean maternityBase;
    @TableField("housing_fund_base")
    private Boolean housingFundBase;
    @TableField("formula_json")
    private String formulaJson;
    @TableField("precision_scale")
    private Integer precisionScale;
    @TableField("rounding_mode")
    private String roundingMode;
    @TableField("effective_from")
    private java.time.LocalDate effectiveFrom;
    @TableField("effective_to")
    private java.time.LocalDate effectiveTo;
}
