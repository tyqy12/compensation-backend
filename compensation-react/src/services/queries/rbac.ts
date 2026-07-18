import { useQuery, UseQueryOptions } from '@tanstack/react-query';
import { getMeResources, getMeActions } from '@services/rbac';
import type { MeResourcesData, MeActionsData } from '@/types/api';

export function useMeResourcesQuery(options?: UseQueryOptions<MeResourcesData>) {
  return useQuery<MeResourcesData>({
    queryKey: ['me', 'resources'],
    queryFn: getMeResources,
    staleTime: 0,
    gcTime: 5 * 60_000,
    ...options,
  });
}

export function useMeActionsQuery(options?: UseQueryOptions<MeActionsData>) {
  return useQuery<MeActionsData>({
    queryKey: ['me', 'actions'],
    queryFn: () => getMeActions(),
    staleTime: 0,
    gcTime: 5 * 60_000,
    ...options,
  });
}

export function useHasAction(actionCode: string): boolean {
  const { data } = useMeActionsQuery({
    queryKey: ['me', 'actions'],
    // keep previous data to avoid flickers
    placeholderData: (prev: MeActionsData | undefined) => prev,
  });
  return Boolean(data && data.includes(actionCode));
}
