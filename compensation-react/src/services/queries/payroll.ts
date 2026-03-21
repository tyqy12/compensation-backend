import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import api, { unwrap } from '@services/api';
import type {
  PayrollPreviewDto,
  PayrollLedgerDto,
  PayrollManagerReviewDto,
  PayrollValidationIssueDto,
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
} from '../../types/openapi';
import { qk, type PageParams } from '../../types/api';

export interface PayrollManagerReviewFilters {
  department?: string;
  managerId?: number;
  keyword?: string;
}

export interface PayrollConfirmationSummaryDto {
  batchId: number;
  batchStatus?: string;
  confirmationMode?: string;
  totalLines?: number;
  pendingCount?: number;
  confirmedCount?: number;
  objectedCount?: number;
  objectedApprovedCount?: number;
  objectedRejectedCount?: number;
}

export interface PayrollPendingConfirmationDto {
  lineId: number;
  batchId?: number;
  periodLabel?: string;
  employeeId?: number;
  employeeNo?: string;
  employeeName?: string;
  department?: string;
  netAmount?: number;
  currency?: string;
  confirmationStatus?: string;
}

export interface PayrollPendingConfirmationQueryParams extends PageParams {
  current?: number;
  pageSize?: number;
  batchId?: number;
}

export interface PayrollDistributionListParams extends PageParams {
  current?: number;
  pageSize?: number;
  batchId?: number;
  batchRevision?: number;
  distributionId?: number;
  status?: string;
}

export interface PayrollReconciliationListParams extends PageParams {
  current?: number;
  pageSize?: number;
  batchId?: number;
  batchRevision?: number;
  distributionId?: number;
  taskStatus?: string;
  result?: string;
}

export interface PayslipConfirmPayload {
  signature: string;
  comment?: string;
}

export interface PayslipObjectionPayload {
  reason: string;
  comment?: string;
}

export interface PayrollBatchConfirmPayload {
  lineIds?: number[];
  signature?: string;
  comment?: string;
}

export interface PayrollAssignPayload {
  assigneeEmployeeId: number;
  lineIds?: number[];
  employeeIds?: number[];
  applyAll?: boolean;
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
  periodLabel?: string;
  // 兼容历史 URL 参数，后续可移除
  keyword?: string;
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
    Object.entries(params).filter(
      ([, value]) => value !== undefined && value !== null && value !== '',
    ),
  );

const toOptionalNumber = (value: unknown): number | undefined => {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    const next = Number(value);
    return Number.isFinite(next) ? next : undefined;
  }
  return undefined;
};

const normalizePayrollWarningText = (warning: string) => {
  const text = warning.trim();
  const lowerText = text.toLowerCase();
  if (!text) {
    return '';
  }

  if (lowerText.startsWith('missing required item:')) {
    return `缺少必填薪资项：${text.slice('missing required item:'.length).trim()}`;
  }

  if (lowerText.startsWith('item ') && lowerText.includes(' below min:')) {
    const marker = ' below min:';
    const markerIndex = lowerText.indexOf(marker);
    const itemCode = text.slice('item '.length, markerIndex).trim();
    const amount = text.slice(markerIndex + marker.length).trim();
    return `薪资项 ${itemCode} 低于最小值：${amount}`;
  }

  if (lowerText.startsWith('item ') && lowerText.includes(' above max:')) {
    const marker = ' above max:';
    const markerIndex = lowerText.indexOf(marker);
    const itemCode = text.slice('item '.length, markerIndex).trim();
    const amount = text.slice(markerIndex + marker.length).trim();
    return `薪资项 ${itemCode} 超过最大值：${amount}`;
  }

  if (lowerText.startsWith('net change exceeds threshold:')) {
    return `实发变动超过阈值：${text.slice('net change exceeds threshold:'.length).trim()}`;
  }

  if (lowerText.startsWith('no active salary template for batch type')) {
    return `批次类型 ${text.slice('no active salary template for batch type'.length).trim()} 未找到启用中的薪资模板`;
  }

  return text;
};

const parseWarningSource = (value: unknown): string[] => {
  if (Array.isArray(value)) {
    return value.filter(
      (warning): warning is string => typeof warning === 'string' && warning.trim().length > 0,
    );
  }
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (!trimmed) {
      return [];
    }
    try {
      const parsed = JSON.parse(trimmed);
      if (Array.isArray(parsed)) {
        return parsed.filter(
          (warning): warning is string => typeof warning === 'string' && warning.trim().length > 0,
        );
      }
    } catch {}
    return [trimmed];
  }
  return [];
};

const normalizeIssueSeverity = (severity?: string, blocking?: boolean) => {
  const normalized = String(severity ?? '').trim().toLowerCase();
  if (normalized === 'blocking' || blocking) {
    return 'blocking';
  }
  if (normalized === 'info') {
    return 'info';
  }
  return 'review';
};

const normalizePayrollIssueMessage = (message?: string) => {
  if (!message) {
    return undefined;
  }
  const normalized = normalizePayrollWarningText(message);
  return normalized || undefined;
};

const mapIssueRecord = (record: any): PayrollValidationIssueDto | null => {
  if (!record) {
    return null;
  }
  if (typeof record === 'string') {
    const message = normalizePayrollIssueMessage(record);
    return message
      ? {
          code: 'LEGACY_WARNING',
          severity: 'review',
          blocking: false,
          message,
        }
      : null;
  }
  const severity = normalizeIssueSeverity(record.severity, record.blocking);
  const message = normalizePayrollIssueMessage(record.message ?? record.warning ?? record.text);
  if (!message) {
    return null;
  }
  return {
    ...record,
    severity,
    blocking: severity === 'blocking',
    message,
  };
};

