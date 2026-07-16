import React from 'react';
import { act, renderHook } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loginApi, logoutApi } from '@services/auth';
import { clearSession, store } from '@services/stores/authSlice';
import { useLoginMutation, useLogoutMutation } from './auth';

vi.mock('@services/auth', () => ({
  loginApi: vi.fn(),
  logoutApi: vi.fn(),
  oauthCallbackApi: vi.fn(),
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { error: vi.fn() } }),
  },
}));

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return ({ children }: { children: React.ReactNode }) => React.createElement(
    Provider,
    { store },
    React.createElement(
      QueryClientProvider,
      { client: queryClient },
      React.createElement(MemoryRouter, null, children),
    ),
  );
};

describe('auth mutations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    store.dispatch(clearSession());
  });

  afterEach(() => {
    store.dispatch(clearSession());
  });

  it('stores the session returned by the login API', async () => {
    vi.mocked(loginApi).mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      user: { id: 'finance', username: 'finance', roles: ['FINANCE'] },
    });

    const { result } = renderHook(() => useLoginMutation(), { wrapper: createWrapper() });
    await act(async () => {
      await result.current.mutateAsync({ username: 'finance', password: 'secret' });
    });

    expect(loginApi).toHaveBeenCalledWith({ username: 'finance', password: 'secret' });
    expect(store.getState().auth).toMatchObject({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      user: { username: 'finance' },
    });
  });

  it('clears the session after logout settles', async () => {
    store.dispatch({
      type: 'auth/setSession',
      payload: { user: { id: 'finance', username: 'finance', roles: ['FINANCE'] } },
    });
    vi.mocked(logoutApi).mockResolvedValue();

    const { result } = renderHook(() => useLogoutMutation(), { wrapper: createWrapper() });
    await act(async () => {
      await result.current.mutateAsync();
    });

    expect(logoutApi).toHaveBeenCalledOnce();
    expect(store.getState().auth.user).toBeNull();
  });

  it('propagates login errors to the mutation caller', async () => {
    vi.mocked(loginApi).mockRejectedValue(new Error('登录失败'));

    const { result } = renderHook(() => useLoginMutation(), { wrapper: createWrapper() });

    await expect(result.current.mutateAsync({ username: 'finance', password: 'bad' }))
      .rejects.toThrow('登录失败');
  });
});
