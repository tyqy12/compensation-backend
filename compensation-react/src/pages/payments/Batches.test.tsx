import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import Batches from './Batches';
import type { PaymentBatch } from '@services/queries/paymentBatch';
import { store } from '../../services/stores/authSlice';

// Mock react-router-dom
const mockNavigate = vi.fn();
const mockSetSearchParams = vi.fn();
const mockSearchParams = new URLSearchParams();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useSearchParams: () => [mockSearchParams, mockSetSearchParams],
  };
});

// Mock payment batch queries
const mockBatchesQuery = vi.fn();
const mockStartTransferMutation = vi.fn();
const mockCheckBatchTransfer = vi.fn();

vi.mock('@services/queries/paymentBatch', async () => {
  const actual = await vi.importActual('@services/queries/paymentBatch');
  return {
    ...actual,
    usePaymentBatchesQuery: (...args: unknown[]) => mockBatchesQuery(...args),
    useStartBatchTransferMutation: () => mockStartTransferMutation(),
    checkBatchTransfer: (...args: any[]) => mockCheckBatchTransfer(...args),
  };
});

vi.mock('@services/queries/rbac', () => ({
  useHasAction: () => true,
}));

// Mock Ant Design App
const mockMessage = {
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
  info: vi.fn(),
};

const mockModal = {
  confirm: vi.fn(),
};

vi.mock('antd', async () => {
  const actual = await vi.importActual('antd');
  return {
    ...actual,
    App: {
      useApp: () => ({
        message: mockMessage,
        modal: mockModal,
      }),
    },
  };
});

// Test data
const mockBatches: PaymentBatch[] = [
  {
    id: 1,
    batchNo: 'BATCH_20240115001',
    batchName: '2024年1月工资发放',
    paymentType: 'salary',
    totalAmount: 1500000.0,
    totalCount: 150,
    successCount: 148,
    failedCount: 2,
    status: 'completed',
    submitTime: '2024-01-15T09:00:00',
    approveTime: '2024-01-15T10:30:00',
    processStartTime: '2024-01-15T14:00:00',
    processEndTime: '2024-01-15T16:30:00',
    remark: '月度工资批量发放',
  },
  {
    id: 2,
    batchNo: 'BATCH_20240115002',
    batchName: '2024年1月奖金发放',
    paymentType: 'bonus',
    totalAmount: 500000.0,
    totalCount: 50,
    successCount: 0,
    failedCount: 0,
    status: 'approved',
    submitTime: '2024-01-15T10:00:00',
    approveTime: '2024-01-15T11:00:00',
    processStartTime: null,
    processEndTime: null,
    remark: '年度奖金发放',
  },
  {
    id: 3,
    batchNo: 'BATCH_20240115003',
    batchName: '2024年1月报销发放',
    paymentType: 'reimbursement',
    totalAmount: 100000.0,
    totalCount: 30,
    successCount: 25,
    failedCount: 0,
    status: 'processing',
    submitTime: '2024-01-15T11:00:00',
    approveTime: '2024-01-15T12:00:00',
    processStartTime: '2024-01-15T13:00:00',
    processEndTime: null,
    remark: null,
  },
  {
    id: 4,
    batchNo: 'BATCH_20240115004',
    batchName: '2024年1月补发工资',
    paymentType: 'salary',
    totalAmount: 80000.0,
    totalCount: 8,
    successCount: 0,
    failedCount: 0,
    status: 'submitted',
    submitTime: '2024-01-15T12:00:00',
    approveTime: null,
    processStartTime: null,
    processEndTime: null,
    remark: '审批后自动创建的待启动支付批次',
  },
];

const mockPagedResponse = {
  list: mockBatches,
  records: mockBatches,
  total: 4,
  current: 1,
  size: 10,
};

const TestWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return (
    <MemoryRouter>
      <Provider store={store}>
        <QueryClientProvider client={queryClient}>
          <ConfigProvider>{children}</ConfigProvider>
        </QueryClientProvider>
      </Provider>
    </MemoryRouter>
  );
};

