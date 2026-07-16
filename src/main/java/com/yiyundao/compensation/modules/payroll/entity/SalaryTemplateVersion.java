package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 薪资规则包的不可变版本快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("salary_template_version")
public class SalaryTemplateVersion extends BaseEntity {

    @TableField("template_id")
    private Long templateId;

    @TableField("version_no")
    private Long versionNo;

    private String name;
    private String type;

    @TableField("items_json")
    private String itemsJson;

    @TableField("tax_rule_json")
    private String taxRuleJson;

    private String status;
}
