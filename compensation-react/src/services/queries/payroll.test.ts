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
  usePayrollDistributionsQuery,
  usePayrollDistributionDetailQuery,
  usePayrollDistributionItemsQuery,
  usePayrollDistributionReconciliationQuery,
  usePayrollReconciliationsQuery,
  usePayrollReconciliationDetailQuery,
  usePayrollTemplatesQuery,
  useImportPayrollBatchCsvMutation,
  useRetryPayrollPaymentMutation,
  useUpdatePayrollImportItemMutation,
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
  PayrollDistributionDto,
  PayrollDistributionItemDto,
  PayrollReconciliationTaskDto,
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
    put: vi.fn(),
  },
  unwrap: vi.fn((data) => data),
}));

const mockedApi = api as unknown as {
  get: ReturnType<typeof vi.fn>;
  post: ReturnType<typeof vi.fn>;
  put: ReturnType<typeof vi.fn>;
};

const createWrapper = () => {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  const Wrapper = ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client }, children);

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
      warnings: ['missing required item: BASE'],
      lines: [
        {
          employeeNo: 'emp-4',
          employeeName: '王欣浩',
          warnings: ['item BASE below min: 100.00'],
        },
      ],
    };

    mockedApi.post.mockResolvedValue({ data: mockResponse });

    const payload = { preview: true };

    const { result } = renderHook(() => usePayrollDryRunQuery(1001, payload), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.post).toHaveBeenCalledWith('/payroll/batches/1001/dry-run', payload);
    expect(result.current.data).toMatchObject({
      warnings: ['缺少必填薪资项：BASE'],
      lines: [
        expect.objectContaining({
          warnings: ['薪资项 BASE 低于最小值：100.00'],
        }),
      ],
    });
  });

  it('fetches payroll ledger data', async () => {
    const mockResponse: PayrollLedgerDto = {
      batchId: 1001,
      status: 'approved',
      periodLabel: '2024-01',
      netTotal: 15000,
      warnings: ['no active salary template for batch type full_time'],
      lines: [
        {
          employeeNo: 'emp-3',
          employeeName: '侯旭柳',
          warnings: ['missing required item: BASE'],
        },
      ],
    };

    mockedApi.get.mockResolvedValue({ data: mockResponse });

    const { result } = renderHook(() => usePayrollLedgerQuery(1001), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenCalledWith('/payroll/batches/1001/ledger');
    expect(result.current.data).toMatchObject({
      warnings: ['批次类型 full_time 未找到启用中的薪资模板'],
      lines: [
        expect.objectContaining({
          warnings: ['缺少必填薪资项：BASE'],
        }),
      ],
    });
  });

  it('fetches manager review data with filters', async () => {
    const mockResponse: PayrollManagerReviewDto = {
      batchId: 1001,
      status: 'approved',
      department: '销售部',
      warnings: ['net change exceeds threshold: 0.25'],
      lines: [
        {
          employeeNo: 'emp-4',
          employeeName: '王欣浩',
          warnings: ['item BASE above max: 100000.00'],
        },
      ],
    };

    mockedApi.get.mockResolvedValue({ data: mockResponse });

    const filters = { department: '销售部', keyword: '张', managerId: 2001 };

    const { result } = renderHook(() => usePayrollManagerReviewQuery(1001, filters), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenCalledWith('/payroll/batches/1001/manager-review', {
      params: {
        department: '销售部',
        keyword: '张',
        managerId: 2001,
      },
    });
    expect(result.current.data).toMatchObject({
      warnings: ['实发变动超过阈值：0.25'],
      lines: [
        expect.objectContaining({
          warnings: ['薪资项 BASE 超过最大值：100000.00'],
        }),
      ],
    });
  });

  it('fetches payroll batches with backend-compatible params and normalizes records', async () => {
    const mockPaged: PagedResponse<PayrollBatchSummaryDto> = {
      current: 2,
      size: 10,
      total: 1,
      records: [
        {
          batch_id: 1001,
          batch_no: 'PAYROLL-1001',
          payroll_type: 'full_time',
          period_label: '2024-01',
          status: 'PAY_FAILED',
          calculation_status: 'CALCULATED',
          batch_revision: 2,
          approval_workflow_id: 8001,
          payment_batch_no: 'PMT-1001',
          total_employees: 120,
          total_lines: 120,
          gross_total: 1800000,
          net_total: 1500000,
          warnings: '["当前批次支付失败，可返回批次列表执行重试支付"]',
          updated_at: '2026-03-06T10:00:00',
        } as unknown as PayrollBatchSummaryDto,
      ],
    };

    mockedApi.get.mockResolvedValue({ data: mockPaged });

    const { result } = renderHook(
      () =>
        usePayrollBatchesQuery({
          current: 2,
          pageSize: 10,
          payrollType: 'full_time',
          status: 'pay_failed',
          period: '2024-01',
        }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenCalledWith('/payroll/batches', {
      params: {
        page: 2,
        size: 10,
        type: 'full_time',
        status: 'pay_failed',
        periodLabel: '2024-01',
      },
    });
    expect(result.current.data?.records?.[0]).toMatchObject({
      batchId: 1001,
      batchNo: 'PAYROLL-1001',
      payrollType: 'full_time',
      status: 'pay_failed',
      calculationStatus: 'calculated',
      batchRevision: 2,
      approvalWorkflowId: 8001,
      paymentBatchNo: 'PMT-1001',
      totalEmployees: 120,
      totalLines: 120,
      warnings: ['当前批次支付失败，可返回批次列表执行重试支付'],
    });
  });

  it('fetches payroll distributions list and normalizes records', async () => {
    const mockPaged: PagedResponse<PayrollDistributionDto> = {
      current: 1,
      size: 10,
      total: 1,
      records: [
        {
          id: 2001,
          distribution_no: 'DIST-2001',
          batch_id: 1001,
          batch_revision: 3,
          period_label: '2026-02',
          payroll_type: 'full_time',
          distribution_status: 'PARTIALLY_COMPLETED',
          total_amount: 1800000,
          actual_amount: 1500000,
          approval_status: 'APPROVED',
          reconciliation_task_status: 'COMPLETED',
          reconciliation_result: 'MISMATCH',
          payment_batch_no: 'PMT-2001',
          create_time: '2026-03-07T09:00:00',
          update_time: '2026-03-07T10:00:00',
        } as unknown as PayrollDistributionDto,
      ],
    };

    mockedApi.get.mockResolvedValue({ data: mockPaged });

    const { result } = renderHook(
      () =>
        usePayrollDistributionsQuery({
          current: 1,
          pageSize: 10,
          batchId: 1001,
          batchRevision: 3,
          status: 'partially_completed',
        }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenCalledWith('/payroll/distributions', {
      params: {
        page: 1,
        size: 10,
        batchId: 1001,
        batchRevision: 3,
        status: 'partially_completed',
      },
    });
    expect(result.current.data?.records?.[0]).toMatchObject({
      id: 2001,
      distributionNo: 'DIST-2001',
      batchId: 1001,
      batchRevision: 3,
      distributionStatus: 'partially_completed',
      approvalStatus: 'approved',
      reconciliationTaskStatus: 'completed',
      reconciliationResult: 'mismatch',
      paymentBatchNo: 'PMT-2001',
    });
  });

  it('fetches payroll distribution detail, items and reconciliation', async () => {
    mockedApi.get
      .mockResolvedValueOnce({
        data: {
          id: 2001,
          distribution_no: 'DIST-2001',
          batch_id: 1001,
          distribution_status: 'PROCESSING',
        },
      })
      .mockResolvedValueOnce({
        data: [
          {
            id: 1,
            distribution_id: 2001,
            employee_id: 3001,
            employee_name: '王欣浩',
            account_no_masked: '6222****9988',
            item_status: 'FAILED',
            payment_record_status: 'FAILED',
          },
        ],
      })
      .mockResolvedValueOnce({
        data: {
          id: 9001,
          distribution_id: 2001,
          task_status: 'COMPLETED',
          result: 'MATCHED',
        },
      });

    const detailHook = renderHook(() => usePayrollDistributionDetailQuery(2001), {
      wrapper: createWrapper(),
    });
    const itemsHook = renderHook(() => usePayrollDistributionItemsQuery(2001), {
      wrapper: createWrapper(),
    });
    const reconciliationHook = renderHook(
      () => usePayrollDistributionReconciliationQuery(2001),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(detailHook.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(itemsHook.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(reconciliationHook.result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/payroll/distributions/2001');
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/payroll/distributions/2001/items');
    expect(mockedApi.get).toHaveBeenNthCalledWith(3, '/payroll/distributions/2001/reconciliation');
    expect(detailHook.result.current.data).toMatchObject({
      id: 2001,
      distributionStatus: 'processing',
    });
    expect(itemsHook.result.current.data?.[0]).toMatchObject({
      distributionId: 2001,
      employeeId: 3001,
      employeeName: '王欣浩',
      itemStatus: 'failed',
      paymentRecordStatus: 'failed',
    });
    expect(reconciliationHook.result.current.data).toMatchObject({
      id: 9001,
      distributionId: 2001,
      taskStatus: 'completed',
      result: 'matched',
    });
  });

  it('fetches payroll reconciliations and detail', async () => {
    const mockPaged: PagedResponse<PayrollReconciliationTaskDto> = {
      current: 1,
      size: 10,
      total: 1,
      records: [
        {
          id: 9001,
          distribution_id: 2001,
          distribution_no: 'DIST-2001',
          distribution_status: 'COMPLETED',
          batch_id: 1001,
          batch_revision: 3,
          task_status: 'COMPLETED',
          result: 'MISMATCH',
          difference: 100,
        } as unknown as PayrollReconciliationTaskDto,
      ],
    };

    mockedApi.get
      .mockResolvedValueOnce({ data: mockPaged })
      .mockResolvedValueOnce({
        data: {
          id: 9001,
          distribution_id: 2001,
          task_status: 'COMPLETED',
          result: 'MATCHED',
          difference_detail: '{"matched":true}',
        },
      });

    const listHook = renderHook(
      () =>
        usePayrollReconciliationsQuery({
          current: 1,
          pageSize: 10,
          batchId: 1001,
          taskStatus: 'completed',
          result: 'mismatch',
        }),
      { wrapper: createWrapper() },
    );
    const detailHook = renderHook(() => usePayrollReconciliationDetailQuery(9001), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(listHook.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(detailHook.result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/payroll/reconciliations', {
      params: {
        page: 1,
        size: 10,
        batchId: 1001,
        taskStatus: 'completed',
        result: 'mismatch',
      },
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/payroll/reconciliations/9001');
    expect(listHook.result.current.data?.records?.[0]).toMatchObject({
      id: 9001,
      distributionId: 2001,
      distributionStatus: 'completed',
      taskStatus: 'completed',
      result: 'mismatch',
    });
    expect(detailHook.result.current.data).toMatchObject({
      id: 9001,
      distributionId: 2001,
      taskStatus: 'completed',
      result: 'matched',
      differenceDetail: '{"matched":true}',
    });
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
    expect(result.current.data).toMatchObject(mockPaged);
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

    const { result } = renderHook(() => usePayrollTemplateDetailQuery(1), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenCalledWith('/payroll/templates/1');
    expect(result.current.data).toMatchObject(detail);
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
          periodLabel: '2024-01',
        }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedApi.get).toHaveBeenCalledWith('/payroll/cycles', {
      params: {
        page: 1,
        size: 10,
        periodLabel: '2024-01',
      },
    });
    expect(result.current.data).toMatchObject({
      current: 1,
      size: 10,
      total: 1,
      records: [
        {
          id: 10,
          cycleName: '月度周期',
          cycleCode: 'CYCLE_MONTHLY',
          cycleType: 'monthly',
          type: 'monthly',
          status: 'active',
          cutoffDay: 25,
          payDay: 28,
        },
      ],
    });
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
        usePartTimePayrollLinesQuery(
          1001,
          { employeeRef: 'emp:E001' },
          { accessToken: 'token-abc' },
        ),
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

  it('imports payroll csv for a batch', async () => {
    mockedApi.post.mockResolvedValue({
      data: {
        importSummary: '{"total":10,"valid":9,"invalid":1}',
        previewGenerated: true,
        totalEmployees: 3,
      },
    });

    const { result } = renderHook(() => useImportPayrollBatchCsvMutation(), {
      wrapper: createWrapper(),
    });

    const file = new File(['employeeId,itemCode,amount,note'], 'batch.csv', { type: 'text/csv' });
    await result.current.mutateAsync({ batchId: 1001, file });

    expect(mockedApi.post).toHaveBeenCalledWith('/payroll/import/commit', expect.any(FormData), {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  });

  it('updates payroll import items for inline correction scenarios', async () => {
    mockedApi.put.mockResolvedValue({
      data: {
        id: 1,
        batchId: 1001,
        employeeNo: 'emp-4',
        itemCode: 'BASE',
        amount: 200,
        rowNo: 1,
      },
    });

    const { result } = renderHook(() => useUpdatePayrollImportItemMutation(), {
      wrapper: createWrapper(),
    });

    await result.current.mutateAsync({
      batchId: 1001,
      itemId: 1,
      payload: {
        employeeNo: 'emp-4',
        itemCode: 'BASE',
        amount: 200,
        rowNo: 1,
        note: '修正基本工资',
      },
    });

    expect(mockedApi.put).toHaveBeenCalledWith('/payroll/import/batches/1001/items/1', {
      employeeNo: 'emp-4',
      itemCode: 'BASE',
      amount: 200,
      rowNo: 1,
      note: '修正基本工资',
    });
  });

  it('retries failed payroll payment batches', async () => {
    mockedApi.post.mockResolvedValue({ data: { batchNo: 'PAYROLL-1001' } });

    const { result } = renderHook(() => useRetryPayrollPaymentMutation(), {
      wrapper: createWrapper(),
    });

    await result.current.mutateAsync({ batchId: 1001, triggerTransfer: true });

    expect(mockedApi.post).toHaveBeenCalledWith('/payroll/batches/1001/retry-payment', null, {
      params: { triggerTransfer: true },
    });
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
