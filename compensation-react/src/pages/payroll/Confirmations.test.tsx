import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider, App as AntdApp } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import Confirmations from './Confirmations';

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ header, children }: { header?: any; children?: React.ReactNode }) => (
    <div>
      <div>{header?.title}</div>
      <div>{header?.subTitle}</div>
      <div>{header?.extra}</div>
      {children}
    </div>
  ),
}));

const mockUsePendingQuery = vi.fn();
const mockUseBatchDetailQuery = vi.fn();
const mockUseSummaryQuery = vi.fn();
const mockUseConfirmMutation = vi.fn();
const mockUseObjectMutation = vi.fn();
const mockUseBatchConfirmMutation = vi.fn();
const mockUseAssignMutation = vi.fn();

vi.mock('@services/queries/payroll', () => ({
  usePayrollPendingConfirmationsQuery: (params: any) => mockUsePendingQuery(params),
  usePayrollBatchDetailQuery: (batchId: number, options?: any) =>
    mockUseBatchDetailQuery(batchId, options),
  usePayrollConfirmationSummaryQuery: (batchId: number, options?: any) =>
    mockUseSummaryQuery(batchId, options),
  useConfirmPayrollPayslipMutation: () => mockUseConfirmMutation(),
  useObjectPayrollPayslipMutation: () => mockUseObjectMutation(),
  useBatchConfirmPayrollMutation: () => mockUseBatchConfirmMutation(),
  useAssignPayrollConfirmationMutation: () => mockUseAssignMutation(),
}));

const pendingRefetch = vi.fn().mockResolvedValue(undefined);
const summaryRefetch = vi.fn().mockResolvedValue(undefined);
const confirmMutateAsync = vi.fn().mockResolvedValue(true);
const objectMutateAsync = vi.fn().mockResolvedValue({ workflowId: 91001 });
const batchConfirmMutateAsync = vi.fn().mockResolvedValue({ affected: 1 });
const assignMutateAsync = vi.fn().mockResolvedValue({ affected: 1 });

const pendingRows = [
  {
    lineId: 6001,
    batchId: 5001,
    periodLabel: '2026-02',
    employeeId: 2001,
    employeeNo: 'EMP-2001',
    employeeName: '张三',
    department: '财务部',
    netAmount: 12500,
    currency: 'CNY',
    confirmationStatus: 'pending',
  },
];

const TestWrapper = ({
  children,
  initialEntries = ['/payroll/confirmations?batchId=5001'],
}: {
  children: React.ReactNode;
  initialEntries?: string[];
}) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return (
    <MemoryRouter initialEntries={initialEntries}>
      <QueryClientProvider client={queryClient}>
        <ConfigProvider>
          <AntdApp>{children}</AntdApp>
        </ConfigProvider>
      </QueryClientProvider>
    </MemoryRouter>
  );
};

