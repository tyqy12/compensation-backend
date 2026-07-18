package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
public class PayrollTaxPreviewRequest {
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal cumulativeIncome = BigDecimal.ZERO;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal cumulativeTaxExemptIncome = BigDecimal.ZERO;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal cumulativeBasicDeduction = BigDecimal.ZERO;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal cumulativeSpecialDeduction = BigDecimal.ZERO;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal cumulativeSpecialAdditionalDeduction = BigDecimal.ZERO;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal cumulativeOtherDeduction = BigDecimal.ZERO;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal cumulativeTaxReduction = BigDecimal.ZERO;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal cumulativeWithheldTax = BigDecimal.ZERO;
    @Min(0)
    @Max(8)
    private int scale = 2;
    private RoundingMode roundingMode = RoundingMode.HALF_UP;
}
