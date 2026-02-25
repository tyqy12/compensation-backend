package com.yiyundao.compensation.interfaces.dto.org;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class OrgDeptNodeDto {
    private String platformDeptId;
    private String name;
    private String parentPlatformDeptId;
    private List<OrgDeptNodeDto> children = new ArrayList<>();
    private List<OrgMemberPreviewDto> members = new ArrayList<>();
}

