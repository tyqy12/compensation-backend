import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider } from 'react-redux';
import { App as AntdApp } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { store } from '@services/stores/authSlice';
import EmployeeProfile from './Profile';

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

const mockUseCurrentUserSummaryQuery = vi.fn();
const mockUseMyEmployeeProfileQuery = vi.fn();
const mockUseMyEmployeeChangeRequestsQuery = vi.fn();
const mockUseUpdateMyEmployeeContactMutation = vi.fn();
const mockUseSubmitMyEmployeeChangeRequestMutation = vi.fn();

vi.mock('@services/queries/session', () => ({
  useCurrentUserSummaryQuery: () => mockUseCurrentUserSummaryQuery(),
}));

vi.mock('@services/queries/employee', () => ({
  useMyEmployeeProfileQuery: (options?: { enabled?: boolean }) => mockUseMyEmployeeProfileQuery(options),
  useMyEmployeeChangeRequestsQuery: (
    params: { current: number; pageSize: number },
    options?: { enabled?: boolean },
  ) => mockUseMyEmployeeChangeRequestsQuery(params, options),
  useUpdateMyEmployeeContactMutation: () => mockUseUpdateMyEmployeeContactMutation(),
  useSubmitMyEmployeeChangeRequestMutation: () => mockUseSubmitMyEmployeeChangeRequestMutation(),
}));

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return ({ children }: { children: React.ReactNode }) => (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <AntdApp>{children}</AntdApp>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  );
};

describe('EmployeeProfile', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mockUseUpdateMyEmployeeContactMutation.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockUseSubmitMyEmployeeChangeRequestMutation.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
  });

  it('未绑定员工档案时不触发员工自助查询，并显示提示', () => {
    mockUseCurrentUserSummaryQuery.mockReturnValue({
      data: {
        id: '1',
        username: 'admin',
        roles: ['ROLE_ADMIN'],
        employeeId: null,
        hasEmployeeProfile: false,
      },
      isLoading: false,
      isError: false,
      isFetching: false,
      refetch: vi.fn(),
    });
    mockUseMyEmployeeProfileQuery.mockReturnValue({
      data: undefined,
      isLoading: false,
      isFetching: false,
      refetch: vi.fn(),
    });
    mockUseMyEmployeeChangeRequestsQuery.mockReturnValue({
      data: undefined,
      isLoading: false,
      isFetching: false,
      refetch: vi.fn(),
    });

    render(<EmployeeProfile />, { wrapper: createWrapper() });

    expect(mockUseMyEmployeeProfileQuery).toHaveBeenCalledWith({ enabled: false });
    expect(mockUseMyEmployeeChangeRequestsQuery).toHaveBeenCalledWith(
      { current: 1, pageSize: 10 },
      { enabled: false },
    );
    expect(screen.getByText('当前账号未绑定员工档案')).toBeInTheDocument();
    expect(screen.queryByText('我的基础信息')).not.toBeInTheDocument();
  });
});
