import api, { unwrap } from '@services/api';
import type { ApiResponse } from '@types/api';
import type { MeResourcesData, MeActionsData, SysResource } from '@types/api';

// Auth: Me
export async function getMeResources(): Promise<MeResourcesData> {
  const { data } = await api.get<ApiResponse<MeResourcesData>>('/auth/me/resources');
  return unwrap(data);
}

export async function getMeActions(): Promise<MeActionsData> {
  const { data } = await api.get<ApiResponse<MeActionsData>>('/auth/me/actions');
  return unwrap(data);
}

// Resource Admin (basic APIs; UI may call these via query/mutation hooks when built)
export async function listResources(params?: { type?: 'MENU' | 'VIEW' | 'ACTION' | 'API' }): Promise<SysResource[]> {
  const { data } = await api.get<ApiResponse<SysResource[]>>('/admin/resources/v2/tree', { params });
  return unwrap(data);
}

