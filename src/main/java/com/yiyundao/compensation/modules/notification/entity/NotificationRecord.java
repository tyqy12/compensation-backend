package com.yiyundao.compensation.modules.notification.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import com.yiyundao.compensation.enums.NotificationChannel;
import com.yiyundao.compensation.enums.NotificationStatus;
import com.yiyundao.compensation.enums.NotificationType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification_record")
public class NotificationRecord extends BaseEntity {

    @TableField("notification_type")
    private NotificationType notificationType;

    @TableField("channel")
    private NotificationChannel channel;

    @TableField("recipient_id")
    private String recipientId; // 接收人ID（用户ID、平台用户ID、手机号等）

    @TableField("recipient_name")
    private String recipientName; // 接收人姓名

    @TableField("title")
    private String title; // 通知标题

    @TableField("content")
    private String content; // 通知内容

    @TableField("template_id")
    private String templateId; // 模板ID（可选）

    @TableField("template_params")
    private String templateParams; // 模板参数（JSON格式）

    @TableField("business_type")
    private String businessType; // 业务类型（PAYMENT、APPROVAL等）

    @TableField("business_key")
    private String businessKey; // 业务关键字（batch_no、approval_id等）

    @TableField("status")
    private NotificationStatus status;

    @TableField("retry_count")
    private Integer retryCount; // 重试次数

    @TableField("max_retry")
    private Integer maxRetry; // 最大重试次数

    @TableField("next_retry_time")
    private LocalDateTime nextRetryTime; // 下次重试时间

    @TableField("send_time")
    private LocalDateTime sendTime; // 实际发送时间

    @TableField("response_code")
    private String responseCode; // 响应码

    @TableField("response_message")
    private String responseMessage; // 响应消息

    @TableField("error_message")
    private String errorMessage; // 错误信息

    @TableField("priority")
    private Integer priority; // 优先级（数字越大优先级越高）

    @TableField("fallback_channels")
    private String fallbackChannels; // 失败回退渠道（JSON数组）

    @TableField("is_read")
    private Boolean isRead; // 是否已读

    @TableField("read_time")
    private LocalDateTime readTime; // 读取时间
}