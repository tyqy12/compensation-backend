import { useMutation, useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import api, { unwrap } from '@services/api';
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
} from '../../types/openapi';
import { qk, type PageParams } from '../../types/api';

export interface PayrollManagerReviewFilters {
  department?: string;
  managerId?: number;
  keyword?: string;
}

export interface PartTimeBatchQueryParams {
  current?: number;
  size?: number;
  period?: string;
  status?: string;
  type?: 'part_time';
  [key: string]: unknown;
}

export interface PartTimeLinesQueryParams {
  current?: number;
  size?: number;
  employeeRef?: string;
  [key: string]: unknown;
}

export interface PartTimePayslipQueryParams {
  employeeRef: string;
  period: string;
  [key: string]: unknown;
}

export interface PayrollBatchListParams extends PageParams {
  current?: number;
  pageSize?: number;
  payrollType?: string;
  cycleType?: string;
  status?: string;
  computeStatus?: string;
  keyword?: string;
  period?: string;
  sortBy?: string;
  order?: 'asc' | 'desc';
  [key: string]: unknown;
}

export interface PayrollTemplateListParams extends PageParams {
  current?: number;
  pageSize?: number;
  keyword?: string;
  payrollType?: string;
  cycleType?: string;
  status?: string;
  [key: string]: unknown;
}

export interface PayrollTemplateDetailParams {
  templateId: string | number;
  [key: string]: unknown;
}

export interface PayrollCycleListParams extends PageParams {
  current?: number;
  pageSize?: number;
  keyword?: string;
  payrollType?: string;
  cycleType?: string;
  status?: string;
  [key: string]: unknown;
}

export interface ClientCredentialsRequest {
  clientId: string;
  clientSecret: string;
  scope?: string[];
}

export interface ClientCredentialsToken {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  scope: string;
}

const encodeBasicAuth = (clientId: string, clientSecret: string) => {
  const raw = `${clientId}:${clientSecret}`;
  if (typeof globalThis !== 'undefined' && typeof (globalThis as any).btoa === 'function') {
    return (globalThis as any).btoa(raw);
  }
  if (typeof globalThis !== 'undefined' && (globalThis as any).Buffer) {
    return (globalThis as any).Buffer.from(raw, 'utf-8').toString('base64');
  }
  throw new Error('Base64 encoding is not supported in this environment');
};

const authHeaders = (token?: string) =>
  token
    ? {
        Authorization: `Bearer ${token}`,
      }
    : undefined;

const cleanParams = (params: Record<string, unknown>) =>
  Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );

