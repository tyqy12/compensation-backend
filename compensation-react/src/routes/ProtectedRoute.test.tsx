import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { store } from '../services/stores/authSlice';
import { ProtectedRoute } from './ProtectedRoute';
import { login } from '../services/stores/authSlice';
import { getMeResources } from '@services/rbac';

vi.mock('@services/rbac', () => ({
  getMeResources: vi.fn(),
}));

const testLocation = vi.hoisted(() => ({ pathname: '/test' }));

// Mock react-router-dom
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');

  const MockNavigate = ({ to, state }: { to: string; state?: any }) => (
    <div data-testid="navigate" data-to={to} data-state={JSON.stringify(state)}>
      Redirecting to {to}
    </div>
  );

  return {
    ...actual,
    Navigate: MockNavigate,
    useLocation: () => ({ pathname: testLocation.pathname, search: '', hash: '', state: null }),
  };
});

const mockGetMeResources = vi.mocked(getMeResources);

const TestWrapper = ({ children }: { children: React.ReactNode }) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>{children}</BrowserRouter>
      </QueryClientProvider>
    </Provider>
  );
};

describe('ProtectedRoute', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    testLocation.pathname = '/test';
    const storage = new Map<string, string>();
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: {
        getItem: (key: string) => storage.get(key) ?? null,
        setItem: (key: string, value: string) => storage.set(key, value),
        removeItem: (key: string) => storage.delete(key),
        clear: () => storage.clear(),
      },
    });
    store.dispatch({ type: 'auth/logout' });
    mockGetMeResources.mockResolvedValue({
      permissionVersion: 1,
      resources: [
        {
          id: 1,
          type: 'VIEW',
          code: 'test',
          name: '测试页面',
          path: '/test',
        },
      ],
      actions: {},
    });
  });

  it('应该在未认证时跳转到登录页', () => {
    render(
      <TestWrapper>
        <ProtectedRoute>
          <div>Protected Content</div>
        </ProtectedRoute>
      </TestWrapper>,
    );

    const navigate = screen.getByTestId('navigate');
    expect(navigate).toHaveAttribute('data-to', '/login');
    expect(navigate).toHaveTextContent('Redirecting to /login');
    expect(mockGetMeResources).not.toHaveBeenCalled();
  });

  it('应该在认证但无权限时跳转到 403 页面', () => {
    const mockUser = {
      id: '1',
      username: 'testuser',
      roles: ['USER'],
    };

    store.dispatch(login(mockUser));

    render(
      <TestWrapper>
        <ProtectedRoute roles={['ADMIN']}>
          <div>Admin Content</div>
        </ProtectedRoute>
      </TestWrapper>,
    );

    const navigate = screen.getByTestId('navigate');
    expect(navigate).toHaveAttribute('data-to', '/403');
    expect(navigate).toHaveTextContent('Redirecting to /403');
    expect(mockGetMeResources).not.toHaveBeenCalled();
  });

  it('应该在有权限时显示内容', async () => {
    const mockUser = {
      id: '1',
      username: 'testuser',
      roles: ['ADMIN'],
    };

    store.dispatch(login(mockUser));

    render(
      <TestWrapper>
        <ProtectedRoute roles={['ADMIN']}>
          <div>Admin Content</div>
        </ProtectedRoute>
      </TestWrapper>,
    );

    expect(await screen.findByText('Admin Content')).toBeInTheDocument();
    expect(screen.queryByTestId('navigate')).not.toBeInTheDocument();
    expect(mockGetMeResources).toHaveBeenCalledTimes(1);
  });

  it('应该在无角色要求时显示内容（仅需要认证）', async () => {
    const mockUser = {
      id: '1',
      username: 'testuser',
      roles: ['USER'],
    };

    store.dispatch(login(mockUser));

    render(
      <TestWrapper>
        <ProtectedRoute>
          <div>Protected Content</div>
        </ProtectedRoute>
      </TestWrapper>,
    );

    expect(await screen.findByText('Protected Content')).toBeInTheDocument();
    expect(screen.queryByTestId('navigate')).not.toBeInTheDocument();
  });

  it('权限资源加载中时不先渲染受保护内容', () => {
    mockGetMeResources.mockReturnValue(new Promise(() => {}) as any);
    const mockUser = {
      id: '1',
      username: 'testuser',
      roles: ['USER'],
    };

    store.dispatch(login(mockUser));

    render(
      <TestWrapper>
        <ProtectedRoute>
          <div>Protected Content</div>
        </ProtectedRoute>
      </TestWrapper>,
    );

    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
    expect(screen.queryByTestId('navigate')).not.toBeInTheDocument();
  });

  it('认证用户没有任何页面资源时默认拒绝访问', async () => {
    mockGetMeResources.mockResolvedValue({
      permissionVersion: 1,
      resources: [],
      actions: {},
    });
    const mockUser = {
      id: '1',
      username: 'testuser',
      roles: ['USER'],
    };

    store.dispatch(login(mockUser));

    render(
      <TestWrapper>
        <ProtectedRoute>
          <div>Protected Content</div>
        </ProtectedRoute>
      </TestWrapper>,
    );

    await waitFor(() => expect(screen.getByTestId('navigate')).toHaveAttribute('data-to', '/403'));
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('首页权限缓存过期时等待服务端刷新后再判断访问权限', async () => {
    testLocation.pathname = '/';
    localStorage.setItem('rbac_cache_1', JSON.stringify({
      permissionVersion: 1,
      resources: [
        { id: 2, type: 'MENU', code: 'employees', name: '员工管理', path: '/employees' },
      ],
      actions: {},
    }));
    mockGetMeResources.mockResolvedValue({
      permissionVersion: 2,
      resources: [
        { id: 1, type: 'MENU', code: 'dashboard', name: '工作台', path: '/' },
      ],
      actions: {},
    });
    store.dispatch(login({ id: '1', username: 'testuser', roles: ['HR'] }));

    render(
      <TestWrapper>
        <ProtectedRoute>
          <div>Protected Content</div>
        </ProtectedRoute>
      </TestWrapper>,
    );

    expect(await screen.findByText('Protected Content')).toBeInTheDocument();
    expect(mockGetMeResources).toHaveBeenCalled();
    expect(screen.queryByTestId('navigate')).not.toBeInTheDocument();
  });

  it('权限资源加载失败时跳转到系统错误页，不按空权限处理', async () => {
    mockGetMeResources.mockRejectedValue(new Error('permission service unavailable'));
    const mockUser = {
      id: '1',
      username: 'testuser',
      roles: ['USER'],
    };

    store.dispatch(login(mockUser));

    render(
      <TestWrapper>
        <ProtectedRoute>
          <div>Protected Content</div>
        </ProtectedRoute>
      </TestWrapper>,
    );

    await waitFor(() => expect(screen.getByTestId('navigate')).toHaveAttribute('data-to', '/500'));
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('管理员在资源尚未初始化时保留基础入口兜底', async () => {
    mockGetMeResources.mockResolvedValue({
      permissionVersion: 1,
      resources: [],
      actions: {},
    });
    const mockUser = {
      id: '1',
      username: 'admin',
      roles: ['ADMIN'],
    };

    store.dispatch(login(mockUser));

    render(
      <TestWrapper>
        <ProtectedRoute>
          <div>Admin Bootstrap Content</div>
        </ProtectedRoute>
      </TestWrapper>,
    );

    expect(await screen.findByText('Admin Bootstrap Content')).toBeInTheDocument();
    expect(screen.queryByTestId('navigate')).not.toBeInTheDocument();
  });
});
