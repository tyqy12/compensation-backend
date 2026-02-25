import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import api from '@services/api';
import {
  useLoginMutation,
  useLogoutMutation,
  useRefreshTokenMutation,
  useUserProfileQuery,
} from './auth';

// Mock the API
vi.mock('@services/api', () => ({
  default: {
    get: vi.fn(),
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

// Test data
const mockUser = {
  id: 1,
  username: 'admin',
  name: '管理员',
  email: 'admin@company.com',
  roles: ['ROLE_ADMIN'],
  permissions: ['USER_READ', 'USER_WRITE'],
  createTime: '2023-01-01T00:00:00Z',
  lastLoginTime: '2024-01-15T10:00:00Z',
};

const mockLoginResponse = {
  user: mockUser,
  token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...',
  refreshToken: 'refresh_token_123',
  expiresIn: 3600,
};

describe('Auth Queries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useUserProfileQuery', () => {
    it('should fetch user profile', async () => {
      const mockResponse = { data: mockUser };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useUserProfileQuery(), { wrapper: createWrapper() });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/auth/profile');
      expect(result.current.data).toEqual(mockUser);
    });

    it('should handle error when fetching profile', async () => {
      const mockError = new Error('Unauthorized');
      mockApi.get.mockRejectedValue(mockError);

      const { result } = renderHook(() => useUserProfileQuery(), { wrapper: createWrapper() });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toEqual(mockError);
    });
  });
});

describe('Auth Mutations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useLoginMutation', () => {
    it('should login successfully', async () => {
      const mockResponse = { data: mockLoginResponse };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useLoginMutation(), { wrapper: createWrapper() });

      const loginData = { username: 'admin', password: 'password123' };
      const mutateResult = await result.current.mutateAsync(loginData);

      expect(mockApi.post).toHaveBeenCalledWith('/auth/login', loginData);
      expect(mutateResult).toEqual(mockLoginResponse);
    });

    it('should handle login failure', async () => {
      const mockError = new Error('Invalid credentials');
      mockApi.post.mockRejectedValue(mockError);

      const { result } = renderHook(() => useLoginMutation(), { wrapper: createWrapper() });

      const loginData = { username: 'admin', password: 'wrong' };

      await expect(result.current.mutateAsync(loginData)).rejects.toThrow('Invalid credentials');
      expect(mockApi.post).toHaveBeenCalledWith('/auth/login', loginData);
    });
  });

  describe('useLogoutMutation', () => {
    it('should logout successfully', async () => {
      const mockResponse = { data: { success: true, message: '登出成功' } };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useLogoutMutation(), { wrapper: createWrapper() });

      const mutateResult = await result.current.mutateAsync();

      expect(mockApi.post).toHaveBeenCalledWith('/auth/logout');
      expect(mutateResult).toEqual(mockResponse.data);
    });

    it('should handle logout error', async () => {
      const mockError = new Error('Session expired');
      mockApi.post.mockRejectedValue(mockError);

      const { result } = renderHook(() => useLogoutMutation(), { wrapper: createWrapper() });

      await expect(result.current.mutateAsync()).rejects.toThrow('Session expired');
    });
  });

  describe('useRefreshTokenMutation', () => {
    it('should refresh token successfully', async () => {
      const mockRefreshResponse = {
        token: 'new_jwt_token',
        refreshToken: 'new_refresh_token',
        expiresIn: 3600,
      };
      const mockResponse = { data: mockRefreshResponse };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useRefreshTokenMutation(), { wrapper: createWrapper() });

      const mutateResult = await result.current.mutateAsync('refresh_token_123');

      expect(mockApi.post).toHaveBeenCalledWith('/auth/refresh', {
        refreshToken: 'refresh_token_123',
      });
      expect(mutateResult).toEqual(mockRefreshResponse);
    });

    it('should handle refresh token failure', async () => {
      const mockError = new Error('Refresh token expired');
      mockApi.post.mockRejectedValue(mockError);

      const { result } = renderHook(() => useRefreshTokenMutation(), { wrapper: createWrapper() });

      await expect(result.current.mutateAsync('invalid_token')).rejects.toThrow(
        'Refresh token expired',
      );
      expect(mockApi.post).toHaveBeenCalledWith('/auth/refresh', {
        refreshToken: 'invalid_token',
      });
    });
  });

  describe('mutation callbacks', () => {
    it('should invalidate profile query on successful login', async () => {
      const mockResponse = { data: mockLoginResponse };
      mockApi.post.mockResolvedValue(mockResponse);

      const queryClient = new QueryClient();
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useLoginMutation(), {
        wrapper: function TestWrapper({ children }: { children: React.ReactNode }) {
          return React.createElement(QueryClientProvider, { client: queryClient }, children);
        },
      });

      await result.current.mutateAsync({ username: 'admin', password: 'password123' });

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user', 'profile'] });
    });

    it('should clear all queries on logout', async () => {
      const mockResponse = { data: { success: true } };
      mockApi.post.mockResolvedValue(mockResponse);

      const queryClient = new QueryClient();
      const clearSpy = vi.spyOn(queryClient, 'clear');

      const { result } = renderHook(() => useLogoutMutation(), {
        wrapper: function TestWrapper({ children }: { children: React.ReactNode }) {
          return React.createElement(QueryClientProvider, { client: queryClient }, children);
        },
      });

      await result.current.mutateAsync();

      expect(clearSpy).toHaveBeenCalled();
    });
  });
});