const parseIssueSource = (value: unknown): PayrollValidationIssueDto[] => {
  if (Array.isArray(value)) {
    return value.map((item) => mapIssueRecord(item)).filter(Boolean) as PayrollValidationIssueDto[];
  }
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (!trimmed) {
      return [];
    }
    try {
      const parsed = JSON.parse(trimmed);
      if (Array.isArray(parsed)) {
        return parsed
          .map((item) => mapIssueRecord(item))
          .filter(Boolean) as PayrollValidationIssueDto[];
      }
      const mapped = mapIssueRecord(parsed);
      return mapped ? [mapped] : [];
    } catch {}
    const mapped = mapIssueRecord(trimmed);
    return mapped ? [mapped] : [];
  }
  const mapped = mapIssueRecord(value);
  return mapped ? [mapped] : [];
};

function parsePayrollIssues(record: any): PayrollValidationIssueDto[] {
  const issues = [
    ...parseIssueSource(record?.issues),
    ...parseIssueSource(record?.issueList),
  ];
  return Array.from(
    new Map(
      issues.map((issue) => {
        const key = `${issue.code ?? 'UNKNOWN'}|${issue.message ?? ''}|${issue.itemCode ?? ''}|${issue.severity ?? ''}`;
        return [key, issue];
      }),
    ).values(),
  );
}

function parsePayrollWarnings(record: any): string[] {
  const warnings = [
    ...parseWarningSource(record?.warnings),
    ...parseWarningSource(record?.warningList),
    ...parseWarningSource(record?.warning),
    ...parsePayrollIssues(record)
      .map((issue) => issue.message)
      .filter((warning): warning is string => Boolean(warning)),
  ];
  return Array.from(
    new Set(
      warnings
        .map((warning) => normalizePayrollWarningText(warning))
        .filter((warning) => warning.length > 0),
    ),
  );
}

function normalizePayrollPreviewLike<
  T extends {
    warnings?: unknown;
    issues?: unknown;
    lines?: any[];
    blockingIssueCount?: number;
    reviewIssueCount?: number;
    totalWarnings?: number;
    hasBlockingIssues?: boolean;
  },
>(payload: T): T {
  const issues = parsePayrollIssues(payload);
  const warnings = parsePayrollWarnings(payload);

  return {
    ...payload,
    issues,
    warnings,
    blockingIssueCount: payload.blockingIssueCount ?? issues.filter((issue) => issue.severity === 'blocking').length,
    reviewIssueCount: payload.reviewIssueCount ?? issues.filter((issue) => issue.severity === 'review').length,
    totalWarnings: payload.totalWarnings ?? warnings.length,
    hasBlockingIssues:
      payload.hasBlockingIssues ?? issues.some((issue) => issue.severity === 'blocking'),
    lines: Array.isArray(payload?.lines)
      ? payload.lines.map((line: any) => {
          const lineIssues = parsePayrollIssues(line);
          const lineWarnings = parsePayrollWarnings(line);
          return {
            ...line,
            issues: lineIssues,
            warnings: lineWarnings,
            blockingIssueCount:
              line.blockingIssueCount ?? lineIssues.filter((issue) => issue.severity === 'blocking').length,
            reviewIssueCount:
              line.reviewIssueCount ?? lineIssues.filter((issue) => issue.severity === 'review').length,
            hasBlockingIssues:
              line.hasBlockingIssues ?? lineIssues.some((issue) => issue.severity === 'blocking'),
          };
        })
      : payload?.lines,
  } as T;
}

