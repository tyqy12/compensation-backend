import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import api from '@services/api';
import {
  usePayrollDryRunQuery,
  usePayrollLedgerQuery,
  usePayrollManagerReviewQuery,
  usePayrollBatchesQuery,
  usePayrollTemplatesQuery,
  usePayrollTemplateDetailQuery,
  usePayrollCyclesQuery,
  usePartTimePayrollBatchesQuery,
  usePartTimePayrollLinesQuery,
  usePartTimePayslipsQuery,
  useClientCredentialsTokenMutation,
} from './payroll';
import type {
  PayrollPreviewDto,
  PayrollLedgerDto,
  PayrollManagerReviewDto,
  PayrollBatchSummaryDto,
  PayrollTemplateDto,
  PayrollTemplateDetailDto,
  PayrollCycleDto,
  OpenApiPayrollBatchDto,
  OpenApiPayrollLineDto,
  OpenApiPayslipDto,
  PagedResponse,
} from '@types/openapi';

vi.mock('@services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
  unwrap: vi.fn((data) => data),
}));

const mockedApi = api as unknown as {
  get: ReturnType<typeof vi.fn>;
  post: ReturnType<typeof vi.fn>;
};

const createWrapper = () => {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  const Wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );

  return Wrapper;
};

describe('payroll queries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches payroll dry-run data via POST', async () => {
    const mockResponse: PayrollPreviewDto = {
      batchId: 1001,
      status: 'draft',
      periodLabel: '2024-01',
      currency: 'CNY',
      totalEmployees: 2,
      grossTotal: 20000,
      netTotal: 15000,
      lines: [],
    };

    mockedApi.post.mockResolvedValue({ data: mockResponse });

    const payload = { preview: true };

    const { result } = renderHook(
      () => usePayrollDryRunQuery(1001, payload),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.post).toHaveBeenCalledWith('/payroll/batches/1001/dry-run', payload);
    expect(result.current.data).toEqual(mockResponse);
  });

  it('fetches payroll ledger data', async () => {
    const mockResponse: PayrollLedgerDto = {
      batchId: 1001,
      status: 'approved',
      periodLabel: '2024-01',
      netTotal: 15000,
      lines: [],
    };

    mockedApi.get.mockResolvedValue({ data: mockResponse });

    const { result } = renderHook(() => usePayrollLedgerQuery(1001), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenCalledWith('/payroll/batches/1001/ledger');
    expect(result.current.data).toEqual(mockResponse);
  });

  it('fetches manager review data with filters', async () => {
    const mockResponse: PayrollManagerReviewDto = {
      batchId: 1001,
      status: 'approved',
      department: '销售部',
      lines: [],
    };

    mockedApi.get.mockResolvedValue({ data: mockResponse });

    const filters = { department: '销售部', keyword: '张', managerId: 2001 };

    const { result } = renderHook(
      () => usePayrollManagerReviewQuery(1001, filters),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenCalledWith('/payroll/batches/1001/manager-review', {
      params: {
        department: '销售部',
        keyword: '张',
        managerId: 2001,
      },
    });
    expect(result.current.data).toEqual(mockResponse);
  });

  it('fetches payroll batches with filters', async () => {
    const mockPaged: PagedResponse<PayrollBatchSummaryDto> = {
      current: 1,
      size: 10,
      total: 1,
      records: [
        {
          batchId: 1001,
          batchNo: 'FT_2024_01',
          payrollType: 'full_time',
          cycleType: 'monthly',
          status: 'approved',
          computeStatus: 'completed',
          approvalStatus: 'approved',
          totalEmployees: 120,
          grossTotal: 1800000,
          netTotal: 1500000,
          periodLabel: '2024-01',
        },
      ],
    };

    mockedApi.get.mockResolvedValue({ data: mockPaged });

    const { result } = renderHook(
      () =>
        usePayrollBatchesQuery({
          current: 1,
          pageSize: 10,
          payrollType: 'full_time',
          status: 'approved',
          keyword: '2024',
          period: '2024-01',
        }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenCalledWith('/payroll/batches', {
      params: {
        current: 1,
        size: 10,
        payrollType: 'full_time',
        status: 'approved',
        keyword: '2024',
        period: '2024-01',
      },
    });
    expect(result.current.data).toEqual(mockPaged);
  });

  it('fetches payroll templates list', async () => {
    const mockPaged: PagedResponse<PayrollTemplateDto> = {
      current: 1,
      size: 10,
      total: 2,
      records: [
        {
          id: 1,
          templateName: '默认全职模板',
          templateCode: 'TMP_FT_DEFAULT',
          payrollType: 'full_time',
          cycleType: 'monthly',
          status: 'active',
          itemsCount: 24,
        },
      ],
    };

    mockedApi.get.mockResolvedValue({ data: mockPaged });

    const { result } = renderHook(
      () =>
        usePayrollTemplatesQuery({
          current: 1,
          pageSize: 10,
          payrollType: 'full_time',
          status: 'active',
        }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenCalledWith('/payroll/templates', {
      params: {
        current: 1,
        size: 10,
        payrollType: 'full_time',
        status: 'active',
      },
    });
    expect(result.current.data).toEqual(mockPaged);
  });

  it('fetches payroll template detail', async () => {
    const detail: PayrollTemplateDetailDto = {
      id: 1,
      templateName: '默认模板',
      templateCode: 'TMP01',
      payrollType: 'full_time',
      cycleType: 'monthly',
      status: 'active',
    };

    mockedApi.get.mockResolvedValue({ data: detail });

    const { result } = renderHook(
      () => usePayrollTemplateDetailQuery(1),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenCalledWith('/payroll/templates/1');
    expect(result.current.data).toEqual(detail);
  });

  it('fetches payroll cycles list', async () => {
    const mockPaged: PagedResponse<PayrollCycleDto> = {
      current: 1,
      size: 10,
      total: 1,
      records: [
        {
          id: 10,
          cycleName: '月度周期',
          cycleCode: 'CYCLE_MONTHLY',
          payrollType: 'full_time',
          cycleType: 'monthly',
          status: 'active',
          cutoffDay: 25,
          payDay: 28,
        },
      ],
    };

    mockedApi.get.mockResolvedValue({ data: mockPaged });

    const { result } = renderHook(
      () =>
        usePayrollCyclesQuery({
          current: 1,
          pageSize: 10,
          payrollType: 'full_time',
        }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenCalledWith('/payroll/cycles', {
      params: {
        current: 1,
        size: 10,
        payrollType: 'full_time',
      },
    });
    expect(result.current.data).toEqual(mockPaged);
  });

  it('fetches part-time payroll batches with default type', async () => {
    const mockPaged: PagedResponse<OpenApiPayrollBatchDto> = {
      current: 1,
      size: 10,
      total: 1,
      records: [
        {
          id: 1,
          batchNo: 'PT_202401',
          periodLabel: '2024-01',
          lineCount: 5,
          status: 'paid',
        },
      ],
    };

    mockedApi.get.mockResolvedValue({ data: mockPaged });

    const { result } = renderHook(
      () => usePartTimePayrollBatchesQuery({ current: 1, size: 10 }, { accessToken: 'token-123' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenCalledWith('/v1/payroll/batches', {
      params: {
        current: 1,
        size: 10,
        type: 'part_time',
      },
      headers: { Authorization: 'Bearer token-123' },
    });
    expect(result.current.data).toEqual(mockPaged);
  });

  it('fetches part-time payroll lines with filters', async () => {
    const mockPaged: PagedResponse<OpenApiPayrollLineDto> = {
      current: 1,
      size: 20,
      total: 1,
      records: [
        {
          id: 1,
          batchId: 1001,
          employeeRef: 'emp:E001',
          netAmount: 3200,
        },
      ],
    };

    mockedApi.get.mockResolvedValue({ data: mockPaged });

    const { result } = renderHook(
      () =>
        usePartTimePayrollLinesQuery(1001, { employeeRef: 'emp:E001' }, { accessToken: 'token-abc' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenCalledWith('/v1/payroll/batches/1001/lines', {
      params: {
        current: 1,
        size: 20,
        employeeRef: 'emp:E001',
      },
      headers: { Authorization: 'Bearer token-abc' },
    });
    expect(result.current.data).toEqual(mockPaged);
  });

  it('fetches part-time payslips when params complete', async () => {
    const mockPayslips: OpenApiPayslipDto[] = [
      {
        id: 1,
        employeeRef: 'emp:E001',
        period: '2024-01',
        netAmount: 3200,
      },
    ];

    mockedApi.get.mockResolvedValue({ data: mockPayslips });

    const { result } = renderHook(
      () =>
        usePartTimePayslipsQuery(
          { employeeRef: 'emp:E001', period: '2024-01' },
          { accessToken: 'token-xyz' },
        ),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenCalledWith('/v1/payslips', {
      params: {
        employeeRef: 'emp:E001',
        period: '2024-01',
      },
      headers: { Authorization: 'Bearer token-xyz' },
    });
    expect(result.current.data).toEqual(mockPayslips);
  });

  it('requests client credentials token with basic auth header', async () => {
    const mockToken = {
      accessToken: 'token',
      tokenType: 'Bearer',
      expiresIn: 1800,
      scope: 'payroll:read',
    };

    mockedApi.post.mockResolvedValue({ data: mockToken });

    const { result } = renderHook(() => useClientCredentialsTokenMutation(), {
      wrapper: createWrapper(),
    });

    await result.current.mutateAsync({
      clientId: 'app_123',
      clientSecret: 'secret_456',
      scope: ['payroll:read'],
    });

    expect(mockedApi.post).toHaveBeenCalled();
    const [url, body, config] = mockedApi.post.mock.calls[0];
    expect(url).toBe('/v1/oauth/token');
    expect(body).toBe('grant_type=client_credentials&scope=payroll%3Aread');
    expect(config.headers.Authorization).toContain('Basic ');
    expect(config.headers['Content-Type']).toBe('application/x-www-form-urlencoded');
  });
});
