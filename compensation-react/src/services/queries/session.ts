import { useQuery, type UseQueryOptions } from '@tanstack/react-query';
import api, { unwrap } from '@services/api';

export interface CurrentUserSummary {
  id: string;
  username: string;
  roles: string[];
  employeeId?: number | null;
  hasEmployeeProfile?: boolean;
}

export async function fetchCurrentUserSummary(): Promise<CurrentUserSummary> {
  const { data } = await api.get('/auth/me');
  const summary = unwrap<CurrentUserSummary>(data);

  return {
    ...summary,
    hasEmployeeProfile: summary.hasEmployeeProfile ?? Boolean(summary.employeeId),
  };
}

export function useCurrentUserSummaryQuery(
  options?: Omit<UseQueryOptions<CurrentUserSummary>, 'queryKey' | 'queryFn'>,
) {
  return useQuery<CurrentUserSummary>({
    queryKey: ['me', 'summary'],
    queryFn: fetchCurrentUserSummary,
    staleTime: 30_000,
    ...options,
  });
}
