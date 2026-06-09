package com.yiyundao.compensation.interfaces.controller.payroll;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchConfirmRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollConfirmationAssignRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollConfirmationSummaryDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPendingConfirmationDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayslipConfirmRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayslipObjectionRequest;
import com.yiyundao.compensation.modules.payroll.service.PayrollConfirmationService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@RestController
@RequestMapping("/payroll/confirmations")
@RequiredArgsConstructor
@SecurityAnnotations.IsAuthenticated
public class PayrollConfirmationController {

    private final PayrollConfirmationService payrollConfirmationService;
    private final SysUserService sysUserService;

    @PostMapping("/payslips/{lineId}/confirm")
    public ApiResponse<Boolean> confirmPayslip(@PathVariable Long lineId, @Valid @RequestBody PayslipConfirmRequest request) {
        payrollConfirmationService.confirmPayslip(lineId, currentUser(), request);
        return ApiResponse.success(true);
    }

    @PostMapping("/payslips/{lineId}/object")
    public ApiResponse<Map<String, Object>> objectPayslip(@PathVariable Long lineId, @Valid @RequestBody PayslipObjectionRequest request) {
        Long workflowId = payrollConfirmationService.objectPayslip(lineId, currentUser(), request);
        return ApiResponse.success(Map.of("workflowId", workflowId));
    }

    @PostMapping("/batches/{batchId}/batch-confirm")
    public ApiResponse<Map<String, Object>> batchConfirm(@PathVariable Long batchId,
                                                         @RequestBody(required = false) PayrollBatchConfirmRequest request) {
        int affected = payrollConfirmationService.batchConfirm(batchId, currentUser(), request);
        return ApiResponse.success(Map.of("affected", affected));
    }

    @PostMapping("/batches/{batchId}/assign")
    @SecurityAnnotations.IsFinanceOrAdmin
    public ApiResponse<Map<String, Object>> assign(@PathVariable Long batchId,
                                                   @Valid @RequestBody PayrollConfirmationAssignRequest request) {
        int affected = payrollConfirmationService.assignConfirmationAssignee(batchId, currentUser(), request);
        return ApiResponse.success(Map.of("affected", affected));
    }

    @GetMapping("/pending")
    public ApiResponse<Page<PayrollPendingConfirmationDto>> pending(@RequestParam(defaultValue = "1") int page,
                                                                    @RequestParam(defaultValue = "10") int size,
                                                                    @RequestParam(required = false) Long batchId) {
        return ApiResponse.success(payrollConfirmationService.pagePendingConfirmations(currentUser(), batchId, page, size));
    }

    @GetMapping("/batches/{batchId}/summary")
    @SecurityAnnotations.IsFinanceOrHrOrAdmin
    public ApiResponse<PayrollConfirmationSummaryDto> summary(@PathVariable Long batchId) {
        return ApiResponse.success(payrollConfirmationService.getBatchSummary(batchId));
    }

    private SysUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return sysUserService.findByUsername(authentication.getName());
    }
}
