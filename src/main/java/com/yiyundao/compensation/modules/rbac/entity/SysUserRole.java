package com.yiyundao.compensation.modules.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户角色关联实体
 * <p>
 * 记录用户与角色的关联关系，支持完整的审计追踪。
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_role")
public class SysUserRole extends BaseEntity {

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 角色ID
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 授权人ID（谁授予了这个角色）
     */
    @TableField("granted_by")
    private Long grantedBy;

    /**
     * 授权时间
     */
    @TableField("granted_at")
    private LocalDateTime grantedAt;

    /**
     * 过期时间（可选，支持临时授权）
     */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /**
     * 备注信息
     */
    private String remarks;

    /**
     * 删除人（用于审计追踪）
     */
    @TableField("delete_by")
    private String deleteBy;

    /**
     * 删除时间
     */
    @TableField("delete_time")
    private LocalDateTime deleteTime;

    /**
     * 判断角色是否有效（未过期且未禁用）
     */
    public boolean isEffective() {
        // 检查是否过期
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            return false;
        }
        return true;
    }

    /**
     * 判断是否即将过期（7天内）
     */
    public boolean isExpiringSoon() {
        if (expiresAt == null) {
            return false;
        }
        LocalDateTime sevenDaysLater = LocalDateTime.now().plusDays(7);
        return expiresAt.isAfter(LocalDateTime.now()) && expiresAt.isBefore(sevenDaysLater);
    }
}

