package com.yiyundao.compensation.modules.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_resource")
public class SysRoleResource extends BaseEntity {
    @TableField("role_id")
    private Long roleId;
    @TableField("resource_id")
    private Long resourceId;
    @TableField("actions_json")
    private String actionsJson; // JSON 数组字符串
}

