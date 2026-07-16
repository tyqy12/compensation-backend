package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PayrollBatchCreateRequest {
    @NotNull(message = "必须选择发薪日历")
    private Long payCycleId;
    private String periodLabel; // 可冗余传入
    @NotBlank
    private String type; // full_time / part_time
    private String scopeJson; // JSON string
    private String currency; // 默认 CNY
    private Boolean confirmationRequired; // 默认 true
    private String confirmationMode; // individual/group
    private String remark;
}
