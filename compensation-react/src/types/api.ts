// 通用响应包裹
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

// 分页约定
export interface PageParams {
  page?: number; // 1-based
  pageSize?: number; // 默认 10/20
}

export interface Paged<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

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

/**
 * 从分页响应中获取数据列表
 * 兼容两种格式: list + pageNum/pageSize 或 records + current/size
 */
export function getPagedRecords<T>(response: PagedResponse<T> | undefined): T[] {
  if (!response) return [];
  // 优先使用 list 格式
  if (response.list && response.list.length > 0) return response.list;
  // 回退到 records 格式
  if (response.records && response.records.length > 0) return response.records;
  return [];
}

/**
 * 从分页响应中获取当前页码
 * 兼容两种格式: pageNum 或 current
 */
export function getPagedCurrent<T>(response: PagedResponse<T> | undefined): number {
  if (!response) return 1;
  return response.pageNum ?? response.current ?? 1;
}

/**
 * 从分页响应中获取每页大小
 * 兼容两种格式: pageSize 或 size
 */
export function getPagedPageSize<T>(response: PagedResponse<T> | undefined): number {
  if (!response) return 10;
  return response.pageSize ?? response.size ?? 10;
}

// 部门树
export interface DepartmentNode {
  id: number;
  provider: Platform;
  platformDeptId: string;
  parentPlatformDeptId?: string | null;
  name: string;
  children?: DepartmentNode[];
}

// 平台枚举
export type Platform = 'wechat' | 'dingtalk' | 'feishu' | 'alipay' | 'yunzhanghu' | 'sms' | 'email' | 'encryption';

// 认证与会话
export interface LoginRequest {
  username: string;
  password: string;
}
export interface UserSession {
  id: string;
  username: string;
  roles: string[];
}
export interface LoginResponse {
  accessToken: string;
  refreshToken?: string;
  user: UserSession;
}

export interface RefreshResponse {
  accessToken: string;
  refreshToken?: string;
}

// 集成配置列表项
export interface IntegrationConfigListItem {
  platformType: Platform;
  platformName: string;
  enabled: boolean;
  configured: boolean;
  connectionStatus: 'connected' | 'disconnected' | 'unknown';
  lastModified: string | null;
}

// 集成配置详情
export interface IntegrationConfigDetail {
  platformType: Platform;
  platformName: string;
  enabled: boolean;
  config: PlatformConfig;
  connectionStatus: 'connected' | 'disconnected' | 'unknown';
  lastModified: string | null;
}

// 各平台配置格式
export interface WechatConfig {
  corpId: string;
  corpSecret: string;
  agentId?: string;
}

export interface DingtalkConfig {
  appKey: string;
  appSecret: string;
}

export interface FeishuConfig {
  appId: string;
  appSecret: string;
}

export interface AlipayConfig {
  appId: string;
  privateKey: string;
  publicKey?: string;
  serverUrl?: string;
  charset?: string;
  signType?: string;
  format?: string;
  notifyUrl?: string;
  returnUrl?: string;
}

export interface YunzhanghuConfig {
  dealerId: string;
  brokerId: string;
  appKey: string;
  des3Key: string;
  rsaPrivateKey: string;
  rsaPublicKey: string;
  signType: 'rsa' | 'sha256' | string;
  url: string;
  notifyUrl?: string;
  projectId?: string;
  checkName?: string;
  dealerPlatformName?: string;
  isDebug?: boolean;
}

export interface SmsConfig {
  provider: 'aliyun' | 'tencent' | 'huawei' | 'mock';
  accessKeyId?: string;
  accessKeySecret?: string;
  signName?: string;
  templateCode?: string;
  dailyLimit?: number;
  rateLimitPerMinute?: number;
}

export interface EmailConfig {
  host: string;
  port?: number;
  username: string;
  password?: string;
  fromAddress?: string;
  fromName?: string;
  ssl?: boolean;
  tls?: boolean;
  encoding?: string;
}

export interface EncryptionConfig {
  aesKey?: string;
  sm4Key?: string;
  algorithm?: string;
  keyDerivation?: string;
  keyRotationDays?: number;
}

