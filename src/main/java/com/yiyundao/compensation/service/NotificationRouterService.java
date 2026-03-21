package com.yiyundao.compensation.service;

import com.yiyundao.compensation.enums.NotificationChannel;
import com.yiyundao.compensation.enums.NotificationStatus;
import com.yiyundao.compensation.enums.NotificationType;
import com.yiyundao.compensation.interfaces.adapter.NotificationAdapter;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import com.yiyundao.compensation.modules.notification.service.NotificationRecordService;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 通知路由服务
 * 负责根据用户配置选择合适的通知渠道，并处理失败回退
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRouterService {

    private final NotificationRecordService notificationRecordService;
    private final SysUserService sysUserService;
    private final ExternalIdentityService externalIdentityService;
    private final Map<NotificationChannel, NotificationAdapter> adapters;

    /**
     * 发送通知给指定用户
     */
    public void sendNotificationToUser(Long userId, NotificationType type, String title, String content,
                                     String businessType, String businessKey) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            log.warn("用户不存在，无法发送通知: userId={}", userId);
            return;
        }

        // 确定通知渠道策略
        List<NotificationChannel> channels = determineNotificationChannels(user, type);

        for (NotificationChannel channel : channels) {
            NotificationRecord record = createNotificationRecord(
                type, channel, user, title, content, businessType, businessKey);

            // 保存通知记录
            notificationRecordService.save(record);

            // 异步发送通知
            sendNotificationAsync(record);
        }
    }

    /**
     * 发送通知给平台用户
     */
    public void sendNotificationToPlatformUser(String provider, String subjectId,
                                             NotificationType type, String title, String content,
                                             String businessType, String businessKey) {
        NotificationChannel channel = convertPlatformToChannel(provider);
        if (channel == null) {
            log.warn("不支持的平台类型: {}", provider);
            return;
        }

        NotificationRecord record = createNotificationRecord(
            type, channel, subjectId, title, content, businessType, businessKey);

        // 保存通知记录
        notificationRecordService.save(record);

        // 异步发送通知
        sendNotificationAsync(record);
    }

    /**
     * 确定通知渠道策略
     */
    private List<NotificationChannel> determineNotificationChannels(SysUser user, NotificationType type) {
        List<NotificationChannel> channels = new ArrayList<>();

        // 优先使用用户绑定的平台
        ExternalIdentity primaryIdentity = externalIdentityService.findPrimaryByUserId(user.getId());
        if (primaryIdentity != null && StringUtils.hasText(primaryIdentity.getProvider())) {
            NotificationChannel platformChannel = convertPlatformToChannel(primaryIdentity.getProvider());
            if (platformChannel != null) {
                channels.add(platformChannel);
            }
        }

        // 重要通知添加回退渠道
        if (isImportantNotification(type)) {
            if (StringUtils.hasText(user.getPhone())) {
                channels.add(NotificationChannel.SMS);
            }
            if (StringUtils.hasText(user.getEmail())) {
                channels.add(NotificationChannel.EMAIL);
            }
        }

        // 如果没有任何渠道，使用系统通知
        if (channels.isEmpty()) {
            channels.add(NotificationChannel.SYSTEM);
        }

        return channels;
    }

    /**
     * 判断是否为重要通知
     */
    private boolean isImportantNotification(NotificationType type) {
        return type == NotificationType.PAYMENT_SUCCESS ||
               type == NotificationType.PAYMENT_FAILED ||
               type == NotificationType.BATCH_COMPLETE ||
               type == NotificationType.SYSTEM_ALERT;
    }

    /**
     * 平台类型转换为通知渠道
     */
    private NotificationChannel convertPlatformToChannel(String provider) {
        if (provider == null) return null;

        return switch (provider.toLowerCase()) {
            case "wechat" -> NotificationChannel.WECHAT;
            case "dingtalk" -> NotificationChannel.DINGTALK;
            case "feishu" -> NotificationChannel.FEISHU;
            default -> null;
        };
    }

    /**
     * 创建通知记录
     */
    private NotificationRecord createNotificationRecord(NotificationType type, NotificationChannel channel,
                                                       SysUser user, String title, String content,
                                                       String businessType, String businessKey) {
        NotificationRecord record = new NotificationRecord();
        record.setNotificationType(type);
        record.setChannel(channel);
        record.setRecipientId(getRecipientId(user, channel));
        record.setRecipientName(user.getUsername());
        record.setTitle(title);
        record.setContent(content);
        record.setBusinessType(businessType);
        record.setBusinessKey(businessKey);
        record.setStatus(NotificationStatus.PENDING);
        record.setRetryCount(0);
        record.setMaxRetry(3);
        record.setPriority(getPriority(type));

        // 设置回退渠道
        List<NotificationChannel> fallbackChannels = getFallbackChannels(user, channel);
        record.setFallbackChannels(convertChannelsToJson(fallbackChannels));

        return record;
    }

    /**
     * 创建通知记录（平台用户）
     */
    private NotificationRecord createNotificationRecord(NotificationType type, NotificationChannel channel,
                                                       String recipientId, String title, String content,
                                                       String businessType, String businessKey) {
        NotificationRecord record = new NotificationRecord();
        record.setNotificationType(type);
        record.setChannel(channel);
        record.setRecipientId(recipientId);
        record.setTitle(title);
        record.setContent(content);
        record.setBusinessType(businessType);
        record.setBusinessKey(businessKey);
        record.setStatus(NotificationStatus.PENDING);
        record.setRetryCount(0);
        record.setMaxRetry(3);
        record.setPriority(getPriority(type));

        return record;
    }

    /**
     * 获取接收人ID
     */
    private String getRecipientId(SysUser user, NotificationChannel channel) {
        return switch (channel) {
            case WECHAT, DINGTALK, FEISHU -> resolvePlatformRecipientId(user.getId(), channel);
            case SMS -> user.getPhone();
            case EMAIL -> user.getEmail();
            case SYSTEM -> user.getId().toString();
        };
    }

    private String resolvePlatformRecipientId(Long userId, NotificationChannel channel) {
        if (userId == null) {
            return null;
        }
        String provider = providerForChannel(channel);
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        ExternalIdentity identity = externalIdentityService.findByUserIdAndProvider(userId, provider);
        return identity != null ? identity.getSubjectId() : null;
    }

    private String providerForChannel(NotificationChannel channel) {
        if (channel == null) {
            return null;
        }
        return switch (channel) {
            case WECHAT -> "wechat";
            case DINGTALK -> "dingtalk";
            case FEISHU -> "feishu";
            default -> null;
        };
    }

    /**
     * 获取通知优先级
     */
    private Integer getPriority(NotificationType type) {
        return switch (type) {
            case SYSTEM_ALERT -> 10;
            case PAYMENT_FAILED -> 8;
            case PAYMENT_SUCCESS -> 6;
            case BATCH_COMPLETE -> 5;
            case APPROVAL_PENDING, APPROVAL_APPROVED, APPROVAL_REJECTED -> 4;
            default -> 1;
        };
    }

    /**
     * 获取回退渠道
     */
    private List<NotificationChannel> getFallbackChannels(SysUser user, NotificationChannel primaryChannel) {
        List<NotificationChannel> fallbacks = new ArrayList<>();

        // 如果主渠道不是短信且有手机号，添加短信作为回退
        if (primaryChannel != NotificationChannel.SMS && StringUtils.hasText(user.getPhone())) {
            fallbacks.add(NotificationChannel.SMS);
        }

        // 如果主渠道不是邮件且有邮箱，添加邮件作为回退
        if (primaryChannel != NotificationChannel.EMAIL && StringUtils.hasText(user.getEmail())) {
            fallbacks.add(NotificationChannel.EMAIL);
        }

        // 最后添加系统通知作为兜底
        if (primaryChannel != NotificationChannel.SYSTEM) {
            fallbacks.add(NotificationChannel.SYSTEM);
        }

        return fallbacks;
    }

    /**
     * 渠道列表转JSON
     */
    private String convertChannelsToJson(List<NotificationChannel> channels) {
        if (channels.isEmpty()) return "[]";

        String channelCodes = channels.stream()
            .map(NotificationChannel::getCode)
            .collect(Collectors.joining("\",\"", "[\"", "\"]"));

        return channelCodes;
    }

    /**
     * 异步发送通知
     */
    private void sendNotificationAsync(NotificationRecord record) {
        // 这里可以使用消息队列或线程池异步处理
        // 暂时使用简单的异步调用
        NotificationAdapter adapter = adapters.get(record.getChannel());
        if (adapter == null) {
            log.warn("未找到通知适配器: channel={}", record.getChannel());
            return;
        }

        try {
            record.setStatus(NotificationStatus.SENDING);
            record.setSendTime(LocalDateTime.now());
            notificationRecordService.updateById(record);

            NotificationAdapter.NotificationSendResult result = adapter.sendNotification(record);

            if (result.isSuccess()) {
                record.setStatus(NotificationStatus.SUCCESS);
                record.setResponseCode(result.getResponseCode());
                record.setResponseMessage(result.getResponseMessage());
            } else {
                record.setStatus(NotificationStatus.FAILED);
                record.setErrorMessage(result.getErrorMessage());

                // 如果发送失败，尝试回退渠道
                handleFailedNotification(record);
            }

            notificationRecordService.updateById(record);

        } catch (Exception e) {
            log.error("发送通知异常: recordId={}", record.getId(), e);
            record.setStatus(NotificationStatus.FAILED);
            record.setErrorMessage("发送异常: " + e.getMessage());
            notificationRecordService.updateById(record);

            handleFailedNotification(record);
        }
    }

    /**
     * 处理失败的通知
     */
    private void handleFailedNotification(NotificationRecord record) {
        // 如果还有重试次数，安排重试
        if (record.getRetryCount() < record.getMaxRetry()) {
            scheduleRetry(record);
        } else {
            // 重试次数用完，尝试回退渠道
            triggerFallbackNotification(record);
        }
    }

    /**
     * 安排重试
     */
    private void scheduleRetry(NotificationRecord record) {
        record.setRetryCount(record.getRetryCount() + 1);
        record.setStatus(NotificationStatus.RETRY);

        // 计算下次重试时间（指数退避）
        int delay = (int) Math.pow(2, record.getRetryCount()) * 60; // 秒
        record.setNextRetryTime(LocalDateTime.now().plusSeconds(delay));

        notificationRecordService.updateById(record);

        log.info("安排通知重试: recordId={}, retryCount={}, nextRetryTime={}",
            record.getId(), record.getRetryCount(), record.getNextRetryTime());
    }

    /**
     * 触发回退通知
     */
    private void triggerFallbackNotification(NotificationRecord record) {
        String fallbackChannelsJson = record.getFallbackChannels();
        if (!StringUtils.hasText(fallbackChannelsJson) || "[]".equals(fallbackChannelsJson)) {
            log.warn("没有可用的回退渠道: recordId={}", record.getId());
            return;
        }

        // 解析回退渠道并发送
        // 这里简化处理，实际应该解析JSON
        log.info("触发回退通知: recordId={}, fallbackChannels={}", record.getId(), fallbackChannelsJson);

        // 创建短信回退通知
        if (fallbackChannelsJson.contains("sms")) {
            createFallbackNotification(record, NotificationChannel.SMS);
        }

        // 创建邮件回退通知
        if (fallbackChannelsJson.contains("email")) {
            createFallbackNotification(record, NotificationChannel.EMAIL);
        }

        // 创建系统通知回退
        if (fallbackChannelsJson.contains("system")) {
            createFallbackNotification(record, NotificationChannel.SYSTEM);
        }
    }

    /**
     * 创建回退通知
     */
    private void createFallbackNotification(NotificationRecord originalRecord, NotificationChannel fallbackChannel) {
        NotificationRecord fallbackRecord = new NotificationRecord();
        fallbackRecord.setNotificationType(originalRecord.getNotificationType());
        fallbackRecord.setChannel(fallbackChannel);
        fallbackRecord.setRecipientId(originalRecord.getRecipientId());
        fallbackRecord.setRecipientName(originalRecord.getRecipientName());
        fallbackRecord.setTitle(originalRecord.getTitle());
        fallbackRecord.setContent(originalRecord.getContent() + "\n\n[通过回退渠道发送]");
        fallbackRecord.setBusinessType(originalRecord.getBusinessType());
        fallbackRecord.setBusinessKey(originalRecord.getBusinessKey());
        fallbackRecord.setStatus(NotificationStatus.PENDING);
        fallbackRecord.setRetryCount(0);
        fallbackRecord.setMaxRetry(1); // 回退通知只重试1次
        fallbackRecord.setPriority(originalRecord.getPriority());

        notificationRecordService.save(fallbackRecord);
        sendNotificationAsync(fallbackRecord);

        log.info("创建回退通知: originalRecordId={}, fallbackChannel={}, fallbackRecordId={}",
            originalRecord.getId(), fallbackChannel, fallbackRecord.getId());
    }
}
