package com.yiyundao.compensation.interfaces.dto.org;

import lombok.Data;
import java.util.List;

@Data
public class OrgDeptTreeResponse {
    private String platformType;
    private List<OrgDeptNodeDto> roots;
}