export type PlatformConfig =
  | WechatConfig
  | DingtalkConfig
  | FeishuConfig
  | AlipayConfig
  | YunzhanghuConfig
  | SmsConfig
  | EmailConfig
  | EncryptionConfig;

// 保存配置请求
export interface SaveConfigRequest {
  enabled: boolean;
  wechat?: WechatConfig;
  dingtalk?: DingtalkConfig;
  feishu?: FeishuConfig;
  alipay?: AlipayConfig;
  yunzhanghu?: YunzhanghuConfig;
  sms?: SmsConfig;
  email?: EmailConfig;
  encryption?: EncryptionConfig;
}

// 向后兼容的配置接口
export interface IntegrationConfig {
  platform: Platform;
  clientId: string;
  clientSecret: string;
  callbackUrl?: string;
  extra?: Record<string, unknown>;
  enabled?: boolean;
  lastTestedAt?: string;
  createdAt?: string;
}

export interface TestConnectionResult {
  success: boolean;
  message?: string;
  detail?: string;
}

// Dashboard DTOs
export interface DashboardMetrics {
  employeeTotal: number;
  employeeGrowthRate: number;
  monthlyPaymentAmount: number;
  monthlyPaymentGrowthRate: number;
  pendingBatchCount: number;
  pendingBatchChangeRate: number;
  userBindingRate: number;
  userBindingGrowthRate: number;
}

export interface SystemComponentStatus {
  name: string;
  runRate: number;
  status: string;
}

export interface SystemStatus {
  overallStatus: string;
  components: SystemComponentStatus[];
}

export interface TodoItem {
  title: string;
  priority: string;
  due: string;
}

export interface ActivityItem {
  actor?: string;
  initial?: string;
  description: string;
  timeAgo: string;
}

// 组织同步/平台
export interface PlatformOption {
  code: Platform | string;
  name: string;
  status?: string | null;
  configured?: boolean;
}
export interface OrgCheckResult {
  platform: Platform | string;
  status: string;
  message?: string | null;
  detail?: string | null;
}

export interface OrgSyncHistoryItem {
  id?: string | number;
  provider?: Platform | string;
  success: boolean;
  message?: string | null;
  syncTime: string;
  totalEmployees?: number;
  newEmployees?: number;
  updatedEmployees?: number;
  inactiveEmployees?: number;
  errors?: string[] | null;
  operation?: string | null;
  durationMs?: number | null;
}

// 新增：手动同步相关类型
export interface EmployeePreviewDto {
  provider?: Platform | string;
  subjectId?: string;
  employeeId: string;
  name: string;
  phone?: string;
  email?: string;
  department?: string;
  departments?: string[];
  position?: string;
  employmentType: 'full_time' | 'part_time';
  alreadyImported?: boolean;
  existingEmployeeDbId?: number;
  existingEmployeeNo?: string;
  importAction?: 'CREATE' | 'UPDATE' | string;
  rowKey?: string;
  raw?: Record<string, unknown>;
}

export interface OrgFetchPreviewResponse {
  provider?: Platform | string;
  totalEmployees: number;
  newEmployees?: number;
  existingEmployees?: number;
  employees: EmployeePreviewDto[];
  raw?: Record<string, unknown>;
}

export interface OrgImportRequestItem {
  employeeId: string;
  name: string;
  phone?: string;
  email?: string;
  department?: string;
  position?: string;
  employmentType: 'full_time' | 'part_time';
  provider?: Platform | string;
  subjectId?: string;
  username?: string; // 偏好用户名
}

export interface OrgImportRequest {
  provider?: Platform | string;
  importMode?: 'new_only' | 'upsert' | string;
  items: OrgImportRequestItem[];
  metadata?: Record<string, unknown>;
}

export interface OrgImportResponse {
  success: number;
  created?: number;
  updated?: number;
  skipped?: number;
  importMode?: string;
  upsertMode?: boolean;
  skippedItems?: string[];
  failed: number;
  errors: string[];
}

