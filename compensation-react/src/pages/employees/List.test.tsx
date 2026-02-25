import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
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

vi.mock('@services/queries/employee', () => ({
  useEmployeesQuery: () => mockUseEmployeesQuery(),
  useCreateEmployeeMutation: () => mockUseCreateEmployeeMutation(),
  useUpdateEmployeeStatusMutation: () => mockUseUpdateEmployeeStatusMutation(),
  useBindPlatformMutation: () => mockUseBindPlatformMutation(),
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

    // employmentType select -> choose 兼职 (part_time)
    const typeSelect = (await screen.findAllByRole('combobox'))[0];
    fireEvent.mouseDown(typeSelect);
    fireEvent.click(await screen.findByTitle('兼职'));

    // username optional
    fireEvent.change(await screen.findByPlaceholderText('如 zhangfei 或 wbzhangfei'), {
      target: { value: 'wbzhangfei' },
    });

    // submit
    fireEvent.click(screen.getByText('确定'));

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

