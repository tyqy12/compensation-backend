package com.yiyundao.compensation.interfaces.controller.approval;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/approval/workflows")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','FINANCE','MANAGER')")
public class ApprovalController {

    private final ApprovalEngine approvalEngine;
    private final SysUserService sysUserService;
    private final PayrollBatchService payrollBatchService;
    private final PayrollPaymentService payrollPaymentService;

    @PostMapping("/{id}/approve")
    public ApiResponse<Boolean> approve(@PathVariable Long id, @RequestBody(required = false) DecisionRequest req) {
        return decide(id, ApprovalStatus.APPROVED, req != null ? req.getComment() : null);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Boolean> reject(@PathVariable Long id, @RequestBody(required = false) DecisionRequest req) {
        return decide(id, ApprovalStatus.REJECTED, req != null ? req.getComment() : null);
    }

    private ApiResponse<Boolean> decide(Long workflowId, ApprovalStatus decision, String comment) {
        SysUser approver = resolveCurrentUser();
        if (approver == null) {
            return ApiResponse.error(401, "未登录");
        }

        try {
            approvalEngine.processApproval(workflowId, approver.getId(), decision, comment);
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }

        // Post decision handling for payroll business
        ApprovalWorkflow wf = approvalEngine.getById(workflowId);
        if (wf != null && "payroll".equalsIgnoreCase(wf.getBusinessType())) {
            String key = wf.getBusinessKey(); // e.g., payroll_batch:{id}
            Long batchId = parseBatchId(key);
            if (batchId != null) {
                PayrollBatch b = payrollBatchService.getById(batchId);
                if (b != null) {
                    if (wf.getStatus() == ApprovalStatus.APPROVED) {
                        payrollBatchService.updateStatus(batchId, "approved");
                        b.setStatus("approved");
                        payrollPaymentService.createPaymentBatch(b, approver, true);
                    } else if (wf.getStatus() == ApprovalStatus.REJECTED) {
                        payrollBatchService.updateStatus(batchId, "rejected");
                    }
                }
            }
        }
        return ApiResponse.success(true);
    }

    private Long parseBatchId(String businessKey) {
        if (businessKey == null) return null;
        int idx = businessKey.indexOf(":");
        if (idx < 0) return null;
        try { return Long.parseLong(businessKey.substring(idx + 1)); }
        catch (Exception e) { return null; }
    }

    private SysUser resolveCurrentUser() {
        try {
            org.springframework.security.core.Authentication a = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String username = a != null ? a.getName() : null;
            if (username == null || username.isBlank()) return null;
            return sysUserService.findByUsername(username);
        } catch (Exception e) {
            return null;
        }
    }

    @Data
    public static class DecisionRequest { private String comment; }
}
