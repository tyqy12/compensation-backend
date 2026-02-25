package com.yiyundao.compensation.modules.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_registry")
public class AppRegistry extends BaseEntity {

    private String appName;
    private String clientId;
    private String clientSecret;  // 数据库字段名为 client_secret
    private String scopes;
    private String ipWhitelist;
    private String webhookUrl;
    private String status;
    private Boolean rateLimitEnabled;  // 新增字段
    private Integer rateLimitPerMinute;  // 新增字段
}
