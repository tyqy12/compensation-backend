package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PayCycleUpsertRequest {
    @NotBlank
    private String type; // monthly/custom
    @NotBlank
    private String periodLabel; // YYYY-MM
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate cutoffDate;
    private String status; // open/closed/archived
}

