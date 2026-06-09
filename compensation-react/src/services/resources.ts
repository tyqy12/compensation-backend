import api, { unwrap } from '@services/api';
import type { ApiResponse } from '@types/api';
import type { SysResource } from '@types/api';

export type ResourceTypeFilter = 'MENU' | 'VIEW' | 'ACTION' | 'API';
const RESOURCE_API_PREFIX = '/admin/resources/v2';

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
  // 权限配置页需要完整资源清单；tree 接口仅返回根节点，导致子级/API 资源丢失
  const { data } = await api.get<ApiResponse<SysResource[]>>(`${RESOURCE_API_PREFIX}/list`, { params });
  return unwrap(data);
}

export async function createResource(payload: ResourceInput): Promise<SysResource> {
  const { data } = await api.post<ApiResponse<SysResource>>(RESOURCE_API_PREFIX, payload);
  return unwrap(data);
}

export async function updateResource(id: number, payload: ResourceInput): Promise<SysResource> {
  const { data } = await api.put<ApiResponse<SysResource>>(`${RESOURCE_API_PREFIX}/${id}`, payload);
  return unwrap(data);
}

export async function deleteResource(id: number): Promise<void> {
  const { data } = await api.delete<ApiResponse<null>>(`${RESOURCE_API_PREFIX}/${id}`);
  unwrap(data);
}

export async function sortResources(items: Array<{ id: number; orderNum: number }>): Promise<void> {
  const { data } = await api.post<ApiResponse<null>>(`${RESOURCE_API_PREFIX}/sort`, items);
  unwrap(data);
}

export async function importResources(items: SysResource[]): Promise<{ created: number; updated: number }> {
  const { data } = await api.post<ApiResponse<{ created: number; updated: number }>>(`${RESOURCE_API_PREFIX}/import`, items);
  return unwrap(data);
}

export async function exportResources(): Promise<SysResource[]> {
  const { data } = await api.get<ApiResponse<SysResource[]>>(`${RESOURCE_API_PREFIX}/export`);
  return unwrap(data);
}