export async function fetchPayrollBatches(params: PayrollBatchListParams) {
  const query = cleanParams({
    current: params.current ?? params.page ?? 1,
    size: params.pageSize ?? params.size ?? 10,
    payrollType: params.payrollType,
    cycleType: params.cycleType,
    status: params.status,
    computeStatus: params.computeStatus,
    keyword: params.keyword,
    period: params.period,
    sortBy: params.sortBy,
    order: params.order,
  });
  const { data } = await api.get('/payroll/batches', { params: query });
  const raw = unwrap<PagedResponse<any>>(data);
  // 兼容两种格式: list + pageNum/pageSize 或 records + current/size
  const sourceRecords = raw.list ?? raw.records ?? [];
  const normalizedRecords: PayrollBatchSummaryDto[] = sourceRecords.map((record: any) => {
    // 处理字段名映射 - 支持驼峰和下划线格式
    const payrollType = record.payrollType ?? record.type ?? record.batchType ?? record.payroll_type;
    const cycleType = record.cycleType ?? record.payCycle?.cycleType ?? record.periodType ?? record.period_type ?? 'monthly';
    const batchStatus = record.status ?? record.batchStatus ?? record.state ?? 'draft';
    const computeStatus = record.computeStatus ?? record.calculateStatus ?? record.compute_status ?? batchStatus;

    // 解析 warnings JSON 数组
    let warnings: string[] = [];
    if (record.warnings) {
      try {
        const parsed = JSON.parse(record.warnings);
        warnings = Array.isArray(parsed) ? parsed.filter((w: string) => w && w.trim()) : [];
      } catch {
        warnings = [];
      }
    } else if (record.warningList) {
      warnings = Array.isArray(record.warningList) ? record.warningList : [];
    }

    // 处理时间字段映射
    const createdAt = record.createTime ?? record.createdAt ?? record.create_time ?? record.created_at;
    const updatedAt = record.updateTime ?? record.updatedAt ?? record.update_time ?? record.updated_at;

    // 处理 ID 和批次号映射
    const batchId = record.id ?? record.batchId ?? record.payCycleId ?? record.pay_cycle_id;
    const batchNo = record.paymentBatchNo ?? record.batchNo ?? record.payment_batch_no ?? String(batchId ?? '');

    // 处理数值字段映射
    const totalEmployees = record.totalEmployees ?? record.employeeCount ?? record.headcount ?? record.total_employees ?? record.employee_count ?? 0;
    const totalLines = record.totalLines ?? record.lineCount ?? record.total_lines ?? record.line_count ?? 0;
    const grossTotal = record.grossTotal ?? record.grossAmount ?? record.totalGrossAmount ?? record.gross_total ?? record.gross_amount ?? record.total_gross_amount ?? null;
    const netTotal = record.netTotal ?? record.netAmount ?? record.totalNetAmount ?? record.net_total ?? record.net_amount ?? record.total_net_amount ?? null;

    // 处理备注/描述字段映射
    const remark = record.remark ?? record.description ?? record.note ?? record.remark ?? '';

    return {
      ...record,
      batchId,
      batchNo,
      payrollType,
      cycleType,
      status: batchStatus,
      computeStatus,
      totalEmployees,
      totalLines,
      grossTotal,
      netTotal,
      currency: record.currency ?? 'CNY',
      warnings,
      remark,
      createdAt,
      updatedAt,
    };
  });

  return {
    ...raw,
    list: undefined, // 清除原始 list，使用 records
    records: normalizedRecords,
  } as PagedResponse<PayrollBatchSummaryDto>;
}

export function usePayrollDryRunQuery(
  batchId: string | number,
  payload?: Record<string, unknown>,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.payrollDryRun(batchId, payload),
    queryFn: async () => {
      const { data } = await api.post(`/payroll/batches/${batchId}/dry-run`, payload ?? {});
      return unwrap<PayrollPreviewDto>(data);
    },
    enabled: !!batchId && (options?.enabled ?? true),
  });
}

export function usePayrollLedgerQuery(batchId: string | number, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: qk.payrollLedger(batchId),
    queryFn: async () => {
      const { data } = await api.get(`/payroll/batches/${batchId}/ledger`);
      return unwrap<PayrollLedgerDto>(data);
    },
    enabled: !!batchId && (options?.enabled ?? true),
  });
}

export function usePayrollManagerReviewQuery(
  batchId: string | number,
  filters: PayrollManagerReviewFilters,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.payrollManagerReview(batchId, filters),
    queryFn: async () => {
      const params = cleanParams({
        department: filters.department,
        managerId: filters.managerId,
        keyword: filters.keyword,
      });
      const { data } = await api.get(`/payroll/batches/${batchId}/manager-review`, { params });
      return unwrap<PayrollManagerReviewDto>(data);
    },
    enabled: !!batchId && (options?.enabled ?? true),
  });
}

export function usePayrollBatchesQuery(params: PayrollBatchListParams, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: qk.payrollBatches(params),
    queryFn: () => fetchPayrollBatches(params),
    enabled: options?.enabled ?? true,
  });
}

