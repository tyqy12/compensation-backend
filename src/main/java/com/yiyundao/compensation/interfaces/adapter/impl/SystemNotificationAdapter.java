package com.yiyundao.compensation.interfaces.adapter.impl;

import com.yiyundao.compensation.enums.NotificationChannel;
import com.yiyundao.compensation.interfaces.adapter.NotificationAdapter;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 系统通知适配器（站内信）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemNotificationAdapter implements NotificationAdapter {

    @Override
    public NotificationChannel getSupportedChannel() {
        return NotificationChannel.SYSTEM;
    }

    @Override
    public NotificationSendResult sendNotification(NotificationRecord record) {
        try {
            log.info("创建系统通知: recordId={}, userId={}", record.getId(), record.getRecipientId());

            // 系统通知不需要外部API调用，直接存储到数据库
            // 通知记录本身就是系统通知的存储
            // 前端可以通过查询通知记录来显示站内信

            // 模拟站内信创建过程
            createSystemMessage(record);

            return NotificationSendResult.success("SYSTEM", "系统通知创建成功");

        } catch (Exception e) {
            log.error("系统通知创建异常: recordId={}", record.getId(), e);
            return NotificationSendResult.failure("创建异常: " + e.getMessage());
        }
    }

    @Override
    public boolean checkConnection() {
        // 系统通知不依赖外部服务，总是可用
        return true;
    }

    /**
     * 创建系统消息
     */
    private void createSystemMessage(NotificationRecord record) {
        // 在实际项目中，这里可以：
        // 1. 发送WebSocket实时通知给在线用户
        // 2. 更新用户的未读消息计数
        // 3. 触发前端消息提醒

        log.info("系统通知已创建: userId={}, title={}, content={}",
            record.getRecipientId(), record.getTitle(), record.getContent());

        // 如果有WebSocket服务，可以实时推送
        // webSocketService.sendMessageToUser(record.getRecipientId(), record);

        // 更新用户未读消息计数
        // userMessageCountService.incrementUnreadCount(record.getRecipientId());
    }
}