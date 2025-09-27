package com.yiyundao.compensation.modules.audit.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("audit_log")
public class AuditLog extends BaseEntity {

    @TableField("user_id")
    private Long userId;

    private String username;
    private String operation;
    private String method;

    @TableField("request_url")
    private String requestUrl;

    @TableField("request_ip")
    private String requestIp;

    @TableField("user_agent")
    private String userAgent;

    @TableField("request_params")
    private String requestParams;

    @TableField("response_result")
    private String responseResult;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("execution_time")
    private Long executionTime;

    @TableField("business_type")
    private String businessType;

    @TableField("business_key")
    private String businessKey;
}

