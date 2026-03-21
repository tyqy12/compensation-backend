package com.yiyundao.compensation.interfaces.dto.payroll;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollValidationIssueDto {

    public static final String SEVERITY_BLOCKING = "blocking";
    public static final String SEVERITY_REVIEW = "review";
    public static final String SEVERITY_INFO = "info";

    private String code;
    private String severity;
    private Boolean blocking;
    private String message;
    private String itemCode;
    private String currentValue;
    private String expectedValue;
}
