package com.yiyundao.compensation.interfaces.dto.org;

import lombok.Data;
import java.util.List;

@Data
public class OrgFetchPreviewResponse {
    private String platformType;
    private int totalEmployees;
    private List<EmployeePreviewDto> employees;
}

