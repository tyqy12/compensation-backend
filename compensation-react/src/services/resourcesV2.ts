import api, { unwrap } from '@services/api';
import type { ApiResponse } from '@types/api';

export type ResourceTypeFilter = 'MENU' | 'VIEW' | 'ACTION' | 'API';

export interface ResourceV2 {
  id: number;
  type: ResourceTypeFilter;
  code: string;
  name: string;
  path?: string | null;
  component?: string | null;
  icon?: string | null;
  parentId?: number | null;
  parentCode?: string | null; // 父资源代码（导入时使用）
  orderNum?: number | null;
  meta?: Record<string, any> | null;
  status?: 'enabled' | 'disabled' | number | null;
  _children?: number[]; // 子资源ID列表（树结构中）
}

export type ResourceV2Input = Omit<ResourceV2, 'id' | '_children'>;

const PREFIX = '/admin/resources/v2';

export async function fetchResourcesV2(params?: { type?: ResourceTypeFilter }): Promise<ResourceV2[]> {
  const { data } = await api.get<ApiResponse<ResourceV2[]>>(`${PREFIX}/list`, { params });
  return unwrap(data);
}

/**
 * 获取资源树（嵌套结构）
 * 返回的每个资源在 meta 中包含 _children 字段（子资源ID列表）
 */
export async function fetchResourceTreeV2(params?: { type?: ResourceTypeFilter }): Promise<ResourceV2[]> {
  const { data } = await api.get<ApiResponse<ResourceV2[]>>(`${PREFIX}/tree`, { params });
  return unwrap(data);
}

export async function createResourceV2(payload: ResourceV2Input): Promise<ResourceV2> {
  const { data } = await api.post<ApiResponse<ResourceV2>>(PREFIX, payload);
  return unwrap(data);
}

export async function updateResourceV2(id: number, payload: ResourceV2Input): Promise<ResourceV2> {
  const { data } = await api.put<ApiResponse<ResourceV2>>(`${PREFIX}/${id}`, payload);
  return unwrap(data);
}

export async function deleteResourceV2(id: number): Promise<void> {
  const { data } = await api.delete<ApiResponse<null>>(`${PREFIX}/${id}`);
  unwrap(data);
}

export async function sortResourcesV2(items: Array<{ id: number; orderNum: number }>): Promise<void> {
  const { data } = await api.post<ApiResponse<null>>(`${PREFIX}/sort`, items);
  unwrap(data);
}

export async function importResourcesV2(items: ResourceV2Input[]): Promise<{ created: number; updated: number; errors: string[] }> {
  const { data } = await api.post<ApiResponse<{ created: number; updated: number; errors: string[] }>>(`${PREFIX}/import`, items);
  return unwrap(data);
}

export async function exportResourcesV2(): Promise<ResourceV2[]> {
  const { data } = await api.get<ApiResponse<ResourceV2[]>>(`${PREFIX}/export`);
  return unwrap(data);
}

