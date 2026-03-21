package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_cycle")
public class PayCycle extends BaseEntity {
    private String type; // monthly/custom
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
    private String status; // draft/open/closed/archived
}
