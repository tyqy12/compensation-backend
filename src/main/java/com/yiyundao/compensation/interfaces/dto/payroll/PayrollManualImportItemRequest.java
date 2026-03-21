package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayrollManualImportItemRequest {

    private Long employeeId;

    private String employeeNo;

    @NotBlank
    private String itemCode;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal amount;

    @Min(1)
    private Integer rowNo;

    private String note;

    @AssertTrue(message = "employeeId 或 employeeNo 至少提供一个")
    public boolean isEmployeeSpecified() {
        return employeeId != null || (employeeNo != null && !employeeNo.trim().isEmpty());
    }
}
