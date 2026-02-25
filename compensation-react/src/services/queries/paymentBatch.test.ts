import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import api from '@services/api';
import {
  usePaymentBatchesQuery,
  usePaymentBatchQuery,
  usePaymentRecordsQuery,
  useStartBatchTransferMutation,
  useRetryPaymentRecordMutation,
  getBatchStatusInfo,
  getPaymentTypeInfo,
  getPaymentRecordStatusInfo,
  formatAmount,
  calculateBatchProgress,
  calculateBatchSuccessRate,
  type PaymentBatch,
  type PaymentRecord,
} from './paymentBatch';

// Mock the API
vi.mock('@services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
  unwrap: vi.fn((data) => data),
}));

const mockApi = api as any;

// Test wrapper
const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  const Wrapper = ({ children }: { children: React.ReactNode }) => {
    return React.createElement(QueryClientProvider, { client: queryClient }, children);
  };

  return Wrapper;
};

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
  processEndTime: undefined,
  remark: '月度工资批量发放',
};

const mockRecord: PaymentRecord = {
  id: 1001,
  batchNo: 'BATCH_20240115001',
  paymentType: 'salary',
  amount: 8500.0,
  currency: 'CNY',
  status: 'success',
  alipayOrderNo: 'COMP_20240115_1001',
  alipayTradeNo: '2024011522000123456789',
  errorCode: undefined,
  errorMsg: undefined,
  paymentTime: '2024-01-15T14:05:23',
  notificationTime: '2024-01-15T14:05:25',
};

describe('PaymentBatch Queries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('usePaymentBatchesQuery', () => {
    it('should fetch batches with correct parameters', async () => {
      const mockResponse = {
        data: {
          records: [mockBatch],
          total: 1,
          current: 1,
          size: 10,
        },
      };

      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(
        () =>
          usePaymentBatchesQuery({
            current: 1,
            pageSize: 10,
            status: 'processing',
            paymentType: 'salary',
            keyword: 'test',
            sortBy: 'submitTime',
            order: 'desc',
          }),
        { wrapper: createWrapper() },
      );

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/payment/batch', {
        params: {
          page: 1,
          size: 10,
          status: 'processing',
          paymentType: 'salary',
          keyword: 'test',
          sortBy: 'submitTime',
          order: 'desc',
        },
      });

      expect(result.current.data).toEqual(mockResponse.data);
    });

    it('should filter out empty parameters', async () => {
      const mockResponse = { data: { records: [], total: 0 } };
      mockApi.get.mockResolvedValue(mockResponse);

      renderHook(
        () =>
          usePaymentBatchesQuery({
            current: 1,
            pageSize: 10,
            status: undefined,
            paymentType: undefined,
            keyword: '',
          }),
        { wrapper: createWrapper() },
      );

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith('/payment/batch', {
          params: {
            page: 1,
            size: 10,
            sortBy: 'submitTime',
            order: 'desc',
          },
        });
      });
    });
  });

  describe('usePaymentBatchQuery', () => {
    it('should fetch single batch by batchNo', async () => {
      const mockResponse = { data: mockBatch };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => usePaymentBatchQuery('BATCH_20240115001'), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/payment/batch/BATCH_20240115001');
      expect(result.current.data).toEqual(mockBatch);
    });
  });

  describe('usePaymentRecordsQuery', () => {
    it('should fetch payment records for a batch', async () => {
      const mockResponse = { data: [mockRecord] };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(
        () =>
          usePaymentRecordsQuery({
            batchNo: 'BATCH_20240115001',
            status: 'success',
          }),
        { wrapper: createWrapper() },
      );

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/payment/batch/BATCH_20240115001/records', {
        params: { status: 'success' },
      });

      expect(result.current.data).toEqual([mockRecord]);
    });
  });
});

describe('PaymentBatch Mutations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useStartBatchTransferMutation', () => {
    it('should start batch transfer', async () => {
      const mockResponse = { data: { success: true, message: '转账已启动' } };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useStartBatchTransferMutation(), {
        wrapper: createWrapper(),
      });

      const mutateResult = await result.current.mutateAsync('BATCH_20240115001');

      expect(mockApi.post).toHaveBeenCalledWith('/payment/batch/BATCH_20240115001/start');
      expect(mutateResult).toEqual({ success: true, message: '转账已启动' });
    });
  });

  describe('useRetryPaymentRecordMutation', () => {
    it('should retry payment record', async () => {
      const mockResponse = { data: { success: true, message: '重试成功' } };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useRetryPaymentRecordMutation(), {
        wrapper: createWrapper(),
      });

      const mutateResult = await result.current.mutateAsync(1001);

      expect(mockApi.post).toHaveBeenCalledWith('/payment/record/1001/retry');
      expect(mutateResult).toEqual({ success: true, message: '重试成功' });
    });
  });
});

