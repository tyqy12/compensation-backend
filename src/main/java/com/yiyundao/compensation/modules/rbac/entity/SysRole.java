package com.yiyundao.compensation.modules.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体
 * <p>
 * 支持系统角色、业务角色和自定义角色的管理
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /**
     * 角色编码（全局唯一）
     */
    private String code;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 角色类型
     * <ul>
     *   <li>SYSTEM - 系统内置角色，不可删除不可编辑</li>
     *   <li>BUSINESS - 业务角色，可编辑</li>
     *   <li>CUSTOM - 自定义角色，完全可编辑</li>
     * </ul>
     */
    @TableField("role_type")
    private String roleType;

    /**
     * 排序号（用于前端展示排序）
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 是否可编辑
     * <ul>
     *   <li>true - 可编辑</li>
     *   <li>false - 系统保护角色，不可编辑</li>
     * </ul>
     */
    @TableField("is_editable")
    private Boolean isEditable;

    /**
     * 角色图标（前端展示用）
     */
    private String icon;

    /**
     * 备注信息
     */
    private String remarks;

    /**
     * 状态
     * <ul>
     *   <li>enabled - 启用</li>
     *   <li>disabled - 禁用</li>
     * </ul>
     */
    private String status;

    /**
     * 关联用户数量（瞬态字段，不映射到数据库）
     */
    @TableField(exist = false)
    private Integer userCount;

    /**
     * 关联资源数量（瞬态字段,不映射到数据库）
     */
    @TableField(exist = false)
    private Integer resourceCount;

    // ==================== 枚举定义 ====================

    /**
     * 角色类型枚举
     */
    public enum RoleType {
        /** 系统内置角色 */
        SYSTEM,
        /** 业务角色 */
        BUSINESS,
        /** 自定义角色 */
        CUSTOM;

        public static RoleType fromString(String value) {
            if (value == null) {
                return CUSTOM;
            }
            try {
                return RoleType.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return CUSTOM;
            }
        }

        public String getDescription() {
            return switch (this) {
                case SYSTEM -> "系统内置角色";
                case BUSINESS -> "业务角色";
                case CUSTOM -> "自定义角色";
            };
        }
    }

    /**
     * 状态枚举
     */
    public enum Status {
        ENABLED("enabled", "启用"),
        DISABLED("disabled", "禁用");

        private final String code;
        private final String description;

        Status(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public static Status fromCode(String code) {
            for (Status s : values()) {
                if (s.code.equals(code)) {
                    return s;
                }
            }
            return ENABLED;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断是否为系统保护角色
     *
     * @return 是否不可编辑
     */
    public boolean isProtected() {
        return Boolean.FALSE.equals(isEditable) || RoleType.SYSTEM.name().equals(roleType);
    }

    /**
     * 判断是否已启用
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return Status.ENABLED.getCode().equals(status);
    }

    /**
     * 获取角色类型的显示名称
     *
     * @return 类型描述
     */
    public String getTypeDisplayName() {
        return RoleType.fromString(roleType).getDescription();
    }

    /**
     * 获取状态的显示名称
     *
     * @return 状态描述
     */
    public String getStatusDisplayName() {
        return Status.fromCode(status).getDescription();
    }
}
