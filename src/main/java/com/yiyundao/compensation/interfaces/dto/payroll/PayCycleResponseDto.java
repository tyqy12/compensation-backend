package com.yiyundao.compensation.interfaces.dto.payroll;

import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class PayCycleResponseDto {

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
    private Integer leadDays;
    private Integer graceDays;
    private String timezone;
    private String description;
    private LocalDateTime nextExecutionTime;
    private LocalDateTime lastExecutionTime;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static PayCycleResponseDto from(PayCycle cycle) {
        if (cycle == null) {
            return null;
        }
        return PayCycleResponseDto.builder()
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
                .leadDays(cycle.getLeadDays())
                .graceDays(cycle.getGraceDays())
                .timezone(cycle.getTimezone())
                .description(cycle.getDescription())
                .nextExecutionTime(cycle.getNextExecutionTime())
                .lastExecutionTime(cycle.getLastExecutionTime())
                .status(cycle.getStatus())
                .createTime(cycle.getCreateTime())
                .updateTime(cycle.getUpdateTime())
                .build();
    }
}
