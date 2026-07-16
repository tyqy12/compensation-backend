/**
 * 权限守卫组件
 * 提供页面级、组件级、按钮级的权限控制
 */

import React, { useMemo } from 'react';
import { usePermission } from '@hooks/usePermission';
import { Button, Spin } from 'antd';

// ==================== 类型定义 ====================

/**
 * 权限守卫组件属性
 */
export interface PermissionGuardProps {
  /** 要检查的权限列表 */
  permissions: Array<{
    resourceCode: string;
    action: string;
  }>;
  /** 权限检查模式 */
  mode?: 'all' | 'any';
  /** 无权限时显示的内容 */
  fallback?: React.ReactNode;
  /** 是否在无权限时显示 403 页面 */
  showForbidden?: boolean;
  /** 子组件 */
  children: React.ReactNode;
}

/**
 * 按钮权限组件属性
 */
interface ButtonGuardProps {
  /** 要检查的权限列表 */
  permissions: Array<{
    resourceCode: string;
    action: string;
  }>;
  /** 按钮属性 */
  buttonProps?: React.ButtonHTMLAttributes<HTMLButtonElement>;
  /** 有权限时显示的按钮 */
  children: React.ReactElement;
  /** 无权限时隐藏还是禁用 */
  disabled?: boolean;
  /** 是否显示加载状态 */
  loading?: boolean;
}

/**
 * 页面级权限守卫属性
 */
interface PageGuardProps {
  /** 页面所需权限 */
  permissions: Array<{
    resourceCode: string;
    action: string;
  }>;
  /** 重定向路径（默认跳转到 403） */
  redirectTo?: string;
  /** 加载时显示的内容 */
  loadingFallback?: React.ReactNode;
  /** 子组件 */
  children: React.ReactNode;
}

// ==================== 权限守卫组件 ====================

/**
 * 权限守卫组件
 *
 * @example
 * ```tsx
 * <PermissionGuard
 *   permissions={[
 *     { resourceCode: 'employee', action: 'read' },
 *     { resourceCode: 'employee', action: 'write' },
 *   ]}
 *   mode="all"
 * >
 *   <EmployeeForm />
 * </PermissionGuard>
 * ```
 */
export function PermissionGuard({
  permissions,
  mode = 'all',
  fallback = null,
  showForbidden = false,
  children,
}: PermissionGuardProps) {
  const { hasAnyPermission, hasAllPermissions, isLoading } = usePermission();

  const hasPermission = useMemo(() => {
    if (isLoading || permissions.length === 0) return true;

    if (mode === 'any') {
      return hasAnyPermission(permissions);
    }
    return hasAllPermissions(permissions);
  }, [isLoading, permissions, mode, hasAnyPermission, hasAllPermissions]);

  if (isLoading) {
    return <Spin size="small" />;
  }

  if (!hasPermission) {
    if (showForbidden) {
      return <ForbiddenPage />;
    }
    return <>{fallback}</>;
  }

  return <>{children}</>;
}

/**
 * 403 禁止访问页面
 */
function ForbiddenPage() {
  return (
    <div style={{ textAlign: 'center', padding: '100px 0' }}>
      <h1 style={{ fontSize: 72, margin: 0, color: '#ff4d4f' }}>403</h1>
      <p style={{ fontSize: 18, color: '#999', marginTop: 16 }}>抱歉，您没有权限访问此页面</p>
      <Button type="primary" onClick={() => window.history.back()} style={{ marginTop: 24 }}>
        返回上一页
      </Button>
    </div>
  );
}

// ==================== 按钮权限组件 ====================

/**
 * 按钮权限控制组件
 *
 * @example
 * ```tsx
 * <ButtonGuard
 *   permissions={[{ resourceCode: 'employee', action: 'create' }]}
 *   disabled={false}
 * >
 *   <Button type="primary">新建员工</Button>
 * </ButtonGuard>
 * ```
 */
export function ButtonGuard({
  permissions,
  buttonProps,
  children,
  disabled = true,
  loading = false,
}: ButtonGuardProps) {
  const { isLoading, hasAnyPermission } = usePermission();

  const hasPermission = useMemo(() => {
    if (permissions.length === 0) return true;
    return hasAnyPermission(permissions);
  }, [permissions, hasAnyPermission]);

  if (isLoading) {
    return <Spin size="small" />;
  }

  if (!hasPermission) {
    if (disabled) {
      return React.cloneElement(children, {
        ...buttonProps,
        disabled: true,
        style: {
          ...(children.props.style || {}),
          ...(buttonProps?.style || {}),
          opacity: 0.5,
          cursor: 'not-allowed',
        },
      });
    }
    return null;
  }

  return React.cloneElement(children, {
    ...buttonProps,
    loading: loading || buttonProps?.loading,
  });
}

// ==================== 页面级权限守卫 ====================

/**
 * 页面级权限守卫（用于路由）
 *
 * @example
 * ```tsx
 * <PageGuard
 *   permissions={[{ resourceCode: 'employee', action: 'read' }]}
 *   redirectTo="/403"
 * >
 *   <EmployeeList />
 * </PageGuard>
 * ```
 */
export function PageGuard({
  permissions,
  redirectTo = '/403',
  loadingFallback = <Spin size="large" description="正在检查权限..." />,
  children,
}: PageGuardProps & { children: React.ReactNode }) {
  const { hasAllPermissions, isLoading } = usePermission();

  const hasPermission = useMemo(() => {
    if (permissions.length === 0) return true;
    return hasAllPermissions(permissions);
  }, [permissions, hasAllPermissions]);

  if (isLoading) {
    return <>{loadingFallback}</>;
  }

  if (!hasPermission) {
    if (redirectTo) {
      window.location.href = redirectTo;
      return null;
    }
    return <ForbiddenPage />;
  }

  return <>{children}</>;
}

// ==================== 角色守卫组件 ====================

/**
 * 角色守卫组件属性
 */
interface RoleGuardProps {
  /** 必需的角色列表 */
  roles: string[];
  /** 无权限时显示的内容 */
  fallback?: React.ReactNode;
  /** 是否显示 403 页面 */
  showForbidden?: boolean;
  /** 子组件 */
  children: React.ReactNode;
}

/**
 * 角色守卫组件
 * 检查用户是否拥有指定角色之一
 *
 * @example
 * ```tsx
 * <RoleGuard roles={['admin', 'manager']}>
 *   <AdminPanel />
 * </RoleGuard>
 * ```
 */
export function RoleGuard({
  roles,
  fallback = null,
  showForbidden = false,
  children,
}: RoleGuardProps) {
  const { isLoading } = usePermission();

  // 获取用户角色（从 auth store）
  const userRoles = React.useMemo(() => {
    try {
      const authState = localStorage.getItem('auth-storage');
      if (authState) {
        const parsed = JSON.parse(authState);
        return parsed?.state?.auth?.user?.roles || [];
      }
    } catch {
      // 忽略解析错误
    }
    return [];
  }, []);

  const hasRole = React.useMemo(() => {
    if (!roles || roles.length === 0) return true;
    return roles.some((role) => userRoles.includes(role));
  }, [roles, userRoles]);

  if (isLoading) {
    return <Spin size="small" />;
  }

  if (!hasRole) {
    if (showForbidden) {
      return <ForbiddenPage />;
    }
    return <>{fallback}</>;
  }

  return <>{children}</>;
}

// ==================== 导出 ====================

export default {
  PermissionGuard,
  ButtonGuard,
  PageGuard,
  RoleGuard,
};
