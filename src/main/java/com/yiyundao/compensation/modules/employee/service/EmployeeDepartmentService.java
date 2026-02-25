package com.yiyundao.compensation.modules.employee.service;

import java.util.List;

public interface EmployeeDepartmentService {
    void replaceDepartments(Long employeeId, String platformType, List<String> deptNames);
}

