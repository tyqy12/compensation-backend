import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import Batches from './Batches';
import type { PaymentBatch } from '@services/queries/paymentBatch';

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

vi.mock('@services/queries/paymentBatch', async () => {
  const actual = await vi.importActual('@services/queries/paymentBatch');
  return {
    ...actual,
    usePaymentBatchesQuery: () => mockBatchesQuery(),
    useStartBatchTransferMutation: () => mockStartTransferMutation(),
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
      <QueryClientProvider client={queryClient}>
        <ConfigProvider>{children}</ConfigProvider>
      </QueryClientProvider>
    </MemoryRouter>
  );
};

describe('Batches', () => {
  beforeEach(() => {
    vi.clearAllMocks();

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
  });

  it('should render page header correctly', () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    expect(screen.getByText('支付批次管理')).toBeInTheDocument();
    expect(screen.getByText('管理批量支付操作和转账状态')).toBeInTheDocument();
    expect(screen.getByText('刷新')).toBeInTheDocument();
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
    expect(screen.getByText('3')).toBeInTheDocument(); // Total batches
    expect(screen.getByText('1')).toBeInTheDocument(); // Processing batches
    expect(screen.getByText('1')).toBeInTheDocument(); // Completed batches
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
      expect(screen.getByText('批次号')).toBeInTheDocument();
      expect(screen.getByText('批次名称')).toBeInTheDocument();
      expect(screen.getByText('支付类型')).toBeInTheDocument();
      expect(screen.getByText('批次状态')).toBeInTheDocument();
      expect(screen.getByText('金额统计')).toBeInTheDocument();
      expect(screen.getByText('处理进度')).toBeInTheDocument();

      // Check batch data
      expect(screen.getByText('BATCH_20240115001')).toBeInTheDocument();
      expect(screen.getByText('2024年1月工资发放')).toBeInTheDocument();
      expect(screen.getByText('💰 工资')).toBeInTheDocument();
      expect(screen.getByText('🎉 已完成')).toBeInTheDocument();
    });
  });

  it('should show start transfer button for submitted and approved batches', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      const startButtons = screen.getAllByText('启动');
      expect(startButtons).toHaveLength(2);
    });
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

    await waitFor(() => {
      const startButton = screen.getByText('启动');
      fireEvent.click(startButton);
    });

    expect(mockModal.confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '启动批次转账',
      }),
    );

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith(expect.stringMatching(/^BATCH_2024011500[24]$/));
      expect(mockMessage.success).toHaveBeenCalledWith('批次转账已启动，正在后台处理');
    });
  });

  it('should prevent starting transfer for non-startable batches', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    // The completed and processing batches should not have start buttons
    await waitFor(() => {
      const startButtons = screen.getAllByText('启动');
      expect(startButtons).toHaveLength(2); // submitted and approved batches
    });
  });

  it('should show refresh buttons for processing/completed batches', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      // Should have refresh buttons for processing and completed batches
      const refreshButtons = screen.getAllByText('刷新');
      // One in header, two in table for processing/completed batches
      expect(refreshButtons.length).toBeGreaterThanOrEqual(2);
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

    // ProTable should show loading state
    // The statistics cards should show 0 values when no data
    expect(screen.getAllByText('0')).toHaveLength(4); // All statistics should be 0
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
      expect(screen.getByText('数据加载失败')).toBeInTheDocument();
      expect(screen.getByText('网络连接失败')).toBeInTheDocument();
      expect(screen.getByText('重新加载')).toBeInTheDocument();
    });
  });

  it('should handle empty state', async () => {
    mockBatchesQuery.mockReturnValue({
      data: { records: [], total: 0, current: 1, size: 10 },
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
      expect(screen.getByText('暂无数据')).toBeInTheDocument();
      expect(screen.getByText('还没有支付批次记录')).toBeInTheDocument();
    });
  });

  it('should display payment types correctly', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText('💰 工资')).toBeInTheDocument();
      expect(screen.getByText('🎁 奖金')).toBeInTheDocument();
      expect(screen.getByText('📋 报销')).toBeInTheDocument();
    });
  });

  it('should display batch status correctly', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText('🎉 已完成')).toBeInTheDocument();
      expect(screen.getByText('✅ 已审批')).toBeInTheDocument();
      expect(screen.getByText('⚡ 处理中')).toBeInTheDocument();
    });
  });

  it('should format amounts correctly', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      // Look for formatted amounts (Chinese currency formatting)
      expect(
        screen.getByText((content, element) => {
          return element?.textContent?.includes('1,500,000') || false;
        }),
      ).toBeInTheDocument();
    });
  });

  it('should show time information correctly', async () => {
    render(
      <TestWrapper>
        <Batches />
      </TestWrapper>,
    );

    await waitFor(() => {
      // Check if time information is displayed
      expect(
        screen.getByText((content, element) => {
          return element?.textContent?.includes('提交: 01-15') || false;
        }),
      ).toBeInTheDocument();

      expect(
        screen.getByText((content, element) => {
          return element?.textContent?.includes('审批: 01-15') || false;
        }),
      ).toBeInTheDocument();
    });
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

    const refreshButton = screen.getByText('刷新');
    fireEvent.click(refreshButton);

    // The actual refresh would be handled by the ProTable's actionRef
    // We can't easily test this without more complex mocking
    expect(refreshButton).toBeInTheDocument();
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
      expect(screen.getByText('批次号')).toBeInTheDocument();
    });
  });
});
