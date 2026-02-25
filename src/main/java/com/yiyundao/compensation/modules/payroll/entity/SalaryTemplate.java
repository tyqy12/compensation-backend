package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("salary_template")
public class SalaryTemplate extends BaseEntity {
    private String name;
    private String type; // full_time/part_time
    @TableField("items_json")
    private String itemsJson;
    @TableField("tax_rule_json")
    private String taxRuleJson;
    private String status;
    @TableField("data_version")
    private Long dataVersion; // 数据版本号，用于确保确定性重新计算（区别于BaseEntity中的乐观锁version）
}

