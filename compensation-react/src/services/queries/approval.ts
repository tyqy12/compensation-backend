import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api, { unwrap } from '@services/api';
import type { PageParams } from '@/types/api';
import type { PagedResponse } from '@/types/openapi';

// 审批流程状态类型
export type ApprovalStatus = 'pending' | 'approved' | 'rejected' | 'cancelled';

// 审批流程类型
export type ApprovalWorkflowType =
  | 'BATCH'
  | 'PAYROLL_DISTRIBUTION'
  | 'ADHOC'
  | 'OFFLINE'
  | 'PERMISSION'
  | 'PAYROLL_DISPUTE';

// 审批流程数据定义
export interface ApprovalWorkflow {
  id: number;
  workflowName: string;
  workflowType: ApprovalWorkflowType;
  workflowTypeName: string;
  businessKey: string;
  businessType: string;
  currentStep: number;
  totalSteps: number;
  status: ApprovalStatus;
  statusName: string;
  initiatorId: number;
  initiatorName?: string | null;
  currentApproverId: number | null;
  currentApproverName?: string | null;
  submitTime: string;
  completeTime: string | null;
}

// 审批步骤数据定义
export interface ApprovalStep {
  id: number;
  stepNo: number;
  stepName: string;
  approverId: number;
  approverName: string;
  status: ApprovalStatus;
  statusName: string;
  approveComment: string;
  rejectReason: string;
  timeoutHours: number;
  approveTime: string | null;
}

// 审批流程详情
export interface ApprovalWorkflowDetail extends ApprovalWorkflow {
  steps: ApprovalStep[];
  businessInfo: Record<string, unknown>;
}

// 审批决策请求
export interface ApprovalDecisionRequest {
  comment?: string;
}

// 查询参数
export interface ApprovalQueryParams extends PageParams {
  status?: ApprovalStatus;
  workflowType?: ApprovalWorkflowType;
  keyword?: string;
  startDate?: string;
  endDate?: string;
  sortBy?: string;
  order?: 'asc' | 'desc';
}

// 我发起的查询参数
export interface ApprovalMyQueryParams extends PageParams {
  status?: ApprovalStatus;
}

// ==================== 查询接口 ====================

/**
 * 分页查询审批流程列表
 */
export function useApprovalWorkflowsQuery(
  params: ApprovalQueryParams,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: ['approvalWorkflows', params],
    queryFn: async () => {
      const queryParams: Record<string, unknown> = {
        page: params.current || 1,
        size: params.pageSize || 10,
        status: params.status,
        workflowType: params.workflowType,
        keyword: params.keyword,
        startDate: params.startDate,
        endDate: params.endDate,
        sortBy: params.sortBy || 'submitTime',
        order: params.order || 'desc',
      };

      // 移除空值参数
      const cleanParams = Object.fromEntries(
        Object.entries(queryParams).filter(([, value]) => value !== undefined && value !== ''),
      );

      const { data } = await api.get('/approval/workflows', { params: cleanParams });
      return unwrap<PagedResponse<ApprovalWorkflow>>(data);
    },
    enabled: options?.enabled,
  });
}

/**
 * 查询待我审批的流程
 */
export function usePendingApprovalsQuery(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['pendingApprovals'],
    queryFn: async () => {
      const { data } = await api.get('/approval/workflows/pending');
      return unwrap<ApprovalWorkflow[]>(data);
    },
    enabled: options?.enabled,
  });
}

/**
 * 查询我发起的流程
 */
export function useMyApprovalsQuery(
  params: ApprovalMyQueryParams,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: ['myApprovals', params],
    queryFn: async () => {
      const queryParams: Record<string, unknown> = {
        page: params.current || 1,
        size: params.pageSize || 10,
        status: params.status,
      };

      const cleanParams = Object.fromEntries(
        Object.entries(queryParams).filter(([, value]) => value !== undefined && value !== ''),
      );

      const { data } = await api.get('/approval/workflows/my', { params: cleanParams });
      return unwrap<PagedResponse<ApprovalWorkflow>>(data);
    },
    enabled: options?.enabled,
  });
}

/**
 * 查询审批流程详情
 */
