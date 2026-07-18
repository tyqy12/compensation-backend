package com.yiyundao.compensation.interfaces.dto.payroll;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PayrollBatchUpdateRequest {
    private Long payCycleId;
    private String periodLabel;
    private String scopeJson;
    private String currency;
    private Boolean confirmationRequired;
    private String confirmationMode;
    private String remark;
    private LocalDate payDate;
    private Integer taxYear;
    private Integer taxMonth;
    private Long taxWithholdingEntityId;
    private Integer taxBasicDeductionMonths;
    private Long policyPackageId;
}
