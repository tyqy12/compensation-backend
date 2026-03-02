package com.yiyundao.compensation.modules.payment.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import com.yiyundao.compensation.enums.EmploymentType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 员工类型渠道映射实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("employee_type_provider_mapping")
public class EmployeeTypeProviderMapping extends BaseEntity {

    /**
     * 员工类型：full_time/part_time/intern/contract
     */
    @TableField("employment_type")
    private EmploymentType employmentType;

    /**
     * 结算渠道编码
     */
    @TableField("provider_code")
    private String providerCode;

    /**
     * 优先级（数字越大越高）
     */
    @TableField("priority")
    private Integer priority;

    /**
     * 是否启用：1-启用，0-禁用
     */
    @TableField("enabled")
    private Boolean enabled;
}
