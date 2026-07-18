package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PayrollTaxDeductionDeclarationRequest {
    @NotNull
    private Long employeeId;
    @NotNull
    private Integer taxYear;
    @NotBlank
    private String deductionType;
    private String subjectKey;
    @DecimalMin(value = "0", inclusive = true)
    @DecimalMax(value = "1", inclusive = true)
    private BigDecimal allocationRatio = BigDecimal.ONE;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal monthlyAmount;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal annualAmount;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String credentialRef;
    private String evidenceJson;
    private String sourceType = "employee_declaration";
}
