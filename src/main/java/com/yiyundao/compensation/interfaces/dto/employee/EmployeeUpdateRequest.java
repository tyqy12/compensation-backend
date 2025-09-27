package com.yiyundao.compensation.interfaces.dto.employee;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EmployeeUpdateRequest {
    private String name;
    private String phone;
    private String email;
    private String idCard; // 明文，后端加密
    private String department;
    private String position;
    private LocalDate hireDate;
    private String bankAccount; // 明文，后端加密
    private String bankName;
}

