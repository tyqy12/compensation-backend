package com.yiyundao.compensation.interfaces.vo.role;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 角色详情视图对象
 * <p>
 * 包含角色的详细信息及统计信息
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RoleDetailVO extends RoleVO {

    /**
     * 关联用户数量
     */
    private Integer userCount;

    /**
     * 关联资源数量
     */
    private Integer resourceCount;

    /**
     * 关联的资源列表（简要信息）
     */
    private List<ResourceBriefVO> resources;

    /**
     * 资源简要信息
     */
    @Data
    @NoArgsConstructor
    public static class ResourceBriefVO {
        private Long id;
        private String code;
        private String name;
        private String type;
        private List<String> actions;
    }
}
