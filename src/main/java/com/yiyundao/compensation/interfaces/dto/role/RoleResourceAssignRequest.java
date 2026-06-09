package com.yiyundao.compensation.interfaces.dto.role;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色资源分配请求 DTO
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Data
public class RoleResourceAssignRequest {

    /**
     * 资源分配列表
     * <ul>
     *   <li>支持批量分配</li>
     *   <li>为空时表示清空该角色的所有资源</li>
     * </ul>
     */
    @Size(max = 200, message = "单次分配的资源数量不能超过200")
    private List<@Valid ResourceAssignment> resources;

    /**
     * 是否完全替换现有权限
     * <ul>
     *   <li>true - 替换模式：先清空现有权限，再分配新权限</li>
     *   <li>false - 追加模式：在现有权限基础上追加</li>
     * </ul>
     */
    private Boolean replaceExisting = true;

    /**
     * 资源分配项
     */
    @Data
    public static class ResourceAssignment {

        /**
         * 资源ID
         */
        @NotNull(message = "资源ID不能为空")
        private Long resourceId;

        /**
         * 操作权限列表
         * <ul>
         *   <li>为空或包含 ["*"] 表示所有权限</li>
         *   <li>例如：["read", "write", "delete"]</li>
         * </ul>
         */
        @Size(max = 20, message = "操作权限数量不能超过20")
        private List<String> actions;
    }
}
