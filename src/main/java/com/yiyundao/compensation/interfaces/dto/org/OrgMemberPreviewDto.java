package com.yiyundao.compensation.interfaces.dto.org;

import lombok.Data;

@Data
public class OrgMemberPreviewDto {
    private String platformUserId;
    private String name;
    private String employeeId; // 预览阶段通常为空，供编辑
    private String phone;
    private String email;
    private String position;
    private String employmentType; // full_time/part_time，可编辑
}

