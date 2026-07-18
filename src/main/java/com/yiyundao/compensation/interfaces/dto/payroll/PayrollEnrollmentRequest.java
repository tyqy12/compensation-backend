package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PayrollEnrollmentRequest {
    @NotNull
    private Long employeeId;
    @NotBlank
    private String contributionType;
    @NotBlank
    private String regionCode;
    private String collectionEntityCode;
    private String accountNoEncrypted;
    @NotNull
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String status = "active";
    private Boolean primary = Boolean.TRUE;
    private String eventType = "enroll";
    private Long policyId;
}
