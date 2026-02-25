/**
 * 授权中心路由配置
 *
 * 新架构：解耦的独立页面，遵循 Ant Design 设计规范
 *
 * 使用方法：
 * 1. 将这些路由添加到主路由配置文件 (routes/index.tsx)
 * 2. 导入对应的页面组件
 * 3. 确保 withGuard 包裹，限制管理员权限
 */

import React, { Suspense } from 'react';
import Loading from '@components/Common/Loading';

// ==================== 页面组件懒加载 ====================

// 用户授权模块
const UserList = React.lazy(() => import('@pages/admin/auth-center/users/UserList'));
const UserRoleAssign = React.lazy(() => import('@pages/admin/auth-center/users/UserRoleAssign'));
const UserPermissionConfig = React.lazy(() => import('@pages/admin/auth-center/users/UserPermissionConfig'));
const UserPermissionView = React.lazy(() => import('@pages/admin/auth-center/users/UserPermissionView'));

// 角色管理模块
const RoleList = React.lazy(() => import('@pages/admin/auth-center/roles/RoleList'));
const RoleEdit = React.lazy(() => import('@pages/admin/auth-center/roles/RoleEdit'));
const RolePermissionConfig = React.lazy(() => import('@pages/admin/auth-center/roles/RolePermissionConfig'));
const RoleMembers = React.lazy(() => import('@pages/admin/auth-center/roles/RoleMembers'));

// 资源管理模块
const ResourceList = React.lazy(() => import('@pages/admin/auth-center/resources/ResourceList'));
const ResourceEdit = React.lazy(() => import('@pages/admin/auth-center/resources/ResourceEdit'));

// ==================== 路由配置 ====================

/**
 * 授权中心路由配置数组
 *
 * 在主路由文件中使用：
 * ```typescript
 * import { authCenterRoutes } from './authCenterRoutes';
 *
 * // 在 children 数组中添加：
 * ...authCenterRoutes.map(route => ({
 *   ...route,
 *   element: withGuard(route.element, ['ADMIN'])
 * }))
 * ```
 */
export const authCenterRoutes = [
  // ==================== 用户授权 ====================
  {
    path: 'admin/auth-center/users',
    element: <Suspense fallback={<Loading />}><UserList /></Suspense>,
    meta: {
      title: '用户授权',
      roles: ['ADMIN'],
    },
  },
  {
    path: 'admin/auth-center/users/:userId/roles',
    element: <Suspense fallback={<Loading />}><UserRoleAssign /></Suspense>,
    meta: {
      title: '分配角色',
      roles: ['ADMIN'],
    },
  },
  {
    path: 'admin/auth-center/users/:userId/permissions',
    element: <Suspense fallback={<Loading />}><UserPermissionConfig /></Suspense>,
    meta: {
      title: '配置权限',
      roles: ['ADMIN'],
    },
  },
  {
    path: 'admin/auth-center/users/:userId/view',
    element: <Suspense fallback={<Loading />}><UserPermissionView /></Suspense>,
    meta: {
      title: '查看权限',
      roles: ['ADMIN'],
    },
  },

  // ==================== 角色管理 ====================
  {
    path: 'admin/auth-center/roles',
    element: <Suspense fallback={<Loading />}><RoleList /></Suspense>,
    meta: {
      title: '角色管理',
      roles: ['ADMIN'],
    },
  },
  {
    path: 'admin/auth-center/roles/create',
    element: <Suspense fallback={<Loading />}><RoleEdit /></Suspense>,
    meta: {
      title: '新建角色',
      roles: ['ADMIN'],
    },
  },
  {
    path: 'admin/auth-center/roles/:roleId/edit',
    element: <Suspense fallback={<Loading />}><RoleEdit /></Suspense>,
    meta: {
      title: '编辑角色',
      roles: ['ADMIN'],
    },
  },
  {
    path: 'admin/auth-center/roles/:roleId/permissions',
    element: <Suspense fallback={<Loading />}><RolePermissionConfig /></Suspense>,
    meta: {
      title: '角色权限配置',
      roles: ['ADMIN'],
    },
  },
  {
    path: 'admin/auth-center/roles/:roleId/members',
    element: <Suspense fallback={<Loading />}><RoleMembers /></Suspense>,
    meta: {
      title: '角色成员',
      roles: ['ADMIN'],
    },
  },

  // ==================== 资源管理 ====================
  {
    path: 'admin/auth-center/resources',
    element: <Suspense fallback={<Loading />}><ResourceList /></Suspense>,
    meta: {
      title: '资源管理',
      roles: ['ADMIN'],
    },
  },
  {
    path: 'admin/auth-center/resources/create',
    element: <Suspense fallback={<Loading />}><ResourceEdit /></Suspense>,
    meta: {
      title: '新建资源',
      roles: ['ADMIN'],
    },
  },
  {
    path: 'admin/auth-center/resources/:resourceId/edit',
    element: <Suspense fallback={<Loading />}><ResourceEdit /></Suspense>,
    meta: {
      title: '编辑资源',
      roles: ['ADMIN'],
    },
  },
];

// ==================== 路由路径常量 ====================

/**
 * 路由路径常量，方便在代码中引用
 */
export const AUTH_CENTER_PATHS = {
  // 用户授权
  USER_LIST: '/admin/auth-center/users',
  USER_ROLE_ASSIGN: (userId: number) => `/admin/auth-center/users/${userId}/roles`,
  USER_PERMISSION_CONFIG: (userId: number) => `/admin/auth-center/users/${userId}/permissions`,
  USER_PERMISSION_VIEW: (userId: number) => `/admin/auth-center/users/${userId}/view`,

  // 角色管理
  ROLE_LIST: '/admin/auth-center/roles',
  ROLE_CREATE: '/admin/auth-center/roles/create',
  ROLE_EDIT: (roleId: number) => `/admin/auth-center/roles/${roleId}/edit`,
  ROLE_PERMISSION_CONFIG: (roleId: number) => `/admin/auth-center/roles/${roleId}/permissions`,
  ROLE_MEMBERS: (roleId: number) => `/admin/auth-center/roles/${roleId}/members`,

  // 资源管理
  RESOURCE_LIST: '/admin/auth-center/resources',
  RESOURCE_CREATE: '/admin/auth-center/resources/create',
  RESOURCE_EDIT: (resourceId: number) => `/admin/auth-center/resources/${resourceId}/edit`,
};

// ==================== 集成示例 ====================

/**
 * 在主路由文件 (routes/index.tsx) 中添加：
 *
 * ```typescript
 * import { authCenterRoutes } from './authCenterRoutes';
 *
 * export const router = createBrowserRouter([
 *   {
 *     path: '/',
 *     element: <RootLayout />,
 *     children: [
 *       // ... 其他路由
 *
 *       // 授权中心路由（新架构）
 *       ...authCenterRoutes.map(route => ({
 *         ...route,
 *         element: withGuard(route.element, route.meta.roles)
 *       })),
 *     ],
 *   },
 * ]);
 * ```
 */
