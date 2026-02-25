import api, { unwrap } from '@services/api';
import type {
  ApiResponse,
  PagedResponse,
  RoleInfo,
  RoleDetail,
  RoleResourceBrief,
  CreateRoleRequest,
  UpdateRoleRequest,
  RoleResourceAssignRequest,
} from '@types/api';

export interface RoleResource {
  roleId: number;
  resourceId: number;
  actionsJson?: string | null;
}

export interface UserResource {
  userId: number;
  resourceId: number;
  actionsJson?: string | null;
}

/**
 * 用户聚合权限项（角色权限 + 个性化权限）
 */
export interface UserAggregateResource {
  userId: number;
  resourceId: number;
  actions?: string[];
  actionsJson?: string | null;
}

export interface AdminRoleSummary {
  id: number;
  name: string;
  code: string;
  description?: string | null;
}

export interface AdminUserAggregateItem {
  userId: number;
  username: string;
  realName?: string | null;
  employeeName?: string | null;
  email?: string | null;
  phone?: string | null;
}

export interface UserAggregateQueryParams {
  q?: string;
  page?: number;
  size?: number;
}

// ==================== 角色管理 API ====================

/**
 * 获取角色列表
 */
export async function listRoles(params?: {
  keyword?: string;
  roleType?: string;
  status?: string;
}): Promise<RoleInfo[]> {
  const cleanParams = Object.fromEntries(
    Object.entries(params ?? {}).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );
  const { data } = await api.get<ApiResponse<RoleInfo[]>>('/admin/roles', { params: cleanParams });
  return unwrap(data);
}

/**
 * 获取启用的角色列表
 */
export async function listEnabledRoles(): Promise<RoleInfo[]> {
  const { data } = await api.get<ApiResponse<RoleInfo[]>>('/admin/roles/enabled');
  return unwrap(data);
}

/**
 * 获取角色详情
 */
export async function getRoleDetail(id: number): Promise<RoleDetail> {
  const { data } = await api.get<ApiResponse<RoleDetail>>(`/admin/roles/${id}`);
  return unwrap(data);
}

/**
 * 创建角色
 */
export async function createRole(request: CreateRoleRequest): Promise<RoleInfo> {
  const { data } = await api.post<ApiResponse<RoleInfo>>('/admin/roles', request);
  return unwrap(data);
}

/**
 * 更新角色
 */
export async function updateRole(id: number, request: UpdateRoleRequest): Promise<RoleInfo> {
  const { data } = await api.put<ApiResponse<RoleInfo>>(`/admin/roles/${id}`, request);
  return unwrap(data);
}

/**
 * 删除角色
 */
export async function deleteRole(id: number): Promise<void> {
  const { data } = await api.delete<ApiResponse<void>>(`/admin/roles/${id}`);
  return unwrap(data);
}

/**
 * 禁用角色
 */
export async function disableRole(id: number): Promise<RoleInfo> {
  const { data } = await api.put<ApiResponse<RoleInfo>>(`/admin/roles/${id}/disable`);
  return unwrap(data);
}

/**
 * 启用角色
 */
export async function enableRole(id: number): Promise<RoleInfo> {
  const { data } = await api.put<ApiResponse<RoleInfo>>(`/admin/roles/${id}/enable`);
  return unwrap(data);
}

/**
 * 复制角色
 */
export async function copyRole(id: number, newCode: string, newName: string): Promise<RoleInfo> {
  const { data } = await api.post<ApiResponse<RoleInfo>>(
    `/admin/roles/${id}/copy`,
    null,
    { params: { newCode, newName } },
  );
  return unwrap(data);
}

/**
 * 获取角色资源权限
 */
export async function getRoleResources(id: number): Promise<RoleResourceBrief[]> {
  const { data } = await api.get<ApiResponse<RoleResourceBrief[]>>(`/admin/roles/${id}/permissions`);
  return unwrap(data);
}

/**
 * 分配角色资源权限
 */
export async function assignRoleResources(id: number, request: RoleResourceAssignRequest): Promise<void> {
  const { data } = await api.put<ApiResponse<void>>(`/admin/roles/${id}/permissions`, request);
  return unwrap(data);
}

/**
 * 撤销角色资源权限
 */
export async function revokeRoleResources(id: number, resourceIds?: number[]): Promise<void> {
  const { data } = await api.delete<ApiResponse<void>>(`/admin/roles/${id}/permissions`, {
    params: resourceIds ? { resourceIds } : undefined,
  });
  return unwrap(data);
}

// ==================== 原有 API（保留兼容） ====================

export async function listAdminRoles(): Promise<AdminRoleSummary[]> {
  const { data } = await api.get<ApiResponse<AdminRoleSummary[]>>('/admin/roles');
  return unwrap(data);
}

export async function searchAdminUsers(
  params: UserAggregateQueryParams,
): Promise<PagedResponse<AdminUserAggregateItem>> {
  const cleanParams = Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );
  const { data } = await api.get<ApiResponse<PagedResponse<AdminUserAggregateItem>>>(
    '/admin/users/search',
    { params: cleanParams },
  );
  return unwrap(data);
}

export async function getRoleResourcesOld(roleId: number): Promise<RoleResource[]> {
  const { data } = await api.get<ApiResponse<RoleResource[]>>(`/admin/roles/${roleId}/resources`);
  return unwrap(data);
}

export async function putRoleResources(
  roleId: number,
  body: { resourceIds: number[]; actions: Record<string, string[]> },
): Promise<{ workflowId: number }> {
  const { data } = await api.put<ApiResponse<{ workflowId: number }>>(`/admin/roles/${roleId}/resources`, body);
  return unwrap(data);
}

export async function getUserResources(userId: number): Promise<UserResource[]> {
  const { data } = await api.get<ApiResponse<UserResource[]>>(`/admin/users/${userId}/resources`);
  return unwrap(data);
}

/**
 * 获取用户聚合权限（角色权限 + 个性化权限）
 */
export async function getUserAggregateResources(userId: number): Promise<UserAggregateResource[]> {
  const { data } = await api.get<ApiResponse<UserAggregateResource[]>>(`/admin/users/${userId}/aggregate-resources`);
  return unwrap(data);
}

export async function putUserResources(
  userId: number,
  body: { resourceIds: number[]; actions: Record<string, string[]> },
): Promise<{ workflowId: number }> {
  const { data } = await api.put<ApiResponse<{ workflowId: number }>>(`/admin/users/${userId}/resources`, body);
  return unwrap(data);
}

// ==================== 用户角色管理 API ====================

/**
 * 获取用户已有的角色ID列表
 */
export async function getUserRoles(userId: number): Promise<number[]> {
  const { data } = await api.get<ApiResponse<number[]>>(`/admin/users/${userId}/roles`);
  return unwrap(data);
}

/**
 * 设置用户角色（覆盖式）
 */
export async function setUserRoles(userId: number, roleIds: number[]): Promise<string> {
  const { data } = await api.put<ApiResponse<string>>(`/admin/users/${userId}/roles`, { roleIds });
  return unwrap(data);
}
