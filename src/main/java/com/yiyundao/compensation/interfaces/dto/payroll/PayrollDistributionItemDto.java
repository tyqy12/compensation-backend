package com.yiyundao.compensation.interfaces.dto.payroll;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PayrollDistributionItemDto {

    private Long id;
    private Long distributionId;
    private Long employeeId;
    private Long lineId;

    private String employeeName;
    private String recipientName;
    private String accountNoMasked;
    private String accountType;
    private String paymentMethod;
    private String providerCode;

    private BigDecimal amount;
    private String itemStatus;
    private Long paymentRecordId;
    private Integer retryCount;
    private String failureReason;

    private String paymentRecordStatus;
    private String providerOrderNo;
    private String providerTradeNo;
    private String errorCode;
    private String errorMsg;
    private LocalDateTime paymentTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
