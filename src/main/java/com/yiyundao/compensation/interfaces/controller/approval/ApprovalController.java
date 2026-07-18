package com.yiyundao.compensation.interfaces.controller.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.exception.BusinessException;
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
import com.yiyundao.compensation.security.DatabasePermissionService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/approval/workflows")
@RequiredArgsConstructor
public class ApprovalController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;

    private final ApprovalEngine approvalEngine;
    private final ApprovalStepService approvalStepService;
    private final ApprovalWorkflowMapper approvalWorkflowMapper;
    private final SysUserService sysUserService;
    private final AuditLogService auditLogService;
    private final DatabasePermissionService databasePermissionService;

    // ==================== 查询接口 ====================

    /**
     * 分页查询审批流程列表
     */
    @GetMapping
    @SecurityAnnotations.IsAuthenticated
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

        IPage<ApprovalWorkflow> pageParam = new Page<>(safePage(page), safeSize(size));

        QueryWrapper<ApprovalWorkflow> queryWrapper = new QueryWrapper<>();
        SysUser currentUser = resolveCurrentUser();
        if (currentUser == null || currentUser.getId() == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED, "未登录");
        }
        if (!databasePermissionService.hasCurrentRequestScope(currentUser.getId(), "ALL")) {
            restrictToReadableWorkflows(queryWrapper, currentUser.getId());
        }

        // 状态筛选
        if (StringUtils.hasText(status)) {
            queryWrapper.eq("status", parseApprovalStatus(status).getCode());
        }

        // 流程类型筛选
        if (StringUtils.hasText(workflowType)) {
            queryWrapper.eq("workflow_type", parseWorkflowType(workflowType).getCode());
        }

        // 关键词搜索（流程ID、流程名称、业务Key）
        if (StringUtils.hasText(keyword)) {
            String trimmedKeyword = keyword.trim();
            Long workflowId = parseWorkflowIdKeyword(trimmedKeyword);
            queryWrapper.and(w -> w
                    .like("workflow_name", trimmedKeyword)
                    .or()
                    .like("business_key", trimmedKeyword)
                    .or(workflowId != null)
                    .eq(workflowId != null, "id", workflowId));
        }

        // 时间范围筛选
        if (StringUtils.hasText(startDate)) {
            queryWrapper.ge("submit_time", parseDate(startDate, "开始日期").atStartOfDay());
        }
        if (StringUtils.hasText(endDate)) {
            queryWrapper.le("submit_time", parseDate(endDate, "结束日期").atTime(23, 59, 59));
        }

        // 排序
        if ("asc".equalsIgnoreCase(order)) {
            queryWrapper.orderByAsc("submit_time");
        } else {
            queryWrapper.orderByDesc("submit_time");
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
    @SecurityAnnotations.IsAuthenticated
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
    @SecurityAnnotations.IsAuthenticated
    public ApiResponse<PageResponse<ApprovalWorkflowVO>> getMy(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status) {

        SysUser currentUser = resolveCurrentUser();
        if (currentUser == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED, "未登录");
        }

        IPage<ApprovalWorkflow> pageParam = new Page<>(safePage(page), safeSize(size));
        LambdaQueryWrapper<ApprovalWorkflow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ApprovalWorkflow::getInitiatorId, currentUser.getId());

        if (StringUtils.hasText(status)) {
            queryWrapper.eq(ApprovalWorkflow::getStatus, parseApprovalStatus(status));
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
    @SecurityAnnotations.IsAuthenticated
    public ApiResponse<ApprovalWorkflowDetailVO> getDetail(@PathVariable Long id) {
        ApprovalWorkflow workflow = approvalEngine.getById(id);
        if (workflow == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "审批流程不存在");
        }
        SysUser currentUser = resolveCurrentUser();
        if (!canReadWorkflow(workflow, currentUser)) {
            return ApiResponse.error(ErrorCode.FORBIDDEN, "无权限查看该审批流程");
        }

        ApprovalWorkflowDetailVO detailVO = convertToDetailVO(workflow);

        // 获取审批步骤
        List<ApprovalStep> steps = approvalStepService.listByWorkflow(id);
        detailVO.setSteps(steps.stream()
                .map(ApprovalStepVO::from)
                .collect(Collectors.toList()));

        // 当前薪资流程使用 payroll_distribution 业务类型，旧实现只识别 payroll，导致详情页没有业务上下文。
        if (isPayrollBusiness(workflow.getBusinessType())) {
            Long businessId = parseBusinessId(workflow.getBusinessKey());
            if (businessId != null) {
                Map<String, Object> businessInfo = new LinkedHashMap<>();
                businessInfo.put("businessType", workflow.getBusinessType());
                businessInfo.put("status", workflow.getStatus() != null ? workflow.getStatus().getCode() : "");
                if ("payroll_distribution".equalsIgnoreCase(workflow.getBusinessType())) {
                    businessInfo.put("distributionId", String.valueOf(businessId));
                } else {
                    businessInfo.put("batchId", String.valueOf(businessId));
                }
                detailVO.setBusinessInfo(businessInfo);
            }
        }

        return ApiResponse.success(detailVO);
    }

    /**
     * 获取审批步骤列表
     */
    @GetMapping("/{id}/steps")
    @SecurityAnnotations.IsAuthenticated
    public ApiResponse<List<ApprovalStepVO>> getSteps(@PathVariable Long id) {
        ApprovalWorkflow workflow = approvalEngine.getById(id);
        if (workflow == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "审批流程不存在");
        }
        SysUser currentUser = resolveCurrentUser();
        if (!canReadWorkflow(workflow, currentUser)) {
            return ApiResponse.error(ErrorCode.FORBIDDEN, "无权限查看该审批流程");
        }
        List<ApprovalStep> steps = approvalStepService.listByWorkflow(id);
        List<ApprovalStepVO> voList = steps.stream()
                .map(ApprovalStepVO::from)
                .collect(Collectors.toList());
        return ApiResponse.success(voList);
    }

    // ==================== 审批操作接口 ====================

    @PostMapping("/{id}/approve")
    @SecurityAnnotations.IsAuthenticated
    public ApiResponse<Boolean> approve(@PathVariable Long id, @RequestBody(required = false) DecisionRequest req) {
        return decide(id, ApprovalStatus.APPROVED, req != null ? req.getComment() : null);
    }

    @PostMapping("/{id}/reject")
    @SecurityAnnotations.IsAuthenticated
    public ApiResponse<Boolean> reject(@PathVariable Long id, @RequestBody(required = false) DecisionRequest req) {
        return decide(id, ApprovalStatus.REJECTED, req != null ? req.getComment() : null);
    }

    @PostMapping("/{id}/cancel")
    @SecurityAnnotations.IsAuthenticated
    public ApiResponse<Boolean> cancel(@PathVariable Long id, @RequestBody(required = false) DecisionRequest req) {
        SysUser operator = resolveCurrentUser();
        if (operator == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED, "未登录");
        }

        long begin = System.currentTimeMillis();
        String reason = req != null ? req.getComment() : null;
        try {
            approvalEngine.cancelWorkflow(id, operator.getId(), reason);
        } catch (Exception e) {
            audit("审批撤销", operator.getUsername(), id.toString(), "APPROVAL", false, e.getMessage(), begin);
            return ApiResponse.error(resolveApprovalOperationError(e), e.getMessage());
        }

        ApprovalWorkflow wf = approvalEngine.getById(id);
        String detail = wf != null
                ? "businessKey=" + wf.getBusinessKey() + ",status=" + wf.getStatus() + ",type=" + wf.getBusinessType()
                : "status=cancelled";
        audit("审批撤销", operator.getUsername(), id.toString(), "APPROVAL", true, detail, begin);
        return ApiResponse.success(true);
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
            return ApiResponse.error(resolveApprovalOperationError(e), e.getMessage());
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

    private boolean isPayrollBusiness(String businessType) {
        return "payroll".equalsIgnoreCase(businessType)
                || "payroll_distribution".equalsIgnoreCase(businessType);
    }

    private Long parseBusinessId(String businessKey) {
        if (businessKey == null) return null;
        String[] segments = businessKey.split(":");
        if (segments.length < 2) return null;
        try { return Long.parseLong(segments[1]); }
        catch (Exception e) { return null; }
    }

    private Long parseWorkflowIdKeyword(String keyword) {
        if (!StringUtils.hasText(keyword) || !keyword.matches("\\d{1,18}")) {
            return null;
        }
        try {
            return Long.parseLong(keyword);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ApprovalStatus parseApprovalStatus(String status) {
        try {
            return ApprovalStatus.fromCode(status.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的审批状态: " + status);
        }
    }

    private WorkflowType parseWorkflowType(String workflowType) {
        try {
            return WorkflowType.fromCode(workflowType.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的流程类型: " + workflowType);
        }
    }

    private ErrorCode resolveApprovalOperationError(Exception ex) {
        if (ex instanceof BusinessException businessException && businessException.getErrorCode() != null) {
            return businessException.getErrorCode();
        }
        String message = ex != null ? ex.getMessage() : null;
        if (!StringUtils.hasText(message)) {
            return ErrorCode.BUSINESS_ERROR;
        }
        if (message.contains("审批流程不存在")) {
            return ErrorCode.RESOURCE_NOT_FOUND;
        }
        if (message.contains("无权限")
                || message.contains("只有发起人")
                || message.contains("发起人不能审批")) {
            return ErrorCode.FORBIDDEN;
        }
        if (message.contains("状态已变更") || message.contains("请刷新后重试")) {
            return ErrorCode.REQUEST_CONFLICT;
        }
        if (message.contains("不是待审批状态")
                || message.contains("只能撤销待审批")
                || message.contains("无效的审批决策")
                || message.contains("数据不完整")
                || message.contains("找不到当前审批步骤")
                || message.contains("找不到下一审批步骤")) {
            return ErrorCode.INVALID_STATUS;
        }
        return ErrorCode.BUSINESS_ERROR;
    }

    private LocalDate parseDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的" + fieldName + ": " + value);
        }
    }

    private int safePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int safeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private void restrictToReadableWorkflows(QueryWrapper<ApprovalWorkflow> queryWrapper, Long userId) {
        queryWrapper.and(w -> w
                .eq("initiator_id", userId)
                .or()
                .eq("current_approver_id", userId)
                .or()
                .inSql("id", "SELECT workflow_id FROM approval_step WHERE approver_id = " + userId + " AND deleted = 0"));
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

    private boolean canReadWorkflow(ApprovalWorkflow workflow, SysUser user) {
        if (workflow == null || user == null || user.getId() == null) {
            return false;
        }
        if (databasePermissionService.hasCurrentRequestScope(user.getId(), "ALL")) {
            return true;
        }
        if (user.getId().equals(workflow.getInitiatorId())) {
            return true;
        }
        if (user.getId().equals(workflow.getCurrentApproverId())) {
            return true;
        }
        List<ApprovalStep> steps = approvalStepService.listByWorkflow(workflow.getId());
        return steps.stream().anyMatch(step -> user.getId().equals(step.getApproverId()));
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
                vo.setInitiatorName(displayName(initiator));
            }
        }
        if (w.getCurrentApproverId() != null) {
            SysUser approver = sysUserService.getById(w.getCurrentApproverId());
            if (approver != null) {
                vo.setCurrentApproverName(displayName(approver));
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
                vo.setInitiatorName(displayName(initiator));
            }
        }

        // 填充当前审批人名称
        if (w.getCurrentApproverId() != null) {
            SysUser approver = sysUserService.getById(w.getCurrentApproverId());
            if (approver != null) {
                vo.setCurrentApproverName(displayName(approver));
            }
        }

        return vo;
    }

    private String displayName(SysUser user) {
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    @Data
    public static class DecisionRequest { private String comment; }
}
