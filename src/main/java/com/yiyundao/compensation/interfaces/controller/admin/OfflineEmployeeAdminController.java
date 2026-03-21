package com.yiyundao.compensation.interfaces.controller.admin;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import com.yiyundao.compensation.security.SecurityAnnotations;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/employees")
@RequiredArgsConstructor
@SecurityAnnotations.IsAdmin
public class OfflineEmployeeAdminController {

    private final EmployeeService employeeService;

    // 设置/取消架构外标记
    @PatchMapping("/{id}/offline")
    public ApiResponse<Void> setOffline(@PathVariable Long id, @RequestParam boolean value) {
        Employee e = new Employee();
        e.setOffline(value);
        employeeService.updateEmployee(id, e);
        return ApiResponse.success(null);
    }

    // 指定架构外员工负责人
    @PutMapping("/{id}/manager")
    public ApiResponse<Void> setManager(@PathVariable Long id, @RequestParam Long managerId) {
        employeeService.setOfflineManager(id, managerId);
        return ApiResponse.success(null);
    }
}
