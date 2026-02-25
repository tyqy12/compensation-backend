package com.yiyundao.compensation.modules.rbac.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.rbac.entity.SysUserResource;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户资源服务接口
 * <p>
 * 处理用户个性资源分配（独立于角色之外的个性化权限）
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
public interface UserResourceService extends IService<SysUserResource> {

    /**
     * 为用户分配资源（覆盖模式）
     *
     * @param userId 用户ID
     * @param resourceIds 资源ID列表
     * @param actionsMap 资源ID到操作权限列表的映射
     * @param operatorId 操作人ID
     */
    void assignResources(Long userId, List<Long> resourceIds, Map<Long, List<String>> actionsMap, Long operatorId);

    /**
     * 为用户分配资源（追加模式）
     *
     * @param userId 用户ID
     * @param resourceIds 资源ID列表
     * @param actionsMap 资源ID到操作权限列表的映射
     * @param operatorId 操作人ID
     */
    void addResources(Long userId, List<Long> resourceIds, Map<Long, List<String>> actionsMap, Long operatorId);

    /**
     * 撤销用户资源
     *
     * @param userId 用户ID
     * @param resourceIds 资源ID列表（为空则撤销所有）
     * @param operatorId 操作人ID
     */
    void revokeResources(Long userId, List<Long> resourceIds, Long operatorId);

    /**
     * 获取用户的资源权限
     *
     * @param userId 用户ID
     * @return 资源ID到操作权限集合的映射
     */
    Map<Long, Set<String>> getUserResources(Long userId);

    /**
     * 清除用户资源缓存
     *
     * @param userId 用户ID
     */
    void clearUserCache(Long userId);
}
