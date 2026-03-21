import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import api from '@services/api';
import {
  useUserBindingsQuery,
  useBindUserMutation,
  useUnbindUserMutation,
  useBatchBindUsersMutation,
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

      expect(mockApi.get).toHaveBeenCalledWith('/user-binding', {
        params: {
          page: 1,
          size: 10,
          platform: 'wechat',
          bindStatus: 'active',
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
        expect(mockApi.get).toHaveBeenCalledWith('/user-binding', {
          params: {
            page: 1,
            size: 10,
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
  });
});

describe('UserBinding Mutations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useBindUserMutation', () => {
    it('should bind user successfully', async () => {
      const bindData = {
        employeeId: 'EMP001',
        provider: 'wechat',
        subjectId: 'wx_user_456',
      };
      const mockResponse = { data: { success: true, message: '绑定成功' } };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useBindUserMutation(), { wrapper: createWrapper() });

      const mutateResult = await result.current.mutateAsync(bindData);

      expect(mockApi.post).toHaveBeenCalledWith('/user-binding', {
        employeeId: 'EMP001',
        provider: 'wechat',
        subjectId: 'wx_user_456',
      });
      expect(mutateResult).toEqual(mockResponse.data);
    });

    it('should handle bind failure', async () => {
      const bindData = {
        employeeId: 'EMP001',
        provider: 'wechat',
        subjectId: 'invalid_user',
      };
      const mockError = new Error('User not found on platform');
      mockApi.post.mockRejectedValue(mockError);

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
      mockApi.post.mockResolvedValue(mockResponse);

      const queryClient = new QueryClient();
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useBindUserMutation(), {
        wrapper: function TestWrapper({ children }: { children: React.ReactNode }) {
          return React.createElement(QueryClientProvider, { client: queryClient }, children);
        },
      });

      await result.current.mutateAsync({
        employeeId: 'EMP001',
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

      expect(mockApi.delete).toHaveBeenCalledWith('/user-binding/123');
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

  describe('useBatchBindUsersMutation', () => {
    it('should batch bind users successfully', async () => {
      const batchData = {
        provider: 'wechat',
        bindings: [
          { employeeId: 'EMP001', subjectId: 'wx_user_001' },
          { employeeId: 'EMP002', subjectId: 'wx_user_002' },
        ],
      };
      const mockResponse = {
        data: {
          success: true,
          message: '批量绑定完成',
          successCount: 2,
          failedCount: 0,
          results: [
            { employeeId: 'EMP001', success: true },
            { employeeId: 'EMP002', success: true },
          ],
        },
      };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useBatchBindUsersMutation(), {
        wrapper: createWrapper(),
      });

      const mutateResult = await result.current.mutateAsync(batchData);

      expect(mockApi.post).toHaveBeenCalledWith('/user-binding/batch', {
        provider: 'wechat',
        bindings: [
          { employeeId: 'EMP001', subjectId: 'wx_user_001' },
          { employeeId: 'EMP002', subjectId: 'wx_user_002' },
        ],
      });
      expect(mutateResult).toEqual(mockResponse.data);
    });

    it('should handle partial batch bind success', async () => {
      const batchData = {
        provider: 'wechat',
        bindings: [
          { employeeId: 'EMP001', subjectId: 'wx_user_001' },
          { employeeId: 'EMP002', subjectId: 'invalid_user' },
        ],
      };
      const mockResponse = {
        data: {
          success: true,
          message: '批量绑定完成，部分失败',
          successCount: 1,
          failedCount: 1,
          results: [
            { employeeId: 'EMP001', success: true },
            { employeeId: 'EMP002', success: false, error: 'User not found' },
          ],
        },
      };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useBatchBindUsersMutation(), {
        wrapper: createWrapper(),
      });

      const mutateResult = await result.current.mutateAsync(batchData);

      expect(mutateResult.successCount).toBe(1);
      expect(mutateResult.failedCount).toBe(1);
    });

    it('should handle batch bind network error', async () => {
      const batchData = {
        provider: 'wechat',
        bindings: [{ employeeId: 'EMP001', subjectId: 'wx_user_001' }],
      };
      const mockError = new Error('Network timeout');
      mockApi.post.mockRejectedValue(mockError);

      const { result } = renderHook(() => useBatchBindUsersMutation(), {
        wrapper: createWrapper(),
      });

      await expect(result.current.mutateAsync(batchData)).rejects.toThrow('Network timeout');
    });

    it('should invalidate bindings query on success', async () => {
      const mockResponse = { data: { success: true, successCount: 1, failedCount: 0 } };
      mockApi.post.mockResolvedValue(mockResponse);

      const queryClient = new QueryClient();
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useBatchBindUsersMutation(), {
        wrapper: function TestWrapper({ children }: { children: React.ReactNode }) {
          return React.createElement(QueryClientProvider, { client: queryClient }, children);
        },
      });

      await result.current.mutateAsync({
        provider: 'wechat',
        bindings: [{ employeeId: 'EMP001', subjectId: 'wx_user_001' }],
      });

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['userBindings'] });
    });
  });

  describe('mutation loading states', () => {
    it('should track loading state during bind', async () => {
      let resolvePromise: (value: any) => void;
      const promise = new Promise((resolve) => {
        resolvePromise = resolve;
      });
      mockApi.post.mockReturnValue(promise);

      const { result } = renderHook(() => useBindUserMutation(), { wrapper: createWrapper() });

      const bindPromise = result.current.mutateAsync({
        employeeId: 'EMP001',
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
