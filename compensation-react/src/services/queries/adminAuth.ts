import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  listAdminRoles,
  searchAdminUsers,
  getRoleResources,
  putRoleResources,
  getUserResources,
  getUserAggregateResources,
  putUserResources,
  getUserRoles,
  setUserRoles,
  listPermissionActions,
  type PermissionActionCatalogItem,
  type UserAggregateQueryParams,
} from '@services/adminAuth';

export function useAdminRolesQuery() {
  return useQuery({
    queryKey: ['admin', 'roles', 'list'],
    queryFn: () => listAdminRoles(),
  });
}

export function useUserAggregateSearchQuery(params: UserAggregateQueryParams) {
  return useQuery({
    queryKey: ['admin', 'users', 'aggregate', params],
    queryFn: () => searchAdminUsers(params),
  });
}

export function useRoleResourcesQuery(roleId: number | undefined) {
  return useQuery({
    queryKey: ['admin', 'roles', roleId, 'resources'],
    queryFn: () => getRoleResources(roleId as number),
    enabled: !!roleId,
  });
}

export function usePutRoleResourcesMutation(roleId: number) {
  return useMutation({
    mutationFn: (body: { resourceIds: number[]; actions: Record<string, string[]> }) => putRoleResources(roleId, body),
  });
}

export function useUserResourcesQuery(userId: number | undefined) {
  return useQuery({
    queryKey: ['admin', 'users', userId, 'resources'],
    queryFn: () => getUserResources(userId as number),
    enabled: !!userId,
  });
}

/**
 * 获取用户聚合权限（角色权限 + 个性化权限）
 */
export function useUserAggregateResourcesQuery(userId: number | undefined) {
  return useQuery({
    queryKey: ['admin', 'users', userId, 'aggregate-resources'],
    queryFn: () => getUserAggregateResources(userId as number),
    enabled: !!userId,
  });
}

export function usePutUserResourcesMutation(userId: number) {
  return useMutation({
    mutationFn: (body: { resourceIds: number[]; actions: Record<string, string[]> }) => putUserResources(userId, body),
  });
}

// ==================== 用户角色管理 Hooks ====================

/**
 * 获取用户已有的角色ID列表
 */
export function useUserRolesQuery(userId: number | undefined) {
  return useQuery({
    queryKey: ['admin', 'users', userId, 'roles'],
    queryFn: () => getUserRoles(userId as number),
    enabled: !!userId,
  });
}

/**
 * 设置用户角色（覆盖式）
 */
export function useSetUserRolesMutation(userId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (roleIds: number[]) => setUserRoles(userId, roleIds),
    onSuccess: () => {
      // 清除相关缓存
      queryClient.invalidateQueries({ queryKey: ['admin', 'users', userId, 'roles'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'users', 'aggregate'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'users', userId, 'resources'] });
    },
  });
}

export function usePermissionActionsQuery() {
  return useQuery<PermissionActionCatalogItem[]>({
    queryKey: ['admin', 'permission-actions'],
    queryFn: () => listPermissionActions({ status: 'enabled' }),
    staleTime: 0,
  });
}
