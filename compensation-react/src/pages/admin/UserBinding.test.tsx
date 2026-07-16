import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider } from 'react-redux';
import { App as AntdApp } from 'antd';
import { vi, beforeEach } from 'vitest';
import { store } from '../../services/stores/authSlice';
import UserBindingPage from './UserBinding';

const mockUseUserBindingsQuery = vi.fn();
const mockUseBindUserMutation = vi.fn();
const mockUseUnbindUserMutation = vi.fn();
const mockUseBindEmployeeMutation = vi.fn();

vi.mock('@services/queries/userBinding', () => ({
  useUserBindingsQuery: () => mockUseUserBindingsQuery(),
  useBindUserMutation: () => mockUseBindUserMutation(),
  useUnbindUserMutation: () => mockUseUnbindUserMutation(),
  useBindEmployeeMutation: () => mockUseBindEmployeeMutation(),
}));

const TestWrapper = ({ children }: { children: React.ReactNode }) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AntdApp>{children}</AntdApp>
        </BrowserRouter>
      </QueryClientProvider>
    </Provider>
  );
};

beforeEach(() => {
  vi.clearAllMocks();

  mockUseUserBindingsQuery.mockReturnValue({
    data: {
      records: [
        {
          id: 1,
          username: 'alice',
          provider: 'wechat',
          subjectId: 'wx_001',
          bound: true,
          employeeId: 1001,
          employeeName: '张三',
        },
        {
          id: 2,
          username: 'bob',
          provider: null,
          subjectId: null,
          bound: false,
          employeeId: null,
          employeeName: null,
        },
      ],
      total: 2,
    },
    isLoading: false,
    isError: false,
    error: null,
    refetch: vi.fn().mockResolvedValue({
      data: {
        records: [
          {
            id: 1,
            username: 'alice',
            provider: 'wechat',
            subjectId: 'wx_001',
            bound: true,
            employeeId: 1001,
            employeeName: '张三',
          },
          {
            id: 2,
            username: 'bob',
            provider: null,
            subjectId: null,
            bound: false,
            employeeId: null,
            employeeName: null,
          },
        ],
        total: 2,
      },
    }),
  });

  mockUseBindUserMutation.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
  mockUseUnbindUserMutation.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
  mockUseBindEmployeeMutation.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
});

describe('UserBindingPage', () => {
  it('renders list rows with platform and employee info', async () => {
    render(
      <TestWrapper>
        <UserBindingPage />
      </TestWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText('alice')).toBeInTheDocument();
      expect(screen.getByText('企业微信')).toBeInTheDocument();
      expect(screen.getByText('wx_001')).toBeInTheDocument();
      expect(screen.getByText('张三')).toBeInTheDocument();
      expect(screen.getByText('#1001')).toBeInTheDocument();
      expect(screen.getByText('bob')).toBeInTheDocument();
      expect(screen.getAllByText('未绑定')).toHaveLength(2);
    });
  });

  it('opens platform binding modal', async () => {
    const bindMutation = vi.fn().mockResolvedValue(undefined);
    mockUseBindUserMutation.mockReturnValue({ mutateAsync: bindMutation, isPending: false });
    const bindingData = {
      records: [
        {
          id: 1,
          username: 'alice',
          provider: 'wechat',
          subjectId: 'wx_001',
          bound: true,
          employeeId: 1001,
          employeeName: '张三',
        },
        {
          id: 2,
          username: 'bob',
          provider: 'wechat',
          subjectId: null,
          bound: false,
          employeeId: null,
          employeeName: null,
        },
      ],
      total: 2,
    };
    mockUseUserBindingsQuery.mockReturnValue({
      data: bindingData,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn().mockResolvedValue({ data: bindingData }),
    });

    render(
      <TestWrapper>
        <UserBindingPage />
      </TestWrapper>,
    );

    const bobCell = await screen.findByText('bob');
    const bobRow = bobCell.closest('tr') as HTMLElement;
    const bindButton = await within(bobRow).findByRole('button', { name: /绑定/ });
    fireEvent.click(bindButton);

    const bindDialog = await screen.findByRole('dialog');
    expect(within(bindDialog).getByText(/绑定平台/)).toBeInTheDocument();
    const accountInput = within(bindDialog).getByPlaceholderText('请输入平台用户标识');
    fireEvent.change(accountInput, { target: { value: 'wx_999' } });
    fireEvent.click(within(bindDialog).getByRole('button', { name: /确\s*定/ }));

    await waitFor(() => {
      expect(bindMutation).toHaveBeenCalledWith({
        id: 2,
        provider: 'wechat',
        subjectId: 'wx_999',
      });
    });
  });

  it('allows unbinding platform account', async () => {
    const unbindMutation = vi.fn().mockResolvedValue(undefined);
    mockUseUnbindUserMutation.mockReturnValue({ mutateAsync: unbindMutation, isPending: false });

    render(
      <TestWrapper>
        <UserBindingPage />
      </TestWrapper>,
    );

    const unbindButton = await screen.findByText('解绑');
    fireEvent.click(unbindButton);

    const confirmTitles = await screen.findAllByText('确认解绑');
    const confirmTitle =
      confirmTitles.find((element) => {
        const dialog = element.closest('[role="dialog"]');
        return dialog && window.getComputedStyle(dialog).display !== 'none';
      }) ?? confirmTitles[confirmTitles.length - 1];
    const confirmDialog = confirmTitle.closest('[role="dialog"]') as HTMLElement;
    fireEvent.click(within(confirmDialog).getByRole('button', { name: 'OK' }));

    await waitFor(() => {
      expect(unbindMutation).toHaveBeenCalledWith(1);
    });
  });

  it('allows binding employee manually', async () => {
    const bindEmployeeMutation = vi.fn().mockResolvedValue(undefined);
    mockUseBindEmployeeMutation.mockReturnValue({
      mutateAsync: bindEmployeeMutation,
      isPending: false,
    });

    render(
      <TestWrapper>
        <UserBindingPage />
      </TestWrapper>,
    );

    const linkButtons = await screen.findAllByRole('button', { name: '指定员工' });
    fireEvent.click(linkButtons[0]);

    const input = await screen.findByPlaceholderText('请输入员工ID');
    fireEvent.change(input, { target: { value: '2001' } });
    const employeeTitle = await screen.findByText('指定员工 - alice');
    const employeeDialog = employeeTitle.closest('[role="dialog"]') as HTMLElement;
    fireEvent.click(within(employeeDialog).getByRole('button', { name: /确\s*定/ }));

    await waitFor(() => {
      expect(bindEmployeeMutation).toHaveBeenCalledWith({ id: 1, employeeId: 2001 });
    });
  });
});
