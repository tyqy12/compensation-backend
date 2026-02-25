/**
 * 授权中心 - 页面组件导出
 *
 * 新架构设计：
 * - 解耦复杂页面为多个独立页面
 * - 每个页面专注一个核心任务
 * - 遵循 Ant Design 设计规范
 */

// 用户授权模块
export { default as UserList } from './users/UserList';
export { default as UserRoleAssign } from './users/UserRoleAssign';
export { default as UserPermissionConfig } from './users/UserPermissionConfig';
export { default as UserPermissionView } from './users/UserPermissionView';

// 角色管理模块
export { default as RoleList } from './roles/RoleList';
export { default as RoleEdit } from './roles/RoleEdit';
export { default as RolePermissionConfig } from './roles/RolePermissionConfig';
export { default as RoleMembers } from './roles/RoleMembers';

// 资源管理模块
export { default as ResourceList } from './resources/ResourceList';
export { default as ResourceEdit } from './resources/ResourceEdit';

// 路由配置参考
export const AUTH_CENTER_ROUTES = {
  // 用户授权
  USER_LIST: '/admin/auth-center/users',
  USER_ROLE_ASSIGN: '/admin/auth-center/users/:userId/roles',
  USER_PERMISSION_CONFIG: '/admin/auth-center/users/:userId/permissions',
  USER_PERMISSION_VIEW: '/admin/auth-center/users/:userId/view',

  // 角色管理
  ROLE_LIST: '/admin/auth-center/roles',
  ROLE_CREATE: '/admin/auth-center/roles/create',
  ROLE_EDIT: '/admin/auth-center/roles/:roleId/edit',
  ROLE_PERMISSION_CONFIG: '/admin/auth-center/roles/:roleId/permissions',
  ROLE_MEMBERS: '/admin/auth-center/roles/:roleId/members',

  // 资源管理
  RESOURCE_LIST: '/admin/auth-center/resources',
  RESOURCE_CREATE: '/admin/auth-center/resources/create',
  RESOURCE_EDIT: '/admin/auth-center/resources/:resourceId/edit',
};
