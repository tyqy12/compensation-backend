// Auto-derived minimal types from Apifox OAS (curated for used endpoints)
// Source: apifox components/schemas

// Generic API wrapper
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

// Employee
export interface EmployeeVO {
  id?: number;
  employeeId?: string;
  name?: string;
  phoneMasked?: string;
  email?: string;
  department?: string; // 展示用主部门
  departments?: string[]; // 多部门名称数组
  position?: string;
  provider?: string;
  subjectId?: string;
  managerId?: number;
  managerName?: string;
  hireDate?: string; // date
  status?: string; // active | inactive | suspended
  settlementAccountType?: 'bank_card' | 'alipay' | 'wechat' | 'other';
  settlementAccountTypeName?: string;
  settlementAccountMasked?: string;
  settlementAccountName?: string;
  bankAccountMasked?: string;
  bankName?: string;
  bankBranchName?: string;
  offline?: boolean; // 是否架构外员工
  employmentType?: 'full_time' | 'part_time';
  createTime?: string; // date-time
  updateTime?: string; // date-time
}

export interface EmployeeCreateRequest {
  employeeId: string;
  name: string;
  phone?: string;
  email?: string;
  idCard?: string;
  department?: string; // 展示用主部门
  departments?: string[]; // 多部门数组
  position?: string;
  provider?: string; // 推荐字段：平台类型（wechat|dingtalk|feishu）
  subjectId?: string; // 推荐字段：平台主体ID
  managerId?: number;
  hireDate?: string; // date
  status?: string;
  settlementAccountType?: 'bank_card' | 'alipay' | 'wechat' | 'other';
  settlementAccount?: string;
  settlementAccountName?: string;
  bankAccount?: string;
  bankName?: string;
  bankBranchName?: string;
  offline?: boolean; // 是否架构外员工
  // New optional fields after backend upgrade
  employmentType?: 'full_time' | 'part_time';
  username?: string;
}

export interface EmployeeUpdateRequest {
  name?: string;
  phone?: string;
  email?: string;
  idCard?: string;
  department?: string; // 展示用主部门
  departments?: string[]; // 多部门数组
  position?: string;
  hireDate?: string; // date
  managerId?: number;
  status?: string;
  settlementAccountType?: 'bank_card' | 'alipay' | 'wechat' | 'other';
  settlementAccount?: string;
  settlementAccountName?: string;
  bankAccount?: string;
  bankName?: string;
  bankBranchName?: string;
  employmentType?: 'full_time' | 'part_time';
  offline?: boolean; // 是否架构外员工
}

export interface UpdateStatusRequest {
  status: string;
}

export interface BindPlatformRequest {
  provider?: string;
  subjectId?: string;
  forceBind?: boolean;
}

export interface BatchImportRequest {
  employees: EmployeeCreateRequest[];
}

// Payment
export interface PaymentBatchVO {
  id?: number;
  batchNo?: string;
  batchName?: string;
  paymentType?: string;
  totalAmount?: number;
  totalCount?: number;
  successCount?: number;
  failedCount?: number;
  status?: string;
  remark?: string;
  submitTime?: string; // date-time
  approveTime?: string; // date-time
  processStartTime?: string; // date-time
  processEndTime?: string; // date-time
}

export interface PaymentRecordItemVO {
  id?: number;
  batchNo?: string;
  employeeId?: number;
  employeeNo?: string;
  employeeName?: string;
  paymentType?: string;
  paymentMethod?: string;
  amount?: number;
  currency?: string;
  recipientName?: string;
  recipientAccountMasked?: string;
  status?: string;
  alipayOrderNo?: string;
  alipayTradeNo?: string;
  providerCode?: string;
  providerOrderNo?: string;
  providerTradeNo?: string;
  errorCode?: string;
  errorMsg?: string;
  paymentTime?: string; // date-time
  notificationTime?: string; // date-time
  createTime?: string; // date-time
  updateTime?: string; // date-time
}

// Common helpers for paged structures used in code
export interface PagedResponse<T> {
  total: number;
  // 格式1: list + pageNum + pageSize (如员工列表)
  list?: T[];
  pageNum?: number;
  // 格式2: records + current + size (如薪资模板)
  records?: T[];
  current?: number;
  size?: number;
  pageSize?: number;
  totalPages?: number;
  hasNextPage?: boolean;
  hasPreviousPage?: boolean;
  offset?: number;
}

