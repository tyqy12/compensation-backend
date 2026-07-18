import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { App as AntdApp, ConfigProvider } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import PartTimeReadonly from './PartTimeReadonly';

const mockTokenMutation = vi.fn();
const mockBatchesQuery = vi.fn();
const mockLinesQuery = vi.fn();
const mockPayslipsQuery = vi.fn();

vi.mock('@services/queries/payroll', () => ({
  useClientCredentialsTokenMutation: () => ({
    mutateAsync: mockTokenMutation,
    isPending: false,
  }),
  usePartTimePayrollBatchesQuery: (...args: unknown[]) => mockBatchesQuery(...args),
  usePartTimePayrollLinesQuery: (...args: unknown[]) => mockLinesQuery(...args),
  usePartTimePayslipsQuery: (...args: unknown[]) => mockPayslipsQuery(...args),
}));

const queryState = (data: unknown = undefined) => ({
  data,
  isLoading: false,
  isFetching: false,
  isError: false,
  error: null,
  refetch: vi.fn(),
  remove: vi.fn(),
});

const TestWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <MemoryRouter>
    <ConfigProvider>
      <AntdApp>{children}</AntdApp>
    </ConfigProvider>
  </MemoryRouter>
);

describe('PartTimeReadonly', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    mockTokenMutation.mockResolvedValue({
      accessToken: 'new-token-123456',
      tokenType: 'Bearer',
      expiresIn: 1800,
      scope: 'payroll:read payslip:read',
    });
    mockBatchesQuery.mockReturnValue(queryState({ list: [], total: 0, current: 1, size: 10 }));
    mockLinesQuery.mockReturnValue(queryState({ list: [], total: 0, current: 1, size: 20 }));
    mockPayslipsQuery.mockReturnValue(queryState([]));
  });

  it('renders the credential, batch and payslip work areas', () => {
    render(
      <TestWrapper>
        <PartTimeReadonly />
      </TestWrapper>,
    );

    expect(screen.getByText('PT 只读工作台')).toBeInTheDocument();
    expect(screen.getByText('客户端凭证')).toBeInTheDocument();
    expect(screen.getByText('兼职批次')).toBeInTheDocument();
    expect(screen.getByText('工资条查询')).toBeInTheDocument();
    expect(screen.getByText('请先获取访问令牌')).toBeInTheDocument();
  });

  it('requests and caches a client credentials token', async () => {
    render(
      <TestWrapper>
        <PartTimeReadonly />
      </TestWrapper>,
    );

    fireEvent.change(screen.getByLabelText('Client ID'), { target: { value: 'pt-app' } });
    fireEvent.change(screen.getByLabelText('Client Secret'), { target: { value: 'secret' } });
    fireEvent.click(screen.getByRole('button', { name: /获取 Token/ }));

    await waitFor(() => {
      expect(mockTokenMutation).toHaveBeenCalledWith({
        clientId: 'pt-app',
        clientSecret: 'secret',
        scope: ['payroll:read', 'payslip:read'],
      });
      expect(screen.getByText('AccessToken 已缓存')).toBeInTheDocument();
    });
    expect(sessionStorage.getItem('pt_readonly_token')).toContain('new-token-123456');
  });

  it('reveals line details after selecting a batch', async () => {
    sessionStorage.setItem(
      'pt_readonly_token',
      JSON.stringify({
        accessToken: 'cached-token',
        tokenType: 'Bearer',
        expiresIn: 1800,
        scope: 'payroll:read',
        expiresAt: Date.now() + 60_000,
      }),
    );
    mockBatchesQuery.mockReturnValue(
      queryState({
        list: [
          {
            batchId: 42,
            batchNo: 'PT-2026-01',
            periodLabel: '2026-01',
            status: 'paid',
            lineCount: 2,
          },
        ],
        total: 1,
        current: 1,
        size: 10,
      }),
    );

    render(
      <TestWrapper>
        <PartTimeReadonly />
      </TestWrapper>,
    );

    const batchCell = await screen.findByText('PT-2026-01');
    fireEvent.click(batchCell.closest('tr') as HTMLElement);

    await waitFor(() => {
      expect(screen.getByText('工资行明细')).toBeInTheDocument();
      expect(screen.getAllByText(/PT-2026-01/).length).toBeGreaterThan(1);
    });
  });
});
