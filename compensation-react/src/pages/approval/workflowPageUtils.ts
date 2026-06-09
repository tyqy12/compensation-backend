import type { ApprovalQueryParams, ApprovalWorkflow } from '@services/queries/approval';

export type ApprovalTab = 'list' | 'pending' | 'my';

export const buildApprovalSearchParams = (params: ApprovalQueryParams, tab: ApprovalTab) => {
  const next = new URLSearchParams();
  if (params.current) next.set('page', String(params.current));
  if (params.pageSize) next.set('size', String(params.pageSize));
  if (params.status) next.set('status', params.status);
  if (params.workflowType) next.set('workflowType', params.workflowType);
  if (params.keyword) next.set('keyword', params.keyword);
  if (params.startDate) next.set('startDate', params.startDate);
  if (params.endDate) next.set('endDate', params.endDate);
  if (params.sortBy) next.set('sortBy', params.sortBy);
  if (params.order) next.set('order', params.order);
  next.set('tab', tab);
  return next;
};

export const getApprovalTableRecords = (
  data?: ApprovalWorkflow[] | { list?: ApprovalWorkflow[]; records?: ApprovalWorkflow[] },
): ApprovalWorkflow[] => {
  if (Array.isArray(data)) {
    return data;
  }
  if (data?.list) {
    return data.list;
  }
  if (data?.records) {
    return data.records;
  }
  return [];
};
