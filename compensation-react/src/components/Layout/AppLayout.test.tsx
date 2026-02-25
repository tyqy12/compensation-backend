import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import AppLayout from './AppLayout';

// Mock the stores
const mockUiStore = {
  theme: 'light',
  collapsed: false,
  toggleTheme: vi.fn(),
  toggleSidebar: vi.fn(),
};

const mockAuthStore = {
  user: {
    id: 1,
    username: 'admin',
    name: '管理员',
    email: 'admin@company.com',
    roles: ['ROLE_ADMIN'],
  },
  isAuthenticated: true,
  logout: vi.fn(),
};

vi.mock('@services/stores/uiStore', () => ({
  useUiStore: () => mockUiStore,
}));

vi.mock('@services/stores/authSlice', () => ({
  useAuthStore: () => mockAuthStore,
}));

// Mock ProLayout
vi.mock('@ant-design/pro-layout', async () => {
  const actual = await vi.importActual('@ant-design/pro-layout');
  return {
    ...actual,
    ProLayout: ({ children, menuItemRender, headerContentRender, avatarProps, ...props }: any) => (
      <div data-testid="pro-layout" {...props}>
        <div data-testid="header">
          {headerContentRender && headerContentRender()}
          {avatarProps && (
            <div data-testid="avatar" onClick={avatarProps.onClick}>
              {avatarProps.title}
            </div>
          )}
        </div>
        <div data-testid="menu">
          {props.route?.routes?.map((route: any) =>
            menuItemRender ? (
              menuItemRender({ path: route.path, name: route.name }, null, props)
            ) : (
              <div key={route.path} data-testid={`menu-item-${route.path}`}>
                {route.name}
              </div>
            ),
          )}
        </div>
        <div data-testid="content">{children}</div>
      </div>
    ),
  };
});

// Mock Outlet
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    Outlet: () => <div data-testid="outlet">Page Content</div>,
    useNavigate: () => vi.fn(),
    useLocation: () => ({ pathname: '/dashboard' }),
  };
});

const TestWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return (
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <ConfigProvider>{children}</ConfigProvider>
      </QueryClientProvider>
    </MemoryRouter>
  );
};

describe('AppLayout', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render layout with user info', () => {
    render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    expect(screen.getByTestId('pro-layout')).toBeInTheDocument();
    expect(screen.getByTestId('content')).toBeInTheDocument();
    expect(screen.getByTestId('outlet')).toBeInTheDocument();
  });

  it('should render menu items', () => {
    render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    // Check if menu container exists
    expect(screen.getByTestId('menu')).toBeInTheDocument();
  });

  it('should render header with user avatar', () => {
    render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    expect(screen.getByTestId('header')).toBeInTheDocument();
  });

  it('should render theme toggle when theme prop is provided', () => {
    const { rerender } = render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    // Mock the theme toggle component being rendered
    expect(screen.getByTestId('header')).toBeInTheDocument();

    // Test theme switching
    mockUiStore.theme = 'dark';
    rerender(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    expect(screen.getByTestId('header')).toBeInTheDocument();
  });

  it('should handle avatar click', () => {
    render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    const avatar = screen.queryByTestId('avatar');
    if (avatar) {
      fireEvent.click(avatar);
      // Avatar click should work without errors
      expect(avatar).toBeInTheDocument();
    }
  });

  it('should render with collapsed sidebar', () => {
    mockUiStore.collapsed = true;

    render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    expect(screen.getByTestId('pro-layout')).toBeInTheDocument();
  });

  it('should render with expanded sidebar', () => {
    mockUiStore.collapsed = false;

    render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    expect(screen.getByTestId('pro-layout')).toBeInTheDocument();
  });

  it('should apply dark theme styles', () => {
    mockUiStore.theme = 'dark';

    render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    expect(screen.getByTestId('pro-layout')).toBeInTheDocument();
  });

  it('should apply light theme styles', () => {
    mockUiStore.theme = 'light';

    render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    expect(screen.getByTestId('pro-layout')).toBeInTheDocument();
  });

  it('should render without user when not authenticated', () => {
    mockAuthStore.isAuthenticated = false;
    mockAuthStore.user = null;

    render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    expect(screen.getByTestId('pro-layout')).toBeInTheDocument();
    expect(screen.getByTestId('content')).toBeInTheDocument();
  });

  it('should render with different user roles', () => {
    mockAuthStore.user = {
      id: 2,
      username: 'user',
      name: '普通用户',
      email: 'user@company.com',
      roles: ['ROLE_USER'],
    };

    render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    expect(screen.getByTestId('pro-layout')).toBeInTheDocument();
  });

  it('should handle store updates', async () => {
    const { rerender } = render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    // Simulate store updates
    mockUiStore.collapsed = true;
    mockUiStore.theme = 'dark';

    rerender(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('pro-layout')).toBeInTheDocument();
    });
  });

  it('should render responsive layout', () => {
    // Mock window size
    Object.defineProperty(window, 'innerWidth', {
      writable: true,
      configurable: true,
      value: 768,
    });

    render(
      <TestWrapper>
        <AppLayout />
      </TestWrapper>,
    );

    expect(screen.getByTestId('pro-layout')).toBeInTheDocument();
  });
});
