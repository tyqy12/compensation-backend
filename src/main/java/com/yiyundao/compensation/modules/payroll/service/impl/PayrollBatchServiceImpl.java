package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.enums.WorkflowType;
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
    private final PayrollPaymentService payrollPaymentService;

    @Override
    @Transactional
    public boolean lockBatch(Long batchId) {
        log.info("Lock payroll batch: {}", batchId);
        PayrollBatch b = getById(batchId);
        if (b == null) return false;
        if (!"draft".equalsIgnoreCase(b.getStatus())) return false;
        return update(new LambdaUpdateWrapper<PayrollBatch>()
                .eq(PayrollBatch::getId, batchId)
                .set(PayrollBatch::getStatus, "locked"));
    }

    @Override
    @Transactional
    public boolean submitForApproval(Long batchId) {
        log.info("Submit payroll batch for approval: {}", batchId);
        PayrollBatch b = getById(batchId);
        if (b == null) return false;
        String st = b.getStatus();
        if (!("draft".equalsIgnoreCase(st) || "locked".equalsIgnoreCase(st))) return false;
        SysUser currentUser = resolveCurrentUser();
        Long initiatorId = currentUser != null ? currentUser.getId() : 1L;

        if (hasAdminRole(currentUser)) {
            log.info("Admin user bypass approval, batch={}", batchId);
            boolean updated = update(new LambdaUpdateWrapper<PayrollBatch>()
                    .eq(PayrollBatch::getId, batchId)
                    .set(PayrollBatch::getStatus, "approved")
                    .set(PayrollBatch::getApprovalWorkflowId, null)
                    .set(PayrollBatch::getUpdateBy, currentUser != null ? currentUser.getUsername() : "system"));
            if (updated) {
                b.setStatus("approved");
                payrollPaymentService.createPaymentBatch(b, currentUser, true);
            }
            return updated;
        }

        // Start approval workflow and persist workflow id on batch
        try {
            Long wfId = approvalEngine.startWorkflow(
                    WorkflowType.BATCH,
                    "payroll_batch:" + batchId,
                    "payroll",
                    initiatorId,
                    java.util.Map.of("batchId", batchId)
            );
            return update(new LambdaUpdateWrapper<PayrollBatch>()
                    .eq(PayrollBatch::getId, batchId)
                    .set(PayrollBatch::getStatus, "submitted")
                    .set(PayrollBatch::getApprovalWorkflowId, wfId));
        } catch (Exception e) {
            log.warn("submitForApproval failed to start workflow, fallback to status only: {}", e.getMessage());
            return update(new LambdaUpdateWrapper<PayrollBatch>()
                    .eq(PayrollBatch::getId, batchId)
                    .set(PayrollBatch::getStatus, "submitted"));
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
        if (user == null || !StringUtils.hasText(user.getRoles())) {
            return false;
        }
        return user.getRoles().contains("ROLE_ADMIN");
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
