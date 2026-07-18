package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

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

    /** 实际发放日；为空时由税务所属期按批次规则补充。 */
    private LocalDate payDate;
    private Integer taxYear;
    private Integer taxMonth;
    private Long taxWithholdingEntityId;
    private Integer taxBasicDeductionMonths;
    private Long policyPackageId;
}
