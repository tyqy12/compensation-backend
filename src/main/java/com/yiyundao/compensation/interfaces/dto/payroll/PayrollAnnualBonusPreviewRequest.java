package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayrollAnnualBonusPreviewRequest {
    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal annualBonus;
}
