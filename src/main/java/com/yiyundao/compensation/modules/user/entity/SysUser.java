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

    @TableField("platform_user_id")
    private String platformUserId;

    @TableField("platform_type")
    private String platformType;

    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    @TableField("last_login_ip")
    private String lastLoginIp;

    @TableField("permission_version")
    private Integer permissionVersion;
}
