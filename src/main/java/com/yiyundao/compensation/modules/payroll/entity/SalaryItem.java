package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("salary_item")
public class SalaryItem extends BaseEntity {
    private String code;
    private String name;
    private String type; // earning/deduction
    private Boolean taxable;
    private Boolean showOnPayslip;
    private Integer orderNum;
    private String status; // enabled/disabled
}