export async function fetchPayrollTemplates(params: PayrollTemplateListParams) {
  const query = cleanParams({
    current: params.current ?? params.page ?? 1,
    size: params.pageSize ?? params.size ?? 10,
    keyword: params.keyword,
    payrollType: params.payrollType,
    cycleType: params.cycleType,
    status: params.status,
  });
  const { data } = await api.get('/payroll/templates', { params: query });
  const raw = unwrap<PagedResponse<any>>(data);
  const normalizedRecords: PayrollTemplateDto[] = (raw.records ?? []).map((record: any) => {
    const payrollType = record.payrollType ?? record.type ?? record.scopeType;
    const cycleType = record.cycleType ?? record.periodType ?? record.cycleCategory;
    const status = record.status ?? record.templateStatus ?? 'draft';
    const itemsJson = record.itemsJson ?? record.items_snapshot_json;
    const itemsCount = record.itemsCount ?? (Array.isArray(record.items) ? record.items.length : undefined);

    let parsedItems: any[] | undefined;
    if (!itemsCount && typeof itemsJson === 'string') {
      try {
        const parsed = JSON.parse(itemsJson);
        parsedItems = Array.isArray(parsed) ? parsed : undefined;
      } catch {}
    }

    let parsedTaxRules: any[] | undefined;
    if (record.taxRuleJson && typeof record.taxRuleJson === 'string') {
      try {
        const taxParsed = JSON.parse(record.taxRuleJson);
        parsedTaxRules = Array.isArray(taxParsed)
          ? taxParsed
          : taxParsed && typeof taxParsed === 'object'
            ? Object.entries(taxParsed).map(([ruleCode, detail]) => ({ ruleCode, ...(detail as object) }))
            : undefined;
      } catch {}
    }

    return {
      ...record,
      id: record.id,
      templateName: record.templateName ?? record.name ?? '未命名模板',
      templateCode: record.templateCode ?? record.code ?? `TMP_${record.id ?? ''}`,
      payrollType,
      cycleType,
      status,
      version: record.version ?? record.templateVersion ?? 1,
      description: record.description ?? record.remark ?? null,
      defaultFlag: Boolean(record.defaultFlag ?? record.isDefault),
      itemsCount: itemsCount ?? parsedItems?.length ?? 0,
      items: record.items ?? parsedItems,
      taxRules: record.taxRules ?? parsedTaxRules,
      lastPublishedAt: record.lastPublishedAt ?? record.publishTime ?? record.updateTime,
      updatedAt: record.updateTime ?? record.updatedAt,
      createdAt: record.createTime ?? record.createdAt,
    };
  });

  return {
    ...raw,
    records: normalizedRecords,
  } as PagedResponse<PayrollTemplateDto>;
}

export function usePayrollTemplatesQuery(
  params: PayrollTemplateListParams,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.payrollTemplates(params),
    queryFn: () => fetchPayrollTemplates(params),
    enabled: options?.enabled ?? true,
  });
}

export function usePayrollTemplateDetailQuery(
  templateId: string | number,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.payrollTemplateDetail(templateId),
    queryFn: async () => {
      const { data } = await api.get(`/payroll/templates/${templateId}`);
      const detail = unwrap<PayrollTemplateDetailDto & Record<string, any>>(data);

      const payrollType = detail.payrollType ?? detail.type;
      const cycleType = detail.cycleType ?? detail.periodType;
      const status = detail.status ?? detail.templateStatus ?? 'draft';
      const templateName = detail.templateName ?? detail.name ?? '未命名模板';
      const templateCode = detail.templateCode ?? detail.code ?? `TMP_${detail.id ?? ''}`;
      const version = detail.version ?? detail.templateVersion ?? 1;

      let items = detail.items;
      if ((!items || items.length === 0) && typeof detail.itemsJson === 'string') {
        try {
          const parsed = JSON.parse(detail.itemsJson);
          if (Array.isArray(parsed)) {
            items = parsed.map((item: any, index: number) => ({
              itemName: item.itemName ?? item.name ?? item.code ?? `项目${index + 1}`,
              itemCode: item.itemCode ?? item.code,
              category: item.category ?? item.type,
              amountType: item.amountType ?? item.mode ?? item.valueType,
              formula: item.formula ?? item.expression ?? null,
              showOnPayslip: item.showOnPayslip ?? item.required ?? false,
              order: item.order ?? index + 1,
              min: item.min,
              max: item.max,
            }));
          }
        } catch {}
      }

      let taxRules = detail.taxRules;
      if ((!taxRules || taxRules.length === 0) && typeof detail.taxRuleJson === 'string') {
        try {
          const parsed = JSON.parse(detail.taxRuleJson);
          if (Array.isArray(parsed)) {
            taxRules = parsed as any[];
          } else if (parsed && typeof parsed === 'object') {
            taxRules = Object.entries(parsed).map(([ruleCode, config]) => ({
              ruleCode,
              ...(config as Record<string, any>),
            }));
          }
        } catch {}
      }

      const itemsCount = detail.itemsCount ?? items?.length ?? 0;

      return {
        ...detail,
        templateName,
        templateCode,
        payrollType,
        cycleType,
        status,
        version,
        defaultFlag: Boolean(detail.defaultFlag ?? detail.isDefault),
        items,
        itemsCount,
        taxRules,
        lastPublishedAt: detail.lastPublishedAt ?? detail.publishTime ?? detail.updateTime,
        updatedAt: detail.updateTime ?? detail.updatedAt,
        createdAt: detail.createTime ?? detail.createdAt,
      } as PayrollTemplateDetailDto;
    },
    enabled: !!templateId && (options?.enabled ?? true),
  });
}

