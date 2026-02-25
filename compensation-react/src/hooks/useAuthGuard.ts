import { useMemo } from 'react';
import { useSelector } from 'react-redux';
import type { RootState } from '@services/stores/authSlice';

export function useAuthGuard() {
  const user = useSelector((s: RootState) => s.auth.user);
  const roles = useSelector((s: RootState) => s.auth.user?.roles ?? s.auth.roles ?? []);

  const helpers = useMemo(() => {
    const hasRole = (role: string) => roles.includes(role);
    const hasAnyRole = (required: string[]) => required.some((r) => roles.includes(r));
    return { hasRole, hasAnyRole };
  }, [roles]);

  return { isAuthenticated: Boolean(user), user: user ?? null, ...helpers };
}
