import api, { unwrap } from '@services/api';
import type { ApiResponse, PagedResponse } from '@types/api';
import type { AppRegistryDto, AppRegistryRequest, AppRegistrySecretDto } from '@types/openapi';

export interface AppRegistryQueryParams {
  current?: number;
  size?: number;
  keyword?: string;
  status?: 'enabled' | 'disabled';
}

const cleanParams = (params: Record<string, unknown>) =>
  Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );

export async function listAppRegistries(
  params: AppRegistryQueryParams = {},
): Promise<PagedResponse<AppRegistryDto>> {
  const query = cleanParams({
    current: params.current ?? 1,
    size: params.size ?? 10,
    keyword: params.keyword,
    status: params.status,
  });
  const { data } = await api.get<ApiResponse<PagedResponse<AppRegistryDto>>>('/admin/app-registry', {
    params: query,
  });
  return unwrap(data);
}

export async function getAppRegistry(id: number | string): Promise<AppRegistryDto> {
  const { data } = await api.get<ApiResponse<AppRegistryDto>>(`/admin/app-registry/${id}`);
  return unwrap(data);
}

export async function createAppRegistry(
  payload: AppRegistryRequest,
): Promise<AppRegistryDto & { clientSecret?: string }> {
  const { data } = await api.post<ApiResponse<AppRegistryDto & { clientSecret?: string }>>(
    '/admin/app-registry',
    payload,
  );
  return unwrap(data);
}

export async function updateAppRegistry(
  id: number | string,
  payload: AppRegistryRequest,
): Promise<AppRegistryDto> {
  const { data } = await api.put<ApiResponse<AppRegistryDto>>(`/admin/app-registry/${id}`, payload);
  return unwrap(data);
}

export async function rotateAppRegistrySecret(
  id: number | string,
): Promise<AppRegistrySecretDto> {
  const { data } = await api.post<ApiResponse<AppRegistrySecretDto>>(
    `/admin/app-registry/${id}/rotate-secret`,
  );
  return unwrap(data);
}