export async function fetchPayrollCycles(params: PayrollCycleListParams) {
  const query = cleanParams({
    current: params.current ?? params.page ?? 1,
    size: params.pageSize ?? params.size ?? 10,
    keyword: params.keyword,
    payrollType: params.payrollType,
    cycleType: params.cycleType,
    status: params.status,
  });
  const { data } = await api.get('/payroll/cycles', { params: query });
  const raw = unwrap<PagedResponse<any>>(data);
  const normalizedRecords: PayrollCycleDto[] = (raw.records ?? []).map((record: any) => {
    const cycleType = record.cycleType ?? record.type ?? record.periodType;
    const payrollType = record.payrollType ?? record.typeCategory ?? record.scopeType;
    const cutoffDay = record.cutoffDay ?? (record.cutoffDate ? dayjs(record.cutoffDate).date() : undefined);
    const payDay = record.payDay ?? (record.payDate ? dayjs(record.payDate).date() : undefined);
    const status = record.status ?? record.cycleStatus ?? 'draft';

    return {
      ...record,
      id: record.id,
      cycleCode: record.cycleCode ?? `CYCLE_${record.id ?? ''}`,
      cycleName: record.cycleName ?? record.periodLabel ?? `周期${record.id ?? ''}`,
      payrollType,
      cycleType,
      timezone: record.timezone ?? 'UTC+8',
      status,
      description: record.description ?? record.remark ?? null,
      cutoffDay,
      cutoffDate: record.cutoffDate,
      payDay,
      payDate: record.payDate,
      leadDays: record.leadDays ?? record.leadTime,
      graceDays: record.graceDays ?? record.graceTime,
      periodLabel: record.periodLabel,
      startDate: record.startDate,
      endDate: record.endDate,
      lastExecutionTime: record.lastExecutionTime,
      nextExecutionTime: record.nextExecutionTime,
      createdAt: record.createTime ?? record.createdAt,
      updatedAt: record.updateTime ?? record.updatedAt,
    };
  });

  return {
    ...raw,
    records: normalizedRecords,
  } as PagedResponse<PayrollCycleDto>;
}

export function usePayrollCyclesQuery(params: PayrollCycleListParams, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: qk.payrollCycles(params),
    queryFn: () => fetchPayrollCycles(params),
    enabled: options?.enabled ?? true,
  });
}

export function usePartTimePayrollBatchesQuery(
  params: PartTimeBatchQueryParams,
  options?: { enabled?: boolean; accessToken?: string },
) {
  const token = options?.accessToken;
  const enabled = (options?.enabled ?? true) && Boolean(token);
  return useQuery({
    queryKey: [...qk.ptBatches(params), token ?? null],
    queryFn: async () => {
      const query = cleanParams({
        current: params.current ?? 1,
        size: params.size ?? 10,
        period: params.period,
        status: params.status,
        type: params.type ?? 'part_time',
      });
      const { data } = await api.get('/v1/payroll/batches', {
        params: query,
        headers: authHeaders(token),
      });
      return unwrap<PagedResponse<OpenApiPayrollBatchDto>>(data);
    },
    enabled,
  });
}

