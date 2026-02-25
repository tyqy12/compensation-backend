package com.yiyundao.compensation.interfaces.dto.role;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色基础请求 DTO
 * <p>
 * 包含角色基本信息的请求字段
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Data
public class RoleBaseRequest {

    /**
     * 角色名称
     */
    @Size(min = 2, max = 100, message = "角色名称长度必须在2-100之间")
    private String name;

    /**
     * 角色描述
     */
    @Size(max = 500, message = "角色描述长度不能超过500")
    private String description;

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
}
