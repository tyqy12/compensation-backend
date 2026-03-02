package com.yiyundao.compensation.interfaces.dto.payroll;

import lombok.Data;

@Data
public class PayrollConfirmationSummaryDto {
    private Long batchId;
    private String batchStatus;
    private String confirmationMode;
    private Long totalLines;
    private Long pendingCount;
    private Long confirmedCount;
    private Long objectedCount;
    private Long objectedApprovedCount;
    private Long objectedRejectedCount;
}
