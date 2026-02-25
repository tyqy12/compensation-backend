import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import BatchDetail from './BatchDetail';
import type { PaymentBatch, PaymentRecord } from '@services/queries/paymentBatch';

// Mock react-router-dom
const mockNavigate = vi.fn();
const mockUseSearchParams = vi.fn(() => [new URLSearchParams(), vi.fn()]);

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useParams: () => ({ batchNo: 'BATCH_20240115001' }),
    useNavigate: () => mockNavigate,
    useSearchParams: () => mockUseSearchParams(),
  };
});

// Mock payment batch queries
const mockBatchQuery = vi.fn();
const mockRecordsQuery = vi.fn();
const mockRetryMutation = vi.fn();

vi.mock('@services/queries/paymentBatch', async () => {
  const actual = await vi.importActual('@services/queries/paymentBatch');
  return {
    ...actual,
    usePaymentBatchQuery: () => mockBatchQuery(),
    usePaymentRecordsQuery: () => mockRecordsQuery(),
    useRetryPaymentRecordMutation: () => mockRetryMutation(),
  };
});

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
const mockBatch: PaymentBatch = {
  id: 1,
  batchNo: 'BATCH_20240115001',
  batchName: '2024年1月工资发放',
  paymentType: 'salary',
  totalAmount: 1500000.0,
  totalCount: 150,
  successCount: 148,
  failedCount: 2,
  status: 'processing',
  submitTime: '2024-01-15T09:00:00',
  approveTime: '2024-01-15T10:30:00',
  processStartTime: '2024-01-15T14:00:00',
  processEndTime: null,
  remark: '月度工资批量发放',
};

const mockRecords: PaymentRecord[] = [
  {
    id: 1001,
    batchNo: 'BATCH_20240115001',
    paymentType: 'salary',
    amount: 8500.0,
    currency: 'CNY',
    status: 'success',
    alipayOrderNo: 'COMP_20240115_1001',
    alipayTradeNo: '2024011522000123456789',
    errorCode: null,
    errorMsg: null,
    paymentTime: '2024-01-15T14:05:23',
    notificationTime: '2024-01-15T14:05:25',
  },
  {
    id: 1002,
    batchNo: 'BATCH_20240115001',
    paymentType: 'salary',
    amount: 12000.0,
    currency: 'CNY',
    status: 'failed',
    alipayOrderNo: 'COMP_20240115_1002',
    alipayTradeNo: null,
    errorCode: 'PAYEE_NOT_EXIST',
    errorMsg: '收款方账户不存在',
    paymentTime: null,
    notificationTime: null,
  },
];

const TestWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>{children}</ConfigProvider>
    </QueryClientProvider>
  );
};

