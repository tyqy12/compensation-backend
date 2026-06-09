import type { PayrollBatchSummaryDto } from '@types/openapi';

export interface PayrollBatchFlowLike extends PayrollBatchSummaryDto {
  calculationStatus?: string;
  batchRevision?: number;
  approvalWorkflowId?: number;
  confirmationRequired?: boolean;
  confirmationMode?: string;
  confirmationCompletedTime?: string;
  settlementProviderCode?: string;
  paymentBatchNo?: string;
}

export interface PayrollStatusMeta {
  text: string;
  color: string;
}

const flowStatusMetaMap: Record<string, PayrollStatusMeta> = {
  draft: { text: '草稿', color: 'default' },
  locked: { text: '已锁定', color: 'warning' },
  confirming: { text: '待确认', color: 'processing' },
  dispute_processing: { text: '异议处理中', color: 'warning' },
  confirmed: { text: '确认完成', color: 'success' },
  submitted: { text: '已提交审批', color: 'processing' },
  approved: { text: '审批通过', color: 'success' },
  rejected: { text: '审批驳回', color: 'error' },
  pay_processing: { text: '发放处理中', color: 'processing' },
  pay_failed: { text: '发放失败', color: 'error' },
  paid: { text: '已发放', color: 'success' },
  archived: { text: '已归档', color: 'default' },
};

const calculationStatusMetaMap: Record<string, PayrollStatusMeta> = {
  draft: { text: '未计算', color: 'default' },
  locked: { text: '已锁定', color: 'warning' },
  calculating: { text: '计算中', color: 'processing' },
  calculated: { text: '计算完成', color: 'success' },
  failed: { text: '计算失败', color: 'error' },
};

const distributionStatusMetaMap: Record<string, PayrollStatusMeta> = {
  planned: { text: '待发放', color: 'default' },
  created: { text: '已创建批次', color: 'default' },
  submitted: { text: '已提交渠道', color: 'processing' },
  processing: { text: '发放处理中', color: 'processing' },
  partial_success: { text: '部分成功', color: 'warning' },
  success: { text: '全部成功', color: 'success' },
  failed: { text: '发放失败', color: 'error' },
  cancelled: { text: '已取消', color: 'default' },
  pay_processing: { text: '发放处理中', color: 'processing' },
  pay_failed: { text: '发放失败', color: 'error' },
  paid: { text: '全部成功', color: 'success' },
};

const calculationStatusAliasMap: Record<string, string> = {
  pending: 'draft',
  running: 'calculating',
  completed: 'calculated',
  compute_pending: 'draft',
  compute_running: 'calculating',
  compute_completed: 'calculated',
};

const distributionStatusAliasMap: Record<string, string> = {
  partial_completed: 'partial_success',
  partially_completed: 'partial_success',
  completed: 'success',
};

export const normalizeFlowStatus = (status?: string) => String(status ?? '').trim().toLowerCase();

export const normalizeCalculationStatus = (status?: string) => {
  const normalized = String(status ?? '').trim().toLowerCase();
  return calculationStatusAliasMap[normalized] ?? normalized;
};

export const normalizeDistributionStatus = (status?: string) => {
  const normalized = String(status ?? '').trim().toLowerCase();
  return distributionStatusAliasMap[normalized] ?? normalized;
};

export const getFlowStatusMeta = (status?: string) => flowStatusMetaMap[normalizeFlowStatus(status)];

export const getCalculationStatusMeta = (status?: string) =>
  calculationStatusMetaMap[normalizeCalculationStatus(status)];

export const getDistributionStatusMeta = (status?: string) =>
  distributionStatusMetaMap[normalizeDistributionStatus(status)];

export const isPayrollBatchComputable = (status?: string) => [
  'locked',
  'confirming',
  'dispute_processing',
  'rejected',
].includes(normalizeFlowStatus(status));

export const getBatchRevisionText = (batchRevision?: number | null) => {
  const revision = typeof batchRevision === 'number' && batchRevision > 0 ? batchRevision : 1;
  return `R${revision}`;
};

export const getConfirmationModeText = (confirmationMode?: string, confirmationRequired?: boolean) => {
  if (confirmationRequired === false) {
    return '无需确认';
  }
  switch (String(confirmationMode ?? '').trim().toLowerCase()) {
    case 'group':
      return '组确认';
    case 'individual':
      return '逐人确认';
    default:
      return confirmationRequired === true ? '待定' : '—';
  }
};

export const getPayrollFlowCurrentStep = (record: PayrollBatchFlowLike) => {
  const flowStatus = normalizeFlowStatus(record.status);
  const calculationStatus = normalizeCalculationStatus(
    record.calculationStatus ?? record.computeStatus,
  );
  const distributionStatus = normalizeDistributionStatus(record.paymentStatus);

  if (
    ['archived', 'paid'].includes(flowStatus) ||
    ['success', 'partial_success'].includes(distributionStatus)
  ) {
    return 5;
  }
  if (
    ['pay_processing', 'pay_failed'].includes(flowStatus) ||
    ['planned', 'created', 'submitted', 'processing', 'failed', 'cancelled'].includes(distributionStatus)
  ) {
    return 4;
  }
  if (['submitted', 'approved', 'rejected'].includes(flowStatus)) {
    return 3;
  }
  if (['confirming', 'dispute_processing', 'confirmed'].includes(flowStatus)) {
    return 2;
  }
  if (['locked'].includes(flowStatus) || ['calculating', 'calculated', 'failed'].includes(calculationStatus)) {
    return 1;
  }
  return 0;
};