export function usePartTimePayrollLinesQuery(
  batchId: string | number,
  params: PartTimeLinesQueryParams,
  options?: { enabled?: boolean; accessToken?: string },
) {
  const token = options?.accessToken;
  const enabled = !!batchId && (options?.enabled ?? true) && Boolean(token);
  return useQuery({
    queryKey: [...qk.ptLines(batchId, params), token ?? null],
    queryFn: async () => {
      const query = cleanParams({
        current: params.current ?? 1,
        size: params.size ?? 20,
        employeeRef: params.employeeRef,
      });
      const { data } = await api.get(`/v1/payroll/batches/${batchId}/lines`, {
        params: query,
        headers: authHeaders(token),
      });
      return unwrap<PagedResponse<OpenApiPayrollLineDto>>(data);
    },
    enabled,
  });
}

export function usePartTimePayslipsQuery(
  params: PartTimePayslipQueryParams,
  options?: { enabled?: boolean; accessToken?: string },
) {
  const token = options?.accessToken;
  const enabled = (options?.enabled ?? true) && Boolean(token);
  return useQuery({
    queryKey: [...qk.ptPayslips(params), token ?? null],
    queryFn: async () => {
      const query = cleanParams({
        employeeRef: params.employeeRef,
        period: params.period,
      });
      const { data } = await api.get('/v1/payslips', {
        params: query,
        headers: authHeaders(token),
      });
      return unwrap<OpenApiPayslipDto[]>(data);
    },
    enabled: Boolean(params.employeeRef && params.period) && enabled,
  });
}

export function usePartTimePayslipDetailQuery(
  id: string | number,
  options?: { enabled?: boolean; accessToken?: string },
) {
  const token = options?.accessToken;
  const enabled = !!id && (options?.enabled ?? true) && Boolean(token);
  return useQuery({
    queryKey: ['pt', 'payslip', id, token ?? null],
    queryFn: async () => {
      const { data } = await api.get(`/v1/payslips/${id}`, {
        headers: authHeaders(token),
      });
      return unwrap<OpenApiPayslipDto>(data);
    },
    enabled,
  });
}

export function useClientCredentialsTokenMutation() {
  return useMutation({
    mutationFn: async ({ clientId, clientSecret, scope }: ClientCredentialsRequest) => {
      const basic = encodeBasicAuth(clientId, clientSecret);
      const payload = new URLSearchParams({
        grant_type: 'client_credentials',
      });
      if (scope && scope.length > 0) {
        payload.set('scope', scope.join(' '));
      }

      const { data } = await api.post(
        '/v1/oauth/token',
        payload.toString(),
        {
          headers: {
            Authorization: `Basic ${basic}`,
            'Content-Type': 'application/x-www-form-urlencoded',
          },
        },
      );

      return unwrap<ClientCredentialsToken>(data);
    },
  });
}

// ========== PayCycle CRUD ==========

export interface PayCycleCreateParams {
  type: string;
  periodLabel: string;
  startDate?: string;
  endDate?: string;
  cutoffDate?: string;
  status?: string;
}

export interface PayCycleUpdateParams extends PayCycleCreateParams {
  id: number;
}

export async function createPayrollCycle(params: PayCycleCreateParams): Promise<PayrollCycleDto> {
  const { data } = await api.post('/payroll/cycles', params);
  return unwrap<PayrollCycleDto>(data);
}

export async function updatePayrollCycle(id: number, params: PayCycleUpdateParams): Promise<PayrollCycleDto> {
  const { data } = await api.put(`/payroll/cycles/${id}`, params);
  return unwrap<PayrollCycleDto>(data);
}

export async function deletePayrollCycle(id: number): Promise<void> {
  await api.delete(`/payroll/cycles/${id}`);
}

export function useCreatePayrollCycleMutation() {
  return useMutation({
    mutationFn: createPayrollCycle,
  });
}

export function useUpdatePayrollCycleMutation() {
  return useMutation({
    mutationFn: ({ id, params }: { id: number; params: PayCycleUpdateParams }) => updatePayrollCycle(id, params),
  });
}

