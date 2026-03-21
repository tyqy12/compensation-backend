package com.yiyundao.compensation.interfaces.dto.org;

import lombok.Data;
import java.util.List;

@Data
public class OrgDeptTreeResponse {
    private String provider;
    private List<OrgDeptNodeDto> roots;
}
