package com.yiyundao.compensation.interfaces.vo.payroll;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PayrollImportItemVO {
    private Long id;
    private Long batchId;
    private Long employeeId;
    private String employeeNo;
    private String employeeName;
    private String itemCode;
    private String itemName;
    private String itemType;
    private BigDecimal amount;
    private String note;
    private String sourceName;
    private Integer rowNo;
    private String status;
    private String errorMsg;
    private Boolean manual;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
