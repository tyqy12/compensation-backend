/**
 * 审计日志类型定义
 */

/**
 * 审计日志类型
 */
export type AuditLogType =
  | 'PERMISSION_GRANT'       // 权限授予
  | 'PERMISSION_REVOKE'      // 权限回收
  | 'PERMISSION_MODIFY'      // 权限变更
  | 'ROLE_CREATE'            // 角色创建
  | 'ROLE_UPDATE'            // 角色更新
  | 'ROLE_DELETE'            // 角色删除
  | 'ROLE_COPY'              // 角色复制
  | 'ROLE_ENABLE'            // 角色启用
  | 'ROLE_DISABLE'           // 角色禁用
  | 'USER_ROLE_ASSIGN'       // 用户角色分配
  | 'USER_ROLE_REMOVE'       // 用户角色移除
  | 'RESOURCE_CREATE'        // 资源创建
  | 'RESOURCE_UPDATE'        // 资源更新
  | 'RESOURCE_DELETE'        // 资源删除
  | 'LOGIN'                  // 登录
  | 'LOGOUT'                 // 登出
  | 'PASSWORD_CHANGE'        // 密码修改
  | 'SETTINGS_CHANGE';       // 设置变更

/**
 * 审计日志级别
 */
export type AuditLogLevel = 'INFO' | 'WARNING' | 'ERROR';

/**
 * 审计日志实体
 */
export interface AuditLog {
  id: number;
  type: AuditLogType;
  level: AuditLogLevel;
  userId: number;
  username: string;
  realName?: string | null;
  action: string;
  resourceType?: string | null;
  resourceId?: string | number | null;
  resourceName?: string | null;
  oldValue?: string | null;      // JSON 字符串
  newValue?: string | null;      // JSON 字符串
  ip?: string | null;
  userAgent?: string | null;
  requestId?: string | null;
  status: 'SUCCESS' | 'FAILURE';
  errorMessage?: string | null;
  metadata?: Record<string, unknown> | null;
  createdAt: string;
}

/**
 * 审计日志查询参数
 */
export interface AuditLogQueryParams {
  page?: number;
  pageSize?: number;
  type?: AuditLogType;
  level?: AuditLogLevel;
  userId?: number;
  resourceType?: string;
  resourceId?: string | number;
  status?: 'SUCCESS' | 'FAILURE';
  startTime?: string;
  endTime?: string;
  keyword?: string;
}

/**
 * 审计日志统计
 */
export interface AuditLogStatistics {
  totalCount: number;
  successCount: number;
  failureCount: number;
  typeDistribution: Record<AuditLogType, number>;
  dailyTrend: Array<{
    date: string;
    count: number;
  }>;
}

/**
 * 审计日志类型列表项
 */
export interface AuditLogTypeInfo {
  type: AuditLogType;
  name: string;
  description: string;
}
