package com.yiyundao.compensation.modules.rbac.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.interfaces.dto.role.CreateRoleRequest;
import com.yiyundao.compensation.interfaces.dto.role.RoleResourceAssignRequest;
import com.yiyundao.compensation.interfaces.dto.role.UpdateRoleRequest;
import com.yiyundao.compensation.interfaces.vo.role.RoleDetailVO;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.entity.SysRole;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色服务接口
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
public interface RoleService extends IService<SysRole> {

    // ==================== 角色 CRUD ====================

    /**
     * 创建角色
     *
     * @param request 创建请求
     * @param operatorId 操作人ID
     * @return 创建的角色
     * @throws BusinessException 角色编码已存在
     */
    SysRole createRole(CreateRoleRequest request, Long operatorId);

    /**
     * 更新角色
     *
     * @param id 角色ID
     * @param request 更新请求
     * @param operatorId 操作人ID
     * @return 更新后的角色
     * @throws BusinessException 角色不存在或为保护角色
     */
    SysRole updateRole(Long id, UpdateRoleRequest request, Long operatorId);

    /**
     * 删除角色
     *
     * @param id 角色ID
     * @param operatorId 操作人ID
     * @throws BusinessException 角色不存在、为保护角色或有关联用户
     */
    void deleteRole(Long id, Long operatorId);

    /**
     * 禁用角色
     *
     * @param id 角色ID
     * @param operatorId 操作人ID
     * @throws BusinessException 角色不存在或为保护角色
     */
    void disableRole(Long id, Long operatorId);

    /**
     * 启用角色
     *
     * @param id 角色ID
     * @param operatorId 操作人ID
     * @throws BusinessException 角色不存在
     */
    void enableRole(Long id, Long operatorId);

    /**
     * 复制角色
     *
     * @param sourceRoleId 源角色ID
     * @param newCode 新角色编码
     * @param newName 新角色名称
     * @param operatorId 操作人ID
     * @return 新角色
     * @throws BusinessException 源角色不存在或新编码已存在
     */
    SysRole copyRole(Long sourceRoleId, String newCode, String newName, Long operatorId);

    // ==================== 角色查询 ====================

    /**
     * 获取角色详情（包含统计信息）
     *
     * @param id 角色ID
     * @return 角色详情
     */
    RoleDetailVO getRoleDetail(Long id);

    /**
     * 根据编码获取角色
     *
     * @param code 角色编码
     * @return 角色
     */
    SysRole getByCode(String code);

    /**
     * 获取所有启用的角色列表
     *
     * @return 角色列表
     */
    List<SysRole> listEnabledRoles();

    /**
     * 获取角色列表（带条件筛选）
     *
     * @param keyword 关键词（匹配编码、名称、描述）
     * @param roleType 角色类型筛选
     * @param status 状态筛选
     * @return 角色列表
     */
    List<SysRole> listRoles(String keyword, String roleType, String status);

    // ==================== 权限分配 ====================

    /**
     * 为角色分配资源
     *
     * @param roleId 角色ID
     * @param request 分配请求
     * @param operatorId 操作人ID
     * @throws BusinessException 角色不存在或为保护角色
     */
    void assignResources(Long roleId, RoleResourceAssignRequest request, Long operatorId);

    /**
     * 撤销角色的资源权限
     *
     * @param roleId 角色ID
     * @param resourceIds 资源ID列表（为空则撤销所有）
     * @param operatorId 操作人ID
     */
    void revokeResources(Long roleId, List<Long> resourceIds, Long operatorId);

    /**
     * 获取角色的资源权限
     *
     * @param roleId 角色ID
     * @return 资源列表（含操作权限）
     */
    Map<SysResource, Set<String>> getRoleResources(Long roleId);

    /**
     * 检查角色是否拥有指定资源
     *
     * @param roleId 角色ID
     * @param resourceId 资源ID
     * @param action 操作权限（可选）
     * @return 是否拥有
     */
    boolean hasResource(Long roleId, Long resourceId, String action);
}
