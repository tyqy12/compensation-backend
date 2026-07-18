package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_tax_deduction_declaration")
public class PayrollTaxDeductionDeclaration extends BaseEntity {
    @TableField("employee_id")
    private Long employeeId;
    @TableField("tax_year")
    private Integer taxYear;
    @TableField("deduction_type")
    private String deductionType;
    @TableField("subject_key")
    private String subjectKey;
    @TableField("allocation_ratio")
    private BigDecimal allocationRatio;
    @TableField("monthly_amount")
    private BigDecimal monthlyAmount;
    @TableField("annual_amount")
    private BigDecimal annualAmount;
    @TableField("effective_from")
    private LocalDate effectiveFrom;
    @TableField("effective_to")
    private LocalDate effectiveTo;
    @TableField("credential_ref")
    private String credentialRef;
    @TableField("evidence_json")
    private String evidenceJson;
    private String status;
    @TableField("source_type")
    private String sourceType;
}
