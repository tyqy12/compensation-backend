package com.yiyundao.compensation.interfaces.dto.org;

import lombok.Data;

@Data
public class EmployeePreviewDto {
    private String provider;
    private String subjectId;
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
    // 同一人的分组键：provider + ":" + subjectId
    private String groupKey;
    // 是否已导入（已存在绑定员工）
    private Boolean alreadyImported;
    // 已存在员工主键ID（若已导入）
    private Long existingEmployeeDbId;
    // 已存在员工工号（若已导入）
    private String existingEmployeeNo;
    // 建议动作：CREATE | UPDATE
    private String importAction;
}
