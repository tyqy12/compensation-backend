package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_tax_bracket")
public class PayrollTaxBracket extends BaseEntity {
    @TableField("policy_id")
    private Long policyId;
    @TableField("tax_year")
    private Integer taxYear;
    @TableField("bracket_level")
    private Integer bracketLevel;
    @TableField("upper_limit")
    private BigDecimal upperLimit;
    private BigDecimal rate;
    @TableField("quick_deduction")
    private BigDecimal quickDeduction;
}