function normalizePayrollBatchRecord(record: any): PayrollBatchDetailDto {
  const payrollType = record.payrollType ?? record.type ?? record.batchType ?? record.payroll_type;
  const cycleType =
    record.cycleType ??
    record.payCycle?.cycleType ??
    record.periodType ??
    record.period_type ??
    'monthly';
  const batchStatus = String(
    record.status ?? record.batchStatus ?? record.state ?? 'draft',
  ).toLowerCase();
  const calculationStatus = String(
    record.calculationStatus ??
      record.calculation_status ??
      record.computeStatus ??
      record.calculateStatus ??
      record.compute_status ??
      batchStatus,
  ).toLowerCase();
  const warnings = parsePayrollWarnings(record);
  const createdAt =
    record.createTime ?? record.createdAt ?? record.create_time ?? record.created_at;
  const updatedAt =
    record.updateTime ?? record.updatedAt ?? record.update_time ?? record.updated_at;
  const computedAt = record.computedAt ?? record.computed_at;
  const approvedAt = record.approvedAt ?? record.approved_at;
  const paidAt = record.paidAt ?? record.paid_at;
  const batchId = toOptionalNumber(record.batchId ?? record.batch_id ?? record.id);
  const batchNo = record.batchNo ?? record.batch_no;
  const paymentBatchNo =
    record.paymentBatchNo ?? record.payment_batch_no ?? record.batchNo ?? record.batch_no;
  const totalEmployees =
    record.totalEmployees ??
    record.employeeCount ??
    record.headcount ??
    record.total_employees ??
    record.employee_count ??
    0;
  const totalLines =
    record.totalLines ?? record.lineCount ?? record.total_lines ?? record.line_count ?? 0;
  const grossTotal =
    record.grossTotal ??
    record.grossAmount ??
    record.totalGrossAmount ??
    record.gross_total ??
    record.gross_amount ??
    record.total_gross_amount ??
    null;
  const netTotal =
    record.netTotal ??
    record.netAmount ??
    record.totalNetAmount ??
    record.net_total ??
    record.net_amount ??
    record.total_net_amount ??
    null;
  const remark = record.remark ?? record.description ?? record.note ?? '';
  const paymentStatus = String(
    record.paymentStatus ??
      record.payment_status ??
      record.distributionStatus ??
      record.distribution_status ??
      (batchStatus.startsWith('pay_') || batchStatus === 'paid' ? batchStatus : ''),
  )
    .trim()
    .toLowerCase();
  const batchRevision =
    toOptionalNumber(record.batchRevision ?? record.batch_revision ?? record.runNo ?? record.run_no) ?? 1;
  const approvalWorkflowId = toOptionalNumber(
    record.approvalWorkflowId ?? record.approval_workflow_id,
  );
  const confirmationCompletedTime =
    record.confirmationCompletedTime ?? record.confirmation_completed_time;

  return {
    ...record,
    id: batchId,
    batchId,
    batchNo,
    payCycleId: toOptionalNumber(record.payCycleId ?? record.pay_cycle_id),
    type: record.type ?? payrollType,
    payrollType,
    cycleType,
    status: batchStatus,
    computeStatus: calculationStatus,
    calculationStatus,
    paymentStatus: paymentStatus || undefined,
    batchRevision,
    approvalWorkflowId,
    totalEmployees,
    totalLines,
    grossTotal,
    netTotal,
    currency: record.currency ?? 'CNY',
    warnings,
    remark,
    createdAt,
    updatedAt,
    computedAt,
    approvedAt,
    paidAt,
    scopeJson: record.scopeJson ?? record.scope_json,
    confirmationRequired: record.confirmationRequired ?? record.confirmation_required,
    confirmationMode: record.confirmationMode ?? record.confirmation_mode,
    confirmationCompletedTime,
    paymentBatchNo,
    settlementProviderCode: record.settlementProviderCode ?? record.settlement_provider_code,
  };
}

function normalizePayrollDistributionRecord(record: any): PayrollDistributionDto {
  return {
    ...record,
    id: toOptionalNumber(record.id),
    distributionNo: record.distributionNo ?? record.distribution_no,
    batchId: toOptionalNumber(record.batchId ?? record.batch_id),
    batchRevision:
      toOptionalNumber(record.batchRevision ?? record.batch_revision ?? record.runNo ?? record.run_no) ?? 1,
    periodLabel: record.periodLabel ?? record.period_label,
    payrollType: record.payrollType ?? record.payroll_type ?? record.type,
    distributionStatus: String(
      record.distributionStatus ?? record.distribution_status ?? record.status ?? '',
    )
      .trim()
      .toLowerCase() || undefined,
    totalAmount: toOptionalNumber(record.totalAmount ?? record.total_amount),
    totalCount: toOptionalNumber(record.totalCount ?? record.total_count),
    scheduledDate: record.scheduledDate ?? record.scheduled_date,
    retryLimit: toOptionalNumber(record.retryLimit ?? record.retry_limit),
    allowPartial: record.allowPartial ?? record.allow_partial,
    actualAmount: toOptionalNumber(record.actualAmount ?? record.actual_amount),
    successCount: toOptionalNumber(record.successCount ?? record.success_count),
    failedCount: toOptionalNumber(record.failedCount ?? record.failed_count),
    currentAttempt: toOptionalNumber(record.currentAttempt ?? record.current_attempt),
    approvalWorkflowId: toOptionalNumber(record.approvalWorkflowId ?? record.approval_workflow_id),
    approvalStatus: String(record.approvalStatus ?? record.approval_status ?? '')
      .trim()
      .toLowerCase() || undefined,
    approvalResult: String(record.approvalResult ?? record.approval_result ?? '')
      .trim()
      .toLowerCase() || undefined,
    approvalSubmittedAt: record.approvalSubmittedAt ?? record.approval_submitted_at,
    approvalCompletedAt: record.approvalCompletedAt ?? record.approval_completed_at,
    paymentBatchNo: record.paymentBatchNo ?? record.payment_batch_no,
    settlementProviderCode: record.settlementProviderCode ?? record.settlement_provider_code,
    reconciliationTaskId: toOptionalNumber(
      record.reconciliationTaskId ?? record.reconciliation_task_id,
    ),
    reconciliationTaskStatus: String(
      record.reconciliationTaskStatus ?? record.reconciliation_task_status ?? '',
    )
      .trim()
      .toLowerCase() || undefined,
    reconciliationResult: String(
      record.reconciliationResult ?? record.reconciliation_result ?? '',
    )
      .trim()
      .toLowerCase() || undefined,
    reconciliationDifference: toOptionalNumber(
      record.reconciliationDifference ?? record.reconciliation_difference,
    ),
    createTime: record.createTime ?? record.create_time,
    updateTime: record.updateTime ?? record.update_time,
  };
}

