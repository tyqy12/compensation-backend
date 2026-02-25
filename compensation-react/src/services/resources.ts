import api, { unwrap } from '@services/api';
import type { ApiResponse } from '@types/api';
import type { SysResource } from '@types/api';

export type ResourceTypeFilter = 'MENU' | 'VIEW' | 'ACTION' | 'API';

export interface ResourceInput {
  type: ResourceTypeFilter;
  code: string;
  name: string;
  path?: string | null;
  component?: string | null;
  icon?: string | null;
  parentId?: number | null;
  orderNum?: number | null;
  propsJson?: any;
  status?: 'enabled' | 'disabled';
}

export async function fetchResources(params?: { type?: ResourceTypeFilter }): Promise<SysResource[]> {
  const { data } = await api.get<ApiResponse<SysResource[]>>('/admin/resources/v2/tree', { params });
  return unwrap(data);
}

export async function createResource(payload: ResourceInput): Promise<SysResource> {
  const { data } = await api.post<ApiResponse<SysResource>>('/admin/resources', payload);
  return unwrap(data);
}

export async function updateResource(id: number, payload: ResourceInput): Promise<SysResource> {
  const { data } = await api.put<ApiResponse<SysResource>>(`/admin/resources/${id}`, payload);
  return unwrap(data);
}

export async function deleteResource(id: number): Promise<void> {
  const { data } = await api.delete<ApiResponse<null>>(`/admin/resources/${id}`);
  unwrap(data);
}

export async function sortResources(items: Array<{ id: number; orderNum: number }>): Promise<void> {
  const { data } = await api.post<ApiResponse<null>>('/admin/resources/sort', items);
  unwrap(data);
}

export async function importResources(items: SysResource[]): Promise<{ created: number; updated: number }> {
  const { data } = await api.post<ApiResponse<{ created: number; updated: number }>>('/admin/resources/import', items);
  return unwrap(data);
}

export async function exportResources(): Promise<SysResource[]> {
  const { data } = await api.get<ApiResponse<SysResource[]>>('/admin/resources/export');
  return unwrap(data);
}