// 员工
export interface EmployeeListItem {
  id: string | number;
  name: string;
  department?: string;
}
export interface EmployeeDetail extends EmployeeListItem {
  title?: string;
  email?: string;
}

// 支付批次
export interface PaymentBatch {
  batchNo: string;
  recordCount: number;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  createdAt?: string;
  updatedAt?: string;
}

export interface PaymentRecord {
  id: string | number;
  employeeId: string | number;
  amount: number;
  status: 'PENDING' | 'SUCCESS' | 'FAILED';
  message?: string;
}

// 用户绑定
export interface UserBindingItem {
  id: number;
  username: string;
  provider?: Platform | null;
  subjectId?: string | null;
  email?: string | null;
  phone?: string | null;
  bound: boolean;
  createTime?: string;
  updateTime?: string;
  employeeId?: number | null;
  employeeName?: string | null;
}

// RBAC / Resources
export type ResourceType = 'MENU' | 'VIEW' | 'ACTION' | 'API';

export interface SysResource {
  id: number;
  type: ResourceType;
  code: string; // unique resource code, e.g., dashboard, api.payment.batch.start
  name: string;
  path?: string | null; // frontend route path for MENU/VIEW
  component?: string | null;
  icon?: string | null;
  parentId?: number | null;
  parentCode?: string | null; // 父资源代码（导入时使用）
  orderNum?: number | null;
  meta?: Record<string, unknown> | null; // 后端返回的 meta 对象（从 props_json 解析）
  status?: 'enabled' | 'disabled' | number | null;
  _children?: number[]; // 子资源ID列表（树结构中）
}

export interface MeResourcesData {
  resources: SysResource[]; // flat list of visible MENU/VIEW resources
  actions: Record<string, string[]>; // resourceId -> [actionCode]
  permissionVersion: number; // for cache control
}

export type MeActionsData = string[]; // flattened action codes for user

// ==================== 角色管理类型定义 ====================

/**
 * 角色类型枚举
 */
export type RoleType = 'SYSTEM' | 'BUSINESS' | 'CUSTOM';

/**
 * 角色状态枚举
 */
export type RoleStatus = 'enabled' | 'disabled';

/**
 * 角色基础信息
 */
