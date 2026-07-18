import { useSelector } from 'react-redux';
import type { RootState } from '@services/stores/authSlice';
import { usePermission } from '@hooks/usePermission';

export function useAuthGuard() {
  const user = useSelector((s: RootState) => s.auth.user);
  const { checkPermission, hasAnyPermission, hasAllPermissions, isLoading, error } = usePermission();

  return {
    isAuthenticated: Boolean(user),
    user: user ?? null,
    isLoading,
    error,
    checkPermission,
    hasAnyPermission,
    hasAllPermissions,
  };
}
