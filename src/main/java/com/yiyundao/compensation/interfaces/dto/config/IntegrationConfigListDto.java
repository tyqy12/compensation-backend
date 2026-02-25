package com.yiyundao.compensation.interfaces.dto.config;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 集成配置列表DTO（用于前端展示配置概览）
 */
@Data
public class IntegrationConfigListDto {
    private String platformType; // 平台类型: wechat, dingtalk, feishu, alipay, sms, email, encryption
    private String platformName; // 平台显示名称
    private Boolean enabled; // 是否启用
    private Boolean configured; // 是否已配置
    private String connectionStatus; // 连接状态: connected, disconnected, unknown
    private LocalDateTime lastModified; // 最后修改时间
}