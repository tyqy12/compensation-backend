package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.PayrollConfirmationStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollApprovalProjection;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollValidationIssueDto;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollApprovalProjectionService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollConfirmationAggregateService;
import com.yiyundao.compensation.modules.payroll.service.PayrollDistributionService;
import com.yiyundao.compensation.modules.payroll.service.PayrollProcessManager;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentService;
import com.yiyundao.compensation.modules.payroll.support.PayrollValidationIssueSupport;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollCalculationStatus;
import com.yiyundao.compensation.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollBatchServiceImpl extends ServiceImpl<PayrollBatchMapper, PayrollBatch> implements PayrollBatchService {

    private final ApprovalEngine approvalEngine;
    private final SysUserService sysUserService;
    private final PayrollLineService payrollLineService;
    private final PayrollPaymentService payrollPaymentService;
    private final UserRoleService userRoleService;
    private final AuditLogService auditLogService;
    private final PayrollValidationIssueSupport validationIssueSupport;
    private final PayrollConfirmationAggregateService confirmationAggregateService;
    private final PayrollDistributionService distributionService;
    private final PayrollApprovalProjectionService approvalProjectionService;
    private final PayrollProcessManager payrollProcessManager;

    @Override
    @Transactional
    public boolean lockBatch(Long batchId) {
        log.info("Lock payroll batch: {}", batchId);
        PayrollBatch b = getById(batchId);
        if (b == null) return false;
        if (b.getStatus() != PayrollBatchStatus.DRAFT) return false;
        int batchRevision = b.getBatchRevision() == null || b.getBatchRevision() < 1 ? 1 : b.getBatchRevision();
        return update(new LambdaUpdateWrapper<PayrollBatch>()
                .eq(PayrollBatch::getId, batchId)
                .set(PayrollBatch::getStatus, PayrollBatchStatus.LOCKED)
                .set(PayrollBatch::getCalculationStatus, PayrollCalculationStatus.LOCKED)
                .set(PayrollBatch::getBatchRevision, batchRevision));
    }

    @Override
@Transactional
public boolean submitForApproval(Long batchId) {
    log.info("Submit payroll batch for approval: {}", batchId);
    PayrollBatch b = getById(batchId);
    if (b == null) {
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
    }

    int batchRevision = b.getBatchRevision() == null || b.getBatchRevision() < 1 ? 1 : b.getBatchRevision();
    if (b.getBatchRevision() == null || b.getBatchRevision() < 1) {
        b.setBatchRevision(batchRevision);
        updateById(b);
    }

    if (!isReadyForApproval(b)) {
        throw new BusinessException(ErrorCode.INVALID_STATUS, buildSubmitBlockedStatusMessage(b));
    }

    confirmationAggregateService.syncFromLegacyBatch(batchId, batchRevision);
    if (!confirmationAggregateService.isCompletedForApproval(batchId, batchRevision)) {
        log.warn("批次确认聚合未完成，禁止提交审批: batchId={}, batchRevision={}", batchId, batchRevision);
        throw new BusinessException(ErrorCode.INVALID_STATUS, "还有员工待确认或异议未处理，暂不可提交审批");
    }

    java.util.List<String> blockingMessages = collectBlockingIssueMessages(batchId);
    if (!blockingMessages.isEmpty()) {
        throw new BusinessException(
                ErrorCode.VALIDATION_FAILED,
                "存在阻塞问题，暂不可提交审批：" + String.join("；", blockingMessages)
        );
    }

    PayrollDistribution distribution = distributionService.createOrReuseForBatch(b);
    if (distribution == null) {
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "发放单创建失败");
    }

    PayrollApprovalProjection existingProjection = approvalProjectionService.getByDistributionId(distribution.getId());
    if (distribution.getApprovalWorkflowId() != null && existingProjection != null) {
        String businessStatus = existingProjection.getBusinessStatus();
        if ("IN_PROGRESS".equalsIgnoreCase(businessStatus) || "APPROVED".equalsIgnoreCase(businessStatus)) {
            return true;
        }
    }

    SysUser currentUser = resolveCurrentUser();
    Long initiatorId = currentUser != null ? currentUser.getId() : 1L;

    if (hasAdminRole(currentUser)) {
        log.info("Admin user bypass approval, batch={}, username={}", batchId,
                currentUser != null ? currentUser.getUsername() : "unknown");
        recordAdminBypassAudit(currentUser, b, "APPROVAL_BYPASS");

        Long pseudoWorkflowId = -distribution.getId();
        distributionService.bindApprovalWorkflow(distribution.getId(), pseudoWorkflowId);
        approvalProjectionService.createOrUpdatePending(b, distribution, pseudoWorkflowId, initiatorId);
        approvalProjectionService.markApproved(pseudoWorkflowId, initiatorId);

        boolean updated = update(new LambdaUpdateWrapper<PayrollBatch>()
                .eq(PayrollBatch::getId, batchId)
                .set(PayrollBatch::getStatus, PayrollBatchStatus.APPROVED)
                .set(PayrollBatch::getApprovalWorkflowId, null)
                .set(PayrollBatch::getUpdateBy, currentUser != null ? currentUser.getUsername() : "system"));
        if (updated) {
            payrollProcessManager.onApprovalApproved(distribution.getId(), pseudoWorkflowId, initiatorId);
        }
        return updated;
    }

    Long wfId;
    try {
        wfId = approvalEngine.startWorkflow(
                WorkflowType.PAYROLL_DISTRIBUTION,
                "payroll_distribution:" + distribution.getId(),
                "payroll_distribution",
                initiatorId,
                java.util.Map.of(
                        "batchId", batchId,
                        "batchRevision", batchRevision,
                        "distributionId", distribution.getId()
                )
        );
        distributionService.bindApprovalWorkflow(distribution.getId(), wfId);
        approvalProjectionService.createOrUpdatePending(b, distribution, wfId, initiatorId);
        return update(new LambdaUpdateWrapper<PayrollBatch>()
                .eq(PayrollBatch::getId, batchId)
                .set(PayrollBatch::getStatus, PayrollBatchStatus.SUBMITTED)
                .set(PayrollBatch::getApprovalWorkflowId, wfId));
    } catch (Exception e) {
        log.warn("submitForApproval failed to start workflow, batchId={}, error={}",
                batchId, e.getMessage());
        recordWorkflowStartFailureAudit(currentUser, b, e.getMessage());
        throw new RuntimeException("启动审批流程失败: " + e.getMessage(), e);
    }
}

    @Override
    @Transactional
    public boolean updateStatus(Long batchId, String status) {
        return update(new LambdaUpdateWrapper<PayrollBatch>()
                .eq(PayrollBatch::getId, batchId)
                .set(PayrollBatch::getStatus, status));
    }

    private SysUser resolveCurrentUser() {
        try {
            String username = SecurityUtilsInternal.currentUsername();
            if (!StringUtils.hasText(username)) {
                return sysUserService.findByUsername("admin");
            }
            return sysUserService.findByUsername(username);
        } catch (Exception e) {
            return sysUserService.findByUsername("admin");
        }
    }

    private boolean hasAdminRole(SysUser user) {
        if (user == null) {
            return false;
        }
        // 使用 UserRoleService 检查角色（带缓存）
        return userRoleService.hasRole(user.getId(), SecurityConstants.ROLE_ADMIN);
    }

    private boolean isReadyForApproval(PayrollBatch batch) {
        if (batch == null || batch.getStatus() == null) {
            return false;
        }
        PayrollBatchStatus status = batch.getStatus();
        if (status == PayrollBatchStatus.CONFIRMED) {
            return true;
        }
        // 兼容历史数据：未启用确认机制时仍允许从 LOCKED 进入审批
        return status == PayrollBatchStatus.LOCKED && Boolean.FALSE.equals(batch.getConfirmationRequired());
    }

    private boolean hasPendingOrRejectedConfirmations(Long batchId, PayrollBatch batch) {
        if (batch == null || Boolean.FALSE.equals(batch.getConfirmationRequired())) {
            return false;
        }
        long total = payrollLineService.count(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batchId));
        if (total <= 0) {
            return true;
        }
        long unresolved = payrollLineService.count(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batchId)
                .and(w -> w
                        .isNull(PayrollLine::getConfirmationStatus)
                        .or().eq(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.PENDING.getCode())
                        .or().eq(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.OBJECTED.getCode())
                        .or().eq(PayrollLine::getConfirmationStatus, PayrollConfirmationStatus.OBJECTED_REJECTED.getCode())));
        return unresolved > 0;
    }

    private String buildSubmitBlockedStatusMessage(PayrollBatch batch) {
        if (batch == null || batch.getStatus() == null) {
            return "批次状态异常，暂不可提交审批";
        }
        if (batch.getStatus() == PayrollBatchStatus.LOCKED && !Boolean.FALSE.equals(batch.getConfirmationRequired())) {
            return "批次尚未完成员工确认，暂不可提交审批";
        }
        return "当前状态不可提交审批：" + batch.getStatus().getCode();
    }

    private java.util.List<String> collectBlockingIssueMessages(Long batchId) {
        java.util.List<PayrollLine> lines = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batchId));
        if (lines == null || lines.isEmpty()) {
            return java.util.List.of("未生成工资结果，请先执行计算薪酬");
        }
        java.util.Set<String> messages = new java.util.LinkedHashSet<>();
        for (PayrollLine line : lines) {
            java.util.List<PayrollValidationIssueDto> issues = validationIssueSupport.deserialize(line.getWarning());
            for (PayrollValidationIssueDto issue : issues) {
                if (issue != null && Boolean.TRUE.equals(issue.getBlocking()) && StringUtils.hasText(issue.getMessage())) {
                    messages.add(issue.getMessage().trim());
                }
            }
        }
        return new java.util.ArrayList<>(messages);
    }

    /**
     * 记录管理员绕过审批的审计日志
     * <p>
     * 根据金融合规要求，管理员绕过审批的操作必须记录完整的审计轨迹。
     * </p>
     *
     * @param adminUser 管理员用户
     * @param batch     薪资批次
     * @param operation 操作类型
     */
    private void recordAdminBypassAudit(SysUser adminUser, PayrollBatch batch, String operation) {
        try {
            auditLogService.record(
                    operation,
                    "POST",
                    "/payroll/batch/" + batch.getId() + "/submit",
                    null,
                    null,
                    "PAYROLL_BATCH",
                    String.valueOf(batch.getId()),
                    adminUser != null ? adminUser.getUsername() : "unknown",
                    String.format("batchId=%s, period=%s, status=draft->approved",
                            batch.getId(),
                            batch.getPeriodLabel()),
                    "OK",
                    null,
                    null
            );
            log.debug("管理员绕过审批审计日志记录成功: batchId={}, admin={}",
                    batch.getId(), adminUser != null ? adminUser.getUsername() : "unknown");
        } catch (Exception e) {
            log.warn("记录管理员绕过审批审计日志失败: batchId={}, error={}",
                    batch.getId(), e.getMessage());
        }
    }

    /**
     * 记录工作流启动失败的审计日志
     * <p>
     * 工作流启动失败时记录详细错误信息，便于问题排查和追踪。
     * </p>
     *
     * @param user  当前用户
     * @param batch 薪资批次
     * @param error 错误信息
     */
    private void recordWorkflowStartFailureAudit(SysUser user, PayrollBatch batch, String error) {
        try {
            auditLogService.record(
                    "WORKFLOW_START_FAILED",
                    "POST",
                    "/payroll/batch/" + batch.getId() + "/submit",
                    null,
                    null,
                    "PAYROLL_BATCH",
                    String.valueOf(batch.getId()),
                    user != null ? user.getUsername() : "unknown",
                    String.format("batchId=%s, period=%s, status=%s",
                            batch.getId(),
                            batch.getPeriodLabel(),
                            batch.getStatus()),
                    "FAILED",
                    error,
                    null
            );
            log.debug("工作流启动失败审计日志记录成功: batchId={}, error={}",
                    batch.getId(), error);
        } catch (Exception e) {
            log.warn("记录工作流启动失败审计日志失败: batchId={}, error={}",
                    batch.getId(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean retryCreatePaymentBatch(Long batchId) {
        log.info("重试创建支付批次: batchId={}", batchId);

        PayrollBatch batch = getById(batchId);
        if (batch == null) {
            log.warn("薪资批次不存在: batchId={}", batchId);
            return false;
        }

        // 检查批次状态是否为approved
        if (batch.getStatus() != PayrollBatchStatus.APPROVED) {
            log.warn("薪资批次状态不是approved，无法创建支付批次: batchId={}, status={}",
                    batchId, batch.getStatus());
            return false;
        }

        // 幂等性检查：如果已经有支付批次，直接返回成功
        if (batch.getPaymentBatchNo() != null && !batch.getPaymentBatchNo().isEmpty()) {
            log.info("薪资批次已关联支付批次，无需重试: batchId={}, paymentBatchNo={}",
                    batchId, batch.getPaymentBatchNo());
            return true;
        }

        // 获取当前用户
        SysUser currentUser = resolveCurrentUser();
        if (currentUser == null) {
            log.warn("无法获取当前用户，使用系统用户");
            currentUser = new SysUser();
            currentUser.setId(0L);
            currentUser.setUsername("SYSTEM");
        }

        try {
            // 调用支付服务创建支付批次
            var paymentBatch = payrollPaymentService.createPaymentBatch(batch, currentUser, true);
            if (paymentBatch != null) {
                log.info("重试创建支付批次成功: batchId={}, paymentBatchNo={}",
                        batchId, paymentBatch.getBatchNo());
                return true;
            } else {
                log.warn("重试创建支付批次失败，无有效支付记录: batchId={}", batchId);
                return false;
            }
        } catch (Exception e) {
            log.error("重试创建支付批次异常: batchId={}, error={}", batchId, e.getMessage(), e);
            throw new RuntimeException("重试创建支付批次失败: " + e.getMessage(), e);
        }
    }

    private static class SecurityUtilsInternal {
        static String currentUsername() {
            try {
                var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
                org.springframework.security.core.Authentication a = context.getAuthentication();
                return a != null ? a.getName() : null;
            } catch (Exception ignore) {
                return null;
            }
        }
    }
}
