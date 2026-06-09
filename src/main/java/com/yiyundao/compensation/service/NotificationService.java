package com.yiyundao.compensation.service;

import com.yiyundao.compensation.enums.NotificationType;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRouterService notificationRouterService;
    private final SysConfigService sysConfigService;
    private final SysUserService sysUserService;

    /**
     * 发送支付成功通知
     */
    @Async
    public void sendPaymentSuccessNotification(PaymentRecord record) {
        if (record == null) {
            log.warn("支付成功通知记录为空，跳过发送");
            return;
        }
        try {
            Long userId = resolveRecipientUserId(record);
            log.info("发送支付成功通知: recordId={}, userId={}, employeeId={}, batchNo={}",
                    record.getId(), userId, record.getEmployeeId(), record.getBatchNo());
            if (userId == null) {
                log.warn("支付成功通知缺少接收用户，跳过发送: recordId={}, employeeId={}, batchNo={}",
                        record.getId(), record.getEmployeeId(), record.getBatchNo());
                return;
            }

            String title = "💰 支付成功通知";
            String content = String.format(
                "您的 %s 已成功到账\n" +
                "金额：%s\n" +
                "批次号：%s\n" +
                "支付时间：%s",
                display(record.getPaymentDesc(), "款项"),
                formatCurrency(record.getAmount()),
                display(record.getBatchNo(), "未知"),
                display(record.getPaymentTime(), "未知")
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
        if (record == null) {
            log.warn("支付失败通知记录为空，跳过发送");
            return;
        }
        try {
            Long userId = resolveRecipientUserId(record);
            log.info("发送支付失败通知: recordId={}, userId={}, employeeId={}, batchNo={}",
                    record.getId(), userId, record.getEmployeeId(), record.getBatchNo());
            if (userId == null) {
                log.warn("支付失败通知缺少接收用户，跳过发送: recordId={}, employeeId={}, batchNo={}",
                        record.getId(), record.getEmployeeId(), record.getBatchNo());
                return;
            }

            String title = "❌ 支付失败通知";
            String content = String.format(
                "您的 %s 支付失败\n" +
                "金额：%s\n" +
                "批次号：%s\n" +
                "失败原因：%s\n" +
                "请联系财务部门处理",
                display(record.getPaymentDesc(), "款项"),
                formatCurrency(record.getAmount()),
                display(record.getBatchNo(), "未知"),
                display(record.getErrorMsg(), "未提供")
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
        if (batch == null) {
            log.warn("支付批次为空，跳过完成通知");
            return;
        }
        log.info("发送批次完成通知: batchNo={}, processorId={}, totalCount={}, successCount={}, failedCount={}",
                batch.getBatchNo(), batch.getProcessorId(), batch.getTotalCount(), batch.getSuccessCount(), batch.getFailedCount());

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
                "总笔数：%s\n" +
                "成功：%s 笔\n" +
                "失败：%s 笔\n" +
                "总金额：%s\n" +
                "处理时间：%s",
                display(batch.getBatchNo(), "未知"),
                display(batch.getBatchName(), "未命名批次"),
                display(batch.getTotalCount(), "未知"),
                display(batch.getSuccessCount(), "未知"),
                display(batch.getFailedCount(), "未知"),
                formatCurrency(batch.getTotalAmount()),
                display(batch.getProcessEndTime(), "未知")
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
            log.error("发送系统告警通知异常: businessKey={}, titleLength={}, messageLength={}",
                    businessKey, length(alertTitle), length(alertMessage), e);
        }
    }

    /**
     * 平台通知失败后的回退通知
     */
    @Async
    public void sendFallbackNotification(String provider, String subjectId, String message) {
        log.warn("平台通知失败，启用回退通知: provider={}, subjectId={}, messageLength={}",
                provider, subjectId, length(message));

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

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "未知";
        }
        return "¥" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String display(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private Long resolveRecipientUserId(PaymentRecord record) {
        if (record.getUserId() != null) {
            return record.getUserId();
        }
        if (record.getEmployeeId() == null) {
            return null;
        }

        SysUser user = sysUserService.findByEmployeeId(record.getEmployeeId());
        if (user == null || user.getId() == null) {
            return null;
        }
        record.setUserId(user.getId());
        return user.getId();
    }
}
