package com.yiyundao.compensation.modules.employee.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("employee")
public class Employee extends BaseEntity {

    private String employeeId;
    private String name;
    private String phone;
    private String email;

    @TableField("encrypted_id_card")
    private String encryptedIdCard;

    private String department;
    private String position;
    @TableField("employment_type")
    private String employmentType; // full_time / part_time

    @TableField(exist = false)
    private String subjectId;

    @TableField(exist = false)
    private String provider;

    @Deprecated
    @TableField(exist = false)
    private String platformUserId;

    @Deprecated
    @TableField(exist = false)
    private String platformType;

    @TableField("is_offline")
    private Boolean offline;

    @TableField("manager_id")
    private Long managerId;

    private LocalDate hireDate;
    private String status;

    @TableField("settlement_account_type")
    private String settlementAccountType;

    @TableField("settlement_account")
    private String settlementAccount;

    @TableField("settlement_account_name")
    private String settlementAccountName;

    @TableField("settlement_provider_code")
    private String settlementProviderCode;

    private String bankAccount;
    private String bankName;

    @TableField("bank_branch_name")
    private String bankBranchName;

    public String getSubjectId() {
        return subjectId != null ? subjectId : platformUserId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
        this.platformUserId = subjectId;
    }

    public String getProvider() {
        return provider != null ? provider : platformType;
    }

    public void setProvider(String provider) {
        this.provider = provider;
        this.platformType = provider;
    }

    public String getPlatformUserId() {
        return platformUserId != null ? platformUserId : subjectId;
    }

    public void setPlatformUserId(String platformUserId) {
        this.platformUserId = platformUserId;
        this.subjectId = platformUserId;
    }

    public String getPlatformType() {
        return platformType != null ? platformType : provider;
    }

    public void setPlatformType(String platformType) {
        this.platformType = platformType;
        this.provider = platformType;
    }
}
