package com.yiyundao.compensation.modules.employee.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface EmployeeDepartmentService {

    String MANUAL_PLATFORM = "manual";

    void replaceDepartments(Long employeeId, String platformType, List<String> deptNames);

    List<String> findDepartmentNames(Long employeeId);

    Map<Long, List<String>> findDepartmentNamesByEmployeeIds(Collection<Long> employeeIds);

    List<Long> findEmployeeIdsByDepartmentName(String departmentName);

    List<Long> findEmployeeIdsByLocalDepartmentId(Long localDepartmentId);
}
