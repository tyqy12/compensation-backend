package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PayrollPolicyPackageRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    @NotBlank
    private String policyType;
    private String regionCode;
    private String collectionEntityCode;
    private String personCategory;
    private String industryRiskLevel;
    @NotNull
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    @NotBlank
    private String sourceDocument;
    @NotBlank
    private String sourceUrl;
    @NotBlank
    private String payloadJson;
    private Long versionNo;
}
