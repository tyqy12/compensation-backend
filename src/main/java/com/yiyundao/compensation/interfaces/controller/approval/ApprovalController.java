package com.yiyundao.compensation.interfaces.controller.approval;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.interfaces.dto.approval.ApprovalDecisionRequest;
import com.yiyundao.compensation.interfaces.dto.approval.CancelWorkflowRequest;
import com.yiyundao.compensation.interfaces.dto.approval.StartWorkflowRequest;
import com.yiyundao.compensation.interfaces.vo.approval.ApprovalStepVO;
import com.yiyundao.compensation.interfaces.vo.approval.ApprovalWorkflowVO;
import com.yiyundao.compensation.modules.approval.entity.ApprovalStep;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.approval.service.ApprovalStepService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/approval/workflows")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalEngine approvalEngine;
    private final ApprovalStepService approvalStepService;
    private final com.yiyundao.compensation.modules.audit.service.AuditLogService auditLogService;

    // 发起审批
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER') or hasAuthority('approval:start')")
    public ApiResponse<Long> start(@Valid @RequestBody StartWorkflowRequest req, HttpServletRequest request) {
        long begin = System.currentTimeMillis();
        WorkflowType type = WorkflowType.fromCode(req.getWorkflowType());
        Long id = approvalEngine.startWorkflow(type, req.getBusinessKey(), req.getBusinessType(),
                req.getInitiatorId(), req.getWorkflowData());
        auditLogService.record(
                "APPROVAL_START",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                "APPROVAL",
                req.getBusinessKey(),
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                null,
                id != null ? ("WID=" + id) : "FAILED",
                null,
                System.currentTimeMillis() - begin
        );
        return ApiResponse.success(id);
    }

    // 审批通过
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','APPROVER') or hasAuthority('approval:approve')")
    public ApiResponse<Void> approve(@PathVariable Long id, @Valid @RequestBody ApprovalDecisionRequest req, HttpServletRequest request) {
        long begin = System.currentTimeMillis();
        approvalEngine.processApproval(id, req.getApproverId(), ApprovalStatus.APPROVED, req.getComment());
        auditLogService.record(
                "APPROVAL_APPROVE",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                "APPROVAL",
                String.valueOf(id),
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                null,
                "OK",
                null,
                System.currentTimeMillis() - begin
        );
        return ApiResponse.success(null);
    }

    // 审批拒绝
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','APPROVER') or hasAuthority('approval:reject')")
    public ApiResponse<Void> reject(@PathVariable Long id, @Valid @RequestBody ApprovalDecisionRequest req, HttpServletRequest request) {
        long begin = System.currentTimeMillis();
        approvalEngine.processApproval(id, req.getApproverId(), ApprovalStatus.REJECTED, req.getComment());
        auditLogService.record(
                "APPROVAL_REJECT",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                "APPROVAL",
                String.valueOf(id),
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                null,
                "OK",
                null,
                System.currentTimeMillis() - begin
        );
        return ApiResponse.success(null);
    }

    // 撤销流程（仅发起人）
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER') or hasAuthority('approval:cancel')")
    public ApiResponse<Void> cancel(@PathVariable Long id, @Valid @RequestBody CancelWorkflowRequest req, HttpServletRequest request) {
        long begin = System.currentTimeMillis();
        approvalEngine.cancelWorkflow(id, req.getOperatorId(), req.getReason());
        auditLogService.record(
                "APPROVAL_CANCEL",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                "APPROVAL",
                String.valueOf(id),
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                null,
                "OK",
                null,
                System.currentTimeMillis() - begin
        );
        return ApiResponse.success(null);
    }

    // 我的待办
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','APPROVER') or hasAuthority('approval:read')")
    public ApiResponse<List<ApprovalWorkflowVO>> pending(@RequestParam Long approverId) {
        List<ApprovalWorkflow> list = approvalEngine.getPendingWorkflows(approverId);
        return ApiResponse.success(list.stream().map(ApprovalWorkflowVO::from).collect(Collectors.toList()));
    }

    // 我发起的
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<ApprovalWorkflowVO>> my(@RequestParam Long initiatorId) {
        List<ApprovalWorkflow> list = approvalEngine.getMyWorkflows(initiatorId);
        return ApiResponse.success(list.stream().map(ApprovalWorkflowVO::from).collect(Collectors.toList()));
    }

    // 流程详情
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','APPROVER') or hasAuthority('approval:read')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        ApprovalWorkflow w = approvalEngine.getById(id);
        Map<String, Object> data = approvalEngine.getWorkflowData(id);
        Map<String, Object> result = Map.of(
                "workflow", w == null ? null : ApprovalWorkflowVO.from(w),
                "data", data
        );
        return ApiResponse.success(result);
    }

    // 步骤列表
    @GetMapping("/{id}/steps")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','APPROVER') or hasAuthority('approval:read')")
    public ApiResponse<List<ApprovalStepVO>> steps(@PathVariable Long id) {
        List<ApprovalStep> steps = approvalStepService.listByWorkflow(id);
        return ApiResponse.success(steps.stream().map(ApprovalStepVO::from).collect(Collectors.toList()));
    }
}
