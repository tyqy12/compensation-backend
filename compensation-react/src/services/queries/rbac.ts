import { useQuery, UseQueryOptions } from '@tanstack/react-query';
import { getMeResources, getMeActions } from '@services/rbac';
import type { MeResourcesData, MeActionsData } from '@/types/api';
import { useSelector } from 'react-redux';
import type { RootState } from '@services/stores/authSlice';

// localStorage cache per user with permissionVersion
const cacheKey = (userId?: string | number | null) => (userId ? `rbac_cache_${userId}` : undefined);

function readCached(userId?: string | number | null): MeResourcesData | undefined {
  try {
    const key = cacheKey(userId);
    if (!key) return undefined;
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as MeResourcesData) : undefined;
  } catch {
    return undefined;
  }
}

function writeCached(userId?: string | number | null, data?: MeResourcesData) {
  try {
    const key = cacheKey(userId);
    if (!key) return;
    if (data) localStorage.setItem(key, JSON.stringify(data));
  } catch {}
}

export function useMeResourcesQuery(options?: UseQueryOptions<MeResourcesData>) {
  const userId = useSelector((s: RootState) => s.auth.user?.id as any);
  const cached = readCached(userId);
  return useQuery<MeResourcesData>({
    queryKey: ['me', 'resources', userId],
    queryFn: async () => {
      const latest = await getMeResources();
      // 总是更新缓存（即使版本相同，也可能是新数据）
      writeCached(userId, latest);
      return latest;
    },
    // hydrate with cache if available to avoid layout jank
    initialData: cached,
    // 菜单数据设置较短的 staleTime，保证相对新鲜的缓存
    staleTime: 10_000,
    gcTime: 5 * 60_000,
    ...options,
  });
}

export function useMeActionsQuery(options?: UseQueryOptions<MeActionsData>) {
  const userId = useSelector((s: RootState) => s.auth.user?.id as any);
  return useQuery<MeActionsData>({
    queryKey: ['me', 'actions', userId],
    queryFn: () => getMeActions(),
    staleTime: 30_000,
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