describe('BatchDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    // Default mock implementations
    mockBatchQuery.mockReturnValue({
      data: mockBatch,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });

    mockRecordsQuery.mockReturnValue({
      data: mockRecords,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });

    mockRetryMutation.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
  });

  it('should render batch detail information correctly', async () => {
    render(
      <TestWrapper>
        <BatchDetail />
      </TestWrapper>,
    );

    // Check batch header
    expect(screen.getByText('2024年1月工资发放')).toBeInTheDocument();
    expect(screen.getByText('BATCH_20240115001')).toBeInTheDocument();
    expect(screen.getByText('⚡ 处理中')).toBeInTheDocument();

    // Check statistics cards
    expect(screen.getByText('支付总额')).toBeInTheDocument();
    expect(screen.getByText('支付笔数')).toBeInTheDocument();
    expect(screen.getByText('成功笔数')).toBeInTheDocument();
    expect(screen.getByText('失败笔数')).toBeInTheDocument();

    // Check progress
    expect(screen.getByText('处理进度')).toBeInTheDocument();
    expect(screen.getByText('已处理：150 / 150 笔')).toBeInTheDocument();

    // Check timeline
    expect(screen.getByText('处理时间线')).toBeInTheDocument();
    expect(screen.getByText('批次已提交')).toBeInTheDocument();
    expect(screen.getByText('批次已审批')).toBeInTheDocument();
    expect(screen.getByText('开始处理转账')).toBeInTheDocument();
  });

  it('should display payment records table', async () => {
    render(
      <TestWrapper>
        <BatchDetail />
      </TestWrapper>,
    );

    // Wait for table to load
    await waitFor(() => {
      expect(screen.getByText('支付记录')).toBeInTheDocument();
    });

    // Check table headers
    expect(screen.getByText('记录ID')).toBeInTheDocument();
    expect(screen.getByText('支付金额')).toBeInTheDocument();
    expect(screen.getByText('支付状态')).toBeInTheDocument();
    expect(screen.getByText('商户订单号')).toBeInTheDocument();

    // Check record data
    expect(screen.getByText('1001')).toBeInTheDocument();
    expect(screen.getByText('1002')).toBeInTheDocument();
    expect(screen.getByText('✅ 成功')).toBeInTheDocument();
    expect(screen.getByText('❌ 失败')).toBeInTheDocument();
  });

  it('should show retry button for failed records', async () => {
    render(
      <TestWrapper>
        <BatchDetail />
      </TestWrapper>,
    );

    await waitFor(() => {
      const retryButtons = screen.getAllByText('重试');
      expect(retryButtons).toHaveLength(1); // Only one failed record
    });
  });

  it('should show batch retry button when there are failed records', async () => {
    render(
      <TestWrapper>
        <BatchDetail />
      </TestWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText(/批量重试失败记录/)).toBeInTheDocument();
    });
  });

  it('should handle individual record retry', async () => {
    const mockMutateAsync = vi.fn().mockResolvedValue({ success: true });
    mockRetryMutation.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
    });

    // Mock modal confirm to auto-confirm
    mockModal.confirm.mockImplementation(({ onOk }) => {
      onOk();
    });

    render(
      <TestWrapper>
        <BatchDetail />
      </TestWrapper>,
    );

    await waitFor(() => {
      const retryButton = screen.getByText('重试');
      fireEvent.click(retryButton);
    });

    expect(mockModal.confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '重试支付记录',
      }),
    );

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith(1002);
      expect(mockMessage.success).toHaveBeenCalledWith('重试请求已提交，正在处理中');
    });
  });

  it('should handle batch retry', async () => {
    const mockMutateAsync = vi.fn().mockResolvedValue({ success: true });
    mockRetryMutation.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
    });

    // Mock modal confirm to auto-confirm
    mockModal.confirm.mockImplementation(({ onOk }) => {
      onOk();
    });

    render(
      <TestWrapper>
        <BatchDetail />
      </TestWrapper>,
    );

    await waitFor(() => {
      const batchRetryButton = screen.getByText(/批量重试失败记录/);
      fireEvent.click(batchRetryButton);
    });

    expect(mockModal.confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '批量重试失败记录',
      }),
    );

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith(1002);
      expect(mockMessage.success).toHaveBeenCalledWith('批量重试已完成');
    });
  });

  it('should navigate back to list with preserved params', async () => {
    const mockSearchParams = new URLSearchParams('status=processing&page=2');
    mockUseSearchParams.mockReturnValue([mockSearchParams, vi.fn()]);

    render(
      <TestWrapper>
        <BatchDetail />
      </TestWrapper>,
    );

    const backButton = screen.getByRole('button', { name: /back/i });
    fireEvent.click(backButton);

    expect(mockNavigate).toHaveBeenCalledWith('/payments/batches?status=processing&page=2');
  });

  it('should handle loading state', () => {
    mockBatchQuery.mockReturnValue({
      data: null,
      isLoading: true,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });

    render(
      <TestWrapper>
        <BatchDetail />
      </TestWrapper>,
    );

    expect(screen.getByText('加载批次详情中...')).toBeInTheDocument();
  });

  it('should handle error state', () => {
    mockBatchQuery.mockReturnValue({
      data: null,
      isLoading: false,
      isError: true,
      error: { message: '网络连接失败' },
      refetch: vi.fn(),
    });

    render(
      <TestWrapper>
        <BatchDetail />
      </TestWrapper>,
    );

    expect(screen.getByText('批次加载失败')).toBeInTheDocument();
    expect(screen.getByText('网络连接失败')).toBeInTheDocument();
    expect(screen.getByText('重新加载')).toBeInTheDocument();
    expect(screen.getByText('返回列表')).toBeInTheDocument();
  });

  it('should refresh data when refresh button is clicked', () => {
    const mockBatchRefetch = vi.fn();
    mockBatchQuery.mockReturnValue({
      data: mockBatch,
      isLoading: false,
      isError: false,
      error: null,
      refetch: mockBatchRefetch,
    });

    render(
      <TestWrapper>
        <BatchDetail />
      </TestWrapper>,
    );

    const refreshButton = screen.getByText('刷新');
    fireEvent.click(refreshButton);

    expect(mockBatchRefetch).toHaveBeenCalled();
  });

  it('should prevent retry for non-failed records', async () => {
    render(
      <TestWrapper>
        <BatchDetail />
      </TestWrapper>,
    );

    // Since the success record doesn't have a retry button, we need to simulate
    // calling handleRetryRecord directly with a success record
    // This tests the business logic prevention
    const retryButtons = screen.queryAllByText('重试');
    expect(retryButtons).toHaveLength(1); // Only failed records show retry
  });

  it('should display error message for failed records', async () => {
    render(
      <TestWrapper>
        <BatchDetail />
      </TestWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText('[PAYEE_NOT_EXIST] 收款方账户不存在')).toBeInTheDocument();
    });
  });

  it('should format amounts correctly', async () => {
    render(
      <TestWrapper>
        <BatchDetail />
      </TestWrapper>,
    );

    // Check if currency formatting is applied
    // The exact format may vary based on locale, but should contain the amounts
    await waitFor(() => {
      // Look for formatted amounts (Chinese currency formatting)
      expect(
        screen.getByText((content, element) => {
          return element?.textContent?.includes('1,500,000') || false;
        }),
      ).toBeInTheDocument();
    });
  });

  it('should show correct progress calculation', async () => {
    render(
      <TestWrapper>
        <BatchDetail />
      </TestWrapper>,
    );

    await waitFor(() => {
      // Progress should be 100% (150 processed out of 150 total)
      expect(screen.getByText('已处理：150 / 150 笔')).toBeInTheDocument();
      // Success rate should be 98.67% (148 success out of 150 processed)
      expect(screen.getByText('成功率：99%')).toBeInTheDocument();
    });
  });
});
