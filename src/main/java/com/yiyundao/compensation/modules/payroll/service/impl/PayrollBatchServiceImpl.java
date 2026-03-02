package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.enums.PayrollConfirmationStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
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

    @Override
    @Transactional
    public boolean lockBatch(Long batchId) {
        log.info("Lock payroll batch: {}", batchId);
        PayrollBatch b = getById(batchId);
        if (b == null) return false;
        if (b.getStatus() != PayrollBatchStatus.DRAFT) return false;
        return update(new LambdaUpdateWrapper<PayrollBatch>()
                .eq(PayrollBatch::getId, batchId)
                .set(PayrollBatch::getStatus, PayrollBatchStatus.LOCKED));
    }

    @Override
    @Transactional
    public boolean submitForApproval(Long batchId) {
        log.info("Submit payroll batch for approval: {}", batchId);
        PayrollBatch b = getById(batchId);
        if (b == null) return false;
        if (!isReadyForApproval(b)) return false;
        if (hasPendingOrRejectedConfirmations(batchId, b)) {
            log.warn("批次存在未完成确认或异议未决，禁止提交审批: batchId={}", batchId);
            return false;
        }

        SysUser currentUser = resolveCurrentUser();
        Long initiatorId = currentUser != null ? currentUser.getId() : 1L;

        if (hasAdminRole(currentUser)) {
            log.info("Admin user bypass approval, batch={}, username={}", batchId,
                    currentUser != null ? currentUser.getUsername() : "unknown");

            // 幂等性检查：如果已经创建了支付批次，直接返回成功
            if (b.getPaymentBatchNo() != null && !b.getPaymentBatchNo().isEmpty()) {
                log.info("薪资批次已关联支付批次，跳过重复创建: batchId={}, paymentBatchNo={}",
                        batchId, b.getPaymentBatchNo());
                return true;
            }

            // 记录管理员绕过审批的审计日志（合规要求）
            recordAdminBypassAudit(currentUser, b, "APPROVAL_BYPASS");

            boolean updated = update(new LambdaUpdateWrapper<PayrollBatch>()
                    .eq(PayrollBatch::getId, batchId)
                    .set(PayrollBatch::getStatus, PayrollBatchStatus.APPROVED)
                    .set(PayrollBatch::getApprovalWorkflowId, null)
                    .set(PayrollBatch::getUpdateBy, currentUser != null ? currentUser.getUsername() : "system"));
            if (updated) {
                b.setStatus(PayrollBatchStatus.APPROVED);
                // 管理员绕过审批路径：直接创建支付批次
                // 注意：这是两条支付创建路径之一（另一条是审批通过后的事件驱动路径）
                // 两条路径都调用相同的 createPaymentBatch 方法，确保逻辑一致性
                payrollPaymentService.createPaymentBatch(b, currentUser, true);
            }
            return updated;
        }

        // Start approval workflow and persist workflow id on batch
        Long wfId = null;
        try {
            wfId = approvalEngine.startWorkflow(
                    WorkflowType.BATCH,
                    "payroll_batch:" + batchId,
                    "payroll",
                    initiatorId,
                    java.util.Map.of("batchId", batchId)
            );
            return update(new LambdaUpdateWrapper<PayrollBatch>()
                    .eq(PayrollBatch::getId, batchId)
                    .set(PayrollBatch::getStatus, PayrollBatchStatus.SUBMITTED)
                    .set(PayrollBatch::getApprovalWorkflowId, wfId));
        } catch (Exception e) {
            log.warn("submitForApproval failed to start workflow, batchId={}, error={}",
                    batchId, e.getMessage());
            // 记录工作流启动失败的审计日志
            recordWorkflowStartFailureAudit(currentUser, b, e.getMessage());
            // 工作流启动失败时，不更新批次状态，避免形成"submitted"但无工作流ID的孤立记录
            // 让批次保持在 locked/draft 状态，等待人工处理
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