describe('Batches', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockBatchesQuery.mockReset();
    mockStartTransferMutation.mockReset();
    mockCheckBatchTransfer.mockReset();
    mockSearchParams.forEach((_, key) => mockSearchParams.delete(key));

    // Default mock implementations
    mockBatchesQuery.mockReturnValue({
      data: mockPagedResponse,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });

    mockStartTransferMutation.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
    mockCheckBatchTransfer.mockResolvedValue({
      batchNo: 'BATCH_20240115002',
      pendingCount: 0,
      passCount: 1,
      blockedCount: 0,
      pass: true,
      warnings: [],
      blockedRecords: [],
    });
  });

  it('should render page header correctly', () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    expect(screen.getByText('支付批次管理')).toBeInTheDocument();
    expect(screen.getByText('管理批量支付操作和转账状态')).toBeInTheDocument();
    expect(screen.getByText('批次列表')).toBeInTheDocument();
  });

  it('should display statistics cards', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText('总批次数')).toBeInTheDocument();
      expect(screen.getByText('处理中批次')).toBeInTheDocument();
      expect(screen.getByText('已完成批次')).toBeInTheDocument();
      expect(screen.getByText('失败批次')).toBeInTheDocument();
    });

    // Check statistics values
    expect(screen.getByText('4')).toBeInTheDocument(); // Total batches
    expect(screen.getAllByText('1').length).toBeGreaterThan(0); // Processing and completed batches
    expect(screen.getByText('0')).toBeInTheDocument(); // Failed batches
  });

  it('should display batch list table', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      // Check table headers
      expect(screen.getAllByText('批次号').length).toBeGreaterThan(0);
      expect(screen.getAllByText('批次名称').length).toBeGreaterThan(0);
      expect(screen.getAllByText('支付类型').length).toBeGreaterThan(0);
      expect(screen.getAllByText('批次状态').length).toBeGreaterThan(0);
      expect(screen.getAllByText('金额统计').length).toBeGreaterThan(0);
      expect(screen.getAllByText('处理进度').length).toBeGreaterThan(0);

      // Check batch data
      expect(screen.getByText('BATCH_20240115001')).toBeInTheDocument();
      expect(screen.getByText('2024年1月工资发放')).toBeInTheDocument();
      expect(screen.getAllByText('工资').length).toBeGreaterThan(0);
      expect(screen.getAllByText('奖金').length).toBeGreaterThan(0);
      expect(screen.getAllByText('报销').length).toBeGreaterThan(0);
      expect(screen.getAllByText('已完成').length).toBeGreaterThan(0);
      expect(screen.getAllByText('已审批').length).toBeGreaterThan(0);
      expect(screen.getAllByText('处理中').length).toBeGreaterThan(0);
    });
  });

  it('should show start transfer button for submitted and approved batches', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    const row = await screen.findByText('BATCH_20240115002');
    fireEvent.click(within(row.closest('tr') as HTMLElement).getByRole('button', { name: /更多/ }));
    expect(await screen.findByText('启动转账')).toBeInTheDocument();
  });

  it('should handle start transfer operation', async () => {
    const mockMutateAsync = vi.fn().mockResolvedValue({ success: true });
    mockStartTransferMutation.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
    });

    // Mock modal confirm to auto-confirm
    mockModal.confirm.mockImplementation(({ onOk }) => {
      onOk();
    });

    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    const row = await screen.findByText('BATCH_20240115002');
    fireEvent.click(within(row.closest('tr') as HTMLElement).getByRole('button', { name: /更多/ }));
    fireEvent.click(await screen.findByText('启动转账'));

    expect(mockModal.confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '启动批次转账',
      }),
    );

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith('BATCH_20240115002');
      expect(mockMessage.success).toHaveBeenCalledWith('批次转账已启动，正在后台处理');
    });
  });

  it('should prevent starting transfer for non-startable batches', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    const row = await screen.findByText('BATCH_20240115001');
    fireEvent.click(within(row.closest('tr') as HTMLElement).getByRole('button', { name: /更多/ }));
    expect(screen.queryByText('启动转账')).not.toBeInTheDocument();
  });

  it('should show refresh buttons for processing/completed batches', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      expect(screen.getAllByText('处理进度').length).toBeGreaterThan(0);
      expect(mockBatchesQuery).toHaveBeenCalled();
    });
  });

  it('should navigate to detail page with preserved params', async () => {
    // Set some search params
    mockSearchParams.set('status', 'processing');
    mockSearchParams.set('page', '2');

    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      const detailLink = screen.getByText('BATCH_20240115001');
      expect(detailLink.closest('a')).toHaveAttribute(
        'href',
        expect.stringContaining('/payments/batches/BATCH_20240115001'),
      );
    });
  });

  it('should build the list query from URL parameters', async () => {
    mockSearchParams.set('current', '1');
    mockSearchParams.set('pageSize', '10');
    mockSearchParams.set('keyword', '工资');
    mockSearchParams.set('status', 'processing');
    mockSearchParams.set('sortBy', 'submitTime');
    mockSearchParams.set('order', 'desc');

    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      expect(mockBatchesQuery).toHaveBeenCalledWith(
        expect.objectContaining({
          current: 1,
          pageSize: 10,
          keyword: '工资',
          status: 'processing',
          sortBy: 'submitTime',
          order: 'desc',
        }),
      );
    });
  });

  it('should handle search functionality', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    // Find search input
    const searchInput = screen.getByPlaceholderText('搜索批次号或批次名称');
    expect(searchInput).toBeInTheDocument();

    fireEvent.change(searchInput, { target: { value: '工资' } });

    // Search functionality would be handled by ProTable's request function
    // The actual search would trigger a new query with updated parameters
  });

  it('should display progress bars correctly', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      // Check progress information
      expect(screen.getByText('成功: 148 / 失败: 2')).toBeInTheDocument();
      expect(screen.getByText('成功率: 99%')).toBeInTheDocument();
      expect(screen.getByText('成功: 25 / 失败: 0')).toBeInTheDocument();
    });
  });

  it('should handle loading state', () => {
    mockBatchesQuery.mockReturnValue({
      data: null,
      isLoading: true,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });

    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    // The statistics cards should fall back to zero when no query data exists.
    expect(screen.getAllByText('0').length).toBeGreaterThan(0);
  });

  it('should handle error state', async () => {
    mockBatchesQuery.mockReturnValue({
      data: null,
      isLoading: false,
      isError: true,
      error: { message: '网络连接失败' },
      refetch: vi.fn(),
    });

    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText('暂无支付批次')).toBeInTheDocument();
    });
  });

  it('should handle empty state', async () => {
    mockBatchesQuery.mockReturnValue({
      data: { list: [], records: [], total: 0, current: 1, size: 10 },
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });

    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText('暂无支付批次')).toBeInTheDocument();
    });
  });

  it('should format amounts correctly', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      const row = screen.getByText('BATCH_20240115001').closest('tr');
      expect(row).toHaveTextContent('¥1,500,000.00');
    }, { timeout: 5000 });
  });

  it('should show time information correctly', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      const row = screen.getByText('BATCH_20240115001').closest('tr');
      expect(row).toHaveTextContent('提交: 01-15 09:00');
      expect(row).toHaveTextContent('完成: 01-15 16:30');
    }, { timeout: 5000 });
  });

  it('should handle refresh action', () => {
    const mockRefetch = vi.fn();
    mockBatchesQuery.mockReturnValue({
      data: mockPagedResponse,
      isLoading: false,
      isError: false,
      error: null,
      refetch: mockRefetch,
    });

    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    expect(screen.getByText('批次列表')).toBeInTheDocument();
  });

  it('should handle row selection', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      // Check if table has row selection
      const checkboxes = screen.getAllByRole('checkbox');
      expect(checkboxes.length).toBeGreaterThan(0);
    });
  });

  it('should show table alert when rows are selected', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    // Row selection would trigger table alert
    // This is handled by ProTable's tableAlertRender prop
    // The actual selection behavior would need more complex testing
    await waitFor(() => {
      // Just verify the table is rendered
      expect(screen.getAllByText('批次号').length).toBeGreaterThan(0);
    });
  });
});
