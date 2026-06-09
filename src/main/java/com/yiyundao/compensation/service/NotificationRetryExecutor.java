package com.yiyundao.compensation.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yiyundao.compensation.enums.NotificationChannel;
import com.yiyundao.compensation.enums.NotificationStatus;
import com.yiyundao.compensation.interfaces.adapter.NotificationAdapter;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import com.yiyundao.compensation.modules.notification.service.NotificationRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 单条通知重试执行器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRetryExecutor {

    private final NotificationRecordService notificationRecordService;
    private final Map<NotificationChannel, NotificationAdapter> adapters;
    private final NotificationRouterService notificationRouterService;

    /**
     * 处理单条重试记录。
     */
    @Async
    public void processRetryRecord(NotificationRecord record) {
        try {
            log.info("处理重试通知: recordId={}, retryCount={}", record.getId(), record.getRetryCount());

            if (record.getNextRetryTime() != null && record.getNextRetryTime().isAfter(LocalDateTime.now())) {
                log.debug("重试时间未到: recordId={}, nextRetryTime={}", record.getId(), record.getNextRetryTime());
                return;
            }

            if (safeRetryCount(record) >= safeMaxRetry(record)) {
                log.warn("重试次数已达上限: recordId={}, retryCount={}, maxRetry={}",
                        record.getId(), safeRetryCount(record), safeMaxRetry(record));

                if (!markRetryExhausted(record)) {
                    log.info("通知重试记录已被其他任务处理，跳过回退: recordId={}", record.getId());
                    return;
                }
                record.setStatus(NotificationStatus.FAILED);

                triggerFallbackStrategy(record);
                return;
            }

            retryNotification(record);

        } catch (Exception e) {
            log.error("处理重试记录异常: recordId={}", record.getId(), e);
        }
    }

    private void retryNotification(NotificationRecord record) {
        try {
            int nextRetryCount = safeRetryCount(record) + 1;
            LocalDateTime sendTime = LocalDateTime.now();
            if (!claimRetryRecord(record, nextRetryCount, sendTime)) {
                log.info("通知重试记录已被其他任务处理，跳过: recordId={}", record.getId());
                return;
            }
            record.setRetryCount(nextRetryCount);
            record.setStatus(NotificationStatus.SENDING);
            record.setSendTime(sendTime);

            boolean success = resendNotification(record);

            if (success) {
                record.setStatus(NotificationStatus.SUCCESS);
                record.setNextRetryTime(null);
                log.info("通知重试成功: recordId={}, retryCount={}", record.getId(), record.getRetryCount());
            } else {
                applyRetryFailureOutcome(record);
                log.warn("通知重试失败: recordId={}, retryCount={}, status={}",
                        record.getId(), record.getRetryCount(), record.getStatus());
            }

            if (!completeRetryRecord(record)) {
                log.info("通知重试记录状态已变更，跳过结果回写: recordId={}, finalStatus={}",
                        record.getId(), record.getStatus());
                return;
            }
            if (record.getStatus() == NotificationStatus.FAILED) {
                triggerFallbackStrategy(record);
            }

        } catch (Exception e) {
            log.error("重试通知发送异常: recordId={}", record.getId(), e);

            record.setErrorMessage("重试异常: " + e.getMessage());
            applyRetryFailureOutcome(record);
            if (!completeRetryRecord(record)) {
                log.info("通知重试记录状态已变更，跳过异常结果回写: recordId={}", record.getId());
                return;
            }
            if (record.getStatus() == NotificationStatus.FAILED) {
                triggerFallbackStrategy(record);
            }
        }
    }

    private boolean claimRetryRecord(NotificationRecord record, int nextRetryCount, LocalDateTime sendTime) {
        if (record == null || record.getId() == null) {
            return false;
        }
        return notificationRecordService.update(new UpdateWrapper<NotificationRecord>()
                .eq("id", record.getId())
                .eq("status", NotificationStatus.RETRY.getCode())
                .eq("retry_count", safeRetryCount(record))
                .set("retry_count", nextRetryCount)
                .set("status", NotificationStatus.SENDING.getCode())
                .set("send_time", sendTime));
    }

    private boolean markRetryExhausted(NotificationRecord record) {
        if (record == null || record.getId() == null) {
            return false;
        }
        return notificationRecordService.update(new UpdateWrapper<NotificationRecord>()
                .eq("id", record.getId())
                .eq("status", NotificationStatus.RETRY.getCode())
                .eq("retry_count", safeRetryCount(record))
                .set("status", NotificationStatus.FAILED.getCode()));
    }

    private boolean completeRetryRecord(NotificationRecord record) {
        if (record == null || record.getId() == null || record.getStatus() == null) {
            return false;
        }
        return notificationRecordService.update(new UpdateWrapper<NotificationRecord>()
                .eq("id", record.getId())
                .eq("status", NotificationStatus.SENDING.getCode())
                .eq("retry_count", safeRetryCount(record))
                .set("status", record.getStatus().getCode())
                .set("next_retry_time", record.getNextRetryTime())
                .set("response_code", record.getResponseCode())
                .set("response_message", record.getResponseMessage())
                .set("error_message", record.getErrorMessage()));
    }

    private int safeRetryCount(NotificationRecord record) {
        return record == null || record.getRetryCount() == null ? 0 : record.getRetryCount();
    }

    private int safeMaxRetry(NotificationRecord record) {
        return record == null || record.getMaxRetry() == null || record.getMaxRetry() < 0
                ? 3
                : record.getMaxRetry();
    }

    private boolean resendNotification(NotificationRecord record) {
        try {
            log.info("重新发送通知: recordId={}, channel={}, recipientId={}",
                    record.getId(), record.getChannel(), record.getRecipientId());
            NotificationAdapter adapter = record.getChannel() == null ? null : adapters.get(record.getChannel());
            if (adapter == null) {
                record.setErrorMessage("未找到通知适配器: " + record.getChannel());
                log.warn("通知重试缺少适配器: recordId={}, channel={}", record.getId(), record.getChannel());
                return false;
            }

            NotificationAdapter.NotificationSendResult result = adapter.sendNotification(record);
            if (result == null) {
                record.setErrorMessage("重试发送失败: 响应为空");
                return false;
            }
            if (result.isSuccess()) {
                record.setResponseCode(result.getResponseCode());
                record.setResponseMessage(result.getResponseMessage());
                record.setErrorMessage(null);
                return true;
            }

            record.setErrorMessage(result.getErrorMessage());
            return false;

        } catch (Exception e) {
            log.error("重新发送通知异常: recordId={}", record.getId(), e);
            record.setErrorMessage("重试异常: " + e.getMessage());
            return false;
        }
    }

    private void scheduleNextRetry(NotificationRecord record) {
        int delaySeconds = (int) Math.pow(2, record.getRetryCount()) * 60;
        delaySeconds = Math.min(delaySeconds, 30 * 60);

        LocalDateTime nextRetryTime = LocalDateTime.now().plusSeconds(delaySeconds);
        record.setNextRetryTime(nextRetryTime);
        record.setStatus(NotificationStatus.RETRY);

        log.info("安排下次重试: recordId={}, retryCount={}, nextRetryTime={}",
                record.getId(), record.getRetryCount(), nextRetryTime);
    }

    private void applyRetryFailureOutcome(NotificationRecord record) {
        if (safeRetryCount(record) >= safeMaxRetry(record)) {
            record.setStatus(NotificationStatus.FAILED);
            record.setNextRetryTime(null);
            return;
        }
        scheduleNextRetry(record);
    }

    private void triggerFallbackStrategy(NotificationRecord record) {
        try {
            log.info("触发通知回退策略: recordId={}", record.getId());

            String fallbackChannels = record.getFallbackChannels();
            if (fallbackChannels == null || "[]".equals(fallbackChannels)) {
                log.warn("没有配置回退渠道: recordId={}", record.getId());
                return;
            }

            log.info("创建回退通知: originalRecordId={}, fallbackChannels={}", record.getId(), fallbackChannels);

            if (fallbackChannels.contains("sms")) {
                createFallbackNotification(record, NotificationChannel.SMS);
            }
            if (fallbackChannels.contains("email")) {
                createFallbackNotification(record, NotificationChannel.EMAIL);
            }
            if (fallbackChannels.contains("system")) {
                createFallbackNotification(record, NotificationChannel.SYSTEM);
            }

        } catch (Exception e) {
            log.error("触发回退策略异常: recordId={}", record.getId(), e);
        }
    }

    private void createFallbackNotification(NotificationRecord originalRecord, NotificationChannel fallbackChannel) {
        try {
            if (originalRecord == null) {
                log.warn("原始通知记录为空，跳过创建回退通知: fallbackChannel={}", fallbackChannel);
                return;
            }
            log.info("创建回退通知: originalRecordId={}, fallbackChannel={}",
                    originalRecord.getId(), fallbackChannel);

            notificationRouterService.createAndSendFallbackNotification(originalRecord, fallbackChannel);

        } catch (Exception e) {
            log.error("创建回退通知异常: originalRecordId={}, fallbackChannel={}",
                    originalRecord.getId(), fallbackChannel, e);
        }
    }
}
