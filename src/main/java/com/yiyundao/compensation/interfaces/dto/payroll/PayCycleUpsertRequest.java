package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PayCycleUpsertRequest {
    @NotBlank
    @Size(max = 20)
    private String type; // monthly/custom
    @NotBlank
    @Size(max = 20)
    private String periodLabel; // YYYY-MM
    @Size(max = 64)
    private String cycleCode;
    @Size(max = 100)
    private String cycleName;
    @Size(max = 20)
    private String cycleType;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate cutoffDate;
    @Min(1)
    @Max(31)
    private Integer payDay;
    @Min(0)
    @Max(365)
    private Integer leadDays;
    @Min(0)
    @Max(365)
    private Integer graceDays;
    @Size(max = 50)
    private String timezone;
    @Size(max = 500)
    private String description;
    private String status; // draft/open/closed/archived
}
