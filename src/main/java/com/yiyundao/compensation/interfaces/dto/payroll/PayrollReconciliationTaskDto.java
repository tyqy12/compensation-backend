package com.yiyundao.compensation.interfaces.dto.payroll;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PayrollReconciliationTaskDto {

    private Long id;
    private Long distributionId;
    private String distributionNo;
    private String distributionStatus;

    private Long batchId;
    private Integer batchRevision;
    private String periodLabel;
    private String payrollType;

    private String taskStatus;
    private BigDecimal expectedAmount;
    private BigDecimal actualAmount;
    private BigDecimal difference;
    private String result;
    private String differenceDetail;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