export const getPayrollFlowSteps = (record: PayrollBatchFlowLike) => {
  const calculationMeta = getCalculationStatusMeta(record.calculationStatus ?? record.computeStatus);
  const flowMeta = getFlowStatusMeta(record.status);
  const distributionMeta = getDistributionStatusMeta(record.paymentStatus);
  const confirmationModeText = getConfirmationModeText(
    record.confirmationMode,
    record.confirmationRequired,
  );

  return [
    {
      title: '数据录入',
      description: '导入或手工补录薪资项',
    },
    {
      title: '锁定核算',
      description: calculationMeta?.text ?? '待锁定/待计算',
    },
    {
      title: '员工确认',
      description:
        record.confirmationRequired === false
          ? '已跳过确认'
          : confirmationModeText,
    },
    {
      title: '审批流',
      description: record.approvalWorkflowId ? `工作流 #${record.approvalWorkflowId}` : flowMeta?.text ?? '待提交',
    },
    {
      title: '发放执行',
      description: distributionMeta?.text ?? '待创建发放链路',
    },
    {
      title: '完成归档',
      description: ['archived', 'paid'].includes(normalizeFlowStatus(record.status)) ? '已完成' : '待完成',
    },
  ];
};

export const getPayrollNextAction = (
  record: PayrollBatchFlowLike,
  hasImportData: boolean,
  canManageBatch: boolean,
) => {
  const flowStatus = normalizeFlowStatus(record.status);
  const calculationStatus = normalizeCalculationStatus(
    record.calculationStatus ?? record.computeStatus,
  );
  const distributionStatus = normalizeDistributionStatus(record.paymentStatus);

  if (!canManageBatch) {
    return '当前账号为查看角色，可查看核算、确认、审批与发放结果。';
  }
  if (!hasImportData) {
    return '先录入数据：可上传 CSV，也可手工补录薪资项。';
  }
  if (flowStatus === 'draft') {
    return '录入完成后先锁定批次，再执行薪资计算。';
  }
  if (calculationStatus === 'failed') {
    return '上一次核算失败，请修正导入数据或模板后重新计算。';
  }
  if (flowStatus === 'locked' && calculationStatus !== 'calculated') {
    return '批次已锁定，可开始计算当前版本薪资。';
  }
  if (calculationStatus === 'calculating') {
    return '薪资正在计算，请等待本次核算完成。';
  }
  if (calculationStatus === 'failed') {
    return '上一次核算失败，请修正导入数据或模板后重新计算。';
  }
  if (['confirming', 'dispute_processing'].includes(flowStatus)) {
    return '进入员工确认阶段，建议前往确认工作台跟进待确认与异议处理。';
  }
  if (flowStatus === 'confirmed') {
    return '员工确认已完成，可提交审批。';
  }
  if (flowStatus === 'submitted') {
    return '审批进行中，请等待审批结果。';
  }
  if (flowStatus === 'approved' && !distributionStatus) {
    return '审批已通过，等待系统创建发放链路并提交支付渠道。';
  }
  if (['planned', 'created', 'submitted', 'processing', 'pay_processing'].includes(distributionStatus)) {
    return '发放链路处理中，请跟踪渠道结果与回执。';
  }
  if (['failed', 'pay_failed'].includes(distributionStatus) || flowStatus === 'pay_failed') {
    return '发放失败，可修复失败原因后重试失败子集。';
  }
  if (distributionStatus === 'partial_success') {
    return '存在部分成功记录，请关注失败明细并执行对账。';
  }
  if (flowStatus === 'rejected') {
    return '审批已驳回，可修正数据后重新核算并再次提审。';
  }
  return '当前版本流程已收口，可查看台账、确认结果与发放记录。';
};

export const getPayrollBlockers = (record: PayrollBatchFlowLike, hasImportData: boolean) => {
  const blockers: string[] = [];
  const flowStatus = normalizeFlowStatus(record.status);
  const calculationStatus = normalizeCalculationStatus(
    record.calculationStatus ?? record.computeStatus,
  );
  const distributionStatus = normalizeDistributionStatus(record.paymentStatus);

  if (!hasImportData) {
    blockers.push('当前还没有任何导入项，无法进入后续核算与审批。');
  }
  if (calculationStatus === 'failed') {
    blockers.push('当前版本核算失败，需要先修复数据或规则错误。');
  }
  if (flowStatus === 'confirming') {
    blockers.push('仍有员工待确认，暂不可提交审批。');
  }
  if (flowStatus === 'dispute_processing') {
    blockers.push('存在员工异议，需先完成异议处理。');
  }
  if (flowStatus === 'submitted') {
    blockers.push('审批进行中，需等待审批结果。');
  }
  if (['processing', 'submitted', 'created', 'planned', 'pay_processing'].includes(distributionStatus)) {
    blockers.push('发放链路处理中，当前不可修改导入数据。');
  }
  if (['failed', 'pay_failed'].includes(distributionStatus) || flowStatus === 'pay_failed') {
    blockers.push('发放失败，需修复渠道或账户问题后重试。');
  }
  if (['paid', 'archived'].includes(flowStatus) || ['success', 'partial_success'].includes(distributionStatus)) {
    blockers.push('当前版本已进入完成态，页面以查看为主。');
  }
  return blockers;
};
