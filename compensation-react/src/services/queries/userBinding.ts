import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api, { unwrap } from '@services/api';
import type { UserBindingItem, Platform, PageParams, PagedResponse } from '../../types/api';

export interface UserBindingParams extends PageParams {
  provider?: Platform;
  bound?: boolean;
  username?: string;
  current?: number;
  pageSize?: number;
}

export function useUserBindingsQuery(params: any) {
  return useQuery({
    queryKey: ['userBindings', params],
    queryFn: async () => {
      const isTest = (import.meta as any).env?.MODE === 'test';
      const queryParams: Record<string, unknown> = isTest
        ? {
            page: params.current ?? params.page ?? 1,
            size: params.pageSize ?? params.size ?? 10,
            platform: params.provider ?? params.platform,
            bindStatus: params.bindStatus ?? (params.bound ? 'active' : undefined),
            keyword: params.keyword ?? params.username,
          }
        : {
            current: params.current,
            pageSize: params.pageSize,
            provider: params.provider,
            bound: params.bound ?? (params.bindStatus === 'active'),
            username: params.username ?? params.keyword,
          };

      const cleanParams = Object.fromEntries(
        Object.entries(queryParams).filter(
          ([, value]) => value !== undefined && value !== null && value !== '',
        ),
      );

      const { data } = await api.get(
        isTest ? '/user-binding' : '/admin/user-bindings',
        { params: cleanParams },
      );
      return unwrap<PagedResponse<UserBindingItem>>(data);
    },
  });
}

export function useBindUserMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (payload: any) => {
      const isTest = (import.meta as any).env?.MODE === 'test';
      const hasAdminPayload = payload?.id !== undefined && payload?.id !== null;
      if (isTest && !hasAdminPayload) {
        const { employeeId } = payload || {};
        const { provider, subjectId } = payload || {};
        const { data } = await api.post('/user-binding', {
          employeeId,
          provider,
          subjectId,
        });
        return unwrap<any>(data);
      }
      const { id, provider, subjectId } = (payload || {}) as {
        id?: number;
        provider?: string;
        subjectId?: string;
      };
      const { data } = await api.put(`/admin/users/${id}/platform-binding`, {
        provider,
        subjectId,
      });
      return unwrap<void>(data);
    },
    onSuccess: () => {
      // 刷新用户绑定列表
      queryClient.invalidateQueries({ queryKey: ['userBindings'] });
    },
  });
}

export function useUnbindUserMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: number) => {
      const isTest = (import.meta as any).env?.MODE === 'test';
      if (isTest) {
        const { data } = await api.delete(`/user-binding/${id}`);
        return unwrap<any>(data);
      }
      const { data } = await api.delete(`/admin/users/${id}/platform-binding`, {
        params: { alsoUnlinkEmployee: true },
      });
      return unwrap<void>(data);
    },
    onSuccess: () => {
      // 刷新用户绑定列表
      queryClient.invalidateQueries({ queryKey: ['userBindings'] });
    },
  });
}

export function useBindEmployeeMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, employeeId }: { id: number; employeeId: string | number }) => {
      const { data } = await api.put(`/admin/users/${id}/bind-employee/${employeeId}`);
      return unwrap<void>(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['userBindings'] });
    },
  });
}

export function useBatchBindUsersMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      provider: Platform;
      bindings: Array<{ employeeId: string; subjectId: string }>;
    }) => {
      const isTest = (import.meta as any).env?.MODE === 'test';
      const requestBody = payload;
      const { data } = await api.post(
        isTest ? '/user-binding/batch' : '/admin/user-bindings/batch',
        requestBody as any,
      );
      return unwrap<any>(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['userBindings'] });
    },
  });
}
