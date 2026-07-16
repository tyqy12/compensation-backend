package com.yiyundao.compensation.interfaces.dto.payroll;

import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 批次响应中的发薪日历上下文。
 *
 * 只暴露批次工作台需要的业务字段，不把 PayCycle 持久化实体直接泄露到接口层。
 */
@Data
@Builder
public class PayCycleContextDto {

    private Long id;
    private String type;
    private Long ruleTemplateId;
    private Long ruleTemplateVersion;
    private String periodLabel;
    private String cycleCode;
    private String cycleName;
    private String cycleType;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate cutoffDate;
    private Integer payDay;
    private String timezone;
    private String status;

    public static PayCycleContextDto from(PayCycle cycle) {
        if (cycle == null) {
            return null;
        }
        return PayCycleContextDto.builder()
                .id(cycle.getId())
                .type(cycle.getType())
                .ruleTemplateId(cycle.getRuleTemplateId())
                .ruleTemplateVersion(cycle.getRuleTemplateVersion())
                .periodLabel(cycle.getPeriodLabel())
                .cycleCode(cycle.getCycleCode())
                .cycleName(cycle.getCycleName())
                .cycleType(cycle.getCycleType())
                .startDate(cycle.getStartDate())
                .endDate(cycle.getEndDate())
                .cutoffDate(cycle.getCutoffDate())
                .payDay(cycle.getPayDay())
                .timezone(cycle.getTimezone())
                .status(cycle.getStatus())
                .build();
    }
}
