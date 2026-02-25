import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api, { unwrap } from '@services/api';
import type { PageParams } from '@/types/api';
import type { PagedResponse } from '@/types/openapi';

// 审计日志数据类型定义
export interface AuditLog {
  id: number;
  userId: number;
  username: string;
  operation: string;
  method: string;
  requestUrl: string;
  requestIp: string;
  userAgent: string;
  requestParams: string;
  responseResult: string;
  errorMsg: string;
  executionTime: number;
  businessType: string;
  businessKey: string;
  createTime: string;
}

// 查询参数
export interface AuditLogQueryParams extends PageParams {
  username?: string;
  operation?: string;
  businessType?: string;
  businessKey?: string;
  responseResult?: string;
  startTime?: string;
  endTime?: string;
  keyword?: string;
}

// 审计日志列表响应
export interface AuditLogListResponse {
  records: AuditLog[];
  total: number;
  current: number;
  size: number;
}

// 今日登录统计
export interface TodayLoginStats {
  successCount: number;
  failedCount: number;
  totalCount: number;
  successRate: string;
}

// 统计摘要
export interface AuditSummary {
  operationStats: Record<string, number>;
  businessTypeStats: Record<string, number>;
  resultStats: Record<string, number>;
  totalCount: number;
  successCount: number;
  failedCount: number;
  successRate: string;
}

// 操作类型统计
export interface OperationStat {
  operation: string;
  count: number;
}

// 登录失败监控
export interface LoginFailureCount {
  [username: string]: number;
}

// ==================== 查询接口 ====================

/**
 * 分页查询审计日志列表
 */
export function useAuditLogsQuery(
  params: AuditLogQueryParams,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: ['auditLogs', params],
    queryFn: async () => {
      const queryParams: Record<string, unknown> = {
        current: params.page || 1,
        pageSize: params.pageSize || 10,
        username: params.username,
        operation: params.operation,
        businessType: params.businessType,
        businessKey: params.businessKey,
        responseResult: params.responseResult,
        startTime: params.startTime,
        endTime: params.endTime,
        keyword: params.keyword,
      };

      // 移除空值参数
      const cleanParams = Object.fromEntries(
        Object.entries(queryParams).filter(([, value]) => value !== undefined && value !== ''),
      );

      const { data } = await api.get('/admin/audit-logs', { params: cleanParams });
      return unwrap<AuditLogListResponse>(data);
    },
    enabled: options?.enabled,
  });
}

/**
 * 查询审计日志详情
 */
export function useAuditLogDetailQuery(
  id: number,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: ['auditLog', id],
    queryFn: async () => {
      const { data } = await api.get(`/admin/audit-logs/${id}`);
      return unwrap<AuditLog>(data);
    },
    enabled: !!id && options?.enabled !== false,
  });
}

// ==================== 辅助函数 ====================

/**
 * 获取操作类型显示信息
 */
export function getOperationTypeInfo(operation: string) {
  // 兼容旧的操作类型
  const legacyMapping: Record<string, string> = {
    'LOGIN_PASSWORD': '用户登录',
    'LOGIN_OAUTH_WECOM': '企业微信登录',
    'LOGIN_OAUTH_DINGTALK': 'dingtalk登录',
    'LOGIN_OAUTH_FEISHU': '飞书登录',
    'LOGIN_OAUTH': 'OAuth登录',
  };

  // 先尝试直接匹配
  if (legacyMapping[operation]) {
    operation = legacyMapping[operation];
  }

  const operationMap: Record<string, { text: string; color: string }> = {
    '用户登录': { text: '用户登录', color: 'purple' },
    '企业微信登录': { text: '企业微信登录', color: 'cyan' },
    'dingtalk登录': { text: '钉钉登录', color: 'blue' },
    '飞书登录': { text: '飞书登录', color: 'green' },
    'OAuth登录': { text: 'OAuth登录', color: 'orange' },
    CREATE: { text: '创建', color: 'green' },
    UPDATE: { text: '更新', color: 'blue' },
    DELETE: { text: '删除', color: 'red' },
    QUERY: { text: '查询', color: 'default' },
    LOGIN: { text: '登录', color: 'purple' },
    LOGOUT: { text: '登出', color: 'orange' },
    EXPORT: { text: '导出', color: 'cyan' },
    IMPORT: { text: '导入', color: 'gold' },
    APPROVE: { text: '审批', color: 'green' },
    REJECT: { text: '拒绝', color: 'red' },
    PAYMENT: { text: '支付', color: 'magenta' },
  };
  return operationMap[operation] || operationMap[operation.toUpperCase()] || { text: operation, color: 'default' };
}

/**
 * 获取业务类型显示信息
 */
export function getBusinessTypeInfo(businessType: string) {
  const typeMap: Record<string, { text: string; color: string }> = {
    employee: { text: '员工管理', color: 'blue' },
    payroll: { text: '薪酬管理', color: 'green' },
    payment: { text: '支付管理', color: 'purple' },
    approval: { text: '审批流程', color: 'orange' },
    system: { text: '系统配置', color: 'cyan' },
    auth: { text: '权限管理', color: 'magenta' },
  };
  return typeMap[businessType.toLowerCase()] || { text: businessType, color: 'default' };
}

/**
 * 格式化执行时间
 */
export function formatExecutionTime(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`;
  return `${(ms / 60000).toFixed(2)}min`;
}

/**
 * 格式化请求方法颜色
 */
export function getMethodColor(method: string): string {
  const colorMap: Record<string, string> = {
    GET: 'blue',
    POST: 'green',
    PUT: 'orange',
    DELETE: 'red',
    PATCH: 'purple',
  };
  return colorMap[method.toUpperCase()] || 'default';
}

// ==================== 统计查询接口 ====================

/**
 * 获取今日登录统计
 */
export function useTodayLoginStatsQuery() {
  return useQuery({
    queryKey: ['auditLogs', 'todayLoginStats'],
    queryFn: async () => {
      const { data } = await api.get('/admin/audit-logs/stats/today-login');
      return unwrap<TodayLoginStats>(data);
    },
  });
}

/**
 * 获取今日统计摘要
 */
export function useAuditSummaryQuery() {
  return useQuery({
    queryKey: ['auditLogs', 'summary'],
    queryFn: async () => {
      const { data } = await api.get('/admin/audit-logs/stats/summary');
      return unwrap<AuditSummary>(data);
    },
  });
}

/**
 * 获取操作类型统计
 */
export function useOperationStatsQuery(startTime?: string, endTime?: string) {
  return useQuery({
    queryKey: ['auditLogs', 'operationStats', startTime, endTime],
    queryFn: async () => {
      const params: Record<string, string> = {};
      if (startTime) params.startTime = startTime;
      if (endTime) params.endTime = endTime;
      const { data } = await api.get('/admin/audit-logs/stats/operations', { params });
      return unwrap<OperationStat[]>(data);
    },
  });
}

/**
 * 获取登录失败计数
 */
export function useLoginFailureCountQuery() {
  return useQuery({
    queryKey: ['auditLogs', 'loginFailures'],
    queryFn: async () => {
      const { data } = await api.get('/admin/audit-logs/security/login-failures');
      return unwrap<LoginFailureCount>(data);
    },
  });
}

/**
 * 清除登录失败计数
 */
export function useClearLoginFailureCountMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (username: string) => {
      const { data } = await api.delete(`/admin/audit-logs/security/login-failures/${username}`);
      return unwrap<void>(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['auditLogs', 'loginFailures'] });
    },
  });
}
