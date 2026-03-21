package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollConfirmationMode;
import com.yiyundao.compensation.enums.PayrollConfirmationStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchConfirmRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollConfirmationAssignRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollConfirmationSummaryDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPendingConfirmationDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayslipConfirmRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayslipObjectionRequest;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollConfirmationAggregateService;
import com.yiyundao.compensation.modules.payroll.service.PayrollConfirmationService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.PayrollProcessManager;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollConfirmationServiceImpl implements PayrollConfirmationService {

    private static final String BUSINESS_TYPE_PAYROLL_DISPUTE = "payroll_dispute";
    private static final String DISPUTE_KEY_PREFIX = "payroll_dispute:line:";

    private final PayrollLineService payrollLineService;
    private final PayrollBatchService payrollBatchService;
    private final ApprovalEngine approvalEngine;
    private final SysUserService sysUserService;
    private final EmployeeService employeeService;
    private final UserRoleService userRoleService;
    private final ObjectMapper objectMapper;
    private final PayrollConfirmationAggregateService confirmationAggregateService;
    private final PayrollProcessManager payrollProcessManager;

    @Override
    @Transactional
    public void confirmPayslip(Long lineId, SysUser currentUser, PayslipConfirmRequest request) {
        SysUser operator = requireAuthenticated(currentUser);
        PayrollLine line = requireLine(lineId);
        PayrollBatch batch = requireBatch(line.getBatchId());
        ensureBatchConfirmable(batch);
        ensureLineOperableByCurrentUser(line, operator);
        confirmSingleLine(line, operator, request != null ? request.getSignature() : null, request != null ? request.getComment() : null);
        refreshBatchConfirmationStatus(batch.getId());
    }

    @Override
    @Transactional
    public Long objectPayslip(Long lineId, SysUser currentUser, PayslipObjectionRequest request) {
        SysUser operator = requireAuthenticated(currentUser);
        PayrollLine line = requireLine(lineId);
        PayrollBatch batch = requireBatch(line.getBatchId());
        ensureBatchConfirmable(batch);
        ensureLineOperableByCurrentUser(line, operator);
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "异议原因不能为空");
        }

        PayrollConfirmationStatus currentStatus = PayrollConfirmationStatus.fromCode(line.getConfirmationStatus());
        if (currentStatus == PayrollConfirmationStatus.OBJECTED && line.getDisputeWorkflowId() != null) {
            return line.getDisputeWorkflowId();
        }

        Map<String, Object> workflowData = new LinkedHashMap<>();
        workflowData.put("lineId", line.getId());
        workflowData.put("batchId", line.getBatchId());
        workflowData.put("employeeId", line.getEmployeeId());
        workflowData.put("objectionReason", request.getReason().trim());
        if (StringUtils.hasText(request.getComment())) {
            workflowData.put("objectionComment", request.getComment().trim());
        }

        Long workflowId = approvalEngine.startWorkflow(
                WorkflowType.PAYROLL_DISPUTE,
                buildDisputeBusinessKey(line.getId()),
                BUSINESS_TYPE_PAYROLL_DISPUTE,
                operator.getId(),
                workflowData
        );

        line.setConfirmationStatus(PayrollConfirmationStatus.OBJECTED.getCode());
        line.setObjectionReason(request.getReason().trim());
        line.setObjectionAt(LocalDateTime.now());
        line.setDisputeWorkflowId(workflowId);
        line.setConfirmedByUserId(null);
        line.setConfirmedByEmployeeId(null);
        line.setConfirmedAt(null);
        line.setConfirmationComment(null);
        payrollLineService.updateById(line);

        refreshBatchConfirmationStatus(batch.getId());
        return workflowId;
    }

    @Override
    @Transactional
    public int batchConfirm(Long batchId, SysUser currentUser, PayrollBatchConfirmRequest request) {
        SysUser operator = requireAuthenticated(currentUser);
        PayrollBatch batch = requireBatch(batchId);
        ensureBatchConfirmable(batch);

        List<PayrollLine> candidates = loadBatchConfirmCandidates(batchId, request);
        if (candidates.isEmpty()) {
            return 0;
        }

        int affected = 0;
        for (PayrollLine line : candidates) {
            if (!isElevatedOperator(operator) && !isAssignee(line, operator.getEmployeeId())) {
                continue;
            }
            if (confirmSingleLine(line, operator, request != null ? request.getSignature() : null,
                    request != null ? request.getComment() : null)) {
                affected++;
            }
        }

        if (affected > 0) {
            refreshBatchConfirmationStatus(batchId);
        }
        return affected;
    }

    @Override
    @Transactional
    public int assignConfirmationAssignee(Long batchId, SysUser currentUser, PayrollConfirmationAssignRequest request) {
        SysUser operator = requireAuthenticated(currentUser);
        if (!isFinanceOrAdmin(operator)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "仅财务或管理员可分配确认负责人");
        }
        if (request == null || request.getAssigneeEmployeeId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "负责人员工ID不能为空");
        }
        if (sysUserService.findByEmployeeId(request.getAssigneeEmployeeId()) == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "负责人未绑定系统账号，无法执行确认");
        }

        PayrollBatch batch = requireBatch(batchId);
        ensureBatchConfirmable(batch);
        List<PayrollLine> lines = resolveAssignableLines(batchId, request);
        if (lines.isEmpty()) {
            return 0;
        }

        int affected = 0;
        for (PayrollLine line : lines) {
            PayrollConfirmationStatus status = PayrollConfirmationStatus.fromCode(line.getConfirmationStatus());
            if (status.isFinalForPayment()) {
                continue;
            }
            line.setConfirmationAssigneeEmployeeId(request.getAssigneeEmployeeId());
            payrollLineService.updateById(line);
            affected++;
        }
        if (affected > 0) {
            batch.setConfirmationMode(PayrollConfirmationMode.GROUP.getCode());
            batch.setConfirmationCompletedTime(null);
            if (batch.getStatus() == PayrollBatchStatus.CONFIRMED) {
                batch.setStatus(PayrollBatchStatus.CONFIRMING);
            }
            payrollBatchService.updateById(batch);
        }
        return affected;
    }

    @Override
    public PayrollConfirmationSummaryDto getBatchSummary(Long batchId) {
        PayrollBatch batch = requireBatch(batchId);
        List<PayrollLine> lines = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batchId));
        PayrollConfirmationStats stats = calcStats(lines);

        PayrollConfirmationSummaryDto dto = new PayrollConfirmationSummaryDto();
        dto.setBatchId(batchId);
        dto.setBatchStatus(batch.getStatus() != null ? batch.getStatus().getCode() : null);
        dto.setConfirmationMode(batch.getConfirmationMode());
        dto.setTotalLines(stats.total);
        dto.setPendingCount(stats.pending);
        dto.setConfirmedCount(stats.confirmed);
        dto.setObjectedCount(stats.objected);
        dto.setObjectedApprovedCount(stats.objectedApproved);
        dto.setObjectedRejectedCount(stats.objectedRejected);
        return dto;
    }

    @Override
    public Page<PayrollPendingConfirmationDto> pagePendingConfirmations(SysUser currentUser, Long batchId, int page, int size) {
        SysUser operator = requireAuthenticated(currentUser);
        long current = Math.max(page, 1);
        long pageSize = Math.max(size, 1);

        LambdaQueryWrapper<PayrollLine> wrapper = new LambdaQueryWrapper<PayrollLine>()
                .and(w -> w
                        .isNull(PayrollLine::getConfirmationStatus)
                        .or()
                        .eq(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.PENDING.getCode())
                        .or()
                        .eq(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.OBJECTED_REJECTED.getCode()))
                .orderByAsc(PayrollLine::getBatchId)
                .orderByAsc(PayrollLine::getId);
        if (batchId != null) {
            wrapper.eq(PayrollLine::getBatchId, batchId);
        }
        if (!isElevatedOperator(operator)) {
            if (operator.getEmployeeId() == null) {
                return new Page<>(current, pageSize, 0);
            }
            wrapper.and(w -> w
                    .eq(PayrollLine::getConfirmationAssigneeEmployeeId, operator.getEmployeeId())
                    .or(x -> x
                            .isNull(PayrollLine::getConfirmationAssigneeEmployeeId)
                            .eq(PayrollLine::getEmployeeId, operator.getEmployeeId())));
        }

        Page<PayrollLine> linePage = payrollLineService.page(new Page<>(current, pageSize), wrapper);
        Page<PayrollPendingConfirmationDto> result = new Page<>(current, pageSize, linePage.getTotal());
        if (CollectionUtils.isEmpty(linePage.getRecords())) {
            result.setRecords(List.of());
            return result;
        }

        Set<Long> employeeIds = linePage.getRecords().stream()
                .map(PayrollLine::getEmployeeId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, Employee> employeeMap = employeeIds.isEmpty()
                ? Map.of()
                : employeeService.listByIds(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        Set<Long> batchIds = linePage.getRecords().stream()
                .map(PayrollLine::getBatchId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, PayrollBatch> batchMap = batchIds.isEmpty()
                ? Map.of()
                : payrollBatchService.listByIds(batchIds).stream()
                .collect(Collectors.toMap(PayrollBatch::getId, b -> b));

        List<PayrollPendingConfirmationDto> records = linePage.getRecords().stream()
                .map(line -> toPendingDto(line, employeeMap.get(line.getEmployeeId()), batchMap.get(line.getBatchId())))
                .toList();
        result.setRecords(records);
        return result;
    }

    @Override
    @Transactional
    public void handleDisputeWorkflowCompleted(ApprovalWorkflow workflow, ApprovalStatus finalStatus) {
        if (workflow == null || finalStatus == null) {
            return;
        }
        if (workflow.getWorkflowType() != WorkflowType.PAYROLL_DISPUTE &&
                !BUSINESS_TYPE_PAYROLL_DISPUTE.equalsIgnoreCase(workflow.getBusinessType())) {
            return;
        }

        Long lineId = parseLineId(workflow);
        if (lineId == null) {
            log.warn("无法解析异议对应工资行: workflowId={}, businessKey={}", workflow.getId(), workflow.getBusinessKey());
            return;
        }

        PayrollLine line = payrollLineService.getById(lineId);
        if (line == null) {
            return;
        }
        if (line.getDisputeWorkflowId() != null && !workflow.getId().equals(line.getDisputeWorkflowId())) {
            log.info("忽略过期异议回调: workflowId={}, lineId={}, currentWorkflowId={}",
                    workflow.getId(), lineId, line.getDisputeWorkflowId());
            return;
        }

        if (finalStatus == ApprovalStatus.APPROVED) {
            line.setConfirmationStatus(PayrollConfirmationStatus.OBJECTED_APPROVED.getCode());
            line.setConfirmationComment("异议审批通过");
        } else if (finalStatus == ApprovalStatus.REJECTED || finalStatus == ApprovalStatus.CANCELLED) {
            line.setConfirmationStatus(PayrollConfirmationStatus.OBJECTED_REJECTED.getCode());
            line.setConfirmationComment("异议审批未通过，请重新确认");
        } else {
            return;
        }
        payrollLineService.updateById(line);
        refreshBatchConfirmationStatus(line.getBatchId());
    }

    @Override
    @Transactional
    public void refreshBatchConfirmationStatus(Long batchId) {
        PayrollBatch batch = payrollBatchService.getById(batchId);
        if (batch == null) {
            return;
        }

        if (Boolean.FALSE.equals(batch.getConfirmationRequired())) {
            confirmationAggregateService.syncFromLegacyBatch(batchId, batch.getBatchRevision());
            payrollProcessManager.onConfirmationCompleted(batchId, batch.getBatchRevision());
            return;
        }

        List<PayrollLine> lines = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batchId));
        PayrollConfirmationStats stats = calcStats(lines);
        PayrollBatchStatus targetStatus;
        LocalDateTime completedTime = null;

        if (stats.total == 0) {
            targetStatus = PayrollBatchStatus.CONFIRMING;
        } else if (stats.objected > 0) {
            targetStatus = PayrollBatchStatus.DISPUTE_PROCESSING;
        } else if (stats.pending > 0 || stats.objectedRejected > 0) {
            targetStatus = PayrollBatchStatus.CONFIRMING;
        } else {
            targetStatus = PayrollBatchStatus.CONFIRMED;
            completedTime = LocalDateTime.now();
        }

        batch.setStatus(targetStatus);
        batch.setConfirmationCompletedTime(completedTime);
        payrollBatchService.updateById(batch);

        confirmationAggregateService.syncFromLegacyBatch(batchId, batch.getBatchRevision());
        if (targetStatus == PayrollBatchStatus.CONFIRMED) {
            payrollProcessManager.onConfirmationCompleted(batchId, batch.getBatchRevision());
        }
    }

    private boolean confirmSingleLine(PayrollLine line, SysUser operator, String signature, String comment) {
        if (line == null || operator == null) {
            return false;
        }
        PayrollConfirmationStatus status = PayrollConfirmationStatus.fromCode(line.getConfirmationStatus());
        if (status.isFinalForPayment()) {
            return false;
        }
        if (status == PayrollConfirmationStatus.OBJECTED && line.getDisputeWorkflowId() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "该工资条存在处理中异议，暂不可确认");
        }

        line.setConfirmationStatus(PayrollConfirmationStatus.CONFIRMED.getCode());
        line.setConfirmedByUserId(operator.getId());
        line.setConfirmedByEmployeeId(operator.getEmployeeId());
        line.setConfirmedAt(LocalDateTime.now());
        line.setConfirmationComment(buildComment(signature, comment, operator));
        line.setObjectionReason(null);
        line.setObjectionAt(null);
        line.setDisputeWorkflowId(null);
        return payrollLineService.updateById(line);
    }

    private List<PayrollLine> loadBatchConfirmCandidates(Long batchId, PayrollBatchConfirmRequest request) {
        LambdaQueryWrapper<PayrollLine> wrapper = new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batchId)
                .and(w -> w
                        .eq(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.PENDING.getCode())
                        .or()
                        .eq(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.OBJECTED_REJECTED.getCode())
                        .or()
                        .isNull(PayrollLine::getConfirmationStatus))
                .orderByAsc(PayrollLine::getId);
        if (request != null && !CollectionUtils.isEmpty(request.getLineIds())) {
            wrapper.in(PayrollLine::getId, request.getLineIds());
        }
        return payrollLineService.list(wrapper);
    }

    private List<PayrollLine> resolveAssignableLines(Long batchId, PayrollConfirmationAssignRequest request) {
        LambdaQueryWrapper<PayrollLine> wrapper = new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batchId)
                .orderByAsc(PayrollLine::getId);
        if (!CollectionUtils.isEmpty(request.getLineIds())) {
            wrapper.in(PayrollLine::getId, request.getLineIds());
            return payrollLineService.list(wrapper);
        }
        if (!CollectionUtils.isEmpty(request.getEmployeeIds())) {
            wrapper.in(PayrollLine::getEmployeeId, request.getEmployeeIds());
            return payrollLineService.list(wrapper);
        }
        if (Boolean.TRUE.equals(request.getApplyAll())) {
            return payrollLineService.list(wrapper);
        }
        throw new BusinessException(ErrorCode.PARAM_INVALID, "请选择 lineIds / employeeIds / applyAll 中的一种分配方式");
    }

    private void ensureBatchConfirmable(PayrollBatch batch) {
        if (batch == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "薪酬批次不存在");
        }
        if (Boolean.FALSE.equals(batch.getConfirmationRequired())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前批次未启用确认流程");
        }
        PayrollBatchStatus status = batch.getStatus();
        if (status == null) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "批次状态异常");
        }
        if (status == PayrollBatchStatus.SUBMITTED
                || status == PayrollBatchStatus.APPROVED
                || status == PayrollBatchStatus.PAY_PROCESSING
                || status == PayrollBatchStatus.PAY_FAILED
                || status == PayrollBatchStatus.PAID
                || status == PayrollBatchStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前批次已进入发放阶段，不能再确认或提异议");
        }
    }

    private void ensureLineOperableByCurrentUser(PayrollLine line, SysUser currentUser) {
        if (line == null || currentUser == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限操作");
        }
        if (isElevatedOperator(currentUser)) {
            return;
        }
        if (!isAssignee(line, currentUser.getEmployeeId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "仅确认负责人可操作该工资条");
        }
    }

    private boolean isAssignee(PayrollLine line, Long employeeId) {
        if (line == null || employeeId == null) {
            return false;
        }
        Long assignee = line.getConfirmationAssigneeEmployeeId() != null
                ? line.getConfirmationAssigneeEmployeeId()
                : line.getEmployeeId();
        return employeeId.equals(assignee);
    }

    private boolean isElevatedOperator(SysUser user) {
        return isFinanceOrAdmin(user) || hasRole(user, SecurityConstants.ROLE_HR);
    }

    private boolean isFinanceOrAdmin(SysUser user) {
        return hasRole(user, SecurityConstants.ROLE_FINANCE) || hasRole(user, SecurityConstants.ROLE_ADMIN);
    }

    private boolean hasRole(SysUser user, String roleCode) {
        if (user == null || user.getId() == null || !StringUtils.hasText(roleCode)) {
            return false;
        }
        return userRoleService.hasRole(user.getId(), roleCode);
    }

    private String buildComment(String signature, String comment, SysUser operator) {
        String signedBy = StringUtils.hasText(signature)
                ? signature.trim()
                : (StringUtils.hasText(operator.getRealName()) ? operator.getRealName() : operator.getUsername());
        if (!StringUtils.hasText(comment)) {
            return "签字：" + signedBy;
        }
        return "签字：" + signedBy + "；备注：" + comment.trim();
    }

    private String buildDisputeBusinessKey(Long lineId) {
        return DISPUTE_KEY_PREFIX + lineId;
    }

    private Long parseLineId(ApprovalWorkflow workflow) {
        Long fromData = parseLineIdFromData(workflow != null ? workflow.getWorkflowData() : null);
        if (fromData != null) {
            return fromData;
        }
        if (workflow == null || !StringUtils.hasText(workflow.getBusinessKey())) {
            return null;
        }
        String key = workflow.getBusinessKey().trim().toLowerCase(Locale.ROOT);
        if (!key.startsWith(DISPUTE_KEY_PREFIX)) {
            return null;
        }
        String idPart = key.substring(DISPUTE_KEY_PREFIX.length());
        try {
            return Long.parseLong(idPart);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLineIdFromData(String workflowData) {
        if (!StringUtils.hasText(workflowData)) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(workflowData, new TypeReference<Map<String, Object>>() {});
            Object raw = map.get("lineId");
            if (raw instanceof Number number) {
                return number.longValue();
            }
            if (raw != null) {
                return Long.parseLong(String.valueOf(raw));
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private PayrollPendingConfirmationDto toPendingDto(PayrollLine line, Employee employee, PayrollBatch batch) {
        PayrollPendingConfirmationDto dto = new PayrollPendingConfirmationDto();
        dto.setLineId(line.getId());
        dto.setBatchId(line.getBatchId());
        dto.setPeriodLabel(batch != null ? batch.getPeriodLabel() : null);
        dto.setEmployeeId(line.getEmployeeId());
        dto.setNetAmount(line.getNetAmount());
        dto.setCurrency(line.getCurrency());
        dto.setConfirmationStatus(line.getConfirmationStatus());
        if (employee != null) {
            dto.setEmployeeNo(employee.getEmployeeId());
            dto.setEmployeeName(employee.getName());
            dto.setDepartment(employee.getDepartment());
        }
        return dto;
    }

    private PayrollLine requireLine(Long lineId) {
        PayrollLine line = payrollLineService.getById(lineId);
        if (line == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工资条不存在");
        }
        return line;
    }

    private PayrollBatch requireBatch(Long batchId) {
        PayrollBatch batch = payrollBatchService.getById(batchId);
        if (batch == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "薪酬批次不存在");
        }
        return batch;
    }

    private SysUser requireAuthenticated(SysUser currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return currentUser;
    }

    private PayrollConfirmationStats calcStats(List<PayrollLine> lines) {
        PayrollConfirmationStats stats = new PayrollConfirmationStats();
        if (CollectionUtils.isEmpty(lines)) {
            return stats;
        }
        for (PayrollLine line : lines) {
            stats.total++;
            PayrollConfirmationStatus status = PayrollConfirmationStatus.fromCode(line.getConfirmationStatus());
            switch (status) {
                case CONFIRMED -> stats.confirmed++;
                case OBJECTED -> stats.objected++;
                case OBJECTED_APPROVED -> stats.objectedApproved++;
                case OBJECTED_REJECTED -> stats.objectedRejected++;
                default -> stats.pending++;
            }
        }
        return stats;
    }

    private static class PayrollConfirmationStats {
        long total;
        long pending;
        long confirmed;
        long objected;
        long objectedApproved;
        long objectedRejected;
    }
}
