package com.yiyundao.compensation.modules.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.enums.NotificationChannel;
import com.yiyundao.compensation.enums.NotificationStatus;
import com.yiyundao.compensation.enums.NotificationType;
import com.yiyundao.compensation.infrastructure.dao.NotificationRecordMapper;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import com.yiyundao.compensation.modules.notification.service.NotificationRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class NotificationRecordServiceImpl extends ServiceImpl<NotificationRecordMapper, NotificationRecord>
        implements NotificationRecordService {

    @Override
    public Page<NotificationRecord> pageNotificationRecords(int pageNum, int pageSize,
                                                           NotificationType type,
                                                           NotificationChannel channel,
                                                           NotificationStatus status,
                                                           String businessType,
                                                           String recipientName,
                                                           LocalDateTime startTime,
                                                           LocalDateTime endTime) {
        Page<NotificationRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<NotificationRecord> queryWrapper = new LambdaQueryWrapper<>();

        // 条件查询
        if (type != null) {
            queryWrapper.eq(NotificationRecord::getNotificationType, type);
        }
        if (channel != null) {
            queryWrapper.eq(NotificationRecord::getChannel, channel);
        }
        if (status != null) {
            queryWrapper.eq(NotificationRecord::getStatus, status);
        }
        if (businessType != null && !businessType.trim().isEmpty()) {
            queryWrapper.eq(NotificationRecord::getBusinessType, businessType);
        }
        if (recipientName != null && !recipientName.trim().isEmpty()) {
            queryWrapper.like(NotificationRecord::getRecipientName, recipientName);
        }
        if (startTime != null) {
            queryWrapper.ge(NotificationRecord::getCreateTime, startTime);
        }
        if (endTime != null) {
            queryWrapper.le(NotificationRecord::getCreateTime, endTime);
        }

        // 按创建时间倒序
        queryWrapper.orderByDesc(NotificationRecord::getCreateTime);

        return page(page, queryWrapper);
    }

    @Override
    public List<NotificationRecord> getPendingRetryRecords() {
        LambdaQueryWrapper<NotificationRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NotificationRecord::getStatus, NotificationStatus.RETRY)
                .le(NotificationRecord::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(NotificationRecord::getNextRetryTime);

        return list(queryWrapper);
    }

    @Override
    public List<NotificationRecord> getByBusinessKey(String businessType, String businessKey) {
        LambdaQueryWrapper<NotificationRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NotificationRecord::getBusinessType, businessType)
                .eq(NotificationRecord::getBusinessKey, businessKey)
                .orderByDesc(NotificationRecord::getCreateTime);

        return list(queryWrapper);
    }

    @Override
    public List<NotificationRecord> getUserNotifications(String recipientId, int limit) {
        LambdaQueryWrapper<NotificationRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NotificationRecord::getRecipientId, recipientId)
                .orderByDesc(NotificationRecord::getCreateTime)
                .last("limit " + limit);

        return list(queryWrapper);
    }

    @Override
    public void markAsRead(Long recordId) {
        LambdaUpdateWrapper<NotificationRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(NotificationRecord::getId, recordId)
                .set(NotificationRecord::getIsRead, true)
                .set(NotificationRecord::getReadTime, LocalDateTime.now());

        update(updateWrapper);
    }

    @Override
    public void markAsRead(List<Long> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return;
        }

        LambdaUpdateWrapper<NotificationRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(NotificationRecord::getId, recordIds)
                .set(NotificationRecord::getIsRead, true)
                .set(NotificationRecord::getReadTime, LocalDateTime.now());

        update(updateWrapper);
    }

    @Override
    public void resendNotification(Long recordId) {
        NotificationRecord record = getById(recordId);
        if (record == null) {
            log.warn("通知记录不存在: {}", recordId);
            return;
        }

        // 重置状态为待发送
        LambdaUpdateWrapper<NotificationRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(NotificationRecord::getId, recordId)
                .set(NotificationRecord::getStatus, NotificationStatus.PENDING)
                .set(NotificationRecord::getRetryCount, 0)
                .set(NotificationRecord::getNextRetryTime, null)
                .set(NotificationRecord::getErrorMessage, null);

        update(updateWrapper);
        log.info("通知记录已重置为待发送状态: {}", recordId);
    }

    @Override
    public void cancelNotification(Long recordId) {
        LambdaUpdateWrapper<NotificationRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(NotificationRecord::getId, recordId)
                .set(NotificationRecord::getStatus, NotificationStatus.CANCELLED);

        update(updateWrapper);
        log.info("通知记录已取消: {}", recordId);
    }
}