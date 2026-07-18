package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_contribution_policy")
public class PayrollContributionPolicy extends BaseEntity {
    private String code;
    @TableField("region_code")
    private String regionCode;
    @TableField("collection_entity_code")
    private String collectionEntityCode;
    @TableField("contribution_type")
    private String contributionType;
    @TableField("person_category")
    private String personCategory;
    @TableField("household_type")
    private String householdType;
    @TableField("industry_risk_level")
    private String industryRiskLevel;
    @TableField("effective_from")
    private LocalDate effectiveFrom;
    @TableField("effective_to")
    private LocalDate effectiveTo;
    @TableField("base_min")
    private BigDecimal baseMin;
    @TableField("base_max")
    private BigDecimal baseMax;
    @TableField("employer_rate")
    private BigDecimal employerRate;
    @TableField("employee_rate")
    private BigDecimal employeeRate;
    @TableField("employer_fixed_amount")
    private BigDecimal employerFixedAmount;
    @TableField("employee_fixed_amount")
    private BigDecimal employeeFixedAmount;
    @TableField("rounding_mode")
    private String roundingMode;
    @TableField("minimum_amount")
    private BigDecimal minimumAmount;
    @TableField("source_document")
    private String sourceDocument;
    @TableField("source_url")
    private String sourceUrl;
    private String status;
    @TableField("version_no")
    private Long versionNo;
    @TableField("reviewed_by")
    private Long reviewedBy;
    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;
    @TableField("published_by")
    private Long publishedBy;
    @TableField("published_at")
    private LocalDateTime publishedAt;
}
