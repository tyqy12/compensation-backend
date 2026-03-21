package com.yiyundao.compensation.interfaces.dto.payroll;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PayrollDistributionDto {

    private Long id;
    private String distributionNo;
    private Long batchId;
    private Integer batchRevision;
    private String periodLabel;
    private String payrollType;

    private String distributionStatus;
    private BigDecimal totalAmount;
    private Integer totalCount;
    private LocalDate scheduledDate;
    private Integer retryLimit;
    private Boolean allowPartial;

    private BigDecimal actualAmount;
    private Integer successCount;
    private Integer failedCount;
    private Integer currentAttempt;

    private Long approvalWorkflowId;
    private String approvalStatus;
    private String approvalResult;
    private LocalDateTime approvalSubmittedAt;
    private LocalDateTime approvalCompletedAt;

    private String paymentBatchNo;
    private String settlementProviderCode;

    private Long reconciliationTaskId;
    private String reconciliationTaskStatus;
    private String reconciliationResult;
    private BigDecimal reconciliationDifference;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
