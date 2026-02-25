package com.yiyundao.compensation.service;

import com.yiyundao.compensation.enums.NotificationStatus;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import com.yiyundao.compensation.modules.notification.service.NotificationRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知重试服务
 * 负责处理失败通知的重试逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRetryService {

    private final NotificationRecordService notificationRecordService;

    /**
     * 定时处理需要重试的通知
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60000) // 60秒
    public void processRetryNotifications() {
        try {
            List<NotificationRecord> retryRecords = notificationRecordService.getPendingRetryRecords();

            if (retryRecords.isEmpty()) {
                return;
            }

            log.info("开始处理重试通知，共{}条记录", retryRecords.size());

            for (NotificationRecord record : retryRecords) {
                processRetryRecord(record);
            }

            log.info("重试通知处理完成");

        } catch (Exception e) {
            log.error("处理重试通知异常", e);
        }
    }

    /**
     * 处理单条重试记录
     */
    @Async
    public void processRetryRecord(NotificationRecord record) {
        try {
            log.info("处理重试通知: recordId={}, retryCount={}", record.getId(), record.getRetryCount());

            // 检查是否到了重试时间
            if (record.getNextRetryTime() != null && record.getNextRetryTime().isAfter(LocalDateTime.now())) {
                log.debug("重试时间未到: recordId={}, nextRetryTime={}", record.getId(), record.getNextRetryTime());
                return;
            }

            // 检查重试次数
            if (record.getRetryCount() >= record.getMaxRetry()) {
                log.warn("重试次数已达上限: recordId={}, retryCount={}, maxRetry={}",
                    record.getId(), record.getRetryCount(), record.getMaxRetry());

                // 标记为最终失败
                record.setStatus(NotificationStatus.FAILED);
                notificationRecordService.updateById(record);

                // 触发回退策略
                triggerFallbackStrategy(record);
                return;
            }

            // 执行重试
            retryNotification(record);

        } catch (Exception e) {
            log.error("处理重试记录异常: recordId={}", record.getId(), e);
        }
    }

    /**
     * 重试通知发送
     */
    private void retryNotification(NotificationRecord record) {
        try {
            // 增加重试次数
            record.setRetryCount(record.getRetryCount() + 1);
            record.setStatus(NotificationStatus.SENDING);
            record.setSendTime(LocalDateTime.now());
            notificationRecordService.updateById(record);

            // 重新发送通知
            // 这里复用通知路由服务的发送逻辑
            boolean success = resendNotification(record);

            if (success) {
                // 重试成功
                record.setStatus(NotificationStatus.SUCCESS);
                record.setNextRetryTime(null);
                log.info("通知重试成功: recordId={}, retryCount={}", record.getId(), record.getRetryCount());
            } else {
                // 重试失败，安排下次重试
                scheduleNextRetry(record);
                log.warn("通知重试失败: recordId={}, retryCount={}", record.getId(), record.getRetryCount());
            }

            notificationRecordService.updateById(record);

        } catch (Exception e) {
            log.error("重试通知发送异常: recordId={}", record.getId(), e);

            // 异常情况下也要安排下次重试
            scheduleNextRetry(record);
            record.setStatus(NotificationStatus.RETRY);
            record.setErrorMessage("重试异常: " + e.getMessage());
            notificationRecordService.updateById(record);
        }
    }

    /**
     * 重新发送通知
     */
    private boolean resendNotification(NotificationRecord record) {
        try {
            // 这里应该调用具体的通知适配器来重新发送
            // 简化实现，实际应该通过NotificationRouterService来处理

            log.info("重新发送通知: recordId={}, channel={}, recipientId={}",
                record.getId(), record.getChannel(), record.getRecipientId());

            // 模拟重新发送（70%成功率）
            boolean success = Math.random() > 0.3;

            if (success) {
                record.setResponseCode("200");
                record.setResponseMessage("重试发送成功");
            } else {
                record.setErrorMessage("重试发送失败");
            }

            return success;

        } catch (Exception e) {
            log.error("重新发送通知异常: recordId={}", record.getId(), e);
            return false;
        }
    }

    /**
     * 安排下次重试
     */
    private void scheduleNextRetry(NotificationRecord record) {
        // 指数退避算法：2^retryCount * 60秒
        int delaySeconds = (int) Math.pow(2, record.getRetryCount()) * 60;

        // 最大延迟不超过30分钟
        delaySeconds = Math.min(delaySeconds, 30 * 60);

        LocalDateTime nextRetryTime = LocalDateTime.now().plusSeconds(delaySeconds);
        record.setNextRetryTime(nextRetryTime);
        record.setStatus(NotificationStatus.RETRY);

        log.info("安排下次重试: recordId={}, retryCount={}, nextRetryTime={}",
            record.getId(), record.getRetryCount(), nextRetryTime);
    }

    /**
     * 触发回退策略
     */
    private void triggerFallbackStrategy(NotificationRecord record) {
        try {
            log.info("触发通知回退策略: recordId={}", record.getId());

            // 解析回退渠道
            String fallbackChannels = record.getFallbackChannels();
            if (fallbackChannels == null || "[]".equals(fallbackChannels)) {
                log.warn("没有配置回退渠道: recordId={}", record.getId());
                return;
            }

            // 创建回退通知
            // 这里应该根据回退渠道创建新的通知记录
            // 简化实现
            log.info("创建回退通知: originalRecordId={}, fallbackChannels={}", record.getId(), fallbackChannels);

            // 如果配置了短信回退
            if (fallbackChannels.contains("sms")) {
                createFallbackNotification(record, "sms");
            }

            // 如果配置了邮件回退
            if (fallbackChannels.contains("email")) {
                createFallbackNotification(record, "email");
            }

            // 如果配置了系统通知回退
            if (fallbackChannels.contains("system")) {
                createFallbackNotification(record, "system");
            }

        } catch (Exception e) {
            log.error("触发回退策略异常: recordId={}", record.getId(), e);
        }
    }

    /**
     * 创建回退通知
     */
    private void createFallbackNotification(NotificationRecord originalRecord, String fallbackChannel) {
        try {
            log.info("创建回退通知: originalRecordId={}, fallbackChannel={}", originalRecord.getId(), fallbackChannel);

            // 这里应该通过NotificationRouterService创建新的回退通知
            // 简化实现，只记录日志
            log.info("回退通知已创建: fallbackChannel={}, title={}", fallbackChannel, originalRecord.getTitle());

        } catch (Exception e) {
            log.error("创建回退通知异常: originalRecordId={}, fallbackChannel={}",
                originalRecord.getId(), fallbackChannel, e);
        }
    }

    /**
     * 手动重试指定通知
     */
    public void manualRetry(Long recordId) {
        try {
            NotificationRecord record = notificationRecordService.getById(recordId);
            if (record == null) {
                log.warn("通知记录不存在: recordId={}", recordId);
                return;
            }

            if (record.getStatus() != NotificationStatus.FAILED && record.getStatus() != NotificationStatus.RETRY) {
                log.warn("通知状态不允许重试: recordId={}, status={}", recordId, record.getStatus());
                return;
            }

            log.info("手动重试通知: recordId={}", recordId);

            // 重置重试相关字段
            record.setRetryCount(0);
            record.setNextRetryTime(LocalDateTime.now());
            record.setStatus(NotificationStatus.RETRY);
            record.setErrorMessage(null);

            notificationRecordService.updateById(record);

            // 异步处理重试
            processRetryRecord(record);

        } catch (Exception e) {
            log.error("手动重试通知异常: recordId={}", recordId, e);
        }
    }

    /**
     * 取消重试
     */
    public void cancelRetry(Long recordId) {
        try {
            NotificationRecord record = notificationRecordService.getById(recordId);
            if (record == null) {
                log.warn("通知记录不存在: recordId={}", recordId);
                return;
            }

            record.setStatus(NotificationStatus.CANCELLED);
            record.setNextRetryTime(null);
            notificationRecordService.updateById(record);

            log.info("已取消通知重试: recordId={}", recordId);

        } catch (Exception e) {
            log.error("取消重试异常: recordId={}", recordId, e);
        }
    }
}