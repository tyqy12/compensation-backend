package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_cycle")
public class PayCycle extends BaseEntity {
    private String type; // monthly/custom
    private String periodLabel;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate cutoffDate;
    private String status; // open/closed/archived
}

