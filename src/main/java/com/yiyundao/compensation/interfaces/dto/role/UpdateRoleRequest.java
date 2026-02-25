package com.yiyundao.compensation.interfaces.dto.role;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新角色请求 DTO
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateRoleRequest extends RoleBaseRequest {

    /**
     * 角色状态
     * <ul>
     *   <li>enabled - 启用</li>
     *   <li>disabled - 禁用</li>
     * </ul>
     */
    @Pattern(regexp = "^(enabled|disabled)$", message = "状态只能是 enabled 或 disabled")
    private String status;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 是否可编辑
     * <ul>
     *   <li>注意：系统保护角色不可修改此字段</li>
     * </ul>
     */
    private Boolean isEditable;
}
