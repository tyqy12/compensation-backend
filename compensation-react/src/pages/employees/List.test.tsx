import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider } from 'react-redux';
import { App as AntdApp } from 'antd';
import { vi, beforeEach, describe, it, expect } from 'vitest';
import { store } from '../../services/stores/authSlice';
import EmployeesList from './List';

const mockUseEmployeesQuery = vi.fn();
const mockUseCreateEmployeeMutation = vi.fn();
const mockUseUpdateEmployeeStatusMutation = vi.fn();
const mockUseBindPlatformMutation = vi.fn();
const mockUseBatchImportEmployeesMutation = vi.fn();
const mockFetchEmployees = vi.fn();

vi.mock('@services/queries/employee', () => ({
  useEmployeesQuery: () => mockUseEmployeesQuery(),
  useCreateEmployeeMutation: () => mockUseCreateEmployeeMutation(),
  useUpdateEmployeeStatusMutation: () => mockUseUpdateEmployeeStatusMutation(),
  useBindPlatformMutation: () => mockUseBindPlatformMutation(),
  useBatchImportEmployeesMutation: () => mockUseBatchImportEmployeesMutation(),
  fetchEmployees: (...args: any[]) => mockFetchEmployees(...args),
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

  mockUseEmployeesQuery.mockReturnValue({
    data: { records: [], total: 0 },
    isLoading: false,
    isError: false,
    error: null,
    refetch: vi.fn().mockResolvedValue({ data: { records: [], total: 0 } }),
  });

  mockUseCreateEmployeeMutation.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
  mockUseUpdateEmployeeStatusMutation.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
  mockUseBindPlatformMutation.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
  mockUseBatchImportEmployeesMutation.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
  mockFetchEmployees.mockResolvedValue({ records: [], total: 0 });
});

describe('EmployeesList - Create Employee', () => {
  it('submits employmentType and username when creating', async () => {
    const createMutation = vi.fn().mockResolvedValue(undefined);
    mockUseCreateEmployeeMutation.mockReturnValue({ mutateAsync: createMutation, isPending: false });

    render(
      <TestWrapper>
        <EmployeesList />
      </TestWrapper>,
    );

    // Open create modal
    fireEvent.click(await screen.findByText('新增员工'));

    fireEvent.change(await screen.findByPlaceholderText('请输入唯一的员工ID'), {
      target: { value: 'E2001' },
    });
    fireEvent.change(await screen.findByPlaceholderText('请输入员工姓名'), {
      target: { value: '张飞' },
    });
    fireEvent.change(await screen.findByPlaceholderText('请输入银行卡号'), {
      target: { value: '6222020202020202020' },
    });
    fireEvent.change(await screen.findByPlaceholderText('请输入开户银行'), {
      target: { value: '中国银行' },
    });
    fireEvent.change(await screen.findByPlaceholderText('请输入开户支行'), {
      target: { value: '总行' },
    });

    // employmentType select -> choose 兼职 (part_time)
    const typeSelect = document.getElementById('employmentType') as HTMLInputElement;
    fireEvent.mouseDown(typeSelect);
    fireEvent.click(await screen.findByTitle('兼职'));

    // username optional
    fireEvent.change(await screen.findByPlaceholderText('如 zhangfei 或 wbzhangfei'), {
      target: { value: 'wbzhangfei' },
    });

    // submit
    const dialogs = await screen.findAllByRole('dialog', { name: /新增员工/ });
    const dialog = dialogs.find((item) => window.getComputedStyle(item).display !== 'none') ?? dialogs[0];
    fireEvent.click(within(dialog).getByRole('button', { name: /确\s*定/ }));

    await waitFor(() => {
      expect(createMutation).toHaveBeenCalled();
    });

    const payload = createMutation.mock.calls[0][0];
    expect(payload).toMatchObject({
      employeeId: 'E2001',
      name: '张飞',
      employmentType: 'part_time',
      username: 'wbzhangfei',
    });
  });
});