function normalizePayrollDistributionItemRecord(record: any): PayrollDistributionItemDto {
  return {
    ...record,
    id: toOptionalNumber(record.id),
    distributionId: toOptionalNumber(record.distributionId ?? record.distribution_id),
    employeeId: toOptionalNumber(record.employeeId ?? record.employee_id),
    lineId: toOptionalNumber(record.lineId ?? record.line_id),
    employeeName: record.employeeName ?? record.employee_name,
    recipientName: record.recipientName ?? record.recipient_name,
    accountNoMasked: record.accountNoMasked ?? record.account_no_masked,
    accountType: record.accountType ?? record.account_type,
    paymentMethod: record.paymentMethod ?? record.payment_method,
    providerCode: record.providerCode ?? record.provider_code,
    amount: toOptionalNumber(record.amount),
    itemStatus: String(record.itemStatus ?? record.item_status ?? '')
      .trim()
      .toLowerCase() || undefined,
    paymentRecordId: toOptionalNumber(record.paymentRecordId ?? record.payment_record_id),
    retryCount: toOptionalNumber(record.retryCount ?? record.retry_count),
    failureReason: record.failureReason ?? record.failure_reason,
    paymentRecordStatus: String(record.paymentRecordStatus ?? record.payment_record_status ?? '')
      .trim()
      .toLowerCase() || undefined,
    providerOrderNo: record.providerOrderNo ?? record.provider_order_no,
    providerTradeNo: record.providerTradeNo ?? record.provider_trade_no,
    errorCode: record.errorCode ?? record.error_code,
    errorMsg: record.errorMsg ?? record.error_msg,
    paymentTime: record.paymentTime ?? record.payment_time,
    createTime: record.createTime ?? record.create_time,
    updateTime: record.updateTime ?? record.update_time,
  };
}

function normalizePayrollReconciliationTaskRecord(record: any): PayrollReconciliationTaskDto {
  return {
    ...record,
    id: toOptionalNumber(record.id),
    distributionId: toOptionalNumber(record.distributionId ?? record.distribution_id),
    distributionNo: record.distributionNo ?? record.distribution_no,
    distributionStatus: String(record.distributionStatus ?? record.distribution_status ?? '')
      .trim()
      .toLowerCase() || undefined,
    batchId: toOptionalNumber(record.batchId ?? record.batch_id),
    batchRevision:
      toOptionalNumber(record.batchRevision ?? record.batch_revision ?? record.runNo ?? record.run_no) ?? 1,
    periodLabel: record.periodLabel ?? record.period_label,
    payrollType: record.payrollType ?? record.payroll_type ?? record.type,
    taskStatus: String(record.taskStatus ?? record.task_status ?? '')
      .trim()
      .toLowerCase() || undefined,
    expectedAmount: toOptionalNumber(record.expectedAmount ?? record.expected_amount),
    actualAmount: toOptionalNumber(record.actualAmount ?? record.actual_amount),
    difference: toOptionalNumber(record.difference),
    result: String(record.result ?? '')
      .trim()
      .toLowerCase() || undefined,
    differenceDetail: record.differenceDetail ?? record.difference_detail,
    createTime: record.createTime ?? record.create_time,
    updateTime: record.updateTime ?? record.update_time,
  };
}

export async function fetchPayrollBatches(params: PayrollBatchListParams) {
  const query = cleanParams({
    page: params.current ?? params.page ?? 1,
    size: params.pageSize ?? params.size ?? 10,
    type: params.payrollType,
    periodLabel: params.period,
    status: params.status,
  });
  const { data } = await api.get('/payroll/batches', { params: query });
  const raw = unwrap<PagedResponse<any>>(data);
  const sourceRecords = raw.list ?? raw.records ?? [];
  const normalizedRecords: PayrollBatchSummaryDto[] = sourceRecords.map((record: any) =>
    normalizePayrollBatchRecord(record),
  );

  return {
    ...raw,
    list: undefined,
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
      return normalizePayrollPreviewLike(unwrap<PayrollPreviewDto>(data));
    },
    enabled: !!batchId && (options?.enabled ?? true),
  });
}

export function usePayrollLedgerQuery(batchId: string | number, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: qk.payrollLedger(batchId),
    queryFn: async () => {
      const { data } = await api.get(`/payroll/batches/${batchId}/ledger`);
      return normalizePayrollPreviewLike(unwrap<PayrollLedgerDto>(data));
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
      return normalizePayrollPreviewLike(unwrap<PayrollManagerReviewDto>(data));
    },
    enabled: !!batchId && (options?.enabled ?? true),
  });
}

export function usePayrollBatchesQuery(
  params: PayrollBatchListParams,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.payrollBatches(params),
    queryFn: () => fetchPayrollBatches(params),
    enabled: options?.enabled ?? true,
  });
}

export async function fetchPayrollBatchDetail(batchId: number): Promise<PayrollBatchDetailDto> {
  const { data } = await api.get(`/payroll/batches/${batchId}`);
  const raw = unwrap<any>(data);
  return normalizePayrollBatchRecord(raw ?? {});
}

export function usePayrollBatchDetailQuery(batchId: number, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['payroll', 'batch', batchId],
    queryFn: () => fetchPayrollBatchDetail(batchId),
    enabled: !!batchId && (options?.enabled ?? true),
  });
}

export async function fetchPayrollImportItems(batchId: number): Promise<PayrollImportItemDto[]> {
  const { data } = await api.get(`/payroll/import/batches/${batchId}/items`);
  return unwrap<PayrollImportItemDto[]>(data);
}

export function usePayrollImportItemsQuery(batchId: number, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['payroll', 'import-items', batchId],
    queryFn: () => fetchPayrollImportItems(batchId),
    enabled: !!batchId && (options?.enabled ?? true),
  });
}

