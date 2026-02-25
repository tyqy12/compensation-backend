package com.yiyundao.compensation.dto.org;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DepartmentNodeDto {
    private Long id; // 本地ID
    private String platformType;
    private String platformDeptId;
    private String parentPlatformDeptId;
    private String name;
    private List<DepartmentNodeDto> children = new ArrayList<>();
}

