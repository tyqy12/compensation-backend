import React from 'react';
import { Navigate, useLocation, matchPath } from 'react-router-dom';
import { useSelector } from 'react-redux';
import type { RootState } from '@services/stores/authSlice';
import { hasAnyRole } from '@utils/rbac';
import { useMeResourcesQuery } from '@services/queries/rbac';
import { flattenAllowedPaths } from '@utils/permissions';
import Loading from '@components/Common/Loading';

type Props = {
  roles?: string[];
  children: React.ReactNode;
};

export const ProtectedRoute: React.FC<Props> = ({ roles, children }) => {
  const location = useLocation();
  const isAuthenticated = useSelector((s: RootState) => Boolean(s.auth.user));
  const userRoles = useSelector((s: RootState) => s.auth.user?.roles ?? s.auth.roles ?? []);
  const { data: meRes, isLoading } = useMeResourcesQuery();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  // Do not block while loading permissions; only enforce when data is ready.
  if (roles && roles.length > 0 && !hasAnyRole(userRoles, roles)) {
    return <Navigate to="/403" replace />;
  }
  // Resource-based guard: if resources provided, ensure current path is allowed
  const allowed = flattenAllowedPaths(meRes?.resources || []);
  if (allowed.length > 0) {
    const path = location.pathname;
    // 使用 matchPath 来正确匹配带有路由参数的路径（如 /employees/:id）
    const ok = allowed.some((p) => {
      if (!p) return false;
      // 精确匹配或使用 matchPath 处理动态路由参数
      if (p === path) return true;
      // 使用 matchPath 来匹配带有路由参数的路径
      const match = matchPath({ path: p, end: true }, path);
      if (match) return true;
      // 前缀匹配（用于子路由）
      if (p !== '/' && (path === p || path.startsWith(p + '/'))) return true;
      return false;
    });
    if (!ok) return <Navigate to="/403" replace />;
  }
  return <>{children}</>;
};
