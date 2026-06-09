package com.yiyundao.compensation.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
        String normalizedProvider = normalizeProvider(provider);
        NotificationChannel channel = convertPlatformToChannel(normalizedProvider);
        if (channel == null) {
            log.warn("不支持的平台类型: {}", provider);
            return;
        }
        if (!hasAdapter(channel)) {
            log.warn("平台通知渠道未注册适配器: provider={}, channel={}", provider, channel);
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

        List<NotificationChannel> availableChannels = filterAvailableChannels(channels);
        if (availableChannels.isEmpty() && hasAdapter(NotificationChannel.SYSTEM)) {
            availableChannels.add(NotificationChannel.SYSTEM);
        }
        if (availableChannels.isEmpty()) {
            log.warn("用户没有可用通知渠道: userId={}, type={}", user.getId(), type);
        }
        return availableChannels;
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
        String normalizedProvider = normalizeProvider(provider);
        if (normalizedProvider == null) return null;

        return switch (normalizedProvider) {
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

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        String value = provider.trim().toLowerCase();
        return switch (value) {
            case "wechat", "wecom", "qywx", "wx" -> "wechat";
            case "dingtalk", "dingding", "dd" -> "dingtalk";
            case "feishu", "lark" -> "feishu";
            default -> value;
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
        if (primaryChannel != NotificationChannel.SMS
                && StringUtils.hasText(user.getPhone())
                && hasAdapter(NotificationChannel.SMS)) {
            fallbacks.add(NotificationChannel.SMS);
        }

        // 如果主渠道不是邮件且有邮箱，添加邮件作为回退
        if (primaryChannel != NotificationChannel.EMAIL
                && StringUtils.hasText(user.getEmail())
                && hasAdapter(NotificationChannel.EMAIL)) {
            fallbacks.add(NotificationChannel.EMAIL);
        }

        // 最后添加系统通知作为兜底
        if (primaryChannel != NotificationChannel.SYSTEM && hasAdapter(NotificationChannel.SYSTEM)) {
            fallbacks.add(NotificationChannel.SYSTEM);
        }

        return fallbacks;
    }

    private List<NotificationChannel> filterAvailableChannels(List<NotificationChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            return new ArrayList<>();
        }
        return channels.stream()
                .filter(this::hasAdapter)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean hasAdapter(NotificationChannel channel) {
        return channel != null && adapters.containsKey(channel);
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
        try {
            LocalDateTime sendTime = LocalDateTime.now();
            if (!claimPendingRecord(record, sendTime)) {
                log.info("通知记录已被其他任务处理，跳过发送: recordId={}", record.getId());
                return;
            }
            record.setStatus(NotificationStatus.SENDING);
            record.setSendTime(sendTime);

            NotificationAdapter adapter = adapters.get(record.getChannel());
            if (adapter == null) {
                record.setErrorMessage("未找到通知适配器: " + record.getChannel());
                log.warn("未找到通知适配器: recordId={}, channel={}", record.getId(), record.getChannel());
                applyFailedNotificationOutcome(record);
                completeSendAndTriggerFallback(record);
                return;
            }

            NotificationAdapter.NotificationSendResult result = adapter.sendNotification(record);

            if (result != null && result.isSuccess()) {
                record.setStatus(NotificationStatus.SUCCESS);
                record.setNextRetryTime(null);
                record.setResponseCode(result.getResponseCode());
                record.setResponseMessage(result.getResponseMessage());
                record.setErrorMessage(null);
            } else {
                record.setErrorMessage(result == null ? "发送失败: 响应为空" : result.getErrorMessage());
                applyFailedNotificationOutcome(record);
            }

            completeSendAndTriggerFallback(record);

        } catch (Exception e) {
            log.error("发送通知异常: recordId={}", record.getId(), e);
            record.setErrorMessage("发送异常: " + e.getMessage());
            applyFailedNotificationOutcome(record);
            completeSendAndTriggerFallback(record);
        }
    }

    private boolean claimPendingRecord(NotificationRecord record, LocalDateTime sendTime) {
        if (record == null || record.getId() == null) {
            return false;
        }
        return notificationRecordService.update(new UpdateWrapper<NotificationRecord>()
                .eq("id", record.getId())
                .eq("status", NotificationStatus.PENDING.getCode())
                .set("status", NotificationStatus.SENDING.getCode())
                .set("send_time", sendTime));
    }

    /**
     * 处理失败的通知
     */
    private void applyFailedNotificationOutcome(NotificationRecord record) {
        // 如果还有重试次数，安排重试
        if (safeRetryCount(record) < safeMaxRetry(record)) {
            scheduleRetry(record);
        } else {
            record.setStatus(NotificationStatus.FAILED);
            record.setNextRetryTime(null);
        }
    }

    /**
     * 安排重试
     */
    private void scheduleRetry(NotificationRecord record) {
        int retryCount = safeRetryCount(record);
        record.setRetryCount(retryCount);
        record.setStatus(NotificationStatus.RETRY);

        // 计算下次重试时间（指数退避）
        int nextAttempt = retryCount + 1;
        int delay = (int) Math.pow(2, nextAttempt) * 60; // 秒
        record.setNextRetryTime(LocalDateTime.now().plusSeconds(delay));

        log.info("安排通知重试: recordId={}, retryCount={}, nextRetryTime={}",
            record.getId(), record.getRetryCount(), record.getNextRetryTime());
    }

    private void completeSendAndTriggerFallback(NotificationRecord record) {
        if (!completeSendingRecord(record)) {
            log.info("通知记录状态已变更，跳过发送结果回写: recordId={}, finalStatus={}",
                    record.getId(), record.getStatus());
            return;
        }
        if (record.getStatus() == NotificationStatus.FAILED) {
            // 重试次数用完且终态写入成功后，才创建回退通知，避免取消后仍触发回退。
            triggerFallbackNotification(record);
        }
    }

    private boolean completeSendingRecord(NotificationRecord record) {
        if (record == null || record.getId() == null || record.getStatus() == null) {
            return false;
        }
        return notificationRecordService.update(new UpdateWrapper<NotificationRecord>()
                .eq("id", record.getId())
                .eq("status", NotificationStatus.SENDING.getCode())
                .set("status", record.getStatus().getCode())
                .set("retry_count", safeRetryCount(record))
                .set("next_retry_time", record.getNextRetryTime())
                .set("response_code", record.getResponseCode())
                .set("response_message", record.getResponseMessage())
                .set("error_message", record.getErrorMessage()));
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
    public void createAndSendFallbackNotification(NotificationRecord originalRecord, NotificationChannel fallbackChannel) {
        createFallbackNotification(originalRecord, fallbackChannel);
    }

    private void createFallbackNotification(NotificationRecord originalRecord, NotificationChannel fallbackChannel) {
        if (originalRecord == null) {
            log.warn("原始通知记录为空，跳过创建回退通知: fallbackChannel={}", fallbackChannel);
            return;
        }
        if (!hasAdapter(fallbackChannel)) {
            log.warn("回退通知渠道未注册适配器，跳过创建: originalRecordId={}, fallbackChannel={}",
                    originalRecord.getId(), fallbackChannel);
            return;
        }
        String fallbackRecipientId = resolveFallbackRecipientId(originalRecord, fallbackChannel);
        if (!StringUtils.hasText(fallbackRecipientId)) {
            log.warn("回退通知缺少有效接收人，跳过创建: originalRecordId={}, fallbackChannel={}",
                    originalRecord.getId(), fallbackChannel);
            return;
        }

        NotificationRecord fallbackRecord = new NotificationRecord();
        fallbackRecord.setNotificationType(originalRecord.getNotificationType());
        fallbackRecord.setChannel(fallbackChannel);
        fallbackRecord.setRecipientId(fallbackRecipientId);
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

    private String resolveFallbackRecipientId(NotificationRecord originalRecord, NotificationChannel fallbackChannel) {
        if (originalRecord == null || fallbackChannel == null) {
            return null;
        }
        SysUser user = resolveOriginalRecipientUser(originalRecord);
        if (user == null) {
            return sameRecipientChannel(originalRecord.getChannel(), fallbackChannel)
                    ? originalRecord.getRecipientId()
                    : null;
        }
        return getRecipientId(user, fallbackChannel);
    }

    private SysUser resolveOriginalRecipientUser(NotificationRecord originalRecord) {
        if (originalRecord == null || !StringUtils.hasText(originalRecord.getRecipientId())) {
            return null;
        }
        if (originalRecord.getChannel() == NotificationChannel.SYSTEM) {
            return resolveUserById(originalRecord.getRecipientId());
        }
        String provider = providerForChannel(originalRecord.getChannel());
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        ExternalIdentity identity = externalIdentityService.findActiveIdentity(
                provider,
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                originalRecord.getRecipientId()
        );
        if (identity == null || identity.getUserId() == null) {
            return null;
        }
        return sysUserService.getById(identity.getUserId());
    }

    private SysUser resolveUserById(String recipientId) {
        try {
            return sysUserService.getById(Long.valueOf(recipientId));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean sameRecipientChannel(NotificationChannel originalChannel, NotificationChannel fallbackChannel) {
        return originalChannel == fallbackChannel
                || (originalChannel == NotificationChannel.SMS && fallbackChannel == NotificationChannel.SMS)
                || (originalChannel == NotificationChannel.EMAIL && fallbackChannel == NotificationChannel.EMAIL);
    }

    private int safeRetryCount(NotificationRecord record) {
        return record == null || record.getRetryCount() == null || record.getRetryCount() < 0
                ? 0
                : record.getRetryCount();
    }

    private int safeMaxRetry(NotificationRecord record) {
        return record == null || record.getMaxRetry() == null || record.getMaxRetry() < 0
                ? 3
                : record.getMaxRetry();
    }
}
