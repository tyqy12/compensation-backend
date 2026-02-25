package com.yiyundao.compensation.interfaces.dto.admin;

import lombok.Data;

@Data
public class UserAggregateDto {
    private Long userId;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String roles; // comma separated

    private Long employeeId;     // employee.id
    private String employeeNo;   // employee.employee_id
    private String employeeName; // employee.name
    private String platformType;
    private String platformUserId;
}