export async function fetchPayrollImportSalaryItems(): Promise<PayrollImportSalaryItemDto[]> {
  const { data } = await api.get('/payroll/import/salary-items');
  return unwrap<PayrollImportSalaryItemDto[]>(data);
}

export function usePayrollImportSalaryItemsQuery(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['payroll', 'import-salary-items'],
    queryFn: fetchPayrollImportSalaryItems,
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
    const itemsCount =
      record.itemsCount ?? (Array.isArray(record.items) ? record.items.length : undefined);

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
            ? Object.entries(taxParsed).map(([ruleCode, detail]) => ({
                ruleCode,
                ...(detail as object),
              }))
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
    page: params.current ?? params.page ?? 1,
    size: params.pageSize ?? params.size ?? 10,
    periodLabel: params.periodLabel ?? params.keyword,
    status: params.status,
  });
  const { data } = await api.get('/payroll/cycles', { params: query });
  const raw = unwrap<PagedResponse<any>>(data);
  const sourceRecords = raw.records ?? raw.list ?? [];
  const normalizedRecords: PayrollCycleDto[] = sourceRecords.map((record: any) => {
    const type = record.type ?? record.cycleType ?? record.periodType ?? record.cycle_type;
    const cycleTypeCandidates = ['monthly', 'semi_monthly', 'weekly', 'biweekly', 'custom'];
    const payrollTypeCandidates = ['full_time', 'part_time', 'contractor'];
    const cycleType =
      record.cycleType ??
      record.periodType ??
      record.cycle_type ??
      (cycleTypeCandidates.includes(type) ? type : undefined);
    const payrollType =
      record.payrollType ??
      record.scopeType ??
      (payrollTypeCandidates.includes(type) ? type : undefined);
    const cutoffDateValue = record.cutoffDate ?? record.cutoff_date;
    const cutoffDay =
      record.cutoffDay ?? (cutoffDateValue ? dayjs(cutoffDateValue).date() : undefined);
    const payDay =
      record.payDay ??
      record.pay_day ??
      (record.payDate ? dayjs(record.payDate).date() : undefined);
    const status = record.status ?? record.cycleStatus ?? record.cycle_status;

    return {
      ...record,
      id: record.id,
      type,
      cycleCode: record.cycleCode ?? record.cycle_code,
      cycleName: record.cycleName ?? record.cycle_name,
      payrollType,
      cycleType,
      timezone: record.timezone ?? record.tz,
      status,
      description: record.description ?? record.remark ?? null,
      cutoffDay,
      cutoffDate: cutoffDateValue,
      payDay,
      payDate: record.payDate,
      leadDays: record.leadDays ?? record.lead_days ?? record.leadTime,
      graceDays: record.graceDays ?? record.grace_days ?? record.graceTime,
      periodLabel: record.periodLabel ?? record.period,
      startDate: record.startDate,
      endDate: record.endDate,
      lastExecutionTime: record.lastExecutionTime ?? record.last_execution_time,
      nextExecutionTime: record.nextExecutionTime ?? record.next_execution_time,
      createdAt: record.createTime ?? record.createdAt,
      updatedAt: record.updateTime ?? record.updatedAt,
    };
  });

  return {
    ...raw,
    list: undefined,
    records: normalizedRecords,
  } as PagedResponse<PayrollCycleDto>;
}

export function usePayrollCyclesQuery(
  params: PayrollCycleListParams,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.payrollCycles(params),
    queryFn: () => fetchPayrollCycles(params),
    enabled: options?.enabled ?? true,
  });
}

export async function fetchPayrollConfirmationSummary(batchId: string | number) {
  const { data } = await api.get(`/payroll/confirmations/batches/${batchId}/summary`);
  return unwrap<PayrollConfirmationSummaryDto>(data);
}

export function usePayrollConfirmationSummaryQuery(
  batchId: string | number,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.payrollConfirmationSummary(batchId),
    queryFn: () => fetchPayrollConfirmationSummary(batchId),
    enabled: !!batchId && (options?.enabled ?? true),
  });
}

export async function fetchPayrollPendingConfirmations(
  params: PayrollPendingConfirmationQueryParams,
) {
  const query = cleanParams({
    page: params.current ?? params.page ?? 1,
    size: params.pageSize ?? params.size ?? 10,
    batchId: params.batchId,
  });
  const { data } = await api.get('/payroll/confirmations/pending', { params: query });
  const raw = unwrap<PagedResponse<PayrollPendingConfirmationDto>>(data);
  return {
    ...raw,
    records: raw.records ?? raw.list ?? [],
  } as PagedResponse<PayrollPendingConfirmationDto>;
}

export function usePayrollPendingConfirmationsQuery(
  params: PayrollPendingConfirmationQueryParams,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.payrollPendingConfirmations(params),
    queryFn: () => fetchPayrollPendingConfirmations(params),
    enabled: options?.enabled ?? true,
  });
}

export async function fetchPayrollDistributions(
  params: PayrollDistributionListParams,
): Promise<PagedResponse<PayrollDistributionDto>> {
  const query = cleanParams({
    page: params.current ?? params.page ?? 1,
    size: params.pageSize ?? params.size ?? 10,
    batchId: params.batchId,
    batchRevision: params.batchRevision,
    distributionId: params.distributionId,
    status: params.status,
  });
  const { data } = await api.get('/payroll/distributions', { params: query });
  const raw = unwrap<PagedResponse<any>>(data);
  const sourceRecords = raw.records ?? raw.list ?? [];
  return {
    ...raw,
    records: sourceRecords.map((record: any) => normalizePayrollDistributionRecord(record)),
    list: undefined,
  } as PagedResponse<PayrollDistributionDto>;
}

