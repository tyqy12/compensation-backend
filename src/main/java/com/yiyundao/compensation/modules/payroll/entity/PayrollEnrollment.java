package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_enrollment")
public class PayrollEnrollment extends BaseEntity {
    @TableField("employee_id")
    private Long employeeId;
    @TableField("contribution_type")
    private String contributionType;
    @TableField("region_code")
    private String regionCode;
    @TableField("collection_entity_code")
    private String collectionEntityCode;
    @TableField("account_no_encrypted")
    private String accountNoEncrypted;
    @TableField("effective_from")
    private LocalDate effectiveFrom;
    @TableField("effective_to")
    private LocalDate effectiveTo;
    private String status;
    @TableField("is_primary")
    private Boolean primaryFlag;
    @TableField("event_type")
    private String eventType;
    @TableField("policy_id")
    private Long policyId;

    /** 业务层沿用 primary 语义，持久化属性避免生成保留字 SQL 别名。 */
    public Boolean getPrimary() {
        return primaryFlag;
    }

    public void setPrimary(Boolean primary) {
        this.primaryFlag = primary;
    }
}
