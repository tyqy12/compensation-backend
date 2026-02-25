package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("timesheet_entry")
public class TimesheetEntry extends BaseEntity {
    @TableField("employee_id")
    private Long employeeId;
    @TableField("work_date")
    private LocalDate workDate;
    private BigDecimal hours;
    private BigDecimal units;
    private String project;
    private String department;
    private String source; // manual/import/api
}

