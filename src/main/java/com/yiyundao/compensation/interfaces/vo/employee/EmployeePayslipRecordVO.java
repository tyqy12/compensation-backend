package com.yiyundao.compensation.interfaces.vo.employee;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmployeePayslipRecordVO {
    private Long lineId;
    private Long batchId;
    private Long payCycleId;
    private String periodLabel;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String batchStatus;
    private String paymentBatchNo;
    private String employmentType;
    private String currency;
    private BigDecimal grossAmount;
    private BigDecimal taxAmount;
    private BigDecimal socialAmount;
    private BigDecimal netAmount;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
