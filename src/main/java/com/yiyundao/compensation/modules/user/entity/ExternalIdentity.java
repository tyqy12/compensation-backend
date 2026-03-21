package com.yiyundao.compensation.modules.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("external_identity")
public class ExternalIdentity extends BaseEntity {

    private String provider;

    @TableField("tenant_key")
    private String tenantKey;

    @TableField("subject_type")
    private String subjectType;

    @TableField("subject_id")
    private String subjectId;

    @TableField("employee_id")
    private Long employeeId;

    @TableField("user_id")
    private Long userId;

    @TableField("is_primary")
    private Boolean primaryFlag;

    private String status;
    private String source;

    @TableField("bound_at")
    private LocalDateTime boundAt;

    @TableField("unbound_at")
    private LocalDateTime unboundAt;

    @TableField("last_seen_at")
    private LocalDateTime lastSeenAt;

    @TableField("ext_json")
    private String extJson;
}