export function usePayrollDistributionsQuery(
  params: PayrollDistributionListParams,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.payrollDistributions(params),
    queryFn: () => fetchPayrollDistributions(params),
    enabled: options?.enabled ?? true,
  });
}

export async function fetchPayrollDistributionDetail(
  distributionId: number,
): Promise<PayrollDistributionDto> {
  const { data } = await api.get(`/payroll/distributions/${distributionId}`);
  return normalizePayrollDistributionRecord(unwrap<any>(data));
}

export function usePayrollDistributionDetailQuery(
  distributionId: number,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.payrollDistributionDetail(distributionId),
    queryFn: () => fetchPayrollDistributionDetail(distributionId),
    enabled: !!distributionId && (options?.enabled ?? true),
  });
}

export async function fetchPayrollDistributionItems(
  distributionId: number,
): Promise<PayrollDistributionItemDto[]> {
  const { data } = await api.get(`/payroll/distributions/${distributionId}/items`);
  return unwrap<any[]>(data).map((record: any) => normalizePayrollDistributionItemRecord(record));
}

export function usePayrollDistributionItemsQuery(
  distributionId: number,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.payrollDistributionItems(distributionId),
    queryFn: () => fetchPayrollDistributionItems(distributionId),
    enabled: !!distributionId && (options?.enabled ?? true),
  });
}

export async function fetchPayrollDistributionReconciliation(
  distributionId: number,
): Promise<PayrollReconciliationTaskDto | null> {
  const { data } = await api.get(`/payroll/distributions/${distributionId}/reconciliation`);
  const result = unwrap<any>(data);
  return result ? normalizePayrollReconciliationTaskRecord(result) : null;
}

export function usePayrollDistributionReconciliationQuery(
  distributionId: number,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.payrollDistributionReconciliation(distributionId),
    queryFn: () => fetchPayrollDistributionReconciliation(distributionId),
    enabled: !!distributionId && (options?.enabled ?? true),
  });
}

export async function fetchPayrollReconciliations(
  params: PayrollReconciliationListParams,
): Promise<PagedResponse<PayrollReconciliationTaskDto>> {
  const query = cleanParams({
    page: params.current ?? params.page ?? 1,
    size: params.pageSize ?? params.size ?? 10,
    batchId: params.batchId,
    batchRevision: params.batchRevision,
    distributionId: params.distributionId,
    taskStatus: params.taskStatus,
    result: params.result,
  });
  const { data } = await api.get('/payroll/reconciliations', { params: query });
  const raw = unwrap<PagedResponse<any>>(data);
  const sourceRecords = raw.records ?? raw.list ?? [];
  return {
    ...raw,
    records: sourceRecords.map((record: any) => normalizePayrollReconciliationTaskRecord(record)),
    list: undefined,
  } as PagedResponse<PayrollReconciliationTaskDto>;
}

export function usePayrollReconciliationsQuery(
  params: PayrollReconciliationListParams,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.payrollReconciliations(params),
    queryFn: () => fetchPayrollReconciliations(params),
    enabled: options?.enabled ?? true,
  });
}

export async function fetchPayrollReconciliationDetail(
  reconciliationTaskId: number,
): Promise<PayrollReconciliationTaskDto> {
  const { data } = await api.get(`/payroll/reconciliations/${reconciliationTaskId}`);
  return normalizePayrollReconciliationTaskRecord(unwrap<any>(data));
}

export function usePayrollReconciliationDetailQuery(
  reconciliationTaskId: number,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.payrollReconciliationDetail(reconciliationTaskId),
    queryFn: () => fetchPayrollReconciliationDetail(reconciliationTaskId),
    enabled: !!reconciliationTaskId && (options?.enabled ?? true),
  });
}

export async function confirmPayrollPayslip(lineId: number, payload: PayslipConfirmPayload) {
  const { data } = await api.post(`/payroll/confirmations/payslips/${lineId}/confirm`, payload);
  return unwrap<boolean>(data);
}

export function useConfirmPayrollPayslipMutation() {
  return useMutation({
    mutationFn: ({ lineId, payload }: { lineId: number; payload: PayslipConfirmPayload }) =>
      confirmPayrollPayslip(lineId, payload),
  });
}

export async function objectPayrollPayslip(lineId: number, payload: PayslipObjectionPayload) {
  const { data } = await api.post(`/payroll/confirmations/payslips/${lineId}/object`, payload);
  const result = unwrap<Record<string, unknown>>(data);
  return {
    workflowId: Number(result?.workflowId ?? 0),
  };
}

export function useObjectPayrollPayslipMutation() {
  return useMutation({
    mutationFn: ({ lineId, payload }: { lineId: number; payload: PayslipObjectionPayload }) =>
      objectPayrollPayslip(lineId, payload),
  });
}

export async function batchConfirmPayroll(batchId: number, payload?: PayrollBatchConfirmPayload) {
  const { data } = await api.post(
    `/payroll/confirmations/batches/${batchId}/batch-confirm`,
    payload ?? {},
  );
  const result = unwrap<Record<string, unknown>>(data);
  return {
    affected: Number(result?.affected ?? 0),
  };
}

