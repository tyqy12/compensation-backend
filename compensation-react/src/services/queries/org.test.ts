import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import api from '@services/api';
import { useOrgSyncMutation } from './org';

// Mock the API
vi.mock('@services/api', () => ({
  default: {
    post: vi.fn(),
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

describe('Org Mutations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useOrgSyncMutation', () => {
    it('should start org sync successfully', async () => {
      const mockSyncResult = {
        success: true,
        message: '组织同步已启动',
        taskId: 'sync_task_123',
        estimatedTime: 300,
      };
      const mockResponse = { data: mockSyncResult };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useOrgSyncMutation(), { wrapper: createWrapper() });

      const mutateResult = await result.current.mutateAsync('wecom');

      expect(mockApi.post).toHaveBeenCalledWith('/org/wecom/sync');
      expect(mutateResult).toEqual(mockSyncResult);
    });

    it('should handle sync failure', async () => {
      const mockSyncResult = {
        success: false,
        message: '同步失败：配置不完整',
        error: 'MISSING_CONFIG',
      };
      const mockResponse = { data: mockSyncResult };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useOrgSyncMutation(), { wrapper: createWrapper() });

      const mutateResult = await result.current.mutateAsync('wecom');

      expect(mockApi.post).toHaveBeenCalledWith('/org/wecom/sync');
      expect(mutateResult).toEqual(mockSyncResult);
      expect(mutateResult.success).toBe(false);
    });

    it('should handle network error during sync', async () => {
      const mockError = new Error('Request timeout');
      mockApi.post.mockRejectedValue(mockError);

      const { result } = renderHook(() => useOrgSyncMutation(), { wrapper: createWrapper() });

      await expect(result.current.mutateAsync('wecom')).rejects.toThrow('Request timeout');
    });

    it('should support different platforms', async () => {
      const platforms = ['wecom', 'dingtalk', 'feishu'];
      const mockResponse = { data: { success: true, message: '同步成功' } };
      mockApi.post.mockResolvedValue(mockResponse);

      for (const platform of platforms) {
        const { result } = renderHook(() => useOrgSyncMutation(), { wrapper: createWrapper() });

        await result.current.mutateAsync(platform);
        expect(mockApi.post).toHaveBeenCalledWith(`/org/${platform}/sync`);
      }
    });

    it('should track loading state during sync', async () => {
      let resolvePromise: (value: any) => void;
      const promise = new Promise((resolve) => {
        resolvePromise = resolve;
      });
      mockApi.post.mockReturnValue(promise);

      const { result } = renderHook(() => useOrgSyncMutation(), { wrapper: createWrapper() });

      const syncPromise = result.current.mutateAsync('wecom');

      // Should be loading
      expect(result.current.isPending).toBe(true);

      // Resolve the promise
      resolvePromise!({ data: { success: true } });
      await syncPromise;

      // Should no longer be loading
      await waitFor(() => {
        expect(result.current.isPending).toBe(false);
      });
    });

    it('should invalidate related queries on success', async () => {
      const mockResponse = { data: { success: true } };
      mockApi.post.mockResolvedValue(mockResponse);

      const queryClient = new QueryClient();
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useOrgSyncMutation(), {
        wrapper: function TestWrapper({ children }: { children: React.ReactNode }) {
          return React.createElement(QueryClientProvider, { client: queryClient }, children);
        },
      });

      await result.current.mutateAsync('wecom');

      // Should invalidate employee and user binding queries
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['employees'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['userBindings'] });
    });
  });
});
