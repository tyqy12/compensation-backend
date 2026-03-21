package com.yiyundao.compensation.interfaces.dto.org;

import lombok.Data;
import java.util.List;

@Data
public class OrgFetchPreviewResponse {
    private String provider;
    private int totalEmployees;
    private int newEmployees;
    private int existingEmployees;
    private List<EmployeePreviewDto> employees;
}
