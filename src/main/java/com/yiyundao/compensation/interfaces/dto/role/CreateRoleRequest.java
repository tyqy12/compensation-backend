package com.yiyundao.compensation.interfaces.dto.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建角色请求 DTO
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Data
public class CreateRoleRequest {

    /**
     * 角色编码
     * <ul>
     *   <li>必填</li>
     *   <li>格式：英文字母、数字、下划线</li>
     *   <li>长度：2-50</li>
     * </ul>
     */
    @NotBlank(message = "角色编码不能为空")
    @Size(min = 2, max = 50, message = "角色编码长度必须在2-50之间")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$", message = "角色编码必须以字母开头，只能包含字母、数字和下划线")
    private String code;

    /**
     * 角色名称
     * <ul>
     *   <li>必填</li>
     *   <li>长度：2-100</li>
     * </ul>
     */
    @NotBlank(message = "角色名称不能为空")
    @Size(min = 2, max = 100, message = "角色名称长度必须在2-100之间")
    private String name;

    /**
     * 角色描述
     */
    @Size(max = 500, message = "角色描述长度不能超过500")
    private String description;

    /**
     * 角色类型
     * <ul>
     *   <li>BUSINESS - 业务角色（默认）</li>
     *   <li>CUSTOM - 自定义角色</li>
     * </ul>
     */
    @Pattern(regexp = "^(BUSINESS|CUSTOM)$", message = "角色类型只能是 BUSINESS 或 CUSTOM")
    private String roleType = "BUSINESS";

    /**
     * 排序号
     */
    private Integer sortOrder = 0;

    /**
     * 角色图标
     */
    @Size(max = 100, message = "图标标识长度不能超过100")
    private String icon;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过500")
    private String remarks;

    /**
     * 初始分配的资源ID列表（可选）
     */
    private List<Long> resourceIds;

    /**
     * 初始分配资源的操作权限（可选）
     * 例如：["read", "write", "delete"]
     */
    private List<String> actions;
}
