package com.yiyundao.compensation.modules.notification.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import com.yiyundao.compensation.enums.NotificationChannel;
import com.yiyundao.compensation.enums.NotificationType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification_template")
public class NotificationTemplate extends BaseEntity {

    @TableField("template_code")
    private String templateCode; // 模板代码，唯一标识

    @TableField("template_name")
    private String templateName; // 模板名称

    @TableField("notification_type")
    private NotificationType notificationType;

    @TableField("channel")
    private NotificationChannel channel;

    @TableField("title_template")
    private String titleTemplate; // 标题模板

    @TableField("content_template")
    private String contentTemplate; // 内容模板

    @TableField("external_template_id")
    private String externalTemplateId; // 第三方平台模板ID

    @TableField("enabled")
    private Boolean enabled; // 是否启用

    @TableField("priority")
    private Integer priority; // 优先级

    @TableField("description")
    private String description; // 描述
}