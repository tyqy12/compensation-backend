package com.yiyundao.compensation.modules.employee.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.employee.entity.Employee;

import java.util.List;

public interface EmployeeService extends IService<Employee> {
    Employee createEmployee(Employee employee);
    Employee updateEmployee(Long id, Employee updateInfo);
    void bindPlatformUser(Long employeeId, String platformUserId, String platformType);
    void setOfflineManager(Long employeeId, Long managerId);
    Page<Employee> pageEmployees(int pageNum,
                                 int pageSize,
                                 String keyword,
                                 String department,
                                 String status,
                                 Boolean isOffline,
                                 String platformType,
                                 Long managerId,
                                 String sortBy,
                                 String order);
    List<Employee> getOfflineEmployees(Long managerId);
    Employee getByPlatformUserId(String platformUserId, String platformType);
    boolean existsByEmployeeId(String employeeId);
    void updateStatus(Long employeeId, String status);
    void batchImport(List<Employee> employees);
    String getDecryptedIdCard(Long employeeId);
    String getDecryptedBankAccount(Long employeeId);
}
