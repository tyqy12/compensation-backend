import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useLoginMutation } from './auth';
import { loginApi } from '@services/auth';
import { store } from '@services/stores/authSlice';

const navigate = vi.fn();
const messageError = vi.fn();

vi.mock('@services/auth', () => ({
  loginApi: vi.fn(),
  logoutApi: vi.fn(),
  oauthCallbackApi: vi.fn(),
}));

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => navigate,
  };
});

vi.mock('antd', async () => {
  const actual = await vi.importActual<typeof import('antd')>('antd');
  return {
    ...actual,
    App: {
      ...actual.App,
      useApp: () => ({
        message: { error: messageError },
      }),
    },
  };
});

function createStorageMock() {
  const store = new Map<string, string>();
  return {
    getItem: vi.fn((key: string) => store.get(key) ?? null),
    setItem: vi.fn((key: string, value: string) => store.set(key, String(value))),
    removeItem: vi.fn((key: string) => store.delete(key)),
    clear: vi.fn(() => store.clear()),
  };
}

const createWrapper = (initialEntry = '/login') => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return ({ children }: { children: React.ReactNode }) => (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter>
      </QueryClientProvider>
    </Provider>
  );
};

describe('useLoginMutation redirect', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: createStorageMock(),
    });
    Object.defineProperty(window, 'sessionStorage', {
      configurable: true,
      value: createStorageMock(),
    });
    localStorage.clear();
    sessionStorage.clear();
    store.dispatch({ type: 'auth/logout' });
    vi.mocked(loginApi).mockResolvedValue({
      accessToken: 'token',
      refreshToken: 'refresh',
      user: { id: 'finance', username: 'finance', roles: ['FINANCE'] },
    });
  });

  it('登录成功后消费硬跳兜底保存的原页面路径', async () => {
    sessionStorage.setItem('auth_redirect', '/payments/batches?status=submitted');
    const { result } = renderHook(() => useLoginMutation(), { wrapper: createWrapper('/login') });

    await act(async () => {
      await result.current.mutateAsync({ username: 'finance', password: 'secret' });
    });

    await waitFor(() => {
      expect(navigate).toHaveBeenCalledWith('/payments/batches?status=submitted', { replace: true });
    });
    expect(sessionStorage.getItem('auth_redirect')).toBeNull();
  });
});