// Payroll preview & ledger DTOs
export interface PayrollPreviewItemDto {
  itemCode?: string;
  itemName?: string;
  amount?: number;
  category?: 'earning' | 'deduction' | 'tax' | 'social';
  showOnPayslip?: boolean;
  order?: number;
  code?: string;
  name?: string;
  type?: 'earning' | 'deduction' | string;
  taxable?: boolean;
}

export interface PayrollValidationIssueDto {
  code?: string;
  severity?: 'blocking' | 'review' | 'info' | string;
  blocking?: boolean;
  message?: string;
  itemCode?: string;
  currentValue?: string;
  expectedValue?: string;
}

export interface PayrollPreviewLineDto {
  lineId?: number;
  employeeId?: number;
  employeeNo?: string;
  employeeName?: string;
  department?: string;
  departments?: string[];
  managerId?: number;
  managerName?: string;
  employmentType?: string;
  currency?: string;
  grossAmount?: number;
  earningsAmount?: number;
  earningsTotal?: number;
  deductionsAmount?: number;
  deductionsTotal?: number;
  taxAmount?: number;
  socialAmount?: number;
  netAmount?: number;
  adjustmentsTotal?: number;
  warnings?: string[];
  issues?: PayrollValidationIssueDto[];
  blockingIssueCount?: number;
  reviewIssueCount?: number;
  hasBlockingIssues?: boolean;
  missingItems?: string[];
  differences?: string[];
  diff?: {
    lastGrossAmount?: number;
    lastNetAmount?: number;
    netDeltaAmount?: number;
    netDeltaPercent?: number;
  };
  items?: PayrollPreviewItemDto[];
  lastUpdatedAt?: string;
}

interface PayrollSummaryBase {
  batchId?: number;
  status?: string;
  periodLabel?: string;
  currency?: string;
  totalEmployees?: number;
  earningsTotal?: number;
  deductionsTotal?: number;
  grossTotal?: number;
  taxTotal?: number;
  socialTotal?: number;
  netTotal?: number;
  linesWithWarnings?: number;
  linesWithBlockingIssues?: number;
  totalWarnings?: number;
  blockingIssueCount?: number;
  reviewIssueCount?: number;
  hasBlockingIssues?: boolean;
  warnings?: string[];
  issues?: PayrollValidationIssueDto[];
}

export interface PayrollPreviewDto extends PayrollSummaryBase {
  lines?: PayrollPreviewLineDto[];
}

export interface PayrollLedgerDto extends PayrollSummaryBase {
  lines?: PayrollPreviewLineDto[];
}

export interface PayrollManagerReviewDto extends PayrollSummaryBase {
  department?: string | null;
  managerId?: number | null;
  keyword?: string | null;
  lines?: PayrollPreviewLineDto[];
}

export interface PayrollBatchSummaryDto {
  batchId?: number;
  batchNo?: string;
  payCycle?: string;
  cycleType?: string;
  payrollType?: string;
  periodLabel?: string;
  status?: string;
  computeStatus?: string;
  calculationStatus?: string;
  approvalStatus?: string;
  approvalWorkflowId?: number;
  paymentStatus?: string;
  paymentBatchNo?: string;
  batchRevision?: number;
  confirmationRequired?: boolean;
  confirmationMode?: string;
  confirmationCompletedTime?: string;
  settlementProviderCode?: string;
  totalEmployees?: number;
  totalLines?: number;
  grossTotal?: number;
  netTotal?: number;
  currency?: string;
  warnings?: string[];
  createdAt?: string;
  updatedAt?: string;
  computedAt?: string;
  approvedAt?: string;
  paidAt?: string;
  remark?: string;
}

export interface PayrollDistributionDto {
  id?: number;
  distributionNo?: string;
  batchId?: number;
  batchRevision?: number;
  periodLabel?: string;
  payrollType?: string;
  distributionStatus?: string;
  totalAmount?: number;
  totalCount?: number;
  scheduledDate?: string;
  retryLimit?: number;
  allowPartial?: boolean;
  actualAmount?: number;
  successCount?: number;
  failedCount?: number;
  currentAttempt?: number;
  approvalWorkflowId?: number;
  approvalStatus?: string;
  approvalResult?: string;
  approvalSubmittedAt?: string;
  approvalCompletedAt?: string;
  paymentBatchNo?: string;
  settlementProviderCode?: string;
  reconciliationTaskId?: number;
  reconciliationTaskStatus?: string;
  reconciliationResult?: string;
  reconciliationDifference?: number;
  createTime?: string;
  updateTime?: string;
}

