import React from 'react';
import { render, screen } from '@testing-library/react';
import { App as AntdApp, ConfigProvider } from 'antd';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PayrollBatchEntry from './Entry';
import { store } from '@services/stores/authSlice';

const mockQueries = vi.hoisted(() => ({
  detail: {
    data: {
      batchId: 1001,
      periodLabel: '2026-06',
      payrollType: 'full_time',
      status: 'draft',
      calculationStatus: 'draft',
      batchRevision: 1,
      currency: 'CNY',
    },
  },
  items: {
    data: [
      {
        id: 1,
        rowNo: 2,
        employeeNo: 'emp-001',
        employeeName: '张三',
        itemCode: 'BASE',
        itemName: '基本工资',
        amount: 10000,
        sourceName: 'salary.csv',
        status: 'valid',
      },
      {
        id: 2,
        rowNo: 3,
        employeeNo: 'emp-002',
        itemCode: 'BONUS',
        itemName: '奖金',
        amount: 500,
        sourceName: 'salary.csv',
        status: 'invalid',
        errorMsg: '员工工号不存在',
      },
    ],
  },
}));

vi.mock('@services/queries/payroll', () => ({
  usePayrollBatchDetailQuery: () => ({
    ...mockQueries.detail,
    refetch: vi.fn(),
    isLoading: false,
  }),
  usePayrollImportItemsQuery: () => ({
    ...mockQueries.items,
    refetch: vi.fn(),
    isLoading: false,
    isFetching: false,
  }),
  usePayrollImportSalaryItemsQuery: () => ({
    data: [{ code: 'BASE', name: '基本工资' }],
    isLoading: false,
  }),
  useImportPayrollBatchCsvMutation: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useAddPayrollManualImportItemMutation: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdatePayrollImportItemMutation: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeletePayrollImportItemMutation: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useLockPayrollBatchMutation: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useComputePayrollBatchMutation: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

vi.mock('@services/queries/employee', () => ({
  useEmployeesQuery: () => ({ data: { records: [] }, isFetching: false }),
}));

const renderPage = () =>
  render(
    <Provider store={store}>
      <ConfigProvider>
        <AntdApp>
          <MemoryRouter initialEntries={['/payroll/batches/1001/entry']}>
            <Routes>
              <Route path="/payroll/batches/:batchId/entry" element={<PayrollBatchEntry />} />
            </Routes>
          </MemoryRouter>
        </AntdApp>
      </ConfigProvider>
    </Provider>,
  );

describe('PayrollBatchEntry', () => {
  beforeEach(() => {
    store.dispatch({
      type: 'auth/setSession',
      payload: { user: { id: 'finance', username: 'finance', roles: ['ROLE_FINANCE'] } },
    });
  });

  it('renders the full-page input workbench and exposes invalid input count', async () => {
    renderPage();

    expect(await screen.findByText('数据录入工作台')).toBeInTheDocument();
    expect(screen.getByText('输入明细')).toBeInTheDocument();
    expect(screen.getByText('输入问题')).toBeInTheDocument();
    expect(screen.getAllByText('员工工号不存在').length).toBeGreaterThan(0);
    expect(screen.getAllByText('1').length).toBeGreaterThan(0);
  });
});
