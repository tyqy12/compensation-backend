package com.yiyundao.compensation.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("integration_config")
public class IntegrationConfig extends BaseEntity {
    private String platformType; // wechat/dingtalk/feishu/alipay/yunzhanghu
    private String configJson;   // 平台配置JSON
    private Boolean enabled;     // 是否启用
}