export interface PayrollDistributionItemDto {
  id?: number;
  distributionId?: number;
  employeeId?: number;
  lineId?: number;
  employeeName?: string;
  recipientName?: string;
  accountNoMasked?: string;
  accountType?: string;
  paymentMethod?: string;
  providerCode?: string;
  amount?: number;
  itemStatus?: string;
  paymentRecordId?: number;
  retryCount?: number;
  failureReason?: string;
  paymentRecordStatus?: string;
  providerOrderNo?: string;
  providerTradeNo?: string;
  errorCode?: string;
  errorMsg?: string;
  paymentTime?: string;
  createTime?: string;
  updateTime?: string;
}

export interface PayrollReconciliationTaskDto {
  id?: number;
  distributionId?: number;
  distributionNo?: string;
  distributionStatus?: string;
  batchId?: number;
  batchRevision?: number;
  periodLabel?: string;
  payrollType?: string;
  taskStatus?: string;
  expectedAmount?: number;
  actualAmount?: number;
  difference?: number;
  result?: string;
  differenceDetail?: string;
  createTime?: string;
  updateTime?: string;
}

export interface PayrollTemplateDto {
  id?: number;
  templateCode?: string;
  templateName?: string;
  payrollType?: string;
  cycleType?: string;
  version?: number;
  status?: string;
  description?: string | null;
  defaultFlag?: boolean;
  itemsCount?: number;
  lastPublishedAt?: string;
  updatedAt?: string;
  createdAt?: string;
}

export interface PayrollTemplateDetailDto extends PayrollTemplateDto {
  items?: Array<{
    itemCode?: string;
    itemName?: string;
    category?: string;
    amountType?: string;
    formula?: string;
    showOnPayslip?: boolean;
    order?: number;
  }>;
  taxRules?: Array<{
    ruleCode?: string;
    ruleName?: string;
    threshold?: number;
    rate?: number;
    applyOn?: string;
    mode?: string;
    scale?: number;
  }>;
  itemsJson?: string;
  taxRuleJson?: string;
}

export interface PayrollCycleDto {
  id?: number;
  type?: string;
  cycleCode?: string;
  cycleName?: string;
  payrollType?: string;
  cycleType?: string;
  calendarType?: string;
  timezone?: string;
  status?: string;
  description?: string | null;
  cutoffDay?: number;
  cutoffDate?: string;
  payDay?: number;
  payDate?: string;
  leadDays?: number;
  graceDays?: number;
  periodLabel?: string;
  startDate?: string;
  endDate?: string;
  lastExecutionTime?: string;
  nextExecutionTime?: string;
  createdAt?: string;
  updatedAt?: string;
}

// PT Open API DTOs
export interface OpenApiPayrollBatchDto {
  id?: number;
  batchId?: number;
  batchNo?: string;
  type?: string;
  status?: string;
  periodLabel?: string;
  lineCount?: number;
  paidAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface OpenApiPayrollLineDto {
  id?: number;
  batchId?: number;
  lineNo?: string;
  employeeRef?: string;
  employeeNameMasked?: string;
  phoneMasked?: string;
  departments?: string[];
  grossAmount?: number;
  earningsAmount?: number;
  deductionsAmount?: number;
  taxAmount?: number;
  socialAmount?: number;
  netAmount?: number;
  currency?: string;
  updatedAt?: string;
  warnings?: string[];
}

export interface OpenApiPayslipItemDto {
  itemCode?: string;
  itemName?: string;
  amount?: number;
  showOnPayslip?: boolean;
  order?: number;
}

export interface OpenApiPayslipDto {
  id?: number;
  payslipNo?: string;
  employeeRef?: string;
  period?: string;
  currency?: string;
  grossAmount?: number;
  taxAmount?: number;
  socialAmount?: number;
  netAmount?: number;
  departments?: string[];
  issuedAt?: string;
  updatedAt?: string;
  items?: OpenApiPayslipItemDto[];
}

// App registry DTOs
export interface AppRegistryDto {
  id: number;
  appName: string;
  clientId: string;
  scopes: string[];
  status: 'enabled' | 'disabled';
  description?: string | null;
  ipWhitelist?: string[] | null;
  webhookUrl?: string | null;
  createTime?: string;  // 与后端字段名保持一致
  updateTime?: string;  // 与后端字段名保持一致
  lastUsedAt?: string | null;
}

export interface AppRegistrySecretDto {
  clientId: string;
  clientSecret: string;
  expiresIn?: number;
}

export interface AppRegistryRequest {
  appName: string;
  scopes: string[];
  status?: 'enabled' | 'disabled';
  description?: string | null;
  ipWhitelist?: string[];
  webhookUrl?: string | null;
}
