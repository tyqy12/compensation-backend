package com.yiyundao.compensation.service;

import com.yiyundao.compensation.enums.NotificationType;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRouterService notificationRouterService;
    private final SysConfigService sysConfigService;

    /**
     * 发送支付成功通知
     */
    @Async
    public void sendPaymentSuccessNotification(PaymentRecord record) {
        log.info("发送支付成功通知: recordId={}, amount={}", record.getId(), record.getAmount());

        try {
            // 从 PaymentRecord 获取 userId（假设已存储）
            Long userId = record.getUserId() != null ? record.getUserId() : 1L;

            String title = "💰 支付成功通知";
            String content = String.format(
                "您的 %s 已成功到账\n" +
                "金额：¥%.2f\n" +
                "批次号：%s\n" +
                "支付时间：%s",
                record.getPaymentDesc(),
                record.getAmount(),
                record.getBatchNo(),
                record.getPaymentTime()
            );

            notificationRouterService.sendNotificationToUser(
                userId,
                NotificationType.PAYMENT_SUCCESS,
                title,
                content,
                "PAYMENT",
                record.getBatchNo()
            );

        } catch (Exception e) {
            log.error("发送支付成功通知异常: recordId={}", record.getId(), e);
        }
    }

    /**
     * 发送支付失败通知
     */
    @Async
    public void sendPaymentFailedNotification(PaymentRecord record) {
        log.info("发送支付失败通知: recordId={}, amount={}", record.getId(), record.getAmount());

        try {
            Long userId = record.getUserId() != null ? record.getUserId() : 1L;

            String title = "❌ 支付失败通知";
            String content = String.format(
                "您的 %s 支付失败\n" +
                "金额：¥%.2f\n" +
                "批次号：%s\n" +
                "失败原因：%s\n" +
                "请联系财务部门处理",
                record.getPaymentDesc(),
                record.getAmount(),
                record.getBatchNo(),
                record.getErrorMsg()
            );

            notificationRouterService.sendNotificationToUser(
                userId,
                NotificationType.PAYMENT_FAILED,
                title,
                content,
                "PAYMENT",
                record.getBatchNo()
            );

        } catch (Exception e) {
            log.error("发送支付失败通知异常: recordId={}", record.getId(), e);
        }
    }

    /**
     * 发送批次完成通知
     */
    @Async
    public void sendBatchCompleteNotification(PaymentBatch batch) {
        log.info("发送批次完成通知: batchNo={}, success={}, failed={}",
                batch.getBatchNo(), batch.getSuccessCount(), batch.getFailedCount());

        try {
            // 获取批次处理人
            Long processorId = batch.getProcessorId();
            if (processorId == null) {
                log.warn("批次处理人不存在: batchNo={}", batch.getBatchNo());
                return;
            }

            String title = "📊 批量支付完成通知";
            String content = String.format(
                "批次 %s 处理完成\n" +
                "批次名称：%s\n" +
                "总笔数：%d\n" +
                "成功：%d 笔\n" +
                "失败：%d 笔\n" +
                "总金额：¥%.2f\n" +
                "处理时间：%s",
                batch.getBatchNo(),
                batch.getBatchName(),
                batch.getTotalCount(),
                batch.getSuccessCount(),
                batch.getFailedCount(),
                batch.getTotalAmount(),
                batch.getProcessEndTime()
            );

            notificationRouterService.sendNotificationToUser(
                processorId,
                NotificationType.BATCH_COMPLETE,
                title,
                content,
                "PAYMENT_BATCH",
                batch.getBatchNo()
            );

        } catch (Exception e) {
            log.error("发送批次完成通知异常: batchNo={}", batch.getBatchNo(), e);
        }
    }

    /**
     * 发送审批通知
     */
    @Async
    public void sendApprovalNotification(Long approverId, String workflowId, String workflowName,
                                       NotificationType type, String details) {
        try {
            String title = getApprovalNotificationTitle(type);
            String content = String.format(
                "工作流：%s\n" +
                "流程ID：%s\n" +
                "详情：%s",
                workflowName,
                workflowId,
                details
            );

            notificationRouterService.sendNotificationToUser(
                approverId,
                type,
                title,
                content,
                "APPROVAL",
                workflowId
            );

        } catch (Exception e) {
            log.error("发送审批通知异常: approverId={}, workflowId={}", approverId, workflowId, e);
        }
    }

    /**
     * 发送系统告警通知
     */
    @Async
    public void sendSystemAlert(String alertTitle, String alertMessage, String businessKey) {
        try {
            // 从系统配置中读取管理员用户ID
            Long adminUserId = sysConfigService.getLong("system.admin_user_id", 1L);

            notificationRouterService.sendNotificationToUser(
                adminUserId,
                NotificationType.SYSTEM_ALERT,
                "🚨 " + alertTitle,
                alertMessage,
                "SYSTEM",
                businessKey
            );

        } catch (Exception e) {
            log.error("发送系统告警通知异常: title={}", alertTitle, e);
        }
    }

    /**
     * 平台通知失败后的回退通知
     */
    @Async
    public void sendFallbackNotification(String provider, String subjectId, String message) {
        log.warn("平台通知失败，启用回退通知: provider={}, subjectId={}, message={}", provider, subjectId, message);

        try {
            notificationRouterService.sendNotificationToPlatformUser(
                provider,
                subjectId,
                NotificationType.SYSTEM_ALERT,
                "通知发送失败",
                message,
                "FALLBACK",
                provider + "_" + subjectId
            );

        } catch (Exception e) {
            log.error("发送回退通知异常: provider={}, subjectId={}", provider, subjectId, e);
        }
    }

    /**
     * 获取审批通知标题
     */
    private String getApprovalNotificationTitle(NotificationType type) {
        return switch (type) {
            case APPROVAL_PENDING -> "📋 待审批通知";
            case APPROVAL_APPROVED -> "✅ 审批通过通知";
            case APPROVAL_REJECTED -> "❌ 审批拒绝通知";
            default -> "📋 审批通知";
        };
    }
}
