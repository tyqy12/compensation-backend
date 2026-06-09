import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '@services/api';
import {
  useScheduledTasksQuery,
  useTaskExecutionLogsQuery,
  useTriggerTaskMutation,
} from './taskSchedule';

vi.mock('@services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
  unwrap: vi.fn((data) => data),
}));

const mockApi = api as any;

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  const Wrapper = ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);

  return Wrapper;
};

describe('TaskSchedule Queries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should not repeat the global /api context path', async () => {
    mockApi.get.mockResolvedValue({ data: [] });

    renderHook(() => useScheduledTasksQuery(), { wrapper: createWrapper() });

    await waitFor(() => {
      expect(mockApi.get).toHaveBeenCalledWith('/v1/admin/tasks');
    });
    expect(mockApi.get).not.toHaveBeenCalledWith('/api/v1/admin/tasks');
  });

  it('should request task logs under the versioned task path', async () => {
    mockApi.get.mockResolvedValue({ data: [] });

    renderHook(() => useTaskExecutionLogsQuery(12, 20), { wrapper: createWrapper() });

    await waitFor(() => {
      expect(mockApi.get).toHaveBeenCalledWith('/v1/admin/tasks/12/logs', {
        params: { limit: 20 },
      });
    });
  });

  it('should trigger a task under the versioned task path', async () => {
    mockApi.post.mockResolvedValue({ data: 1001 });

    const { result } = renderHook(() => useTriggerTaskMutation(), { wrapper: createWrapper() });

    await result.current.mutateAsync(12);

    expect(mockApi.post).toHaveBeenCalledWith('/v1/admin/tasks/12/trigger');
  });
});
