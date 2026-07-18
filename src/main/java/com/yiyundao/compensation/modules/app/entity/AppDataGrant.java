package com.yiyundao.compensation.modules.app.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 第三方应用的数据对象授权。scope 只决定能调用什么，grant 才决定能看到哪些数据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_data_grant")
public class AppDataGrant extends BaseEntity {

    @TableField("app_id")
    private Long appId;

    @TableField("scope_type")
    private String scopeType;

    @TableField("scope_value")
    private String scopeValue;

    private String status;
}
