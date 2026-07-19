import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { login, logout, store } from '@services/stores/authSlice';
import { getMeResources } from '@services/rbac';
import { meResourcesQueryKey, useMeResourcesQuery } from './rbac';

vi.mock('@services/rbac', () => ({
  getMeResources: vi.fn(),
  getMeActions: vi.fn(),
}));

const mockGetMeResources = vi.mocked(getMeResources);

const permissionData = {
  permissionVersion: 1,
  resources: [],
  actions: {},
};

const createWrapper = (queryClient: QueryClient) => ({ children }: { children: React.ReactNode }) => (
  <Provider store={store}>
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  </Provider>
);

describe('RBAC permission queries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    store.dispatch(logout());
    mockGetMeResources.mockResolvedValue(permissionData);
  });

  it('未登录时不请求当前用户资源', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(() => useMeResourcesQuery(), {
      wrapper: createWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.fetchStatus).toBe('idle'));
    expect(mockGetMeResources).not.toHaveBeenCalled();
  });

  it('菜单和权限消费者共享同一用户查询，只发起一次请求', async () => {
    store.dispatch(login({ id: 'rbac-user-1', username: 'rbac-user', roles: [] }));
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    const { result } = renderHook(
      () => ({ menu: useMeResourcesQuery(), permission: useMeResourcesQuery() }),
      { wrapper: createWrapper(queryClient) },
    );

    await waitFor(() => expect(result.current.menu.data).toEqual(permissionData));
    expect(result.current.permission.data).toEqual(permissionData);
    expect(mockGetMeResources).toHaveBeenCalledTimes(1);
    expect(queryClient.getQueryData(meResourcesQueryKey('rbac-user-1'))).toEqual(permissionData);
  });
});
