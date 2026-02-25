package com.yiyundao.compensation.modules.approval.listener;

import com.yiyundao.compensation.modules.audit.event.AuditLogSavedEvent;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审批状态变更事件监听器
 * <p>
 * 监听审批流程状态变更，触发通知和后续处理。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalStatusChangeListener {

    private final NotificationService notificationService;

    /**
     * 处理审批审计日志事件
     */
    @Async
    @EventListener
    public void onAuditLogSaved(AuditLogSavedEvent event) {
        // 只处理审批相关的操作
        if (!isApprovalOperation(event)) {
            return;
        }

        String operation = event.getOperation();
        ApprovalStatus status = parseStatus(operation);

        // 审批通过时触发后续处理
        if (event.isSuccess() && status == ApprovalStatus.APPROVED) {
            handleApprovalApproved(event);
        }

        // 审批拒绝时触发通知
        if (!event.isSuccess() || status == ApprovalStatus.REJECTED) {
            handleApprovalRejected(event);
        }
    }

    /**
     * 判断是否是审批操作
     */
    private boolean isApprovalOperation(AuditLogSavedEvent event) {
        String operation = event.getOperation();
        return operation != null && (
                operation.contains("审批") ||
                operation.contains("APPROVE") ||
                operation.contains("审批通过") ||
                operation.contains("审批拒绝")
        );
    }

    /**
     * 解析审批状态
     */
    private ApprovalStatus parseStatus(String operation) {
        if (operation == null) {
            return null;
        }
        if (operation.contains("通过") || operation.toUpperCase().contains("APPROVE")) {
            return ApprovalStatus.APPROVED;
        }
        if (operation.contains("拒绝") || operation.toUpperCase().contains("REJECT")) {
            return ApprovalStatus.REJECTED;
        }
        return null;
    }

    /**
     * 处理审批通过
     */
    private void handleApprovalApproved(AuditLogSavedEvent event) {
        try {
            String businessKey = event.getAuditLog().getBusinessKey();
            log.info("审批流程已通过: workflowId={}, businessKey={}", event.getAuditLog().getBusinessKey(), businessKey);

            // 如果是薪酬批次，触发支付流程
            if (businessKey != null && businessKey.contains("payroll_batch")) {
                log.info("审批通过，触发支付流程: businessKey={}", businessKey);
                // 支付流程由 PaymentBatchController 中的逻辑处理
            }
        } catch (Exception e) {
            log.error("处理审批通过事件失败", e);
        }
    }

    /**
     * 处理审批拒绝
     */
    private void handleApprovalRejected(AuditLogSavedEvent event) {
        try {
            log.info("审批流程已被拒绝: workflowId={}, error={}",
                    event.getAuditLog().getBusinessKey(),
                    event.hasError() ? event.getAuditLog().getErrorMsg() : "无");

            // 发送通知给发起人
            String title = "审批被拒绝";
            String content = String.format(
                    "您的审批申请已被拒绝\n\n" +
                            "审批流程ID：%s\n" +
                            "操作人：%s\n" +
                            "时间：%s\n" +
                            "原因：%s",
                    event.getAuditLog().getBusinessKey(),
                    event.getUsername() != null ? event.getUsername() : "未知",
                    LocalDateTime.now(),
                    event.hasError() ? event.getAuditLog().getErrorMsg() : "未说明原因"
            );

            notificationService.sendSystemAlert(
                    title,
                    content,
                    "APPROVAL_REJECTED_" + event.getAuditLog().getBusinessKey()
            );
        } catch (Exception e) {
            log.error("处理审批拒绝事件失败", e);
        }
    }
}
