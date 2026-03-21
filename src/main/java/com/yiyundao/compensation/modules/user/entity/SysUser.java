package com.yiyundao.compensation.modules.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import com.yiyundao.compensation.enums.UserStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;
    private String password;

    @TableField("real_name")
    private String realName;

    private String email;
    private String phone;
    private String avatar;
    private UserStatus status;
    private String roles;

    @TableField("employee_id")
    private Long employeeId;

    @TableField(exist = false)
    private String subjectId;

    @TableField(exist = false)
    private String provider;

    @Deprecated
    @TableField(exist = false)
    private String platformUserId;

    @Deprecated
    @TableField(exist = false)
    private String platformType;

    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    @TableField("last_login_ip")
    private String lastLoginIp;

    @TableField("permission_version")
    private Integer permissionVersion;

    public String getSubjectId() {
        return subjectId != null ? subjectId : platformUserId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
        this.platformUserId = subjectId;
    }

    public String getProvider() {
        return provider != null ? provider : platformType;
    }

    public void setProvider(String provider) {
        this.provider = provider;
        this.platformType = provider;
    }

    @Deprecated
    public String getPlatformUserId() {
        return platformUserId != null ? platformUserId : subjectId;
    }

    @Deprecated
    public void setPlatformUserId(String platformUserId) {
        this.platformUserId = platformUserId;
        this.subjectId = platformUserId;
    }

    @Deprecated
    public String getPlatformType() {
        return platformType != null ? platformType : provider;
    }

    @Deprecated
    public void setPlatformType(String platformType) {
        this.platformType = platformType;
        this.provider = platformType;
    }
}
