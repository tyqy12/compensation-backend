package com.yiyundao.compensation.interfaces.dto.config;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 集成配置详情DTO（用于前端展示单个配置详情）
 */
@Data
public class IntegrationConfigDetailDto {
    private String platformType; // 平台类型
    private String platformName; // 平台显示名称
    private Boolean enabled; // 是否启用
    private Object config; // 具体配置内容（根据平台类型返回对应的DTO）
    private String connectionStatus; // 连接状态: connected, disconnected, unknown
    private LocalDateTime lastModified; // 最后修改时间
}