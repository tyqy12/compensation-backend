/**
 * 审计日志服务
 */

import api from '@services/api';
import type { AuditLog, AuditLogQueryParams, AuditLogStatistics, AuditLogTypeInfo } from '@types/auditLog';

const AUDIT_API_BASE = '/api/auth-center/audit-logs';

/**
 * 查询审计日志列表
 */
export async function fetchAuditLogs(
  params: AuditLogQueryParams
): Promise<{ list: AuditLog[]; total: number }> {
  const { data } = await api.get<{
    code: number;
    message: string;
    data: { list: AuditLog[]; total: number };
  }>(AUDIT_API_BASE, { params });

  if (data.code !== 0) {
    throw new Error(data.message || '查询审计日志失败');
  }

  return data.data;
}

/**
 * 获取审计日志详情
 */
export async function fetchAuditLogDetail(id: number): Promise<AuditLog> {
  const { data } = await api.get<{
    code: number;
    message: string;
    data: AuditLog;
  }>(`${AUDIT_API_BASE}/${id}`);

  if (data.code !== 0) {
    throw new Error(data.message || '获取审计日志详情失败');
  }

  return data.data;
}

/**
 * 获取审计日志统计
 */
export async function fetchAuditStatistics(
  params: Pick<AuditLogQueryParams, 'type' | 'startTime' | 'endTime'>
): Promise<AuditLogStatistics> {
  const { data } = await api.get<{
    code: number;
    message: string;
    data: AuditLogStatistics;
  }>(`${AUDIT_API_BASE}/statistics`, { params });

  if (data.code !== 0) {
    throw new Error(data.message || '获取审计统计失败');
  }

  return data.data;
}

/**
 * 导出审计日志
 */
export async function exportAuditLogs(
  params: AuditLogQueryParams
): Promise<Blob> {
  const response = await api.get(`${AUDIT_API_BASE}/export`, {
    params,
    responseType: 'blob',
  });

  return response.data as Blob;
}

/**
 * 获取审计日志类型列表
 */
export async function fetchAuditLogTypes(): Promise<AuditLogTypeInfo[]> {
  const { data } = await api.get<{
    code: number;
    message: string;
    data: AuditLogTypeInfo[];
  }>(`${AUDIT_API_BASE}/types`);

  if (data.code !== 0) {
    throw new Error(data.message || '获取审计日志类型失败');
  }

  return data.data;
}
