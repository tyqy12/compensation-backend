package com.yiyundao.compensation.interfaces.dto.config;

import lombok.Data;

/**
 * 邮件服务配置DTO
 */
@Data
public class EmailConfigDto {
    private String host; // SMTP服务器
    private Integer port; // 端口
    private String username; // 用户名
    private String password; // 密码
    private String fromAddress; // 发件人地址
    private String fromName; // 发件人名称
    private Boolean ssl; // 是否启用SSL
    private Boolean tls; // 是否启用TLS
    private String encoding; // 编码
    private Boolean enabled; // 是否启用
}