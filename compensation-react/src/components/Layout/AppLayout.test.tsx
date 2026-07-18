import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App as AntdApp, ConfigProvider } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AppLayout from './AppLayout';
import { store } from '@services/stores/authSlice';
import { useUIStore } from '@services/stores/uiStore';

const mockRbac = vi.hoisted(() => ({ resources: [] as Record<string, unknown>[] }));

vi.mock('@services/queries/auth', () => ({
  useLogoutMutation: () => ({ mutate: vi.fn() }),
}));

vi.mock('@services/queries/rbac', () => ({
  useMeResourcesQuery: () => ({ data: { resources: mockRbac.resources }, isLoading: false }),
}));

vi.mock('@hooks/useWecomRegister', () => ({ useWecomRegister: vi.fn() }));
vi.mock('@hooks/useMenuRefresh', () => ({ useMenuRefresh: vi.fn() }));
vi.mock('@components/Navigation/Breadcrumb', () => ({
  AppBreadcrumb: () => <div data-testid="breadcrumb" />,
}));
vi.mock('@components/Navigation/BackTop', () => ({
  BackTop: () => <div data-testid="back-top" />,
}));

const TestWrapper: React.FC<{ children: React.ReactNode; initialEntries?: string[] }> = ({
  children,
  initialEntries = ['/'],
}) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ConfigProvider>
          <AntdApp>
            <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
          </AntdApp>
        </ConfigProvider>
      </QueryClientProvider>
    </Provider>
  );
};

describe('AppLayout', () => {
  beforeEach(() => {
    mockRbac.resources = [];
    useUIStore.setState({ theme: 'light', collapsed: false });
    store.dispatch({
      type: 'auth/setSession',
      payload: { user: { id: 'admin', username: 'admin', roles: ['ROLE_ADMIN'] } },
    });
  });

  it('renders the application shell and its content', () => {
    render(
      <TestWrapper>
        <AppLayout>
          <div>Page Content</div>
        </AppLayout>
      </TestWrapper>,
    );

    expect(screen.getByText('薪酬管理后台')).toBeInTheDocument();
    expect(screen.getByText('admin')).toBeInTheDocument();
    expect(screen.getByText('Page Content')).toBeInTheDocument();
    expect(screen.getByTestId('breadcrumb')).toBeInTheDocument();
  });

  it('shows the empty-resource fallback menu for administrators', () => {
    render(
      <TestWrapper>
        <AppLayout>
          <div>Page Content</div>
        </AppLayout>
      </TestWrapper>,
    );

    expect(screen.getByText('菜单管理')).toBeInTheDocument();
    expect(screen.getByText('角色管理')).toBeInTheDocument();
  });

  it('renders without page content', () => {
    render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    expect(screen.getByText('薪酬管理后台')).toBeInTheDocument();
  });

  it('toggles the sidebar and keeps the state in sync', () => {
    render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    const sider = screen.getByRole('complementary');
    const collapseButton = screen.getByRole('button', { name: '折叠侧边导航' });

    expect(sider).not.toHaveClass('ant-layout-sider-collapsed');
    fireEvent.click(collapseButton);
    expect(sider).toHaveClass('ant-layout-sider-collapsed');
    expect(screen.getByRole('button', { name: '展开侧边导航' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '展开侧边导航' }));
    expect(sider).not.toHaveClass('ant-layout-sider-collapsed');
  });

  it('uses a dismissible drawer navigation on mobile widths', () => {
    const originalInnerWidth = window.innerWidth;
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 375 });

    const { container, unmount } = render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    try {
      const sider = screen.getByRole('complementary');
      expect(sider).toHaveClass('ant-layout-sider-collapsed');

      fireEvent.click(screen.getByRole('button', { name: '打开侧边导航' }));
      expect(screen.getAllByRole('button', { name: '关闭侧边导航' })).toHaveLength(2);
      expect(container.querySelector('.app-mobile-nav-backdrop')).toBeInTheDocument();
      expect(sider).not.toHaveClass('ant-layout-sider-collapsed');

      fireEvent.click(container.querySelector('.app-mobile-nav-backdrop') as HTMLElement);
      expect(screen.getByRole('button', { name: '打开侧边导航' })).toBeInTheDocument();
      expect(sider).toHaveClass('ant-layout-sider-collapsed');
    } finally {
      unmount();
      Object.defineProperty(window, 'innerWidth', {
        configurable: true,
        value: originalInnerWidth,
      });
    }
  });

  it('renders resource icons so collapsed navigation remains usable', () => {
    mockRbac.resources = [
      {
        id: 1,
        type: 'MENU',
        code: 'employees',
        name: '员工管理',
        path: '/employees',
        icon: 'team',
        parentId: null,
        orderNum: 1,
      },
    ];

    const { container } = render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    expect(container.querySelector('.anticon-team')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '折叠侧边导航' }));
    expect(container.querySelector('.anticon-team')).toBeInTheDocument();
  });

  it('renders every database resource even when paths overlap', async () => {
    mockRbac.resources = [
      {
        id: 1,
        type: 'MENU',
        code: 'menu.system.payroll',
        name: '薪酬管理',
        path: null,
        parentId: null,
        orderNum: 1,
      },
      {
        id: 2,
        type: 'VIEW',
        code: 'view.payroll.batches',
        name: '旧版批次视图',
        path: '/payroll/batches',
        parentId: 1,
        orderNum: 1,
      },
      {
        id: 3,
        type: 'MENU',
        code: 'menu.payroll.batches',
        name: '薪酬批次',
        path: '/payroll/batches',
        parentId: 1,
        orderNum: 2,
      },
    ];

    render(
      <TestWrapper initialEntries={['/payroll/batches']}>
        <AppLayout />
      </TestWrapper>,
    );

    fireEvent.click(screen.getByText('薪酬管理'));
    await waitFor(() => expect(screen.getByText('薪酬批次')).toBeInTheDocument());
    expect(screen.getByText('旧版批次视图')).toBeInTheDocument();
  });
});
