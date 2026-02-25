package com.yiyundao.compensation.interfaces.dto.org;

import lombok.Data;

@Data
public class EmployeePreviewDto {
    private String platformType;
    private String platformUserId;
    private String employeeId;
    private String name;
    private String phone;
    private String email;
    // 展示用的主部门（可为空）
    private String department;
    // 多部门列表（按顺序展示、可编辑）
    private java.util.List<String> departments;
    private String position;
    private String employmentType; // full_time/part_time，可编辑
    // 同一人的分组键：platformType + ":" + platformUserId
    private String groupKey;
}