describe('Helper Functions', () => {
  describe('getBatchStatusInfo', () => {
    it('should return correct info for all batch statuses', () => {
      expect(getBatchStatusInfo('draft')).toEqual({
        text: '草稿',
        color: 'default',
        icon: '📝',
      });

      expect(getBatchStatusInfo('submitted')).toEqual({
        text: '已提交',
        color: 'processing',
        icon: '📤',
      });

      expect(getBatchStatusInfo('approved')).toEqual({
        text: '已审批',
        color: 'success',
        icon: '✅',
      });

      expect(getBatchStatusInfo('processing')).toEqual({
        text: '处理中',
        color: 'warning',
        icon: '⚡',
      });

      expect(getBatchStatusInfo('completed')).toEqual({
        text: '已完成',
        color: 'success',
        icon: '🎉',
      });

      expect(getBatchStatusInfo('failed')).toEqual({
        text: '失败',
        color: 'error',
        icon: '❌',
      });
    });
  });

  describe('getPaymentTypeInfo', () => {
    it('should return correct info for all payment types', () => {
      expect(getPaymentTypeInfo('salary')).toEqual({
        text: '工资',
        color: 'blue',
        icon: '💰',
      });

      expect(getPaymentTypeInfo('bonus')).toEqual({
        text: '奖金',
        color: 'gold',
        icon: '🎁',
      });

      expect(getPaymentTypeInfo('reimbursement')).toEqual({
        text: '报销',
        color: 'green',
        icon: '📋',
      });
    });
  });

  describe('getPaymentRecordStatusInfo', () => {
    it('should return correct info for all record statuses', () => {
      expect(getPaymentRecordStatusInfo('pending')).toEqual({
        text: '待处理',
        color: 'default',
        icon: '⏳',
      });

      expect(getPaymentRecordStatusInfo('processing')).toEqual({
        text: '处理中',
        color: 'processing',
        icon: '⚡',
      });

      expect(getPaymentRecordStatusInfo('success')).toEqual({
        text: '成功',
        color: 'success',
        icon: '✅',
      });

      expect(getPaymentRecordStatusInfo('failed')).toEqual({
        text: '失败',
        color: 'error',
        icon: '❌',
      });

      expect(getPaymentRecordStatusInfo('cancelled')).toEqual({
        text: '已取消',
        color: 'default',
        icon: '🚫',
      });
    });
  });

  describe('formatAmount', () => {
    it('should format amount with default CNY currency', () => {
      const result = formatAmount(1000);
      expect(result).toContain('1,000');
      expect(result).toContain('.00');
    });

    it('should handle zero amount', () => {
      const result = formatAmount(0);
      expect(result).toContain('0.00');
    });
  });

  describe('calculateBatchProgress', () => {
    it('should calculate progress correctly', () => {
      const batch: PaymentBatch = {
        ...mockBatch,
        totalCount: 100,
        successCount: 80,
        failedCount: 10,
      };

      expect(calculateBatchProgress(batch)).toBe(90);
    });

    it('should return 0 for zero total count', () => {
      const batch: PaymentBatch = {
        ...mockBatch,
        totalCount: 0,
        successCount: 0,
        failedCount: 0,
      };

      expect(calculateBatchProgress(batch)).toBe(0);
    });
  });

  describe('calculateBatchSuccessRate', () => {
    it('should calculate success rate correctly', () => {
      const batch: PaymentBatch = {
        ...mockBatch,
        successCount: 80,
        failedCount: 20,
      };

      expect(calculateBatchSuccessRate(batch)).toBe(80);
    });

    it('should return 0 when no records processed', () => {
      const batch: PaymentBatch = {
        ...mockBatch,
        successCount: 0,
        failedCount: 0,
      };

      expect(calculateBatchSuccessRate(batch)).toBe(0);
    });
  });
});