export function useBatchConfirmPayrollMutation() {
  return useMutation({
    mutationFn: ({ batchId, payload }: { batchId: number; payload?: PayrollBatchConfirmPayload }) =>
      batchConfirmPayroll(batchId, payload),
  });
}

export async function assignPayrollConfirmation(batchId: number, payload: PayrollAssignPayload) {
  const { data } = await api.post(`/payroll/confirmations/batches/${batchId}/assign`, payload);
  const result = unwrap<Record<string, unknown>>(data);
  return {
    affected: Number(result?.affected ?? 0),
  };
}

export function useAssignPayrollConfirmationMutation() {
  return useMutation({
    mutationFn: ({ batchId, payload }: { batchId: number; payload: PayrollAssignPayload }) =>
      assignPayrollConfirmation(batchId, payload),
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

      const { data } = await api.post('/v1/oauth/token', payload.toString(), {
        headers: {
          Authorization: `Basic ${basic}`,
          'Content-Type': 'application/x-www-form-urlencoded',
        },
      });

      return unwrap<ClientCredentialsToken>(data);
    },
  });
}

// ========== PayCycle CRUD ==========

export interface PayCycleCreateParams {
  type: string;
  periodLabel: string;
  cycleCode?: string;
  cycleName?: string;
  cycleType?: string;
  startDate?: string;
  endDate?: string;
  cutoffDate?: string;
  payDay?: number;
  leadDays?: number;
  graceDays?: number;
  timezone?: string;
  description?: string;
  status?: string;
}

export interface PayCycleUpdateParams extends PayCycleCreateParams {
  id: number;
}

export async function createPayrollCycle(params: PayCycleCreateParams): Promise<PayrollCycleDto> {
  const { data } = await api.post('/payroll/cycles', params);
  return unwrap<PayrollCycleDto>(data);
}

export async function updatePayrollCycle(
  id: number,
  params: PayCycleUpdateParams,
): Promise<PayrollCycleDto> {
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
    mutationFn: ({ id, params }: { id: number; params: PayCycleUpdateParams }) =>
      updatePayrollCycle(id, params),
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

export async function fetchSalaryTemplates(
  params: SalaryTemplateListParams,
): Promise<PagedResponse<SalaryTemplateDto>> {
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

export async function createSalaryTemplate(
  params: SalaryTemplateCreateParams,
): Promise<SalaryTemplateDto> {
  const { data } = await api.post('/payroll/templates', params);
  return unwrap<SalaryTemplateDto>(data);
}

export async function updateSalaryTemplate(
  id: number,
  params: SalaryTemplateUpdateParams,
): Promise<SalaryTemplateDto> {
  const { data } = await api.put(`/payroll/templates/${id}`, params);
  return unwrap<SalaryTemplateDto>(data);
}

export function useSalaryTemplatesQuery(
  params: SalaryTemplateListParams,
  options?: { enabled?: boolean },
) {
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

export interface PayrollBatchDetailDto extends PayrollBatchSummaryDto {
  id?: number;
  payCycleId?: number;
  type?: string;
  scopeJson?: string;
  confirmationRequired?: boolean;
  confirmationMode?: string;
  confirmationCompletedTime?: string;
  calculationStatus?: string;
  batchRevision?: number;
  approvalWorkflowId?: number;
  settlementProviderCode?: string;
  paymentBatchNo?: string;
}

export interface PayrollImportItemDto {
  id: number;
  batchId?: number;
  employeeId?: number;
  employeeNo?: string;
  employeeName?: string;
  itemCode?: string;
  itemName?: string;
  itemType?: string;
  amount?: number;
  note?: string;
  sourceName?: string;
  rowNo?: number;
  status?: string;
  errorMsg?: string;
  manual?: boolean;
  createTime?: string;
  updateTime?: string;
}

export interface PayrollImportSalaryItemDto {
  code: string;
  name?: string;
  type?: string;
  taxable?: boolean;
  showOnPayslip?: boolean;
  orderNum?: number;
}

export interface PayrollManualImportItemPayload {
  employeeId?: number;
  employeeNo?: string;
  itemCode: string;
  amount: number;
  rowNo?: number;
  note?: string;
}

export interface PayrollBatchImportResult {
  importSummary?: string;
  previewGenerated?: boolean;
  totalEmployees?: number;
  earningsTotal?: number;
  netTotal?: number;
  warningsCount?: number;
  previewError?: string;
  notificationSent?: boolean;
}

const invalidatePayrollBatchQueries = async (
  queryClient: ReturnType<typeof useQueryClient>,
  batchId?: number,
) => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['payroll', 'batches'] }),
    queryClient.invalidateQueries({ queryKey: ['payroll', 'distributions'] }),
    queryClient.invalidateQueries({ queryKey: ['payroll', 'reconciliations'] }),
  ]);
  if (batchId == null) {
    return;
  }
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['payroll', 'batch', batchId] }),
    queryClient.invalidateQueries({ queryKey: ['payroll', 'ledger', batchId] }),
    queryClient.invalidateQueries({ queryKey: ['payroll', 'manager-review', batchId] }),
    queryClient.invalidateQueries({ queryKey: ['payroll', 'dry-run', batchId] }),
    queryClient.invalidateQueries({ queryKey: ['payroll', 'confirmations', 'summary', batchId] }),
    queryClient.invalidateQueries({ queryKey: ['payroll', 'import-items', batchId] }),
  ]);
  await queryClient.invalidateQueries({ queryKey: ['payroll', 'import-salary-items'] });
};

