import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api, { unwrap } from '@services/api';
import type { PageParams, PagedResponse } from '@/types/api';
import type { PaymentBatchVO, PaymentRecordItemVO } from '@/types/openapi';

// 支付批次数据类型定义（基于API文档）
export type PaymentBatch = PaymentBatchVO;

// 支付记录数据类型定义
export type PaymentRecord = PaymentRecordItemVO;

const STARTABLE_BATCH_STATUSES = new Set(['submitted', 'approved']);

export function isStartablePaymentBatch(batch: Pick<PaymentBatch, 'status'>): boolean {
  return STARTABLE_BATCH_STATUSES.has(batch.status || '');
}

export interface TransferValidationIssue {
  recordId?: number;
  employeeId?: number;
  employeeName?: string;
  providerCode?: string;
  paymentMethod?: string;
  recipientAccountMasked?: string;
  errorCode?: string;
  errorMsg?: string;
}

export interface BatchTransferValidation {
  batchNo: string;
  pendingCount: number;
  passCount: number;
  blockedCount: number;
  pass: boolean;
  warnings: string[];
  blockedRecords: TransferValidationIssue[];
}

// 批次查询参数
export interface PaymentBatchQueryParams extends PageParams {
  current?: number;
  pageSize?: number;
  status?: PaymentBatch['status'];
  paymentType?: PaymentBatch['paymentType'];
  startDate?: string;
  endDate?: string;
  keyword?: string;
  sortBy?: string;
  order?: 'asc' | 'desc';
}

// 支付记录查询参数
export interface PaymentRecordQueryParams {
  batchNo: string;
  status?: PaymentRecord['status'];
}

// 转账状态查询结果
export type TransferStatus = string;

export type PaymentBatchListResponse = PagedResponse<PaymentBatch> & {
  list: PaymentBatch[];
};

export async function fetchPaymentBatches(params: PaymentBatchQueryParams): Promise<PaymentBatchListResponse> {
  const queryParams = {
    page: params.current || 1,
    size: params.pageSize || 10,
    status: params.status,
    paymentType: params.paymentType,
    startDate: params.startDate,
    endDate: params.endDate,
    keyword: params.keyword,
    sortBy: params.sortBy || 'submitTime',
    order: params.order || 'desc',
  };

  const cleanParams = Object.fromEntries(
    Object.entries(queryParams).filter(([, value]) => value !== undefined && value !== ''),
  );

  const { data } = await api.get('/payment/batch', { params: cleanParams });
  const pagedData = unwrap<PagedResponse<PaymentBatch>>(data);
  return {
    ...pagedData,
    list: pagedData.records || [],
  };
}

// 查询支付批次列表
export function usePaymentBatchesQuery(
  params: PaymentBatchQueryParams,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: ['paymentBatches', params],
    queryFn: async () => {
      return fetchPaymentBatches(params);
    },
    enabled: options?.enabled,
  });
}

// 查询支付批次详情
export function usePaymentBatchQuery(batchNo: string, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['paymentBatch', batchNo],
    queryFn: async () => {
      const { data } = await api.get(`/payment/batch/${batchNo}`);
      return unwrap<PaymentBatch>(data);
    },
    enabled: !!batchNo && options?.enabled !== false,
  });
}

// 查询批次支付记录
export function usePaymentRecordsQuery(
  params: PaymentRecordQueryParams,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: ['paymentRecords', params.batchNo, params.status],
    queryFn: async () => {
      const queryParams = params.status ? { status: params.status } : {};
      const { data } = await api.get(`/payment/batch/${params.batchNo}/records`, {
        params: queryParams,
      });
      return unwrap<PaymentRecord[]>(data);
    },
    enabled: !!params.batchNo && options?.enabled !== false,
  });
}

// 查询单个支付记录详情
export function usePaymentRecordQuery(recordId: number, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['paymentRecord', recordId],
    queryFn: async () => {
      const { data } = await api.get(`/payment/record/${recordId}`);
      return unwrap<PaymentRecord>(data);
    },
    enabled: !!recordId && options?.enabled !== false,
  });
}

// 查询转账状态
export function useTransferStatusQuery(outBizNo: string, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['transferStatus', outBizNo],
    queryFn: async () => {
      const { data } = await api.get('/payment/transfer-status', { params: { outBizNo } });
      return unwrap<TransferStatus>(data);
    },
    enabled: !!outBizNo && options?.enabled !== false,
    staleTime: 10000, // 10秒内不重新请求
  });
}

export async function checkBatchTransfer(batchNo: string, persistFailure: boolean = false) {
  const { data } = await api.get(`/payment/batch/${batchNo}/precheck`, {
    params: { persistFailure },
  });
  return unwrap<BatchTransferValidation>(data);
}

