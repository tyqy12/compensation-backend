package com.yiyundao.compensation.modules.notification.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.enums.NotificationChannel;
import com.yiyundao.compensation.enums.NotificationStatus;
import com.yiyundao.compensation.enums.NotificationType;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRecordService extends IService<NotificationRecord> {

    /**
     * 分页查询通知记录
     */
    Page<NotificationRecord> pageNotificationRecords(int pageNum, int pageSize,
                                                    NotificationType type,
                                                    NotificationChannel channel,
                                                    NotificationStatus status,
                                                    String businessType,
                                                    String recipientName,
                                                    LocalDateTime startTime,
                                                    LocalDateTime endTime);

    /**
     * 获取需要重试的通知记录
     */
    List<NotificationRecord> getPendingRetryRecords();

    /**
     * 按业务查询通知记录
     */
    List<NotificationRecord> getByBusinessKey(String businessType, String businessKey);

    /**
     * 获取用户的通知记录
     */
    List<NotificationRecord> getUserNotifications(String recipientId, int limit);

    /**
     * 标记通知为已读
     */
    void markAsRead(Long recordId);

    /**
     * 批量标记为已读
     */
    void markAsRead(List<Long> recordIds);

    /**
     * 重新发送通知
     */
    void resendNotification(Long recordId);

    /**
     * 取消通知
     */
    void cancelNotification(Long recordId);
}