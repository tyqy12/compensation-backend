package com.yiyundao.compensation.interfaces.controller.employee;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.interfaces.dto.employee.*;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeVO;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeListItemVO;
import com.yiyundao.compensation.service.EncryptionService;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/employee")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EncryptionService encryptionService;
    private final AuditLogService auditLogService;

    // 创建员工
    @PostMapping
    public ApiResponse<EmployeeVO> create(@Valid @RequestBody EmployeeCreateRequest req) {
        Employee e = new Employee();
        e.setEmployeeId(req.getEmployeeId());
        e.setName(req.getName());
        e.setPhone(req.getPhone());
        e.setEmail(req.getEmail());
        e.setEncryptedIdCard(req.getIdCard());
        e.setDepartment(req.getDepartment());
        e.setPosition(req.getPosition());
        e.setPlatformUserId(req.getPlatformUserId());
        e.setPlatformType(req.getPlatformType());
        e.setManagerId(req.getManagerId());
        e.setHireDate(req.getHireDate());
        e.setStatus(req.getStatus());
        e.setBankAccount(req.getBankAccount());
        e.setBankName(req.getBankName());
        e.setOffline(req.getOffline());
        Employee saved = employeeService.createEmployee(e);
        return ApiResponse.success(EmployeeVO.from(saved, encryptionService));
    }

    // 更新员工
    @PutMapping("/{id}")
    public ApiResponse<EmployeeVO> update(@PathVariable Long id, @Valid @RequestBody EmployeeUpdateRequest req) {
        Employee u = new Employee();
        u.setName(req.getName());
        u.setPhone(req.getPhone());
        u.setEmail(req.getEmail());
        u.setEncryptedIdCard(req.getIdCard());
        u.setDepartment(req.getDepartment());
        u.setPosition(req.getPosition());
        u.setHireDate(req.getHireDate());
        u.setBankAccount(req.getBankAccount());
        u.setBankName(req.getBankName());
        Employee updated = employeeService.updateEmployee(id, u);
        return ApiResponse.success(EmployeeVO.from(updated, encryptionService));
    }

    // 员工详情
    @GetMapping("/{id}")
    public ApiResponse<EmployeeVO> detail(@PathVariable Long id) {
        Employee e = employeeService.getById(id);
        return ApiResponse.success(e == null ? null : EmployeeVO.from(e, encryptionService));
    }

    // 分页查询
    @GetMapping
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean isOffline,
            @RequestParam(required = false) String platformType,
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false, defaultValue = "createTime") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String order
    ) {
        Page<Employee> p = employeeService.pageEmployees(page, size, keyword, department, status, isOffline, platformType, managerId, sortBy, order);
        Map<String, Object> result = new HashMap<>();
        result.put("records", p.getRecords().stream().map(EmployeeListItemVO::from).toList());
        result.put("total", p.getTotal());
        result.put("current", p.getCurrent());
        result.put("size", p.getSize());
        return ApiResponse.success(result);
    }

    // 更新状态
    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        employeeService.updateStatus(id, req.getStatus());
        return ApiResponse.success(null);
    }

    // 绑定平台用户
    @PostMapping("/{id}/bind-platform")
    public ApiResponse<Void> bindPlatform(@PathVariable Long id, @Valid @RequestBody BindPlatformRequest req) {
        employeeService.bindPlatformUser(id, req.getPlatformUserId(), req.getPlatformType());
        return ApiResponse.success(null);
    }

    // 离线员工列表
    @GetMapping("/offline")
    public ApiResponse<List<EmployeeVO>> offlineList(@RequestParam(required = false) Long managerId) {
        List<EmployeeVO> list = employeeService.getOfflineEmployees(managerId)
                .stream().map(e -> EmployeeVO.from(e, encryptionService)).toList();
        return ApiResponse.success(list);
    }

    // 解密身份证号（需要权限控制，示例用途）
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/id-card")
    public ApiResponse<String> decryptedIdCard(@PathVariable Long id, HttpServletRequest request) {
        long start = System.currentTimeMillis();
        String value = employeeService.getDecryptedIdCard(id);
        auditLogService.record(
                "DECRYPT_ID_CARD",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                "EMPLOYEE",
                String.valueOf(id),
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                null,
                value != null ? "OK" : "NOT_FOUND",
                null,
                System.currentTimeMillis() - start
        );
        return ApiResponse.success(value);
    }

    // 解密银行卡号（需要权限控制，示例用途）
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/bank-account")
    public ApiResponse<String> decryptedBank(@PathVariable Long id, HttpServletRequest request) {
        long start = System.currentTimeMillis();
        String value = employeeService.getDecryptedBankAccount(id);
        auditLogService.record(
                "DECRYPT_BANK_ACCOUNT",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                "EMPLOYEE",
                String.valueOf(id),
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                null,
                value != null ? "OK" : "NOT_FOUND",
                null,
                System.currentTimeMillis() - start
        );
        return ApiResponse.success(value);
    }

    // 批量导入
    @PostMapping("/batch-import")
    public ApiResponse<Void> batchImport(@Valid @RequestBody BatchImportRequest req) {
        // 将请求 DTO 转换为实体列表
        List<Employee> list = req.getEmployees().stream().map(e -> {
            Employee em = new Employee();
            em.setEmployeeId(e.getEmployeeId());
            em.setName(e.getName());
            em.setPhone(e.getPhone());
            em.setEmail(e.getEmail());
            em.setEncryptedIdCard(e.getIdCard());
            em.setDepartment(e.getDepartment());
            em.setPosition(e.getPosition());
            em.setPlatformUserId(e.getPlatformUserId());
            em.setPlatformType(e.getPlatformType());
            em.setManagerId(e.getManagerId());
            em.setHireDate(e.getHireDate());
            em.setStatus(e.getStatus());
            em.setBankAccount(e.getBankAccount());
            em.setBankName(e.getBankName());
            em.setOffline(e.getOffline());
            return em;
        }).toList();

        employeeService.batchImport(list);
        return ApiResponse.success(null);
    }
}