describe('PayrollConfirmations 确认工作台', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mockUsePendingQuery.mockReturnValue({
      data: {
        records: pendingRows,
        total: 1,
        current: 1,
        size: 10,
      },
      isLoading: false,
      isFetching: false,
      refetch: pendingRefetch,
    });

    mockUseBatchDetailQuery.mockReturnValue({
      data: {
        batchId: 5001,
        status: 'confirming',
        calculationStatus: 'calculated',
        batchRevision: 2,
        confirmationRequired: true,
        confirmationMode: 'individual',
        approvalWorkflowId: 7001,
      },
      isLoading: false,
      isFetching: false,
      refetch: vi.fn().mockResolvedValue(undefined),
    });

    mockUseSummaryQuery.mockReturnValue({
      data: {
        batchId: 5001,
        batchStatus: 'confirming',
        confirmationMode: 'individual',
        totalLines: 1,
        pendingCount: 1,
        confirmedCount: 0,
        objectedCount: 0,
        objectedApprovedCount: 0,
        objectedRejectedCount: 0,
      },
      isLoading: false,
      isFetching: false,
      refetch: summaryRefetch,
    });

    mockUseConfirmMutation.mockReturnValue({
      mutateAsync: confirmMutateAsync,
      isPending: false,
    });

    mockUseObjectMutation.mockReturnValue({
      mutateAsync: objectMutateAsync,
      isPending: false,
    });

    mockUseBatchConfirmMutation.mockReturnValue({
      mutateAsync: batchConfirmMutateAsync,
      isPending: false,
    });

    mockUseAssignMutation.mockReturnValue({
      mutateAsync: assignMutateAsync,
      isPending: false,
    });
  });

  it('支持单条签字确认', async () => {
    render(
      <TestWrapper>
        <Confirmations />
      </TestWrapper>,
    );

    expect(await screen.findByText('R2')).toBeTruthy();

    fireEvent.click(await screen.findByRole('button', { name: /签字确认/ }));

    fireEvent.change(screen.getByPlaceholderText('请输入签字人（姓名/工号）'), {
      target: { value: '张三' },
    });
    fireEvent.change(screen.getByPlaceholderText('可选，补充说明'), {
      target: { value: '确认无误' },
    });
    const confirmDialog = await screen.findByRole('dialog');
    expect(within(confirmDialog).getByText(/签字确认/)).toBeInTheDocument();
    fireEvent.click(within(confirmDialog).getByRole('button', { name: /OK/i }));

    await waitFor(() => {
      expect(confirmMutateAsync).toHaveBeenCalledWith({
        lineId: 6001,
        payload: {
          signature: '张三',
          comment: '确认无误',
        },
      });
    });

    await waitFor(() => {
      expect(pendingRefetch).toHaveBeenCalled();
      expect(summaryRefetch).toHaveBeenCalled();
    });
  });

  it('支持单条发起异议', async () => {
    render(
      <TestWrapper>
        <Confirmations />
      </TestWrapper>,
    );

    fireEvent.click(await screen.findByRole('button', { name: /发起异议/ }));

    fireEvent.change(screen.getByPlaceholderText('例如：绩效系数有误、个税口径不一致等'), {
      target: { value: '个税口径不一致' },
    });
    fireEvent.change(screen.getByPlaceholderText('可选，补充材料或说明'), {
      target: { value: '请按新政策复核' },
    });
    const objectDialog = await screen.findByRole('dialog');
    expect(within(objectDialog).getByText(/发起异议/)).toBeInTheDocument();
    fireEvent.click(within(objectDialog).getByRole('button', { name: /OK/i }));

    await waitFor(() => {
      expect(objectMutateAsync).toHaveBeenCalledWith({
        lineId: 6001,
        payload: {
          reason: '个税口径不一致',
          comment: '请按新政策复核',
        },
      });
    });
  });

  it('支持批量签字确认选中工资行', async () => {
    render(
      <TestWrapper>
        <Confirmations />
      </TestWrapper>,
    );

    await screen.findByText('待确认工资行');
    const checkboxes = screen.getAllByRole('checkbox');
    fireEvent.click(checkboxes[1]);

    fireEvent.click(screen.getByRole('button', { name: '批量确认选中' }));
    fireEvent.change(screen.getByPlaceholderText('请输入签字人（姓名/工号）'), {
      target: { value: '负责人A' },
    });
    fireEvent.change(screen.getByPlaceholderText('可选，批量确认备注'), {
      target: { value: '按制度批量确认' },
    });
    const batchDialog = await screen.findByRole('dialog');
    expect(within(batchDialog).getByText('批量签字确认', { exact: true })).toBeInTheDocument();
    fireEvent.click(within(batchDialog).getByRole('button', { name: /OK/i }));

    await waitFor(() => {
      expect(batchConfirmMutateAsync).toHaveBeenCalledWith({
        batchId: 5001,
        payload: {
          lineIds: [6001],
          signature: '负责人A',
          comment: '按制度批量确认',
        },
      });
    });
  });

  it('支持分配确认负责人（选中工资行）', async () => {
    render(
      <TestWrapper>
        <Confirmations />
      </TestWrapper>,
    );

    await screen.findByText('负责人分配');
    const checkboxes = screen.getAllByRole('checkbox');
    fireEvent.click(checkboxes[1]);

    const spinbuttons = screen.getAllByRole('spinbutton');
    fireEvent.change(spinbuttons[1], { target: { value: '3001' } });
    fireEvent.click(screen.getByRole('button', { name: /分配负责人/ }));

    await waitFor(() => {
      expect(assignMutateAsync).toHaveBeenCalledWith({
        batchId: 5001,
        payload: {
          assigneeEmployeeId: 3001,
          lineIds: [6001],
        },
      });
    });
  });
});
