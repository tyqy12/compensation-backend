import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import api from '@services/api';
import {
  useUserBindingsQuery,
  useBindUserMutation,
  useUnbindUserMutation,
} from './userBinding';

// Mock the API
vi.mock('@services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  unwrap: vi.fn((data) => data),
}));

const mockApi = api as any;

// Test wrapper
const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  const Wrapper = ({ children }: { children: React.ReactNode }) => {
    return React.createElement(QueryClientProvider, { client: queryClient }, children);
  };

  return Wrapper;
};

// Test data
const mockUserBinding = {
  id: 1,
  employeeId: 'EMP001',
  employeeName: '张三',
  provider: 'wechat',
  subjectId: 'wx_user_123',
  subjectName: '张三(技术部)',
  bindTime: '2024-01-15T10:00:00Z',
  bindStatus: 'active',
  lastSyncTime: '2024-01-15T12:00:00Z',
};

const mockBindingParams = {
  current: 1,
  pageSize: 10,
  provider: 'wechat',
  bindStatus: 'active',
  keyword: '张三',
};

describe('UserBinding Queries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useUserBindingsQuery', () => {
    it('should fetch user bindings with correct parameters', async () => {
      const mockResponse = {
        data: {
          records: [mockUserBinding],
          total: 1,
          current: 1,
          size: 10,
        },
      };

      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useUserBindingsQuery(mockBindingParams), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/admin/user-bindings', {
        params: {
          current: 1,
          pageSize: 10,
          provider: 'wechat',
          bound: true,
          keyword: '张三',
        },
      });

      expect(result.current.data).toEqual(mockResponse.data);
    });

    it('should filter out empty parameters', async () => {
      const mockResponse = { data: { records: [], total: 0 } };
      mockApi.get.mockResolvedValue(mockResponse);

      renderHook(
        () =>
          useUserBindingsQuery({
            current: 1,
            pageSize: 10,
            provider: undefined,
            bindStatus: undefined,
            keyword: '',
          }),
        { wrapper: createWrapper() },
      );

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith('/admin/user-bindings', {
          params: {
            current: 1,
            pageSize: 10,
          },
        });
      });
    });

    it('should handle error when fetching bindings', async () => {
      const mockError = new Error('Network error');
      mockApi.get.mockRejectedValue(mockError);

      const { result } = renderHook(() => useUserBindingsQuery(mockBindingParams), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toEqual(mockError);
    });

    it('should send backend-compatible admin query parameters in production mode', async () => {
      vi.stubEnv('MODE', 'production');
      const mockResponse = { data: { records: [], total: 0, current: 1, size: 10 } };
      mockApi.get.mockResolvedValue(mockResponse);

      renderHook(
        () =>
          useUserBindingsQuery({
            current: 2,
            pageSize: 20,
            provider: 'wechat',
            username: 'alice',
            bound: false,
          }),
        { wrapper: createWrapper() },
      );

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith('/admin/user-bindings', {
          params: {
            current: 2,
            pageSize: 20,
            provider: 'wechat',
            bound: false,
            keyword: 'alice',
          },
        });
      });
      expect(mockApi.get).not.toHaveBeenCalledWith('/admin/user-bindings', {
        params: expect.objectContaining({ username: 'alice' }),
      });

      vi.unstubAllEnvs();
    });
  });
});

describe('UserBinding Mutations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useBindUserMutation', () => {
    it('should reject bind request without user id', async () => {
      const bindData = {
        employeeId: 'EMP001',
        provider: 'wechat',
        subjectId: 'wx_user_456',
      };

      const { result } = renderHook(() => useBindUserMutation(), { wrapper: createWrapper() });

      await expect(result.current.mutateAsync(bindData)).rejects.toThrow('缺少用户ID');
      expect(mockApi.post).not.toHaveBeenCalled();
      expect(mockApi.put).not.toHaveBeenCalled();
    });

    it('should handle bind failure', async () => {
      const bindData = {
        id: 1001,
        provider: 'wechat',
        subjectId: 'invalid_user',
      };
      const mockError = new Error('User not found on platform');
      mockApi.put.mockRejectedValue(mockError);

      const { result } = renderHook(() => useBindUserMutation(), { wrapper: createWrapper() });

      await expect(result.current.mutateAsync(bindData)).rejects.toThrow(
        'User not found on platform',
      );
    });

    it('should send provider/subjectId in admin mode', async () => {
      const mockResponse = { data: null };
      mockApi.put.mockResolvedValue(mockResponse);
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useBindUserMutation(), { wrapper: createWrapper() });

      await result.current.mutateAsync({
        id: 1001,
        provider: 'wechat',
        subjectId: 'wx_user_admin_001',
      });

      expect(mockApi.put).toHaveBeenCalledWith('/admin/users/1001/platform-binding', {
        provider: 'wechat',
        subjectId: 'wx_user_admin_001',
      });
      expect(mockApi.post).not.toHaveBeenCalled();
    });

    it('should invalidate bindings query on success', async () => {
      const mockResponse = { data: { success: true } };
      mockApi.put.mockResolvedValue(mockResponse);

      const queryClient = new QueryClient();
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useBindUserMutation(), {
        wrapper: function TestWrapper({ children }: { children: React.ReactNode }) {
          return React.createElement(QueryClientProvider, { client: queryClient }, children);
        },
      });

      await result.current.mutateAsync({
        id: 1001,
        provider: 'wechat',
        subjectId: 'wx_user_456',
      });

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['userBindings'] });
    });
  });

  describe('useUnbindUserMutation', () => {
    it('should unbind user successfully', async () => {
      const mockResponse = { data: { success: true, message: '解绑成功' } };
      mockApi.delete.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useUnbindUserMutation(), { wrapper: createWrapper() });

      const mutateResult = await result.current.mutateAsync(123);

      expect(mockApi.delete).toHaveBeenCalledWith('/admin/users/123/platform-binding', {
        params: { alsoUnlinkEmployee: true },
      });
      expect(mutateResult).toEqual(mockResponse.data);
    });

    it('should handle unbind failure', async () => {
      const mockError = new Error('Binding not found');
      mockApi.delete.mockRejectedValue(mockError);

      const { result } = renderHook(() => useUnbindUserMutation(), { wrapper: createWrapper() });

      await expect(result.current.mutateAsync(999)).rejects.toThrow('Binding not found');
    });

    it('should invalidate bindings query on success', async () => {
      const mockResponse = { data: { success: true } };
      mockApi.delete.mockResolvedValue(mockResponse);

      const queryClient = new QueryClient();
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useUnbindUserMutation(), {
        wrapper: function TestWrapper({ children }: { children: React.ReactNode }) {
          return React.createElement(QueryClientProvider, { client: queryClient }, children);
        },
      });

      await result.current.mutateAsync(123);

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['userBindings'] });
    });
  });

  describe('mutation loading states', () => {
    it('should track loading state during bind', async () => {
      let resolvePromise: (value: any) => void;
      const promise = new Promise((resolve) => {
        resolvePromise = resolve;
      });
      mockApi.put.mockReturnValue(promise);

      const { result } = renderHook(() => useBindUserMutation(), { wrapper: createWrapper() });

      const bindPromise = result.current.mutateAsync({
        id: 1001,
        provider: 'wechat',
        subjectId: 'wx_user_456',
      });

      // Should be loading
      await waitFor(() => {
        expect(result.current.isPending).toBe(true);
      });

      // Resolve the promise
      resolvePromise!({ data: { success: true } });
      await bindPromise;

      // Should no longer be loading
      await waitFor(() => {
        expect(result.current.isPending).toBe(false);
      });
    });
  });
});
