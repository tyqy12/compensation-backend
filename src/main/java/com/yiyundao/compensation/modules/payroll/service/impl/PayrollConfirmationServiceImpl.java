package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollConfirmationServiceImpl implements PayrollConfirmationService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;

    private static final String BUSINESS_TYPE_PAYROLL_DISPUTE = "payroll_dispute";
    private static final String DISPUTE_KEY_PREFIX = "payroll_dispute:line:";
    private static final List<PayrollBatchStatus> CONFIRMATION_WINDOW_STATUSES = List.of(
            PayrollBatchStatus.CONFIRMING,
            PayrollBatchStatus.DISPUTE_PROCESSING,
            PayrollBatchStatus.CONFIRMED
    );
    private static final String CONFIRMATION_WINDOW_BATCH_EXISTS_SQL =
            "SELECT 1 FROM payroll_batch pb"
                    + " WHERE pb.id = payroll_line.batch_id"
                    + " AND pb.deleted = 0"
                    + " AND COALESCE(pb.confirmation_required, 1) = 1"
                    + " AND pb.status IN (" + sqlStatusCodes(CONFIRMATION_WINDOW_STATUSES) + ")";

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
        boolean confirmed = confirmSingleLine(line, operator,
                request != null ? request.getSignature() : null,
                request != null ? request.getComment() : null);
        if (!confirmed) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "工资条确认状态已变更，请刷新后重试");
        }
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
        if (currentStatus.isFinalForPayment()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "该工资条已完成确认，不能再提异议");
        }

        Map<String, Object> workflowData = new LinkedHashMap<>();
        workflowData.put("lineId", line.getId());
        workflowData.put("batchId", line.getBatchId());
        workflowData.put("batchRevision", normalizeRevision(batch.getBatchRevision()));
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

        LocalDateTime objectionAt = LocalDateTime.now();
        boolean updated = payrollLineService.update(new LambdaUpdateWrapper<PayrollLine>()
                .eq(PayrollLine::getId, line.getId())
                .and(w -> w
                        .isNull(PayrollLine::getConfirmationStatus)
                        .or()
                        .eq(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.PENDING.getCode())
                        .or()
                        .eq(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.OBJECTED_REJECTED.getCode()))
                .set(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.OBJECTED.getCode())
                .set(PayrollLine::getObjectionReason, request.getReason().trim())
                .set(PayrollLine::getObjectionAt, objectionAt)
                .set(PayrollLine::getDisputeWorkflowId, workflowId)
                .set(PayrollLine::getConfirmedByUserId, null)
                .set(PayrollLine::getConfirmedByEmployeeId, null)
                .set(PayrollLine::getConfirmedAt, null)
                .set(PayrollLine::getConfirmationComment, null));
        if (!updated) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "工资条确认状态已变更，请刷新后重试");
        }
        line.setConfirmationStatus(PayrollConfirmationStatus.OBJECTED.getCode());
        line.setObjectionReason(request.getReason().trim());
        line.setObjectionAt(objectionAt);
        line.setDisputeWorkflowId(workflowId);
        line.setConfirmedByUserId(null);
        line.setConfirmedByEmployeeId(null);
        line.setConfirmedAt(null);
        line.setConfirmationComment(null);

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
            if (!canOperateAnyConfirmationLine(operator) && !isAssignee(line, operator.getEmployeeId())) {
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
            boolean updated = payrollLineService.update(new LambdaUpdateWrapper<PayrollLine>()
                    .eq(PayrollLine::getId, line.getId())
                    .and(wrapper -> wrapper
                            .isNull(PayrollLine::getConfirmationStatus)
                            .or()
                            .eq(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.PENDING.getCode())
                            .or()
                            .eq(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.OBJECTED.getCode())
                            .or()
                            .eq(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.OBJECTED_REJECTED.getCode()))
                    .set(PayrollLine::getConfirmationAssigneeEmployeeId, request.getAssigneeEmployeeId()));
            if (updated) {
                affected++;
            }
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
        long current = safePage(page);
        long pageSize = safeSize(size);

        LambdaQueryWrapper<PayrollLine> wrapper = new LambdaQueryWrapper<PayrollLine>()
                .exists(CONFIRMATION_WINDOW_BATCH_EXISTS_SQL)
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
        if (!canViewAllPendingConfirmations(operator)) {
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

    private int safePage(int page) {
        return page < 1 ? 1 : page;
    }

    private int safeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
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
        if (!Objects.equals(workflow.getId(), line.getDisputeWorkflowId())) {
            log.info("忽略过期异议回调: workflowId={}, lineId={}, currentWorkflowId={}",
                    workflow.getId(), lineId, line.getDisputeWorkflowId());
            return;
        }
        PayrollBatch batch = payrollBatchService.getById(line.getBatchId());
        if (batch == null || !matchesCurrentBatchRevision(workflow, batch)) {
            log.info("忽略过期异议回调: workflowId={}, lineId={}, batchId={}, currentRevision={}, workflowRevision={}",
                    workflow.getId(), lineId, line.getBatchId(),
                    batch != null ? batch.getBatchRevision() : null,
                    parseBatchRevisionFromData(workflow.getWorkflowData()));
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
        boolean updated = payrollLineService.update(new LambdaUpdateWrapper<PayrollLine>()
                .eq(PayrollLine::getId, line.getId())
                .eq(PayrollLine::getDisputeWorkflowId, workflow.getId())
                .eq(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.OBJECTED.getCode())
                .set(PayrollLine::getConfirmationStatus, line.getConfirmationStatus())
                .set(PayrollLine::getConfirmationComment, line.getConfirmationComment()));
        if (!updated) {
            log.info("忽略已流转异议回调: workflowId={}, lineId={}", workflow.getId(), lineId);
            return;
        }
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
            if (canMarkConfirmationSkipped(batch.getStatus())) {
                batch.setStatus(PayrollBatchStatus.CONFIRMED);
                if (batch.getConfirmationCompletedTime() == null) {
                    batch.setConfirmationCompletedTime(LocalDateTime.now());
                }
                payrollBatchService.updateById(batch);
            }
            confirmationAggregateService.syncFromLegacyBatch(batchId, batch.getBatchRevision());
            if (batch.getStatus() == PayrollBatchStatus.CONFIRMED) {
                payrollProcessManager.onConfirmationCompleted(batchId, batch.getBatchRevision());
            }
            return;
        }
        if (!canRefreshConfirmationStatus(batch.getStatus())) {
            confirmationAggregateService.syncFromLegacyBatch(batchId, batch.getBatchRevision());
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

    private boolean canMarkConfirmationSkipped(PayrollBatchStatus status) {
        return status == PayrollBatchStatus.LOCKED
                || status == PayrollBatchStatus.CONFIRMING
                || status == PayrollBatchStatus.DISPUTE_PROCESSING
                || status == PayrollBatchStatus.CONFIRMED;
    }

    private boolean canRefreshConfirmationStatus(PayrollBatchStatus status) {
        return status == PayrollBatchStatus.LOCKED
                || status == PayrollBatchStatus.CONFIRMING
                || status == PayrollBatchStatus.DISPUTE_PROCESSING
                || status == PayrollBatchStatus.CONFIRMED;
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

        LocalDateTime confirmedAt = LocalDateTime.now();
        String confirmationComment = buildComment(signature, comment, operator);
        boolean updated = payrollLineService.update(new LambdaUpdateWrapper<PayrollLine>()
                .eq(PayrollLine::getId, line.getId())
                .and(w -> w
                        .isNull(PayrollLine::getConfirmationStatus)
                        .or()
                        .eq(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.PENDING.getCode())
                        .or()
                        .eq(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.OBJECTED_REJECTED.getCode()))
                .set(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.CONFIRMED.getCode())
                .set(PayrollLine::getConfirmedByUserId, operator.getId())
                .set(PayrollLine::getConfirmedByEmployeeId, operator.getEmployeeId())
                .set(PayrollLine::getConfirmedAt, confirmedAt)
                .set(PayrollLine::getConfirmationComment, confirmationComment)
                .set(PayrollLine::getObjectionReason, null)
                .set(PayrollLine::getObjectionAt, null)
                .set(PayrollLine::getDisputeWorkflowId, null));
        if (updated) {
            line.setConfirmationStatus(PayrollConfirmationStatus.CONFIRMED.getCode());
            line.setConfirmedByUserId(operator.getId());
            line.setConfirmedByEmployeeId(operator.getEmployeeId());
            line.setConfirmedAt(confirmedAt);
            line.setConfirmationComment(confirmationComment);
            line.setObjectionReason(null);
            line.setObjectionAt(null);
            line.setDisputeWorkflowId(null);
        }
        return updated;
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
        if (!CONFIRMATION_WINDOW_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前批次不在员工确认阶段，不能确认或提异议");
        }
    }

    private void ensureLineOperableByCurrentUser(PayrollLine line, SysUser currentUser) {
        if (line == null || currentUser == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限操作");
        }
        if (canOperateAnyConfirmationLine(currentUser)) {
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

    private boolean canOperateAnyConfirmationLine(SysUser user) {
        return isFinanceOrAdmin(user);
    }

    private boolean canViewAllPendingConfirmations(SysUser user) {
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

    private static String sqlStatusCodes(List<PayrollBatchStatus> statuses) {
        return statuses.stream()
                .map(status -> "'" + status.getCode() + "'")
                .collect(Collectors.joining(","));
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
        return DISPUTE_KEY_PREFIX + lineId + "-" + System.currentTimeMillis();
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
        int suffixIndex = idPart.indexOf('-');
        if (suffixIndex >= 0) {
            idPart = idPart.substring(0, suffixIndex);
        }
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

    private boolean matchesCurrentBatchRevision(ApprovalWorkflow workflow, PayrollBatch batch) {
        Integer workflowRevision = parseBatchRevisionFromData(workflow != null ? workflow.getWorkflowData() : null);
        if (workflowRevision == null) {
            return true;
        }
        return normalizeRevision(batch != null ? batch.getBatchRevision() : null) == normalizeRevision(workflowRevision);
    }

    private Integer parseBatchRevisionFromData(String workflowData) {
        if (!StringUtils.hasText(workflowData)) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(workflowData, new TypeReference<Map<String, Object>>() {});
            Object raw = map.get("batchRevision");
            if (raw instanceof Number number) {
                return number.intValue();
            }
            if (raw != null) {
                return Integer.parseInt(String.valueOf(raw));
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private int normalizeRevision(Integer revision) {
        return revision == null || revision < 1 ? 1 : revision;
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
