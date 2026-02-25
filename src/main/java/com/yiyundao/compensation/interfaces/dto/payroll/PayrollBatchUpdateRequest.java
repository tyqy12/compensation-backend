package com.yiyundao.compensation.interfaces.dto.payroll;

import lombok.Data;

@Data
public class PayrollBatchUpdateRequest {
    private Long payCycleId;
    private String periodLabel;
    private String scopeJson;
    private String currency;
    private String remark;
}

