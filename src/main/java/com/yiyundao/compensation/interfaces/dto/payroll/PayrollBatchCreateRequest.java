package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PayrollBatchCreateRequest {
    private Long payCycleId;
    private String periodLabel; // 可冗余传入
    @NotBlank
    private String type; // full_time / part_time
    private String scopeJson; // JSON string
    private String currency; // 默认 CNY
    private String remark;
}

