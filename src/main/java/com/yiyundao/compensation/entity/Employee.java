package com.yiyundao.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
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

    private String platformUserId;

    private String platformType;

    @TableField("is_offline")
    private Boolean offline;

    @TableField("manager_id")
    private Long managerId;

    private LocalDate hireDate;

    private String status;

    private String bankAccount;

    private String bankName;
}