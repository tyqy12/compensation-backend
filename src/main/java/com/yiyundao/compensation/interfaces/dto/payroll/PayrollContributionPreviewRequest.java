package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
public class PayrollContributionPreviewRequest {
    @NotBlank
    private String contributionType;
    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal declaredWage;
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
    private RoundingMode roundingMode = RoundingMode.HALF_UP;
}
