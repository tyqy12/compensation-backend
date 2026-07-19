import { useQuery, UseQueryOptions } from '@tanstack/react-query';
import { useSelector } from 'react-redux';
import { getMeResources, getMeActions } from '@services/rbac';
import type { RootState } from '@services/stores/authSlice';
import type { MeResourcesData, MeActionsData } from '@/types/api';

type PermissionQueryOptions<TData> = Omit<UseQueryOptions<TData>, 'queryKey' | 'queryFn'>;

export const meResourcesQueryKey = (userId?: string | number | null) =>
  ['me', 'resources', userId ?? 'anonymous'] as const;

export const meActionsQueryKey = (userId?: string | number | null) =>
  ['me', 'actions', userId ?? 'anonymous'] as const;

export function useMeResourcesQuery(options?: PermissionQueryOptions<MeResourcesData>) {
  const userId = useSelector((state: RootState) => state.auth.user?.id);

  return useQuery<MeResourcesData>({
    ...options,
    queryKey: meResourcesQueryKey(userId),
    queryFn: getMeResources,
    enabled: Boolean(userId) && (options?.enabled ?? true),
    staleTime: 60_000,
    gcTime: 5 * 60_000,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    retry: false,
  });
}

export function useMeActionsQuery(options?: PermissionQueryOptions<MeActionsData>) {
  const userId = useSelector((state: RootState) => state.auth.user?.id);

  return useQuery<MeActionsData>({
    ...options,
    queryKey: meActionsQueryKey(userId),
    queryFn: () => getMeActions(),
    enabled: Boolean(userId) && (options?.enabled ?? true),
    staleTime: 60_000,
    gcTime: 5 * 60_000,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    retry: false,
  });
}

export function useHasAction(actionCode: string): boolean {
  const { data } = useMeActionsQuery({
    // keep previous data to avoid flickers
    placeholderData: (prev: MeActionsData | undefined) => prev,
  });
  return Boolean(data && data.includes(actionCode));
}