// 启动批次转账
export function useStartBatchTransferMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (batchNo: string) => {
      const { data } = await api.post(`/payment/batch/${batchNo}/start`);
      return unwrap<{ success: boolean; message?: string }>(data);
    },
    onSuccess: (_, batchNo) => {
      // 刷新批次详情和列表
      queryClient.invalidateQueries({ queryKey: ['paymentBatch', batchNo] });
      queryClient.invalidateQueries({ queryKey: ['paymentBatches'] });
    },
  });
}

// 重试失败的支付记录
export function useRetryPaymentRecordMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (recordId: number) => {
      const { data } = await api.post(`/payment/record/${recordId}/retry`);
      return unwrap<{ success: boolean; message?: string }>(data);
    },
    onSuccess: (_, recordId) => {
      // 刷新支付记录详情
      queryClient.invalidateQueries({ queryKey: ['paymentRecord', recordId] });
      // 刷新相关的批次记录列表
      queryClient.invalidateQueries({ queryKey: ['paymentRecords'] });
      // 刷新批次详情
      queryClient.invalidateQueries({ queryKey: ['paymentBatch'] });
    },
  });
}

// 重试批次中所有失败的记录
export interface RetryBatchFailedParams {
  batchNo: string;
}

export function useRetryFailedRecordsMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ batchNo }: RetryBatchFailedParams) => {
      const { data } = await api.post(`/payment/batch/${batchNo}/retry-failed`);
      return unwrap<{ success: boolean; message?: string; retriedCount: number }>(data);
    },
    onSuccess: (_, { batchNo }) => {
      // 刷新批次详情
      queryClient.invalidateQueries({ queryKey: ['paymentBatch', batchNo] });
      // 刷新批次记录列表
      queryClient.invalidateQueries({ queryKey: ['paymentRecords', batchNo] });
      // 刷新批次列表
      queryClient.invalidateQueries({ queryKey: ['paymentBatches'] });
    },
  });
}

// 获取批次状态的显示信息
export function getBatchStatusInfo(status: string | undefined) {
  const statusMap: Record<string, { text: string; color: 'default' | 'processing' | 'success' | 'warning' | 'error'; icon: string }> = {
    draft: { text: '草稿', color: 'default', icon: '📝' },
    submitted: { text: '已提交', color: 'processing', icon: '📤' },
    approved: { text: '已审批', color: 'success', icon: '✅' },
    processing: { text: '处理中', color: 'warning', icon: '⚡' },
    completed: { text: '已完成', color: 'success', icon: '🎉' },
    failed: { text: '失败', color: 'error', icon: '❌' },
  };
  return statusMap[status || ''] || { text: status || '未知', color: 'default', icon: '❓' };
}

// 获取支付类型的显示信息
export function getPaymentTypeInfo(paymentType: string | undefined) {
  const typeMap: Record<string, { text: string; color: 'default' | 'blue' | 'gold' | 'green'; icon: string }> = {
    salary: { text: '工资', color: 'blue', icon: '💰' },
    bonus: { text: '奖金', color: 'gold', icon: '🎁' },
    reimbursement: { text: '报销', color: 'green', icon: '📋' },
  };
  return typeMap[paymentType || ''] || { text: paymentType || '未知', color: 'default', icon: '💳' };
}

// 获取支付记录状态的显示信息
export function getPaymentRecordStatusInfo(status: string | undefined) {
  const statusMap: Record<string, { text: string; color: 'default' | 'processing' | 'success' | 'error'; icon: string }> = {
    pending: { text: '待处理', color: 'default', icon: '⏳' },
    processing: { text: '处理中', color: 'processing', icon: '⚡' },
    success: { text: '成功', color: 'success', icon: '✅' },
    failed: { text: '失败', color: 'error', icon: '❌' },
    cancelled: { text: '已取消', color: 'default', icon: '🚫' },
  };
  return statusMap[status || ''] || { text: status || '未知', color: 'default', icon: '❓' };
}

// 格式化金额显示
export function formatAmount(amount: number, currency: string = 'CNY'): string {
  const formatter = new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: currency,
    minimumFractionDigits: 2,
  });
  return formatter.format(amount);
}

// 计算批次进度百分比
export function calculateBatchProgress(batch: PaymentBatch): number {
  if (batch.totalCount === 0) return 0;
  const processedCount = batch.successCount + batch.failedCount;
  return Math.round((processedCount / batch.totalCount) * 100);
}

// 计算批次成功率
export function calculateBatchSuccessRate(batch: PaymentBatch): number {
  const processedCount = batch.successCount + batch.failedCount;
  if (processedCount === 0) return 0;
  return Math.round((batch.successCount / processedCount) * 100);
}
