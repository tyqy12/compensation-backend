import { useQuery } from '@tanstack/react-query';
import api, { unwrap } from '@services/api';

// 监控摘要数据类型定义
export interface MonitorSummary {
  app: {
    profiles: string[];
    now: string;
    uptimeSeconds: number;
  };
  jvm: {
    heapInit: number;
    heapUsed: number;
    heapCommitted: number;
    heapMax: number;
    threadCount: number;
  };
  datasource: {
    ping: string;
    error?: string;
  };
  redis: {
    ping: string;
    error?: string;
  };
}

// 格式化字节单位
export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// 格式化时间单位
export function formatUptime(seconds: number): string {
  if (seconds < 60) return `${seconds} 秒`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} 时 ${Math.floor((seconds % 3600) / 60)} 分`;
  return `${Math.floor(seconds / 86400)} 天 ${Math.floor((seconds % 86400) / 3600)} 时`;
}

// 获取JVM堆使用率颜色
export function getHeapUsageColor(used: number, max: number): 'success' | 'normal' | 'exception' {
  const ratio = used / max;
  if (ratio > 0.9) return 'exception';
  if (ratio > 0.7) return 'normal';
  return 'success';
}

// 获取数据库连接状态
export function getDbStatus(ping: string): 'success' | 'error' | 'processing' {
  if (ping === 'OK') return 'success';
  if (ping === 'FAILED') return 'error';
  return 'processing';
}

// 获取Redis连接状态
export function getRedisStatus(ping: string): 'success' | 'error' | 'processing' {
  if (ping === 'PONG') return 'success';
  if (ping === 'FAILED') return 'error';
  return 'processing';
}

// ==================== 查询接口 ====================

/**
 * 获取监控摘要信息
 */
export function useMonitorSummaryQuery(options?: { enabled?: boolean; refetchInterval?: number }) {
  return useQuery({
    queryKey: ['monitorSummary'],
    queryFn: async () => {
      const { data } = await api.get('/admin/monitor/summary');
      return unwrap<MonitorSummary>(data);
    },
    enabled: options?.enabled,
    refetchInterval: options?.refetchInterval ?? 30000, // 默认30秒刷新
  });
}
