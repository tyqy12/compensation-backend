package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SalaryTemplateUpsertRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String type; // full_time / part_time
    private String itemsJson;
    private String taxRuleJson;
    private String status; // enabled/disabled
}

