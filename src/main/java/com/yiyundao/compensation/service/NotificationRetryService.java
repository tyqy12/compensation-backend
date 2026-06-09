package com.yiyundao.compensation.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yiyundao.compensation.common.utils.DataAccessExceptionUtils;
import com.yiyundao.compensation.enums.NotificationStatus;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import com.yiyundao.compensation.modules.notification.service.NotificationRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final NotificationRetryExecutor retryExecutor;

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
                retryExecutor.processRetryRecord(record);
            }

            log.info("重试通知处理完成");

        } catch (Exception e) {
            if (DataAccessExceptionUtils.isResourceFailure(e)) {
                log.warn("数据库暂不可用，跳过本轮通知重试: {}", e.getMessage());
                return;
            }
            log.error("处理重试通知异常", e);
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
            LocalDateTime nextRetryTime = LocalDateTime.now();
            if (!claimManualRetry(record, nextRetryTime)) {
                log.info("通知记录已被其他任务处理，跳过手动重试: recordId={}", recordId);
                return;
            }
            record.setRetryCount(0);
            record.setNextRetryTime(nextRetryTime);
            record.setStatus(NotificationStatus.RETRY);
            record.setErrorMessage(null);

            // 异步处理重试
            retryExecutor.processRetryRecord(record);

        } catch (Exception e) {
            log.error("手动重试通知异常: recordId={}", recordId, e);
        }
    }

    private boolean claimManualRetry(NotificationRecord record, LocalDateTime nextRetryTime) {
        if (record == null || record.getId() == null) {
            return false;
        }
        return notificationRecordService.update(new UpdateWrapper<NotificationRecord>()
                .eq("id", record.getId())
                .in("status", NotificationStatus.FAILED.getCode(), NotificationStatus.RETRY.getCode())
                .set("retry_count", 0)
                .set("next_retry_time", nextRetryTime)
                .set("status", NotificationStatus.RETRY.getCode())
                .set("error_message", null));
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

            boolean cancelled = notificationRecordService.update(new UpdateWrapper<NotificationRecord>()
                    .eq("id", recordId)
                    .in("status", NotificationStatus.RETRY.getCode(), NotificationStatus.SENDING.getCode())
                    .set("status", NotificationStatus.CANCELLED.getCode())
                    .set("next_retry_time", null));
            if (!cancelled) {
                log.info("通知记录状态已变更，跳过取消重试: recordId={}, status={}", recordId, record.getStatus());
                return;
            }

            log.info("已取消通知重试: recordId={}", recordId);

        } catch (Exception e) {
            log.error("取消重试异常: recordId={}", recordId, e);
        }
    }
}
