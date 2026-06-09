package com.yiyundao.compensation.modules.payment.listener;

import com.yiyundao.compensation.modules.audit.event.AuditLogSavedEvent;
import com.yiyundao.compensation.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 支付状态变更事件监听器
 * <p>
 * 监听支付相关操作，触发通知和后续处理。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStatusChangeListener {

    private final NotificationService notificationService;

    /**
     * 大额支付阈值（10万元）
     */
    private static final long LARGE_PAYMENT_THRESHOLD = 100000L;

    /**
     * 支付失败告警阈值
     */
    private static final int PAYMENT_FAILURE_THRESHOLD = 5;

    /**
     * 支付失败计数缓存
     */
    private final java.util.Map<String, Integer> paymentFailureCount = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_TRACKED_PAYMENT_FAILURE_BATCHES = 1000;

    /**
     * 处理支付审计日志事件
     */
    @Async
    @EventListener
    public void onAuditLogSaved(AuditLogSavedEvent event) {
        // 只处理支付相关的操作
        if (!isPaymentOperation(event)) {
            return;
        }

        String operation = event.getOperation();

        // 启动批量转账
        if (operation.contains("启动批量转账") && event.isSuccess()) {
            handlePaymentStarted(event);
        }

        // 支付失败
        if (!event.isSuccess()) {
            handlePaymentFailed(event);
        }

        // 大额支付监控
        if (operation.contains("启动批量转账") && event.isSuccess()) {
            checkLargePayment(event);
        }
    }

    /**
     * 判断是否是支付操作
     */
    private boolean isPaymentOperation(AuditLogSavedEvent event) {
        String operation = event.getOperation();
        String businessType = event.getBusinessType();
        return operation != null && (
                operation.contains("支付") ||
                operation.contains("转账") ||
                operation.contains("PAYMENT") ||
                "PAYMENT".equals(businessType)
        );
    }

    /**
     * 处理支付启动
     */
    private void handlePaymentStarted(AuditLogSavedEvent event) {
        try {
            String detail = event.getAuditLog().getRequestParams();
            String batchNo = event.getAuditLog().getBusinessKey();

            // 解析金额
            Long amount = parseAmount(detail);

            log.info("批量转账已启动: batchNo={}, amount={}", batchNo, amount);

            // 大额支付发送告警
            if (amount != null && amount >= LARGE_PAYMENT_THRESHOLD) {
                String title = "⚠️ 大额支付提醒";
                String content = String.format(
                        "已启动大额批量转账\n\n" +
                                "批次号：%s\n" +
                                "金额：%s 元\n" +
                                "操作人：%s\n" +
                                "时间：%s\n\n" +
                                "请确认此操作是否正常。",
                        batchNo,
                        String.format("%,.2f", (double) amount),
                        event.getUsername() != null ? event.getUsername() : "系统",
                        LocalDateTime.now()
                );

                notificationService.sendSystemAlert(
                        title,
                        content,
                        "LARGE_PAYMENT_" + batchNo
                );
            }
        } catch (Exception e) {
            log.error("处理支付启动事件失败", e);
        }
    }

    /**
     * 处理支付失败
     */
    private void handlePaymentFailed(AuditLogSavedEvent event) {
        try {
            String batchNo = event.getAuditLog().getBusinessKey();
            if (batchNo == null) {
                return;
            }

            // 增加失败计数
            prunePaymentFailureCountIfNeeded(batchNo);
            int count = paymentFailureCount.getOrDefault(batchNo, 0) + 1;
            paymentFailureCount.put(batchNo, count);

            log.warn("支付失败: batchNo={}, count={}, error={}",
                    batchNo, count, event.getAuditLog().getErrorMsg());

            // 达到阈值，发送告警
            if (count >= PAYMENT_FAILURE_THRESHOLD) {
                String title = "🚨 支付异常告警";
                String content = String.format(
                        "检测到支付批次多次失败\n\n" +
                                "批次号：%s\n" +
                                "失败次数：%d 次\n" +
                                "错误信息：%s\n" +
                                "时间：%s\n\n" +
                                "建议：立即检查支付渠道状态和账户余额。",
                        batchNo,
                        count,
                        event.getAuditLog().getErrorMsg(),
                        LocalDateTime.now()
                );

                notificationService.sendSystemAlert(
                        title,
                        content,
                        "PAYMENT_FAILURE_" + batchNo
                );

                // 告警后重置计数
                paymentFailureCount.put(batchNo, 0);
            }
        } catch (Exception e) {
            log.error("处理支付失败事件失败", e);
        }
    }

    private void prunePaymentFailureCountIfNeeded(String batchNo) {
        if (paymentFailureCount.containsKey(batchNo)) {
            return;
        }
        int overflow = paymentFailureCount.size() - MAX_TRACKED_PAYMENT_FAILURE_BATCHES + 1;
        if (overflow <= 0) {
            return;
        }
        paymentFailureCount.keySet().stream()
                .limit(overflow)
                .forEach(paymentFailureCount::remove);
    }

    /**
     * 检查大额支付
     */
    private void checkLargePayment(AuditLogSavedEvent event) {
        // 大额支付已经在 handlePaymentStarted 中处理
        log.debug("大额支付检查: batchNo={}", event.getAuditLog().getBusinessKey());
    }

    /**
     * 从详情中解析金额
     */
    private Long parseAmount(String detail) {
        if (detail == null) {
            return null;
        }
        try {
            Pattern pattern = Pattern.compile("amount=(\\d+)");
            Matcher matcher = pattern.matcher(detail);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        } catch (Exception e) {
            log.warn("解析金额失败: {}", detail);
        }
        return null;
    }
}
