package com.yiyundao.compensation.interfaces.dto.employee;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmployeeCreateRequest {
    @NotBlank
    private String employeeId;
    @NotBlank
    private String name;

    private String phone;
    private String email;
    // 明文身份证号，后端负责加密存储
    private String idCard;

    private String department;
    private String position;
    // 用工类型: full_time / part_time
    private String employmentType;
    private String platformUserId;
    private String platformType;
    private Long managerId;
    private LocalDate hireDate;
    private String status; // 默认 active
    private String bankAccount; // 明文，后端加密
    private String bankName;
    private Boolean offline;
    // 可选：指定要创建的系统用户名（若冲突会自动追加数字）
    private String username;
}
