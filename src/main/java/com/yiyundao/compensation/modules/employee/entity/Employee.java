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
    private String platformUserId;
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
}
