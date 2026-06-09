import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api, { unwrap } from '@services/api';

const TASKS_PATH = '/v1/admin/tasks';

// 任务状态类型
export type TaskStatus = 'PAUSED' | 'RUNNING' | 'FAILED' | 'SUCCESS';

// 执行状态类型
export type ExecutionStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'RETRYING';

// 任务数据类型定义
export interface ScheduledTask {
  id: number;
  taskKey: string;
  taskName: string;
  taskGroup: string;
  cronExpression: string;
  description: string;
  status: TaskStatus;
  retryCount: number;
  maxRetryCount: number;
  retryIntervalSeconds: number;
  lastExecuteTime: string;
  nextExecuteTime: string;
  lastResult: string;
  alarmEnabled: boolean;
  alarmReceivers: string;
  handlerBean: string;
  createTime: string;
  updateTime: string;
}

// 执行日志数据类型定义
export interface ScheduledTaskExecution {
  id: number;
  taskId: number;
  taskKey: string;
  startTime: string;
  endTime: string;
  durationMs: number;
  status: ExecutionStatus;
  result: string;
  errorMessage: string;
  traceId: string;
  createTime: string;
}

// 任务创建/更新参数
export interface TaskScheduleParams {
  taskKey: string;
  taskName: string;
  taskGroup?: string;
  cronExpression: string;
  description?: string;
  handlerBean: string;
  retryCount?: number;
  maxRetryCount?: number;
  retryIntervalSeconds?: number;
  alarmEnabled?: boolean;
  alarmReceivers?: string;
}

// ==================== 查询接口 ====================

/**
 * 查询任务列表
 */
export function useScheduledTasksQuery(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['scheduledTasks'],
    queryFn: async () => {
      const { data } = await api.get(TASKS_PATH);
      return unwrap<ScheduledTask[]>(data);
    },
    enabled: options?.enabled,
  });
}

/**
 * 查询任务详情
 */
export function useScheduledTaskDetailQuery(
  id: number,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: ['scheduledTask', id],
    queryFn: async () => {
      const { data } = await api.get(`${TASKS_PATH}/${id}`);
      return unwrap<ScheduledTask>(data);
    },
    enabled: !!id && options?.enabled !== false,
  });
}

/**
 * 查询任务执行日志
 */
export function useTaskExecutionLogsQuery(
  taskId: number,
  limit?: number,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: ['taskExecutionLogs', taskId, limit],
    queryFn: async () => {
      const { data } = await api.get(`${TASKS_PATH}/${taskId}/logs`, {
        params: { limit: limit || 50 },
      });
      return unwrap<ScheduledTaskExecution[]>(data);
    },
    enabled: !!taskId && options?.enabled !== false,
  });
}

// ==================== 操作接口 ====================

/**
 * 创建任务
 */
export function useCreateTaskMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (params: TaskScheduleParams) => {
      const { data } = await api.post(TASKS_PATH, params);
      return unwrap<number>(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scheduledTasks'] });
    },
  });
}

/**
 * 更新任务
 */
export function useUpdateTaskMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, params }: { id: number; params: TaskScheduleParams }) => {
      const { data } = await api.put(`${TASKS_PATH}/${id}`, params);
      return unwrap<void>(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scheduledTasks'] });
    },
  });
}

/**
 * 删除任务
 */
export function useDeleteTaskMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: number) => {
      const { data } = await api.delete(`${TASKS_PATH}/${id}`);
      return unwrap<void>(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scheduledTasks'] });
    },
  });
}

/**
 * 暂停任务
 */
export function usePauseTaskMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: number) => {
      const { data } = await api.post(`${TASKS_PATH}/${id}/pause`);
      return unwrap<void>(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scheduledTasks'] });
    },
  });
}

/**
 * 恢复任务
 */
export function useResumeTaskMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: number) => {
      const { data } = await api.post(`${TASKS_PATH}/${id}/resume`);
      return unwrap<void>(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scheduledTasks'] });
    },
  });
}

/**
 * 手动触发任务
 */
export function useTriggerTaskMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: number) => {
      const { data } = await api.post(`${TASKS_PATH}/${id}/trigger`);
      return unwrap<number>(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scheduledTasks'] });
    },
  });
}

// ==================== 辅助函数 ====================

/**
 * 获取任务状态显示信息
 */
export function getTaskStatusInfo(status: TaskStatus) {
  const statusMap: Record<TaskStatus, { text: string; color: 'default' | 'processing' | 'success' | 'error' | 'warning'; icon: string }> = {
    PAUSED: { text: '已暂停', color: 'default', icon: '⏸️' },
    RUNNING: { text: '运行中', color: 'processing', icon: '▶️' },
    FAILED: { text: '失败', color: 'error', icon: '❌' },
    SUCCESS: { text: '成功', color: 'success', icon: '✅' },
  };
  return statusMap[status] || { text: status, color: 'default', icon: '❓' };
}

/**
 * 获取执行状态显示信息
 */
export function getExecutionStatusInfo(status: ExecutionStatus) {
  const statusMap: Record<ExecutionStatus, { text: string; color: 'default' | 'processing' | 'success' | 'error' }> = {
    RUNNING: { text: '运行中', color: 'processing' },
    SUCCESS: { text: '成功', color: 'success' },
    FAILED: { text: '失败', color: 'error' },
    RETRYING: { text: '重试中', color: 'warning' },
  };
  return statusMap[status] || { text: status, color: 'default' };
}

/**
 * 格式化执行时间
 */
export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`;
  return `${(ms / 60000).toFixed(2)}min`;
}

/**
 * 格式化下次执行时间
 */
export function formatNextExecuteTime(value?: string): string {
  if (!value) return '—';
  return value;
}