export interface RoleInfo {
  id: number;
  code: string;
  name: string;
  description?: string | null;
  roleType: RoleType;
  roleTypeDisplayName: string;
  sortOrder: number;
  isEditable: boolean;
  isProtected: boolean;
  icon?: string | null;
  status: RoleStatus;
  statusDisplayName: string;
  remarks?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/**
 * 角色详情（包含统计信息）
 */
export interface RoleDetail extends RoleInfo {
  userCount: number;
  resourceCount: number;
  resources?: RoleResourceBrief[];
}

/**
 * 角色关联的资源简要信息
 */
export interface RoleResourceBrief {
  id: number;
  code: string;
  name: string;
  type: ResourceType;
  actions: string[];
}

/**
 * 创建角色请求
 */
export interface CreateRoleRequest {
  code: string;
  name: string;
  description?: string;
  roleType?: RoleType; // 默认 BUSINESS
  sortOrder?: number; // 默认 0
  icon?: string;
  remarks?: string;
  resourceIds?: number[]; // 可选：初始分配的资源ID
  actions?: string[]; // 可选：初始分配的操作权限
}

/**
 * 更新角色请求
 */
export interface UpdateRoleRequest {
  name?: string;
  description?: string;
  roleType?: RoleType;
  sortOrder?: number;
  icon?: string;
  status?: RoleStatus;
  isEditable?: boolean;
  remarks?: string;
}

/**
 * 资源分配项
 */
export interface ResourceAssignment {
  resourceId: number;
  actions?: string[]; // 为空或 ["*"] 表示所有权限
}

/**
 * 角色资源分配请求
 */
export interface RoleResourceAssignRequest {
  resources?: ResourceAssignment[];
  replaceExisting?: boolean; // 默认 true
}

/**
 * 复制角色请求
 */
export interface CopyRoleRequest {
  newCode: string;
  newName: string;
}

/**
 * 角色列表查询参数
 */
export interface RoleListParams {
  keyword?: string;
  roleType?: string;
  status?: string;
}

// TanStack Query Keys
export const qk = {
  integration: (platform: Platform) => ['integration', platform] as const,
  integrationList: ['integration', 'list'] as const,
  integrationConfig: (platform: Platform) => ['integration', 'config', platform] as const,
  platforms: ['org', 'platforms'] as const,
  orgCheck: (platform: Platform) => ['org', 'check', platform] as const,
  employees: (params: PageParams & Record<string, unknown>) => ['employees', params] as const,
  employeeDetail: (id: string | number) => ['employee', id] as const,
  paymentBatch: (batchNo: string) => ['paymentBatch', batchNo] as const,
  paymentRecords: (batchNo: string) => ['paymentBatch', batchNo, 'records'] as const,
  dashboardMetrics: ['dashboard', 'metrics'] as const,
  dashboardStatus: ['dashboard', 'status'] as const,
  dashboardTodos: ['dashboard', 'todos'] as const,
  dashboardActivities: ['dashboard', 'activities'] as const,
  orgHistory: ['org', 'history'] as const,
  orgDepartments: (platform: Platform) => ['org', 'departments', platform] as const,
  bindings: (userId: string) => ['bindings', userId] as const,
  payrollDryRun: (
    batchId: string | number,
    payload?: Record<string, unknown>,
  ) => ['payroll', 'dry-run', batchId, payload ?? null] as const,
  payrollLedger: (batchId: string | number) => ['payroll', 'ledger', batchId] as const,
  payrollManagerReview: (
    batchId: string | number,
    filters: { department?: string; managerId?: number; keyword?: string },
  ) => ['payroll', 'manager-review', batchId, filters] as const,
  payrollBatches: (params: Record<string, unknown>) => ['payroll', 'batches', params] as const,
  payrollTemplates: (params: Record<string, unknown>) => ['payroll', 'templates', params] as const,
  payrollTemplateDetail: (id: string | number) => ['payroll', 'templates', id] as const,
  payrollCycles: (params: Record<string, unknown>) => ['payroll', 'cycles', params] as const,
  payrollConfirmationSummary: (batchId: string | number) => ['payroll', 'confirmations', 'summary', batchId] as const,
  payrollPendingConfirmations: (params: Record<string, unknown>) =>
    ['payroll', 'confirmations', 'pending', params] as const,
  payrollDistributions: (params: Record<string, unknown>) =>
    ['payroll', 'distributions', params] as const,
  payrollDistributionDetail: (distributionId: string | number) =>
    ['payroll', 'distributions', distributionId] as const,
  payrollDistributionItems: (distributionId: string | number) =>
    ['payroll', 'distributions', distributionId, 'items'] as const,
  payrollDistributionReconciliation: (distributionId: string | number) =>
    ['payroll', 'distributions', distributionId, 'reconciliation'] as const,
  payrollReconciliations: (params: Record<string, unknown>) =>
    ['payroll', 'reconciliations', params] as const,
  payrollReconciliationDetail: (taskId: string | number) =>
    ['payroll', 'reconciliations', taskId] as const,
  ptBatches: (params: Record<string, unknown>) => ['pt', 'payroll', 'batches', params] as const,
  ptLines: (batchId: string | number, params: Record<string, unknown>) =>
    ['pt', 'payroll', 'batches', batchId, 'lines', params] as const,
  ptPayslips: (params: Record<string, unknown>) => ['pt', 'payslips', params] as const,
  appRegistryList: (params: Record<string, unknown> | undefined) => ['admin', 'app-registry', params] as const,
  appRegistryDetail: (id: string | number) => ['admin', 'app-registry', id] as const,
  // 角色管理 Query Keys
  roleList: (params?: { keyword?: string; roleType?: string; status?: string }) =>
    ['admin', 'roles', 'list', params] as const,
  roleDetail: (id: string | number) => ['admin', 'roles', 'detail', id] as const,
  roleResources: (id: string | number) => ['admin', 'roles', 'resources', id] as const,
  roleEnabledList: ['admin', 'roles', 'enabled'] as const,
};
