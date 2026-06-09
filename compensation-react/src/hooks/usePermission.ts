/**
 * 权限 Hook
 * 提供权限检查、资源获取等功能
 */

import { useCallback, useMemo } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import api from '@services/api';
import type {
  PermissionConfig,
  Resource,
  ResourceType,
  EffectivePermission,
  CheckPermissionRequest,
  CheckPermissionResult,
} from '@types/auth';
import type { MeResourcesData } from '@types/api';

/**
 * 权限上下文值
 */
export interface PermissionContextValue {
  // 权限状态
  config: PermissionConfig | null;
  version: number | null;
  isLoading: boolean;
  error: Error | null;

  // 刷新方法
  refresh: () => Promise<void>;
  checkPermission: (resourceCode: string, action: string) => boolean;
  hasAnyPermission: (permissions: Array<{ resourceCode: string; action: string }>) => boolean;
  hasAllPermissions: (permissions: Array<{ resourceCode: string; action: string }>) => boolean;

  // 资源方法
  getResources: (type?: ResourceType) => Resource[];
  getResourceByCode: (code: string) => Resource | undefined;
  getResourceByPath: (path: string) => Resource | undefined;

  // 用户操作权限
  userActions: Record<string, string[]>;
  hasAction: (actionCode: string) => boolean;
}

/**
 * 权限 Hook
 */
export function usePermission(): PermissionContextValue {
  const queryClient = useQueryClient();

  // 获取权限配置
  const { data: permissionData, isLoading, error } = useQuery<MeResourcesData>({
    queryKey: ['me', 'resources'],
    queryFn: async () => {
      const { data } = await api.get<{ code: number; message: string; data: MeResourcesData }>(
        '/auth/me/resources'
      );
      if (data.code !== 0) {
        throw new Error(data.message || '获取权限配置失败');
      }
      return data.data;
    },
    staleTime: 5 * 60 * 1000, // 5分钟内不重新请求
  });

  // 权限版本
  const version = permissionData?.permissionVersion ?? null;

  // 将后端数据转换为 PermissionConfig 格式
  const config = useMemo((): PermissionConfig | null => {
    if (!permissionData) return null;

    return {
      version: permissionData.permissionVersion,
      lastUpdate: new Date().toISOString(),
      roles: [],
      userPermissions: [],
    };
  }, [permissionData]);

  // 用户操作权限映射
  const userActions = useMemo((): Record<string, string[]> => {
    return permissionData?.actions ?? {};
  }, [permissionData]);

  const actionsByResourceKey = useMemo((): Record<string, string[]> => {
    if (!permissionData) return {};

    const indexedActions: Record<string, string[]> = { ...permissionData.actions };
    for (const resource of permissionData.resources) {
      const actions = permissionData.actions[String(resource.id)] ?? [];
      if (actions.length === 0) continue;

      indexedActions[String(resource.id)] = actions;
      if (resource.code) indexedActions[resource.code] = actions;
      if (resource.path) indexedActions[resource.path] = actions;
    }
    return indexedActions;
  }, [permissionData]);

  // 权限检查
  const checkPermission = useCallback(
    (resourceCode: string, action: string): boolean => {
      if (!permissionData) return false;

      // 检查用户操作权限
      const actions = actionsByResourceKey[resourceCode] ?? [];

      if (actions.length === 0) return false;

      // 特殊动作：'*' 表示所有权限
      if (actions.includes('*')) return true;

      return actions.includes(action);
    },
    [permissionData, actionsByResourceKey]
  );

  // 检查任意权限
  const hasAnyPermission = useCallback(
    (permissions: Array<{ resourceCode: string; action: string }>): boolean => {
      return permissions.some((p) => checkPermission(p.resourceCode, p.action));
    },
    [checkPermission]
  );

  // 检查全部权限
  const hasAllPermissions = useCallback(
    (permissions: Array<{ resourceCode: string; action: string }>): boolean => {
      return permissions.every((p) => checkPermission(p.resourceCode, p.action));
    },
    [checkPermission]
  );

  // 检查单个操作权限
  const hasAction = useCallback(
    (actionCode: string): boolean => {
      if (!permissionData) return false;
      return Object.values(userActions).some((actions) => actions.includes(actionCode));
    },
    [permissionData, userActions]
  );

  // 刷新权限
  const refresh = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: ['me', 'resources'] });
  }, [queryClient]);

  // 获取资源
  const getResources = useCallback(
    (type?: ResourceType): Resource[] => {
      if (!permissionData) return [];

      const allResources = permissionData.resources;

      if (type) {
        return allResources.filter((r) => r.type === type);
      }

      return allResources;
    },
    [permissionData]
  );

  // 根据 Code 获取资源
  const getResourceByCode = useCallback(
    (code: string): Resource | undefined => {
      if (!permissionData) return undefined;
      return permissionData.resources.find((r) => r.code === code);
    },
    [permissionData]
  );

  // 根据 Path 获取资源
  const getResourceByPath = useCallback(
    (path: string): Resource | undefined => {
      if (!permissionData) return undefined;
      return permissionData.resources.find((r) => r.path === path);
    },
    [permissionData]
  );

  return {
    config,
    version,
    isLoading,
    error: error ?? null,
    refresh,
    checkPermission,
    hasAnyPermission,
    hasAllPermissions,
    getResources,
    getResourceByCode,
    getResourceByPath,
    userActions,
    hasAction,
  };
}

/**
 * 获取有效权限（合并角色和用户权限）
 * @deprecated 该方法保留用于向后兼容，新代码请使用 usePermission
 */
function mapLegacyEffectivePermissions(
  _config: PermissionConfig,
  _userActions: Record<string, string[]>
): EffectivePermission[] {
  // 实际实现中，权限合并逻辑应该在后端完成
  // 前端直接使用后端返回的 actions 数据
  return [];
}

/**
 * 批量检查权限
 */
export async function batchCheckPermissions(
  request: CheckPermissionRequest
): Promise<CheckPermissionResult> {
  const { data } = await api.post<{
    code: number;
    message: string;
    data: CheckPermissionResult;
  }>('/auth-center/permissions/check', request);

  if (data.code !== 0) {
    throw new Error(data.message || '权限检查失败');
  }

  return data.data;
}

/**
 * 获取用户有效权限
 */
export async function getEffectivePermissions(
  userId: number,
  resourceType?: ResourceType
): Promise<EffectivePermission[]> {
  const params = resourceType ? { resourceType } : {};
  const { data } = await api.get<{
    code: number;
    message: string;
    data: EffectivePermission[];
  }>(`/auth-center/users/${userId}/effective-permissions`, { params });

  if (data.code !== 0) {
    throw new Error(data.message || '获取有效权限失败');
  }

  return data.data;
}
