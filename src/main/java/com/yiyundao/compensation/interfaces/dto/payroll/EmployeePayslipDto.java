package com.yiyundao.compensation.interfaces.dto.payroll;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class EmployeePayslipDto {

    @Data
    public static class PayslipSummary {
        private Long lineId;
        private Long batchId;
        private Long payCycleId;
        private String periodLabel;
        private String currency;
        private BigDecimal grossAmount;
        private BigDecimal taxAmount;
        private BigDecimal socialAmount;
        private BigDecimal netAmount;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String status;
        private String confirmationStatus;
        private Long confirmationAssigneeEmployeeId;
        private LocalDateTime confirmedAt;
        private String objectionReason;
    }

    @Data
    public static class PayslipDetail {
        private Long lineId;
        private Long batchId;
        private Long payCycleId;
        private String periodLabel;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String currency;
        private String employeeNo;
        private String employeeName;
        private String department;
        private String employmentType;
        private String bankName;
        private String bankAccountMasked;
        private BigDecimal earningsTotal;
        private BigDecimal deductionsTotal;
        private BigDecimal grossAmount;
        private BigDecimal taxAmount;
        private BigDecimal socialAmount;
        private BigDecimal netAmount;
        private String confirmationStatus;
        private Long confirmationAssigneeEmployeeId;
        private LocalDateTime confirmedAt;
        private String confirmationComment;
        private String objectionReason;
        private Long disputeWorkflowId;
        private List<PayrollPreviewDto.PayrollPreviewItemDto> items;
        private List<String> warnings;
    }
}
