import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  listRoles,
  listEnabledRoles,
  getRoleDetail,
  createRole,
  updateRole,
  deleteRole,
  disableRole,
  enableRole,
  copyRole,
  getRoleResources,
  assignRoleResources,
  revokeRoleResources,
} from '@services/adminAuth';
import { qk } from '@types/api';
import type { RoleListParams, CreateRoleRequest, UpdateRoleRequest, RoleResourceAssignRequest } from '@types/api';

// ==================== Query Hooks ====================

/**
 * 获取角色列表
 */
export function useRolesQuery(params?: RoleListParams) {
  return useQuery({
    queryKey: qk.roleList(params),
    queryFn: () => listRoles(params),
  });
}

/**
 * 获取启用的角色列表
 */
export function useEnabledRolesQuery() {
  return useQuery({
    queryKey: qk.roleEnabledList,
    queryFn: () => listEnabledRoles(),
    staleTime: 5 * 60 * 1000, // 5分钟内不重新请求
  });
}

/**
 * 获取角色详情
 */
export function useRoleDetailQuery(id: number) {
  return useQuery({
    queryKey: qk.roleDetail(id),
    queryFn: () => getRoleDetail(id),
    enabled: !!id,
  });
}

/**
 * 获取角色资源权限
 */
export function useRoleResourcesQuery(id: number) {
  return useQuery({
    queryKey: qk.roleResources(id),
    queryFn: () => getRoleResources(id),
    enabled: !!id,
  });
}

// ==================== Mutation Hooks ====================

/**
 * 创建角色
 */
export function useCreateRoleMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateRoleRequest) => createRole(request),
    onSuccess: () => {
      // 清除角色列表缓存，触发重新获取
      queryClient.invalidateQueries({ queryKey: ['admin', 'roles', 'list'] });
      queryClient.invalidateQueries({ queryKey: qk.roleEnabledList });
    },
  });
}

/**
 * 更新角色
 */
export function useUpdateRoleMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: UpdateRoleRequest }) =>
      updateRole(id, request),
    onSuccess: (_, variables) => {
      // 清除相关缓存
      queryClient.invalidateQueries({ queryKey: ['admin', 'roles', 'list'] });
      queryClient.invalidateQueries({ queryKey: qk.roleDetail(variables.id) });
      queryClient.invalidateQueries({ queryKey: qk.roleEnabledList });
    },
  });
}

/**
 * 删除角色
 */
export function useDeleteRoleMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => deleteRole(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'roles', 'list'] });
      queryClient.invalidateQueries({ queryKey: qk.roleEnabledList });
    },
  });
}

/**
 * 禁用角色
 */
export function useDisableRoleMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => disableRole(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'roles', 'list'] });
      queryClient.invalidateQueries({ queryKey: qk.roleDetail(id) });
      queryClient.invalidateQueries({ queryKey: qk.roleEnabledList });
    },
  });
}

/**
 * 启用角色
 */
export function useEnableRoleMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => enableRole(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'roles', 'list'] });
      queryClient.invalidateQueries({ queryKey: qk.roleDetail(id) });
      queryClient.invalidateQueries({ queryKey: qk.roleEnabledList });
    },
  });
}

/**
 * 复制角色
 */
export function useCopyRoleMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, newCode, newName }: { id: number; newCode: string; newName: string }) =>
      copyRole(id, newCode, newName),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'roles', 'list'] });
      queryClient.invalidateQueries({ queryKey: qk.roleEnabledList });
    },
  });
}

/**
 * 分配角色资源权限
 */
export function useAssignRoleResourcesMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: RoleResourceAssignRequest }) =>
      assignRoleResources(id, request),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: qk.roleResources(variables.id) });
      // 权限变更后清除用户权限缓存
      queryClient.invalidateQueries({ queryKey: ['me', 'resources'] });
    },
  });
}

/**
 * 撤销角色资源权限
 */
export function useRevokeRoleResourcesMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, resourceIds }: { id: number; resourceIds?: number[] }) =>
      revokeRoleResources(id, resourceIds),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: qk.roleResources(variables.id) });
      // 权限变更后清除用户权限缓存
      queryClient.invalidateQueries({ queryKey: ['me', 'resources'] });
    },
  });
}
