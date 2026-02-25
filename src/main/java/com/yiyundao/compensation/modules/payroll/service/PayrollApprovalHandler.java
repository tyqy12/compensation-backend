package com.yiyundao.compensation.modules.payroll.service;

import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 薪资批次审批处理器
 * <p>
 * 监听审批完成事件，处理审批通过后的薪资批次支付触发操作。
 * </p>
 * <p>
 * <b>核心职责：</b>
 * <ul>
 *   <li>监听薪资批次审批完成事件</li>
 *   <li>审批通过后自动创建支付批次</li>
 *   <li>触发支付宝批量转账流程</li>
 * </ul>
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollApprovalHandler {

    private final PayrollPaymentService payrollPaymentService;
    private final PayrollBatchService payrollBatchService;
    private final SysUserService sysUserService;
    private final NotificationService notificationService;

    /**
     * 内存中的失败队列（生产环境建议使用 Redis 队列）
     */
    private final Map<String, PayrollPaymentFailure> failureQueue = new ConcurrentHashMap<>();

    /**
     * 薪资批次支付失败记录
     */
    public record PayrollPaymentFailure(
            Long workflowId,
            Long batchId,
            String businessKey,
            String errorMessage,
            LocalDateTime failedTime,
            Integer retryCount
    ) {}

    /**
     * 监听审批完成事件
     * <p>
     * 当审批类型为 PAYROLL 时，根据审批结果执行相应操作：
     * <ul>
     *   <li>APPROVED: 创建支付批次并触发支付</li>
     *   <li>REJECTED/CANCELLED: 仅记录日志</li>
     * </ul>
     * </p>
     * <p>
     * <b>事务处理：</b>
     * 使用 @TransactionalEventListener(phase = AFTER_COMMIT) 确保事件处理在审批事务提交后执行，
     * 避免审批事务回滚导致支付批次创建也被回滚的问题。
     * 使用 @Transactional(propagation = REQUIRES_NEW) 开启新事务，确保支付批次创建的独立性。
     * </p>
     *
     * @param event 审批完成事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onApprovalCompleted(ApprovalCompletedEvent event) {
        ApprovalWorkflow workflow = event.getWorkflow();
        ApprovalStatus finalStatus = event.getFinalStatus();

        if (workflow == null) {
            log.warn("PayrollApprovalHandler: workflow is null, skip processing");
            return;
        }

        // 只处理薪资批次审批
        if (!isPayrollBusiness(workflow)) {
            return;
        }

        log.info("处理薪资批次审批: workflowId={}, businessKey={}, status={}",
                workflow.getId(), workflow.getBusinessKey(), finalStatus);

        try {
            switch (finalStatus) {
                case APPROVED -> executeApprovedPayment(workflow, event);
                case REJECTED -> log.info("薪资批次审批已拒绝: workflowId={}", workflow.getId());
                case CANCELLED -> log.info("薪资批次审批已撤销: workflowId={}", workflow.getId());
                default -> log.warn("薪资批次审批未知状态: workflowId={}, status={}",
                        workflow.getId(), finalStatus);
            }
        } catch (Exception e) {
            log.error("处理薪资批次审批失败: workflowId={}, businessKey={}, error={}",
                    workflow.getId(), workflow.getBusinessKey(), e.getMessage(), e);

            // 记录到失败队列
            recordFailureToQueue(workflow, e.getMessage());

            // 发送告警通知
            sendAlertNotification(workflow, e.getMessage());

            // 注意：由于使用了 @TransactionalEventListener(phase = AFTER_COMMIT)，
            // 审批事务已经提交，此处异常只会回滚支付创建事务，不会回滚审批事务。
            // 这是预期行为：审批状态保持APPROVED，但支付批次创建失败。
            // 可以通过重试机制（利用幂等性保护）重新创建支付批次。
            throw new RuntimeException("薪资批次审批回调处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断是否为薪资批次业务
     */
    private boolean isPayrollBusiness(ApprovalWorkflow workflow) {
        String businessType = workflow.getBusinessType();
        String businessKey = workflow.getBusinessKey();

        // businessType 为 "payroll" 或 businessKey 包含 "payroll_batch"
        return "payroll".equalsIgnoreCase(businessType) ||
               (businessKey != null && businessKey.toLowerCase().contains("payroll_batch"));
    }

    /**
     * 审批通过后执行支付流程
     */
    private void executeApprovedPayment(ApprovalWorkflow workflow, ApprovalCompletedEvent event) {
        Long batchId = parseBatchId(workflow.getBusinessKey());
        if (batchId == null) {
            log.error("无法解析薪资批次ID: workflowId={}, businessKey={}",
                    workflow.getId(), workflow.getBusinessKey());
            throw new IllegalArgumentException("无法解析薪资批次ID: " + workflow.getBusinessKey());
        }

        PayrollBatch payrollBatch = payrollBatchService.getById(batchId);
        if (payrollBatch == null) {
            log.error("薪资批次不存在: workflowId={}, batchId={}", workflow.getId(), batchId);
            throw new IllegalArgumentException("薪资批次不存在: batchId=" + batchId);
        }

        // 幂等性保护：检查是否已创建支付批次
        // 使用数据库级别的检查，避免并发场景下的重复创建
        if (payrollBatch.getPaymentBatchNo() != null && !payrollBatch.getPaymentBatchNo().isEmpty()) {
            log.info("薪资批次已关联支付批次，跳过重复创建: workflowId={}, batchId={}, paymentBatchNo={}",
                    workflow.getId(), batchId, payrollBatch.getPaymentBatchNo());
            return;
        }

        // 获取审批人信息
        // 从事件中获取 finalApproverId，确保审计追溯的准确性
        Long approverId = event.getFinalApproverId() != null
                ? event.getFinalApproverId()
                : workflow.getInitiatorId();
        SysUser approver = sysUserService.getById(approverId);
        if (approver == null) {
            log.warn("审批人不存在，使用系统用户: workflowId={}, approverId={}",
                    workflow.getId(), approverId);
            approver = getSystemUser();
        }

        log.info("开始为薪资批次创建支付批次: workflowId={}, batchId={}, approverId={}, approverName={}",
                workflow.getId(), batchId, approver.getId(), approver.getUsername());

        // 创建支付批次
        // 注意：createPaymentBatch 内部会再次检查并更新 payroll_batch.payment_batch_no
        // 如果并发创建，数据库约束会确保只有一个成功
        var paymentBatch = payrollPaymentService.createPaymentBatch(payrollBatch, approver, true);

        if (paymentBatch != null) {
            log.info("薪资批次支付批次创建成功: workflowId={}, batchId={}, paymentBatchNo={}",
                    workflow.getId(), batchId, paymentBatch.getBatchNo());
        } else {
            log.warn("薪资批次无有效支付记录，未创建支付批次: workflowId={}, batchId={}",
                    workflow.getId(), batchId);
        }
    }

    /**
     * 从 businessKey 中解析批次ID
     * <p>
     * 支持格式: payroll_batch:{id} 或 payroll_batch_{id}
     * </p>
     */
    private Long parseBatchId(String businessKey) {
        if (businessKey == null || businessKey.isEmpty()) {
            return null;
        }

        try {
            // 格式: payroll_batch:123 或 payroll_batch_123 或 123
            String idPart = businessKey
                    .replaceAll("(?i)payroll_batch[:_]", "")
                    .trim();

            if (idPart.isEmpty()) {
                return null;
            }

            return Long.parseLong(idPart);
        } catch (NumberFormatException e) {
            log.warn("解析批次ID失败: businessKey={}", businessKey);
            return null;
        }
    }

    /**
     * 获取系统用户（用于自动审批场景）
     */
    private SysUser getSystemUser() {
        SysUser systemUser = new SysUser();
        systemUser.setId(0L);
        systemUser.setUsername("SYSTEM");
        return systemUser;
    }

    /**
     * 记录失败到队列
     *
     * @param workflow    工作流
     * @param errorMessage 错误信息
     */
    private void recordFailureToQueue(ApprovalWorkflow workflow, String errorMessage) {
        try {
            Long batchId = parseBatchId(workflow.getBusinessKey());
            String queueKey = "payroll_payment_failure:" + workflow.getId();

            PayrollPaymentFailure failure = new PayrollPaymentFailure(
                    workflow.getId(),
                    batchId,
                    workflow.getBusinessKey(),
                    errorMessage,
                    LocalDateTime.now(),
                    1
            );

            failureQueue.put(queueKey, failure);
            log.info("薪资批次支付失败已记录到队列: queueKey={}", queueKey);

        } catch (Exception ex) {
            log.error("记录失败队列异常: workflowId={}", workflow.getId(), ex);
        }
    }

    /**
     * 发送告警通知
     *
     * @param workflow     工作流
     * @param errorMessage 错误信息
     */
    private void sendAlertNotification(ApprovalWorkflow workflow, String errorMessage) {
        try {
            Long batchId = parseBatchId(workflow.getBusinessKey());
            String alertTitle = "薪资批次支付处理失败";
            String alertMessage = String.format(
                    """
                    薪资批次支付处理失败告警

                    工作流ID: %d
                    业务Key: %s
                    批次ID: %s
                    失败时间: %s
                    错误信息: %s

                    请及时检查系统状态并处理！
                    """,
                    workflow.getId(),
                    workflow.getBusinessKey(),
                    batchId != null ? batchId.toString() : "未知",
                    LocalDateTime.now(),
                    errorMessage
            );

            notificationService.sendSystemAlert(alertTitle, alertMessage, "PAYROLL_FAILURE:" + workflow.getId());
            log.info("薪资批次支付失败告警已发送: workflowId={}", workflow.getId());

        } catch (Exception ex) {
            log.error("发送告警通知异常: workflowId={}", workflow.getId(), ex);
        }
    }

    /**
     * 从失败队列中获取并清除记录
     *
     * @param workflowId 工作流ID
     * @return 失败记录，不存在则返回 null
     */
    public PayrollPaymentFailure pollFailureFromQueue(Long workflowId) {
        String queueKey = "payroll_payment_failure:" + workflowId;
        return failureQueue.remove(queueKey);
    }

    /**
     * 获取当前失败队列大小
     *
     * @return 队列大小
     */
    public int getFailureQueueSize() {
        return failureQueue.size();
    }
}
