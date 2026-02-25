package com.yiyundao.compensation.modules.org.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("org_department")
public class Department extends BaseEntity {

    @TableField("platform_type")
    private String platformType; // wechat/dingtalk/feishu

    @TableField("platform_dept_id")
    private String platformDeptId; // 第三方部门ID

    private String name;

    @TableField("parent_platform_dept_id")
    private String parentPlatformDeptId;

    @TableField("parent_id")
    private Long parentId; // 本地父ID（可空）

    @TableField("order_num")
    private Integer orderNum;
}

