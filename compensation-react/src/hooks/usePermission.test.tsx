import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { usePermission } from './usePermission';
import api from '@services/api';
import { login, store } from '@services/stores/authSlice';

vi.mock('@services/api', () => ({
  default: {
    get: vi.fn(),
  },
  unwrap: (response: { data: unknown }) => response.data,
}));

const mockApi = vi.mocked(api);

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return ({ children }: { children: React.ReactNode }) => (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </Provider>
  );
};

describe('usePermission', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    store.dispatch(login({ id: 'permission-test-user', username: 'permission-test', roles: [] }));
    mockApi.get.mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          permissionVersion: 7,
          resources: [
            {
              id: 101,
              type: 'MENU',
              code: 'employee',
              name: '员工管理',
              path: '/employees',
            },
            {
              id: 102,
              type: 'VIEW',
              code: 'payroll.confirmations',
              name: '工资确认',
              path: '/payroll/confirmations',
            },
          ],
          actions: {
            101: ['read', 'write'],
            102: ['read'],
          },
        },
      },
    });
  });

  it('按资源 id、code 和 path 都能匹配后端返回的 resourceId actions', async () => {
    const { result } = renderHook(() => usePermission(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.checkPermission('101', 'write')).toBe(true);
    expect(result.current.checkPermission('employee', 'write')).toBe(true);
    expect(result.current.checkPermission('/employees', 'read')).toBe(true);
    expect(result.current.checkPermission('payroll.confirmations', 'write')).toBe(false);
  });

  it('hasAction 基于 actions 映射安全判断，不把 actions 当数组调用', async () => {
    const { result } = renderHook(() => usePermission(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.hasAction('read')).toBe(true);
    expect(result.current.hasAction('delete')).toBe(false);
  });
});
