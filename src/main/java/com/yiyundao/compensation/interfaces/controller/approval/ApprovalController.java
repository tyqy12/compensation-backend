package com.yiyundao.compensation.interfaces.controller.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.interfaces.vo.approval.ApprovalStepVO;
import com.yiyundao.compensation.interfaces.vo.approval.ApprovalWorkflowDetailVO;
import com.yiyundao.compensation.interfaces.vo.approval.ApprovalWorkflowVO;
import com.yiyundao.compensation.modules.approval.entity.ApprovalStep;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.approval.service.ApprovalStepService;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/approval/workflows")
@RequiredArgsConstructor
@SecurityAnnotations.IsFinanceOrManagerOrAdmin
public class ApprovalController {

    private final ApprovalEngine approvalEngine;
    private final ApprovalStepService approvalStepService;
    private final ApprovalWorkflowMapper approvalWorkflowMapper;
    private final SysUserService sysUserService;
    private final AuditLogService auditLogService;

    // ==================== 查询接口 ====================

    /**
     * 分页查询审批流程列表
     */
    @GetMapping
    public ApiResponse<PageResponse<ApprovalWorkflowVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String workflowType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "submitTime") String sortBy,
            @RequestParam(defaultValue = "desc") String order) {

        IPage<ApprovalWorkflow> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<ApprovalWorkflow> queryWrapper = new LambdaQueryWrapper<>();

        // 状态筛选
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(ApprovalWorkflow::getStatus, ApprovalStatus.fromCode(status));
        }

        // 流程类型筛选
        if (StringUtils.hasText(workflowType)) {
            queryWrapper.eq(ApprovalWorkflow::getWorkflowType, WorkflowType.fromCode(workflowType));
        }

        // 关键词搜索（流程名称、业务Key）
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(w -> w
                    .like(ApprovalWorkflow::getWorkflowName, keyword)
                    .or()
                    .like(ApprovalWorkflow::getBusinessKey, keyword));
        }

        // 时间范围筛选
        if (StringUtils.hasText(startDate)) {
            queryWrapper.ge(ApprovalWorkflow::getSubmitTime, LocalDateTime.parse(startDate + "T00:00:00"));
        }
        if (StringUtils.hasText(endDate)) {
            queryWrapper.le(ApprovalWorkflow::getSubmitTime, LocalDateTime.parse(endDate + "T23:59:59"));
        }

        // 排序
        if ("asc".equalsIgnoreCase(order)) {
            queryWrapper.orderByAsc(ApprovalWorkflow::getSubmitTime);
        } else {
            queryWrapper.orderByDesc(ApprovalWorkflow::getSubmitTime);
        }

        IPage<ApprovalWorkflow> result = approvalWorkflowMapper.selectPage(pageParam, queryWrapper);

        List<ApprovalWorkflowVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return ApiResponse.success(PageResponse.of(result, voList));
    }

    /**
     * 查询待我审批的流程
     */
    @GetMapping("/pending")
    public ApiResponse<List<ApprovalWorkflowVO>> getPending() {
        SysUser currentUser = resolveCurrentUser();
        if (currentUser == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED, "未登录");
        }

        List<ApprovalWorkflow> workflows = approvalEngine.getPendingWorkflows(currentUser.getId());
        List<ApprovalWorkflowVO> voList = workflows.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return ApiResponse.success(voList);
    }

    /**
     * 查询我发起的流程
     */
    @GetMapping("/my")
    public ApiResponse<PageResponse<ApprovalWorkflowVO>> getMy(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status) {

        SysUser currentUser = resolveCurrentUser();
        if (currentUser == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED, "未登录");
        }

        IPage<ApprovalWorkflow> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<ApprovalWorkflow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ApprovalWorkflow::getInitiatorId, currentUser.getId());

        if (StringUtils.hasText(status)) {
            queryWrapper.eq(ApprovalWorkflow::getStatus, ApprovalStatus.fromCode(status));
        }

        queryWrapper.orderByDesc(ApprovalWorkflow::getSubmitTime);

        IPage<ApprovalWorkflow> result = approvalWorkflowMapper.selectPage(pageParam, queryWrapper);

        List<ApprovalWorkflowVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return ApiResponse.success(PageResponse.of(result, voList));
    }

    /**
     * 获取审批流程详情
     */
    @GetMapping("/{id}")
    public ApiResponse<ApprovalWorkflowDetailVO> getDetail(@PathVariable Long id) {
        ApprovalWorkflow workflow = approvalEngine.getById(id);
        if (workflow == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "审批流程不存在");
        }

        ApprovalWorkflowDetailVO detailVO = convertToDetailVO(workflow);

        // 获取审批步骤
        List<ApprovalStep> steps = approvalStepService.listByWorkflow(id);
        detailVO.setSteps(steps.stream()
                .map(ApprovalStepVO::from)
                .collect(Collectors.toList()));

        // 获取关联的业务信息（从 workflowData 中解析）
        if ("payroll".equalsIgnoreCase(workflow.getBusinessType())) {
            Long batchId = parseBatchId(workflow.getBusinessKey());
            if (batchId != null) {
                detailVO.setBusinessInfo(Map.of(
                        "batchId", String.valueOf(batchId),
                        "periodLabel", "",
                        "type", "",
                        "status", workflow.getStatus() != null ? workflow.getStatus().getCode() : "",
                        "paymentBatchNo", ""
                ));
            }
        }

        return ApiResponse.success(detailVO);
    }

    /**
     * 获取审批步骤列表
     */
    @GetMapping("/{id}/steps")
    public ApiResponse<List<ApprovalStepVO>> getSteps(@PathVariable Long id) {
        List<ApprovalStep> steps = approvalStepService.listByWorkflow(id);
        List<ApprovalStepVO> voList = steps.stream()
                .map(ApprovalStepVO::from)
                .collect(Collectors.toList());
        return ApiResponse.success(voList);
    }

    // ==================== 审批操作接口 ====================

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
            return ApiResponse.error(ErrorCode.UNAUTHORIZED, "未登录");
        }

        long begin = System.currentTimeMillis();
        String operation = decision == ApprovalStatus.APPROVED ? "审批通过" : "审批拒绝";

        try {
            approvalEngine.processApproval(workflowId, approver.getId(), decision, comment);
        } catch (Exception e) {
            // 审批失败，记录审计日志
            audit(operation, approver.getUsername(), workflowId.toString(), "APPROVAL", false, e.getMessage(), begin);
            return ApiResponse.error(ErrorCode.BUSINESS_ERROR, e.getMessage());
        }

        // 审批后业务处理由 ApprovalEngine.completeWorkflow() 中的 Handler 统一处理
        // 包括薪资批次支付创建、平台绑定等
        ApprovalWorkflow wf = approvalEngine.getById(workflowId);
        if (wf != null) {
            String businessKey = wf.getBusinessKey();
            audit(operation, approver.getUsername(), workflowId.toString(), "APPROVAL", true,
                    "businessKey=" + businessKey + ",status=" + wf.getStatus() + ",type=" + wf.getBusinessType(), begin);
        }
        return ApiResponse.success(true);
    }

    private void audit(String operation, String username, String workflowId, String businessType, boolean success, String detail, long begin) {
        try {
            auditLogService.record(
                    operation,
                    "POST",
                    "/approval/workflows/" + workflowId + "/decide",
                    null,
                    null,
                    businessType,
                    workflowId,
                    username,
                    detail,
                    success ? "OK" : "FAILED",
                    success ? null : detail,
                    System.currentTimeMillis() - begin
            );
        } catch (Exception e) {
            log.warn("审批审计记录失败: {}", e.getMessage());
        }
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

    /**
     * 将实体转换为列表视图对象
     */
    private ApprovalWorkflowVO convertToVO(ApprovalWorkflow w) {
        ApprovalWorkflowVO vo = new ApprovalWorkflowVO();
        vo.setId(w.getId());
        vo.setWorkflowName(w.getWorkflowName());
        if (w.getWorkflowType() != null) {
            vo.setWorkflowType(w.getWorkflowType().getCode());
            vo.setWorkflowTypeName(w.getWorkflowType().getName());
        }
        vo.setBusinessKey(w.getBusinessKey());
        vo.setBusinessType(w.getBusinessType());
        vo.setCurrentStep(w.getCurrentStep());
        vo.setTotalSteps(w.getTotalSteps());
        if (w.getStatus() != null) {
            vo.setStatus(w.getStatus().getCode());
            vo.setStatusName(w.getStatus().getName());
        }
        vo.setInitiatorId(w.getInitiatorId());
        vo.setCurrentApproverId(w.getCurrentApproverId());
        vo.setSubmitTime(w.getSubmitTime());
        vo.setCompleteTime(w.getCompleteTime());

        // 填充发起人名称
        if (w.getInitiatorId() != null) {
            SysUser initiator = sysUserService.getById(w.getInitiatorId());
            if (initiator != null) {
                vo.setInitiatorId(initiator.getId());
            }
        }

        return vo;
    }

    /**
     * 将实体转换为详情视图对象
     */
    private ApprovalWorkflowDetailVO convertToDetailVO(ApprovalWorkflow w) {
        ApprovalWorkflowDetailVO vo = new ApprovalWorkflowDetailVO();
        vo.setId(w.getId());
        vo.setWorkflowName(w.getWorkflowName());
        if (w.getWorkflowType() != null) {
            vo.setWorkflowType(w.getWorkflowType().getCode());
            vo.setWorkflowTypeName(w.getWorkflowType().getName());
        }
        vo.setBusinessKey(w.getBusinessKey());
        vo.setBusinessType(w.getBusinessType());
        vo.setCurrentStep(w.getCurrentStep());
        vo.setTotalSteps(w.getTotalSteps());
        if (w.getStatus() != null) {
            vo.setStatus(w.getStatus().getCode());
            vo.setStatusName(w.getStatus().getName());
        }
        vo.setInitiatorId(w.getInitiatorId());
        vo.setCurrentApproverId(w.getCurrentApproverId());
        vo.setSubmitTime(w.getSubmitTime());
        vo.setCompleteTime(w.getCompleteTime());

        // 填充发起人名称
        if (w.getInitiatorId() != null) {
            SysUser initiator = sysUserService.getById(w.getInitiatorId());
            if (initiator != null) {
                vo.setInitiatorName(initiator.getRealName() != null ? initiator.getRealName() : initiator.getUsername());
            }
        }

        // 填充当前审批人名称
        if (w.getCurrentApproverId() != null) {
            SysUser approver = sysUserService.getById(w.getCurrentApproverId());
            if (approver != null) {
                vo.setCurrentApproverName(approver.getRealName() != null ? approver.getRealName() : approver.getUsername());
            }
        }

        return vo;
    }

    @Data
    public static class DecisionRequest { private String comment; }
}
