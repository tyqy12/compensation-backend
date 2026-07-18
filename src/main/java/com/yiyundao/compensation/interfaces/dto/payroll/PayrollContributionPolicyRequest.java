package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 统筹地区五险一金政策参数，发布前必须完成双人复核。 */
@Data
public class PayrollContributionPolicyRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String regionCode;
    private String collectionEntityCode;
    @NotBlank
    private String contributionType;
    private String personCategory;
    private String householdType;
    private String industryRiskLevel;
    @NotNull
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal baseMin;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal baseMax;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal employerRate = BigDecimal.ZERO;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal employeeRate = BigDecimal.ZERO;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal employerFixedAmount = BigDecimal.ZERO;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal employeeFixedAmount = BigDecimal.ZERO;
    private String roundingMode = "HALF_UP";
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal minimumAmount;
    @NotBlank
    private String sourceDocument;
    @NotBlank
    private String sourceUrl;
    private Long versionNo;
}