export function useDeletePayrollCycleMutation() {
  return useMutation({
    mutationFn: deletePayrollCycle,
  });
}

// ========== SalaryTemplate CRUD ==========

export interface SalaryTemplateListParams {
  current?: number;
  pageSize?: number;
  type?: string;
  status?: string;
}

export interface SalaryTemplateCreateParams {
  name: string;
  type: string;
  itemsJson: string;
  taxRuleJson?: string;
  status?: string;
}

export interface SalaryTemplateUpdateParams extends SalaryTemplateCreateParams {
  id: number;
}

export interface SalaryTemplateDto {
  id?: number;
  name: string;
  type: string;
  itemsJson: string;
  taxRuleJson?: string;
  status: string;
  dataVersion?: number;
  createTime?: string;
  updateTime?: string;
}

export async function fetchSalaryTemplates(params: SalaryTemplateListParams): Promise<PagedResponse<SalaryTemplateDto>> {
  const query = cleanParams({
    page: params.current ?? 1,
    size: params.pageSize ?? 10,
    type: params.type,
    status: params.status,
  });
  const { data } = await api.get('/payroll/templates', { params: query });
  return unwrap<PagedResponse<SalaryTemplateDto>>(data);
}

export async function getSalaryTemplate(id: number): Promise<SalaryTemplateDto> {
  const { data } = await api.get(`/payroll/templates/${id}`);
  return unwrap<SalaryTemplateDto>(data);
}

export async function createSalaryTemplate(params: SalaryTemplateCreateParams): Promise<SalaryTemplateDto> {
  const { data } = await api.post('/payroll/templates', params);
  return unwrap<SalaryTemplateDto>(data);
}

export async function updateSalaryTemplate(id: number, params: SalaryTemplateUpdateParams): Promise<SalaryTemplateDto> {
  const { data } = await api.put(`/payroll/templates/${id}`, params);
  return unwrap<SalaryTemplateDto>(data);
}

export function useSalaryTemplatesQuery(params: SalaryTemplateListParams, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['salaryTemplates', params],
    queryFn: () => fetchSalaryTemplates(params),
    enabled: options?.enabled ?? true,
  });
}

export function useSalaryTemplateQuery(id: number, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['salaryTemplate', id],
    queryFn: () => getSalaryTemplate(id),
    enabled: !!id && options?.enabled !== false,
  });
}

export function useCreateSalaryTemplateMutation() {
  return useMutation({
    mutationFn: createSalaryTemplate,
  });
}

export function useUpdateSalaryTemplateMutation() {
  return useMutation({
    mutationFn: ({ id, params }: { id: number; params: SalaryTemplateUpdateParams }) =>
      updateSalaryTemplate(id, { ...params, id }),
  });
}

// ========== PayrollBatch CRUD ==========

export interface PayrollBatchListParams {
  current?: number;
  pageSize?: number;
  type?: string;
  periodLabel?: string;
  status?: string;
}

export interface PayrollBatchCreateParams {
  payCycleId?: number;
  periodLabel: string;
  type?: string;
  scopeJson?: string;
  currency?: string;
  remark?: string;
}

export interface PayrollBatchUpdateParams {
  payCycleId?: number;
  periodLabel: string;
  type?: string;
  scopeJson?: string;
  currency?: string;
  remark?: string;
}

export async function createPayrollBatch(params: PayrollBatchCreateParams): Promise<any> {
  const { data } = await api.post('/payroll/batches', params);
  return unwrap<any>(data);
}

export async function updatePayrollBatch(id: number, params: PayrollBatchUpdateParams): Promise<any> {
  const { data } = await api.put(`/payroll/batches/${id}`, params);
  return unwrap<any>(data);
}

export function useCreatePayrollBatchMutation() {
  return useMutation({
    mutationFn: createPayrollBatch,
  });
}

export function useUpdatePayrollBatchMutation() {
  return useMutation({
    mutationFn: ({ id, params }: { id: number; params: PayrollBatchUpdateParams }) =>
      updatePayrollBatch(id, params),
  });
}
