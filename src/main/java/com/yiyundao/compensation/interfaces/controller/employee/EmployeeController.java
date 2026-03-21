package com.yiyundao.compensation.interfaces.controller.employee;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.enums.EmployeeStatus;
import com.yiyundao.compensation.interfaces.dto.employee.*;
import com.yiyundao.compensation.modules.employee.dto.BindPlatformRequest;
import com.yiyundao.compensation.modules.employee.dto.BindPlatformResult;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeVO;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeApprovalRecordVO;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeePayslipRecordVO;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeListItemVO;
import com.yiyundao.compensation.interfaces.vo.payment.PaymentRecordItemVO;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import com.yiyundao.compensation.modules.user.service.LegacyPlatformFieldPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/employees", "/employee"})
@RequiredArgsConstructor
@Tag(name = "员工管理", description = "员工信息的增删改查及批量导入")
@SecurityRequirement(name = "Bearer")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final AuditLogService auditLogService;
    private final LegacyPlatformFieldPolicy legacyPlatformFieldPolicy;

    @PostMapping
    @SecurityAnnotations.IsHrOrAdmin
    @Operation(summary = "创建员工", description = "新增员工信息，自动创建关联账号")
    public ApiResponse<EmployeeVO> create(@Valid @RequestBody EmployeeCreateRequest req) {
        legacyPlatformFieldPolicy.handleLegacyInput(
                "employee_controller_create",
                req.getLegacyPlatformType(),
                req.getLegacyPlatformUserId()
        );
        Employee e = mapToEntity(req);
        return ApiResponse.success(employeeService.createEmployeeWithUser(e, req.getUsername()));
    }

    @PutMapping("/{id}")
    @SecurityAnnotations.IsHrOrAdmin
    @Operation(summary = "更新员工", description = "根据ID更新员工信息")
    public ApiResponse<EmployeeVO> update(@PathVariable Long id, @Valid @RequestBody EmployeeUpdateRequest req) {
        Employee u = mapToEntity(req);
        return ApiResponse.success(employeeService.updateEmployee(id, u));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取员工详情", description = "根据ID获取员工详细信息")
    public ApiResponse<EmployeeVO> detail(@PathVariable Long id) {
        return ApiResponse.success(employeeService.getEmployeeVO(id));
    }

    @GetMapping("/{id}/approvals")
    @Operation(summary = "员工审批记录", description = "按员工维度分页查询审批记录")
    public ApiResponse<PageResponse<EmployeeApprovalRecordVO>> approvals(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(employeeService.pageEmployeeApprovals(id, page, size));
    }

    @GetMapping("/{id}/payslips")
    @Operation(summary = "员工发薪记录", description = "按员工维度分页查询工资条记录")
    public ApiResponse<PageResponse<EmployeePayslipRecordVO>> payslips(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(employeeService.pageEmployeePayslips(id, page, size));
    }

    @GetMapping("/{id}/payments")
    @Operation(summary = "员工支付记录", description = "按员工维度分页查询支付明细")
    public ApiResponse<PageResponse<PaymentRecordItemVO>> payments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(employeeService.pageEmployeePayments(id, page, size));
    }

    @GetMapping
    @Operation(summary = "分页查询员工", description = "支持多条件组合查询和分页")
    public ApiResponse<PageResponse<EmployeeListItemVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean isOffline,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false, defaultValue = "createTime") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String order,
            HttpServletRequest request
    ) {
        String legacyPlatformType = request.getParameter("platformType");
        legacyPlatformFieldPolicy.handleLegacyInput(
                "employee_controller_page_query",
                legacyPlatformType,
                null
        );
        String resolvedProvider = StringUtils.hasText(provider) ? provider : null;
        return ApiResponse.success(employeeService.pageEmployees(
                page,
                size,
                keyword,
                department,
                status,
                isOffline,
                resolvedProvider,
                managerId,
                sortBy,
                order
        ));
    }

    @PatchMapping("/{id}/status")
    @SecurityAnnotations.IsHrOrAdmin
    @Operation(summary = "更新员工状态", description = "修改员工在职状态")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        employeeService.updateStatus(id, EmployeeStatus.fromCode(req.getStatus()));
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/bind-platform")
    @SecurityAnnotations.IsHrOrAdmin
    @Operation(summary = "绑定平台用户", description = "绑定企业微信/钉钉/飞书平台用户。冲突时自动发起审批流程，返回审批信息便于追溯")
    public ApiResponse<BindPlatformResult> bindPlatform(@PathVariable Long id, @Valid @RequestBody BindPlatformRequest req) {
        legacyPlatformFieldPolicy.handleLegacyInput(
                "employee_controller_bind_platform",
                req.getLegacyPlatformType(),
                req.getLegacyPlatformUserId()
        );
        BindPlatformResult result = employeeService.bindPlatform(id, req);
        return ApiResponse.success(result);
    }

    @DeleteMapping("/{id}/unbind-platform")
    @SecurityAnnotations.IsAdmin
    @Operation(summary = "解绑平台用户", description = "仅管理员可执行，记录解绑原因用于审计")
    public ApiResponse<Void> unbindPlatform(@PathVariable Long id,
                                            @RequestParam(required = false, defaultValue = "管理员操作") String reason) {
        employeeService.unbindPlatform(id, reason);
        return ApiResponse.success(null);
    }

    @GetMapping("/offline")
    @Operation(summary = "获取架构外员工列表", description = "获取所有架构外员工或指定管理员的架构外员工")
    public ApiResponse<List<EmployeeVO>> offlineList(@RequestParam(required = false) Long managerId) {
        return ApiResponse.success(employeeService.getOfflineEmployees(managerId));
    }

    @GetMapping("/resigned")
    @Operation(summary = "获取离职员工列表", description = "获取所有离职员工或指定管理员的离职员工")
    public ApiResponse<List<EmployeeVO>> resignedList(@RequestParam(required = false) Long managerId) {
        return ApiResponse.success(employeeService.getResignedEmployees(managerId));
    }

    @SecurityAnnotations.IsAdmin
    @GetMapping("/{id}/id-card")
    @Operation(summary = "解密身份证号", description = "管理员权限解密身份证号(完整审计)")
    public ApiResponse<String> decryptedIdCard(@PathVariable Long id, HttpServletRequest request) {
        long start = System.currentTimeMillis();
        String value = employeeService.getDecryptedIdCard(id);
        auditLogService.record("DECRYPT_ID_CARD", request.getMethod(), request.getRequestURI(),
                request.getRemoteAddr(), request.getHeader("User-Agent"), "EMPLOYEE",
                String.valueOf(id), request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                null, value != null ? "OK" : "NOT_FOUND", null, System.currentTimeMillis() - start);
        return ApiResponse.success(value);
    }

    @SecurityAnnotations.IsAdmin
    @GetMapping("/{id}/bank-account")
    @Operation(summary = "解密银行卡号", description = "管理员权限解密银行卡号(完整审计)")
    public ApiResponse<String> decryptedBank(@PathVariable Long id, HttpServletRequest request) {
        long start = System.currentTimeMillis();
        String value = employeeService.getDecryptedBankAccount(id);
        auditLogService.record("DECRYPT_BANK_ACCOUNT", request.getMethod(), request.getRequestURI(),
                request.getRemoteAddr(), request.getHeader("User-Agent"), "EMPLOYEE",
                String.valueOf(id), request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                null, value != null ? "OK" : "NOT_FOUND", null, System.currentTimeMillis() - start);
        return ApiResponse.success(value);
    }

    @SecurityAnnotations.IsAdmin
    @GetMapping("/{id}/settlement-account")
    @Operation(summary = "解密收款账户", description = "管理员权限解密员工收款账户(完整审计)")
    public ApiResponse<String> decryptedSettlementAccount(@PathVariable Long id, HttpServletRequest request) {
        long start = System.currentTimeMillis();
        String value = employeeService.getDecryptedSettlementAccount(id);
        auditLogService.record("DECRYPT_SETTLEMENT_ACCOUNT", request.getMethod(), request.getRequestURI(),
                request.getRemoteAddr(), request.getHeader("User-Agent"), "EMPLOYEE",
                String.valueOf(id), request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                null, value != null ? "OK" : "NOT_FOUND", null, System.currentTimeMillis() - start);
        return ApiResponse.success(value);
    }

    @PostMapping("/batch-import")
    @SecurityAnnotations.IsHrOrAdmin
    @Operation(summary = "批量导入员工", description = "批量导入员工信息，跳过重复工号")
    public ApiResponse<Void> batchImport(@Valid @RequestBody BatchImportRequest req) {
        for (EmployeeCreateRequest item : req.getEmployees()) {
            legacyPlatformFieldPolicy.handleLegacyInput(
                    "employee_controller_batch_import",
                    item.getLegacyPlatformType(),
                    item.getLegacyPlatformUserId()
            );
        }
        List<Employee> list = req.getEmployees().stream().map(this::mapToEntity).toList();
        employeeService.batchImport(list);
        return ApiResponse.success(null);
    }

    private Employee mapToEntity(EmployeeCreateRequest req) {
        Employee e = new Employee();
        e.setEmployeeId(req.getEmployeeId());
        e.setName(req.getName());
        e.setPhone(req.getPhone());
        e.setEmail(req.getEmail());
        e.setEncryptedIdCard(req.getIdCard());
        e.setDepartment(req.getDepartment());
        e.setPosition(req.getPosition());
        e.setEmploymentType(req.getEmploymentType());
        e.setSubjectId(req.getSubjectId());
        e.setProvider(req.getProvider());
        e.setManagerId(req.getManagerId());
        e.setHireDate(req.getHireDate());
        e.setStatus(req.getStatus());
        e.setSettlementAccountType(req.getSettlementAccountType());
        e.setSettlementAccount(req.getSettlementAccount());
        e.setSettlementAccountName(req.getSettlementAccountName());
        e.setBankAccount(req.getBankAccount());
        e.setBankName(req.getBankName());
        e.setBankBranchName(req.getBankBranchName());
        e.setOffline(req.getOffline());
        return e;
    }

    private Employee mapToEntity(EmployeeUpdateRequest req) {
        Employee e = new Employee();
        e.setName(req.getName());
        e.setPhone(req.getPhone());
        e.setEmail(req.getEmail());
        e.setEncryptedIdCard(req.getIdCard());
        e.setDepartment(req.getDepartment());
        e.setPosition(req.getPosition());
        e.setEmploymentType(req.getEmploymentType());
        e.setManagerId(req.getManagerId());
        e.setHireDate(req.getHireDate());
        e.setStatus(req.getStatus());
        e.setSettlementAccountType(req.getSettlementAccountType());
        e.setSettlementAccount(req.getSettlementAccount());
        e.setSettlementAccountName(req.getSettlementAccountName());
        e.setBankAccount(req.getBankAccount());
        e.setBankName(req.getBankName());
        e.setBankBranchName(req.getBankBranchName());
        e.setOffline(req.getOffline());
        return e;
    }
}