export function useApprovalWorkflowDetailQuery(
  id: number,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: ['approvalWorkflow', id],
    queryFn: async () => {
      const { data } = await api.get(`/approval/workflows/${id}`);
      return unwrap<ApprovalWorkflowDetail>(data);
    },
    enabled: !!id && options?.enabled !== false,
  });
}

/**
 * 查询审批步骤列表
 */
export function useApprovalStepsQuery(
  workflowId: number,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: ['approvalSteps', workflowId],
    queryFn: async () => {
      const { data } = await api.get(`/approval/workflows/${workflowId}/steps`);
      return unwrap<ApprovalStep[]>(data);
    },
    enabled: !!workflowId && options?.enabled !== false,
  });
}

// ==================== 操作接口 ====================

/**
 * 审批通过
 */
export function useApproveMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, comment }: { id: number; comment?: string }) => {
      const { data } = await api.post(`/approval/workflows/${id}/approve`, { comment });
      return unwrap<{ success: boolean; message?: string }>(data);
    },
    onSuccess: (_, { id }) => {
      // 刷新相关查询缓存
      queryClient.invalidateQueries({ queryKey: ['approvalWorkflow', id] });
      queryClient.invalidateQueries({ queryKey: ['approvalWorkflows'] });
      queryClient.invalidateQueries({ queryKey: ['pendingApprovals'] });
      queryClient.invalidateQueries({ queryKey: ['myApprovals'] });
    },
  });
}

/**
 * 审批拒绝
 */
export function useRejectMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, comment }: { id: number; comment?: string }) => {
      const { data } = await api.post(`/approval/workflows/${id}/reject`, { comment });
      return unwrap<{ success: boolean; message?: string }>(data);
    },
    onSuccess: (_, { id }) => {
      // 刷新相关查询缓存
      queryClient.invalidateQueries({ queryKey: ['approvalWorkflow', id] });
      queryClient.invalidateQueries({ queryKey: ['approvalWorkflows'] });
      queryClient.invalidateQueries({ queryKey: ['pendingApprovals'] });
      queryClient.invalidateQueries({ queryKey: ['myApprovals'] });
    },
  });
}

// ==================== 辅助函数 ====================

/**
 * 获取审批状态显示信息
 */
export function getApprovalStatusInfo(status: ApprovalStatus) {
  const statusMap: Record<ApprovalStatus, { text: string; color: 'default' | 'processing' | 'success' | 'error' | 'warning'; icon: string }> = {
    pending: { text: '待审批', color: 'processing', icon: '⏳' },
    approved: { text: '已通过', color: 'success', icon: '✅' },
    rejected: { text: '已拒绝', color: 'error', icon: '❌' },
    cancelled: { text: '已取消', color: 'default', icon: '🚫' },
  };
  return statusMap[status] || { text: status, color: 'default', icon: '❓' };
}

/**
 * 获取流程类型显示信息
 */
export function getWorkflowTypeInfo(type: ApprovalWorkflowType) {
  const typeMap: Record<
    ApprovalWorkflowType,
    { text: string; color: 'blue' | 'purple' | 'orange' | 'green' | 'cyan' | 'magenta'; icon: string }
  > = {
    BATCH: { text: '批量支付', color: 'blue', icon: '📊' },
    PAYROLL_DISTRIBUTION: { text: '薪资发放', color: 'green', icon: '💰' },
    ADHOC: { text: '临时支付', color: 'orange', icon: '💸' },
    OFFLINE: { text: '架构外员工', color: 'purple', icon: '👤' },
    PERMISSION: { text: '权限授权', color: 'cyan', icon: '🔐' },
    PAYROLL_DISPUTE: { text: '薪酬异议', color: 'magenta', icon: '🧾' },
  };
  return typeMap[type] || { text: type, color: 'blue', icon: '📋' };
}

/**
 * 格式化步骤进度显示
 */
export function formatStepProgress(currentStep: number, totalSteps: number): string {
  return `${currentStep}/${totalSteps}`;
}

/**
 * 检查是否为待审批状态
 */
export function isPending(status: ApprovalStatus): boolean {
  return status === 'pending';
}
