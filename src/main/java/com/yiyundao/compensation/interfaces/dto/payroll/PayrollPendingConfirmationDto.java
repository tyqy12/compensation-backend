package com.yiyundao.compensation.interfaces.dto.payroll;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayrollPendingConfirmationDto {
    private Long lineId;
    private Long batchId;
    private String periodLabel;
    private Long employeeId;
    private String employeeNo;
    private String employeeName;
    private String department;
    private BigDecimal netAmount;
    private String currency;
    private String confirmationStatus;
}
