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
      const queryParams: Record<string, unknown> = {
        current: params.current,
        pageSize: params.pageSize,
        provider: params.provider ?? params.platform,
        bound: params.bound ?? (params.bindStatus === 'active' ? true : undefined),
        keyword: params.keyword ?? params.username,
      };

      const cleanParams = Object.fromEntries(
        Object.entries(queryParams).filter(
          ([, value]) => value !== undefined && value !== null && value !== '',
        ),
      );

      const { data } = await api.get('/admin/user-bindings', { params: cleanParams });
      return unwrap<PagedResponse<UserBindingItem>>(data);
    },
  });
}

export function useBindUserMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (payload: any) => {
      const hasAdminPayload = payload?.id !== undefined && payload?.id !== null;
      if (!hasAdminPayload) {
        throw new Error('缺少用户ID，无法绑定平台账号');
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
