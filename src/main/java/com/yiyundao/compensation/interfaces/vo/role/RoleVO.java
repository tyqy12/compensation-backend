package com.yiyundao.compensation.interfaces.vo.role;

import com.yiyundao.compensation.modules.rbac.entity.SysRole;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色视图对象
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Data
@NoArgsConstructor
public class RoleVO {

    /**
     * 角色ID
     */
    private Long id;

    /**
     * 角色编码
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
     */
    private String roleType;

    /**
     * 角色类型显示名称
     */
    private String roleTypeDisplayName;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 是否可编辑
     */
    private Boolean isEditable;

    /**
     * 是否保护角色（系统角色）
     */
    private Boolean isProtected;

    /**
     * 角色图标
     */
    private String icon;

    /**
     * 状态
     */
    private String status;

    /**
     * 状态显示名称
     */
    private String statusDisplayName;

    /**
     * 备注
     */
    private String remarks;

    /**
     * 关联用户数量
     */
    private Integer userCount;

    /**
     * 关联资源数量
     */
    private Integer resourceCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    // ==================== 工厂方法 ====================

    /**
     * 从实体转换为 VO
     *
     * @param entity 角色实体
     * @return 角色视图对象
     */
    public static RoleVO fromEntity(SysRole entity) {
        if (entity == null) {
            return null;
        }

        RoleVO vo = new RoleVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setRoleType(entity.getRoleType());
        vo.setRoleTypeDisplayName(entity.getTypeDisplayName());
        vo.setSortOrder(entity.getSortOrder());
        vo.setIsEditable(entity.getIsEditable());
        vo.setIsProtected(entity.isProtected());
        vo.setIcon(entity.getIcon());
        vo.setStatus(entity.getStatus());
        vo.setStatusDisplayName(entity.getStatusDisplayName());
        vo.setRemarks(entity.getRemarks());
        vo.setUserCount(entity.getUserCount());
        vo.setResourceCount(entity.getResourceCount());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());

        return vo;
    }

    /**
     * 从实体列表转换为 VO 列表
     *
     * @param entities 角色实体列表
     * @return 角色视图对象列表
     */
    public static java.util.List<RoleVO> fromEntities(java.util.List<SysRole> entities) {
        return entities.stream()
                .map(RoleVO::fromEntity)
                .toList();
    }
}
