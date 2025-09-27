package com.yiyundao.compensation.service;

import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {

    /**
     * 发送支付成功通知
     */
    @Async
    public void sendPaymentSuccessNotification(PaymentRecord record) {
        log.info("发送支付成功通知: recordId={}, amount={}", record.getId(), record.getAmount());
        // TODO: 实现通知发送逻辑
        // 1. 平台通知 (企微/钉钉/飞书)
        // 2. SMS短信通知
        // 3. 邮件通知
    }

    /**
     * 发送批次完成通知
     */
    @Async
    public void sendBatchCompleteNotification(PaymentBatch batch) {
        log.info("发送批次完成通知: batchNo={}, success={}, failed={}",
                batch.getBatchNo(), batch.getSuccessCount(), batch.getFailedCount());
        // TODO: 实现批次通知逻辑
    }

    /**
     * 平台通知失败后的回退通知
     * 可扩展为：短信、邮件、站内信、多平台广播等
     */
    @Async
    public void sendFallbackNotification(String platformType, String platformUserId, String message) {
        log.warn("平台通知失败，启用回退通知: platform={}, userId={}, message={}", platformType, platformUserId, message);
        // TODO: 集成 SMS/Email 提供商
    }
}