export async function createPayrollBatch(params: PayrollBatchCreateParams): Promise<any> {
  const { data } = await api.post('/payroll/batches', params);
  return unwrap<any>(data);
}

export async function updatePayrollBatch(
  id: number,
  params: PayrollBatchUpdateParams,
): Promise<any> {
  const { data } = await api.put(`/payroll/batches/${id}`, params);
  return unwrap<any>(data);
}

export async function importPayrollBatchCsv(
  batchId: number,
  file: File,
): Promise<PayrollBatchImportResult> {
  const formData = new FormData();
  formData.append('batchId', String(batchId));
  formData.append('file', file);
  const { data } = await api.post('/payroll/import/commit', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return unwrap<PayrollBatchImportResult>(data);
}

export async function lockPayrollBatch(batchId: number): Promise<boolean> {
  const { data } = await api.post(`/payroll/batches/${batchId}/lock`);
  return unwrap<boolean>(data);
}

export async function computePayrollBatch(batchId: number): Promise<boolean> {
  const { data } = await api.post(`/payroll/batches/${batchId}/compute`);
  return unwrap<boolean>(data);
}

export async function submitPayrollBatchApproval(batchId: number): Promise<boolean> {
  const { data } = await api.post(`/payroll/batches/${batchId}/submit-approval`);
  return unwrap<boolean>(data);
}

export async function retryPayrollPayment(
  batchId: number,
  triggerTransfer: boolean = true,
): Promise<any> {
  const { data } = await api.post(`/payroll/batches/${batchId}/retry-payment`, null, {
    params: { triggerTransfer },
  });
  return unwrap<any>(data);
}

export function useCreatePayrollBatchMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createPayrollBatch,
    onSuccess: async () => {
      await invalidatePayrollBatchQueries(queryClient);
    },
  });
}

export function useUpdatePayrollBatchMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, params }: { id: number; params: PayrollBatchUpdateParams }) =>
      updatePayrollBatch(id, params),
    onSuccess: async (_, variables) => {
      await invalidatePayrollBatchQueries(queryClient, variables.id);
    },
  });
}

export function useImportPayrollBatchCsvMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ batchId, file }: { batchId: number; file: File }) =>
      importPayrollBatchCsv(batchId, file),
    onSuccess: async (_, variables) => {
      await invalidatePayrollBatchQueries(queryClient, variables.batchId);
    },
  });
}

export function useLockPayrollBatchMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (batchId: number) => lockPayrollBatch(batchId),
    onSuccess: async (_, batchId) => {
      await invalidatePayrollBatchQueries(queryClient, batchId);
    },
  });
}

export function useComputePayrollBatchMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (batchId: number) => computePayrollBatch(batchId),
    onSuccess: async (_, batchId) => {
      await invalidatePayrollBatchQueries(queryClient, batchId);
    },
  });
}

export function useSubmitPayrollBatchApprovalMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (batchId: number) => submitPayrollBatchApproval(batchId),
    onSuccess: async (_, batchId) => {
      await invalidatePayrollBatchQueries(queryClient, batchId);
    },
  });
}

export function useRetryPayrollPaymentMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ batchId, triggerTransfer }: { batchId: number; triggerTransfer?: boolean }) =>
      retryPayrollPayment(batchId, triggerTransfer),
    onSuccess: async (_, variables) => {
      await invalidatePayrollBatchQueries(queryClient, variables.batchId);
    },
  });
}

export async function addPayrollManualImportItem(
  batchId: number,
  payload: PayrollManualImportItemPayload,
): Promise<PayrollImportItemDto> {
  const { data } = await api.post(`/payroll/import/batches/${batchId}/items/manual`, payload);
  return unwrap<PayrollImportItemDto>(data);
}

export async function updatePayrollImportItem(
  batchId: number,
  itemId: number,
  payload: PayrollManualImportItemPayload,
): Promise<PayrollImportItemDto> {
  const { data } = await api.put(`/payroll/import/batches/${batchId}/items/${itemId}`, payload);
  return unwrap<PayrollImportItemDto>(data);
}

export async function deletePayrollImportItem(batchId: number, itemId: number): Promise<boolean> {
  const { data } = await api.delete(`/payroll/import/batches/${batchId}/items/${itemId}`);
  return unwrap<boolean>(data);
}

export function useAddPayrollManualImportItemMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      batchId,
      payload,
    }: {
      batchId: number;
      payload: PayrollManualImportItemPayload;
    }) => addPayrollManualImportItem(batchId, payload),
    onSuccess: async (_, variables) => {
      await invalidatePayrollBatchQueries(queryClient, variables.batchId);
    },
  });
}

export function useUpdatePayrollImportItemMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      batchId,
      itemId,
      payload,
    }: {
      batchId: number;
      itemId: number;
      payload: PayrollManualImportItemPayload;
    }) => updatePayrollImportItem(batchId, itemId, payload),
    onSuccess: async (_, variables) => {
      await invalidatePayrollBatchQueries(queryClient, variables.batchId);
    },
  });
}

export function useDeletePayrollImportItemMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ batchId, itemId }: { batchId: number; itemId: number }) =>
      deletePayrollImportItem(batchId, itemId),
    onSuccess: async (_, variables) => {
      await invalidatePayrollBatchQueries(queryClient, variables.batchId);
    },
  });
}
