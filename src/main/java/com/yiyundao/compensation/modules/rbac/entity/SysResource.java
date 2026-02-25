package com.yiyundao.compensation.modules.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_resource")
public class SysResource extends BaseEntity {
    private String type;          // MENU/VIEW/ACTION/API
    private String code;          // 全局唯一
    private String name;          // 名称
    private String path;          // 路由或接口路径
    private String component;     // 前端组件
    private String icon;          // 图标
    @TableField("parent_id")
    private Long parentId;        // 父资源
    @TableField("order_num")
    private Integer orderNum;     // 排序
    @TableField("props_json")
    private String propsJson;     // 扩展
    private String status;        // enabled/disabled
}

