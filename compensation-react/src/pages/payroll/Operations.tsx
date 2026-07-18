import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  Badge,
  Button,
  Empty,
  Input,
  Select,
  Space,
  Spin,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  ArrowRightOutlined,
  AuditOutlined,
  CheckCircleOutlined,
  CheckOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  CopyOutlined,
  ExclamationCircleOutlined,
  FileProtectOutlined,
  FileSearchOutlined,
  InboxOutlined,
  LoadingOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SendOutlined,
  TeamOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { getPagedRecords } from '@services/api';
import {
  usePayrollBatchDetailQuery,
  usePayrollBatchesQuery,
  usePayrollConfirmationSummaryQuery,
  usePayrollDistributionItemsQuery,
  usePayrollDistributionsQuery,
  usePayrollImportItemsQuery,
  usePayrollLedgerQuery,
  usePayrollReconciliationsQuery,
  type PayrollBatchListParams,
  type PayrollConfirmationSummaryDto,
} from '@services/queries/payroll';
import type {
  PayrollBatchSummaryDto,
  PayrollDistributionDto,
  PayrollLedgerDto,
  PayrollReconciliationTaskDto,
} from '@types/openapi';
import {
  getBatchRevisionText,
  getCalculationEvidenceMeta,
  getCalculationEvidenceStatus,
  getCalculationStatusMeta,
  getFlowStatusMeta,
  getSnapshotHashPreview,
  isSettlementRouteBlocked,
  normalizeCalculationStatus,
  normalizeDistributionStatus,
  normalizeFlowStatus,
} from './components/payrollFlow';
import './PayrollPages.less';
import './Operations.less';

const { Text, Title } = Typography;

export type PayrollOperationsAction =
  | 'entry'
  | 'ledger'
  | 'confirmation'
  | 'approval'
  | 'distribution'
  | 'reconciliation';

export type PayrollOperationsStageState = 'pending' | 'active' | 'done' | 'blocked';

export interface PayrollOperationsSnapshot {
  batch: PayrollBatchSummaryDto;
  importItemCount: number;
  ledger?: PayrollLedgerDto;
  confirmation?: PayrollConfirmationSummaryDto;
  distributions: PayrollDistributionDto[];
  reconciliations: PayrollReconciliationTaskDto[];
  routeBlockedCount: number;
  staleDistributionCount: number;
}

export interface PayrollOperationsQueueItem {
  key: string;
  title: string;
  description: string;
  count: number;
  tone: 'default' | 'warning' | 'error' | 'success';
  action?: PayrollOperationsAction;
}

export interface PayrollOperationsStage {
  key: PayrollOperationsAction;
  label: string;
  state: PayrollOperationsStageState;
  description: string;
}

const payrollTypeOptions = [
  { label: '全部用工类型', value: '' },
  { label: '全职', value: 'full_time' },
  { label: '兼职', value: 'part_time' },
  { label: '外包', value: 'contractor' },
];

const statusOptions = [
  { label: '全部流程状态', value: '' },
  { label: '草稿', value: 'draft' },
  { label: '待确认', value: 'confirming' },
  { label: '确认完成', value: 'confirmed' },
  { label: '审批中', value: 'submitted' },
  { label: '审批通过', value: 'approved' },
  { label: '发放处理中', value: 'pay_processing' },
  { label: '发放失败', value: 'pay_failed' },
  { label: '已发放', value: 'paid' },
];

const actionLabel: Record<PayrollOperationsAction, string> = {
  entry: '录入数据',
  ledger: '查看核算',
  confirmation: '处理确认',
  approval: '查看审批',
  distribution: '处理发放',
  reconciliation: '处理对账',
};

const stageIcon: Record<PayrollOperationsAction, React.ReactNode> = {
  entry: <InboxOutlined />,
  ledger: <FileSearchOutlined />,
  confirmation: <TeamOutlined />,
  approval: <SafetyCertificateOutlined />,
  distribution: <SendOutlined />,
  reconciliation: <AuditOutlined />,
};

const stageDefinitions: Array<{
  key: PayrollOperationsAction;
  label: string;
}> = [
  { key: 'entry', label: '数据录入' },
  { key: 'ledger', label: '计算核验' },
  { key: 'confirmation', label: '员工确认' },
  { key: 'approval', label: '审批' },
  { key: 'distribution', label: '发放' },
  { key: 'reconciliation', label: '对账' },
];

const normalize = (value?: string | null) =>
  String(value ?? '')
    .trim()
    .toLowerCase();

const getBatchId = (record?: PayrollBatchSummaryDto | null) => {
  const value = record?.batchId ?? (record as PayrollBatchSummaryDto & { id?: number })?.id;
  const batchId = Number(value);
  return Number.isFinite(batchId) && batchId > 0 ? batchId : undefined;
};

const parseBatchId = (value: string | null) => {
  const batchId = Number(value ?? '');
  return Number.isFinite(batchId) && batchId > 0 ? batchId : undefined;
};

const getPayrollTypeLabel = (value?: string) => {
  const option = payrollTypeOptions.find((item) => item.value === value);
  return option?.label === '全部用工类型' ? value || '—' : option?.label || value || '—';
};

const formatCurrency = (value?: number, currency = 'CNY') => {
  if (value === undefined || value === null) {
    return '—';
  }
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(value);
};

const getStageState = (
  key: PayrollOperationsAction,
  snapshot: PayrollOperationsSnapshot,
): PayrollOperationsStageState => {
  const { batch, importItemCount, ledger, confirmation, distributions, reconciliations } = snapshot;
  const flowStatus = normalizeFlowStatus(batch.status);
  const calculationStatus = normalizeCalculationStatus(
    batch.calculationStatus ?? batch.computeStatus,
  );
  const distributionStatus = normalizeDistributionStatus(batch.paymentStatus);

  if (key === 'entry') {
    if (importItemCount > 0 || !['draft', 'locked'].includes(flowStatus)) {
      return 'done';
    }
    return 'active';
  }

  if (key === 'ledger') {
    if (calculationStatus === 'failed' || ledger?.hasBlockingIssues) {
      return 'blocked';
    }
    if (calculationStatus === 'calculated') {
      return 'done';
    }
    if (calculationStatus === 'calculating') {
      return 'active';
    }
    return 'pending';
  }

  if (key === 'confirmation') {
    if (batch.confirmationRequired === false) {
      return 'done';
    }
    if (
      [
        'confirmed',
        'submitted',
        'approved',
        'pay_processing',
        'pay_failed',
        'paid',
        'archived',
      ].includes(flowStatus)
    ) {
      return 'done';
    }
    if (normalize(flowStatus) === 'dispute_processing' || (confirmation?.pendingCount ?? 0) > 0) {
      return 'active';
    }
    return 'pending';
  }

  if (key === 'approval') {
    if (flowStatus === 'rejected') {
      return 'blocked';
    }
    if (['approved', 'pay_processing', 'pay_failed', 'paid', 'archived'].includes(flowStatus)) {
      return 'done';
    }
    if (['submitted', 'confirmed'].includes(flowStatus) || batch.approvalWorkflowId) {
      return 'active';
    }
    return 'pending';
  }

  if (key === 'distribution') {
    if (snapshot.staleDistributionCount > 0) {
      return 'blocked';
    }
    if (distributionStatus === 'failed' || distributionStatus === 'partial_success') {
      return 'blocked';
    }
    if (['success', 'paid'].includes(distributionStatus)) {
      return 'done';
    }
    if (['planned', 'created', 'submitted', 'processing'].includes(distributionStatus)) {
      return 'active';
    }
    if (
      distributions.length > 0 &&
      distributions.some(
        (item) => normalizeDistributionStatus(item.distributionStatus) === 'failed',
      )
    ) {
      return 'blocked';
    }
    return 'pending';
  }

  if (reconciliations.some((item) => normalize(item.result) === 'mismatch')) {
    return 'blocked';
  }
  if (
    reconciliations.some(
      (item) => normalize(item.result) === 'matched' || normalize(item.result) === 'resolved',
    )
  ) {
    return 'done';
  }
  if (
    reconciliations.some((item) => ['processing', 'pending'].includes(normalize(item.taskStatus)))
  ) {
    return 'active';
  }
  return ['success', 'partial_success'].includes(distributionStatus) ? 'active' : 'pending';
};

export const getPayrollOperationsStages = (
  snapshot: PayrollOperationsSnapshot,
): PayrollOperationsStage[] =>
  stageDefinitions.map(({ key, label }) => {
    const state = getStageState(key, snapshot);
    const descriptions: Record<PayrollOperationsStageState, string> = {
      pending: '尚未进入',
      active: key === 'ledger' ? '等待核验' : `有${actionLabel[key]}事项`,
      done: key === 'entry' ? '数据已就绪' : '已完成',
      blocked: '存在阻断',
    };
    return { key, label, state, description: descriptions[state] };
  });

export const getPayrollOperationsQueue = (
  snapshot: PayrollOperationsSnapshot,
): PayrollOperationsQueueItem[] => {
  const { batch, importItemCount, ledger, confirmation, reconciliations } = snapshot;
  const items: PayrollOperationsQueueItem[] = [];
  const calculationStatus = normalizeCalculationStatus(
    batch.calculationStatus ?? batch.computeStatus,
  );
  const evidenceStatus = getCalculationEvidenceStatus(batch);
  const flowStatus = normalizeFlowStatus(batch.status);
  const blockingIssueCount = Math.max(
    ledger?.blockingIssueCount ?? 0,
    ledger?.linesWithBlockingIssues ?? 0,
  );
  const mismatchCount = reconciliations.filter(
    (item) => normalize(item.result) === 'mismatch',
  ).length;

  if (flowStatus === 'draft' && importItemCount === 0) {
    items.push({
      key: 'input-missing',
      title: '输入数据尚未就绪',
      description: '导入或补录本批次的薪资输入项后才能计算',
      count: 1,
      tone: 'warning',
      action: 'entry',
    });
  }
  if (calculationStatus === 'failed' || blockingIssueCount > 0) {
    items.push({
      key: 'calculation-blocked',
      title: '核算存在阻断项',
      description:
        calculationStatus === 'failed'
          ? '最近一次计算失败，需要重新检查输入和规则'
          : '有工资行未通过校验，不能进入后续流程',
      count: Math.max(blockingIssueCount, 1),
      tone: 'error',
      action: 'ledger',
    });
  }
  if (['calculated', 'calculating'].includes(calculationStatus) && evidenceStatus !== 'complete') {
    items.push({
      key: 'evidence-incomplete',
      title: '计算证据不完整',
      description: '当前批次缺少输入快照、规则快照或引擎版本摘要',
      count: 1,
      tone: 'warning',
      action: 'ledger',
    });
  }
  if ((confirmation?.pendingCount ?? 0) > 0) {
    items.push({
      key: 'confirmation-pending',
      title: '员工确认待处理',
      description: '仍有工资行等待员工确认或异议处理',
      count: confirmation?.pendingCount ?? 0,
      tone: 'warning',
      action: 'confirmation',
    });
  }
  if (flowStatus === 'rejected') {
    items.push({
      key: 'approval-rejected',
      title: '审批已驳回',
      description: '需要查看审批意见并修正批次后重新提交',
      count: 1,
      tone: 'error',
      action: 'approval',
    });
  } else if (['confirmed', 'submitted'].includes(flowStatus) || batch.approvalWorkflowId) {
    items.push({
      key: 'approval-pending',
      title: '审批节点待推进',
      description: '批次已经进入审批链路，当前页面只保留流程入口',
      count: 1,
      tone: 'default',
      action: 'approval',
    });
  }
  if (snapshot.routeBlockedCount > 0) {
    items.push({
      key: 'route-blocked',
      title: '收款路由被阻断',
      description: '存在无法匹配结算渠道或收款账户的发放明细',
      count: snapshot.routeBlockedCount,
      tone: 'error',
      action: 'distribution',
    });
  }
  if (snapshot.staleDistributionCount > 0) {
    items.push({
      key: 'stale-distribution',
      title: '发放单版本已过期',
      description: '发放单不是当前批次 revision，不能继续重试',
      count: snapshot.staleDistributionCount,
      tone: 'warning',
      action: 'distribution',
    });
  }
  if (mismatchCount > 0) {
    items.push({
      key: 'reconciliation-mismatch',
      title: '对账存在差异',
      description: '支付渠道回执与应发金额不一致，需要进入对账任务',
      count: mismatchCount,
      tone: 'error',
      action: 'reconciliation',
    });
  }

  return items;
};

const getNextAction = (
  stages: PayrollOperationsStage[],
  queue: PayrollOperationsQueueItem[],
): PayrollOperationsAction => {
  const queueAction = queue.find((item) => item.action)?.action;
  if (queueAction) {
    return queueAction;
  }
  return (
    stages.find((stage) => ['active', 'blocked'].includes(stage.state))?.key ??
    stages.find((stage) => stage.state === 'pending')?.key ??
    'ledger'
  );
};

const statusTag = (record: PayrollBatchSummaryDto) => {
  const flowMeta = getFlowStatusMeta(record.status);
  const calculationMeta = getCalculationStatusMeta(
    record.calculationStatus ?? record.computeStatus,
  );
  return (
    <Space size={4} wrap>
      {flowMeta ? (
        <Tag color={flowMeta.color}>{flowMeta.text}</Tag>
      ) : (
        <Tag>{record.status || '—'}</Tag>
      )}
      {calculationMeta && <Tag color={calculationMeta.color}>{calculationMeta.text}</Tag>}
    </Space>
  );
};

const stageStateIcon: Record<PayrollOperationsStageState, React.ReactNode> = {
  pending: <ClockCircleOutlined />,
  active: <LoadingOutlined />,
  done: <CheckOutlined />,
  blocked: <CloseCircleOutlined />,
};

const PayrollOperations: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [periodInput, setPeriodInput] = useState(searchParams.get('period') ?? '');
  const [typeInput, setTypeInput] = useState(searchParams.get('payrollType') ?? '');
  const [statusInput, setStatusInput] = useState(searchParams.get('status') ?? '');
  const [selectedBatchId, setSelectedBatchId] = useState<number | undefined>(() =>
    parseBatchId(searchParams.get('batchId')),
  );
  const [view, setView] = useState<'summary' | 'evidence' | 'stages'>('summary');

  const filters = useMemo<PayrollBatchListParams>(
    () => ({
      current: 1,
      pageSize: 30,
      period: searchParams.get('period') || undefined,
      payrollType: searchParams.get('payrollType') || undefined,
      status: searchParams.get('status') || undefined,
    }),
    [searchParams],
  );
  const batchesQuery = usePayrollBatchesQuery(filters);
  const batchRecords = useMemo(() => getPagedRecords(batchesQuery.data), [batchesQuery.data]);

  useEffect(() => {
    if (selectedBatchId && batchRecords.some((record) => getBatchId(record) === selectedBatchId)) {
      return;
    }
    const fallbackId = getBatchId(batchRecords[0]);
    if (fallbackId && fallbackId !== selectedBatchId) {
      setSelectedBatchId(fallbackId);
      const next = new URLSearchParams(searchParams);
      next.set('batchId', String(fallbackId));
      setSearchParams(next, { replace: true });
      return;
    }
    if (selectedBatchId) {
      setSelectedBatchId(undefined);
      const next = new URLSearchParams(searchParams);
      next.delete('batchId');
      setSearchParams(next, { replace: true });
    }
  }, [batchRecords, searchParams, selectedBatchId, setSearchParams]);

  const selectedSummary = useMemo(
    () => batchRecords.find((record) => getBatchId(record) === selectedBatchId),
    [batchRecords, selectedBatchId],
  );
  const activeBatchId = selectedBatchId ?? getBatchId(batchRecords[0]);
  const detailQuery = usePayrollBatchDetailQuery(activeBatchId ?? 0, {
    enabled: Boolean(activeBatchId),
  });
  const activeBatch = detailQuery.data ?? selectedSummary;
  const revision = activeBatch?.batchRevision ?? 1;
  const importItemsQuery = usePayrollImportItemsQuery(activeBatchId ?? 0, {
    enabled: Boolean(activeBatchId),
  });
  const ledgerQuery = usePayrollLedgerQuery(activeBatchId ?? 0, {
    enabled: Boolean(activeBatchId),
  });
  const confirmationQuery = usePayrollConfirmationSummaryQuery(activeBatchId ?? 0, {
    enabled: Boolean(activeBatchId),
  });
  const distributionsQuery = usePayrollDistributionsQuery(
    useMemo(
      () => ({ current: 1, pageSize: 30, batchId: activeBatchId, batchRevision: revision }),
      [activeBatchId, revision],
    ),
    { enabled: Boolean(activeBatchId) },
  );
  const reconciliationsQuery = usePayrollReconciliationsQuery(
    useMemo(
      () => ({ current: 1, pageSize: 30, batchId: activeBatchId, batchRevision: revision }),
      [activeBatchId, revision],
    ),
    { enabled: Boolean(activeBatchId) },
  );

  const distributions = useMemo(
    () => distributionsQuery.data?.records ?? [],
    [distributionsQuery.data?.records],
  );
  const reconciliations = useMemo(
    () => reconciliationsQuery.data?.records ?? [],
    [reconciliationsQuery.data?.records],
  );
  const primaryDistribution = distributions[0];
  const distributionItemsQuery = usePayrollDistributionItemsQuery(primaryDistribution?.id ?? 0, {
    enabled: Boolean(primaryDistribution?.id),
  });
  const routeBlockedCount = useMemo(
    () =>
      (distributionItemsQuery.data ?? []).filter((item) => isSettlementRouteBlocked(item)).length,
    [distributionItemsQuery.data],
  );
  const staleDistributionCount = useMemo(
    () =>
      distributions.filter((item) => item.batchRevision != null && item.batchRevision !== revision)
        .length,
    [distributions, revision],
  );

  const snapshot = useMemo<PayrollOperationsSnapshot | undefined>(() => {
    if (!activeBatch) {
      return undefined;
    }
    return {
      batch: activeBatch,
      importItemCount: importItemsQuery.data?.length ?? 0,
      ledger: ledgerQuery.data,
      confirmation: confirmationQuery.data,
      distributions,
      reconciliations,
      routeBlockedCount,
      staleDistributionCount,
    };
  }, [
    activeBatch,
    confirmationQuery.data,
    distributions,
    importItemsQuery.data?.length,
    ledgerQuery.data,
    reconciliations,
    routeBlockedCount,
    staleDistributionCount,
  ]);
  const stages = useMemo(() => (snapshot ? getPayrollOperationsStages(snapshot) : []), [snapshot]);
  const queue = useMemo(() => (snapshot ? getPayrollOperationsQueue(snapshot) : []), [snapshot]);
  const nextAction = getNextAction(stages, queue);
  const evidenceMeta = activeBatch ? getCalculationEvidenceMeta(activeBatch) : undefined;
  const evidenceExpected = Boolean(
    activeBatch &&
    ['calculating', 'calculated'].includes(
      normalizeCalculationStatus(activeBatch.calculationStatus ?? activeBatch.computeStatus),
    ),
  );

  const applyFilters = useCallback(() => {
    const next = new URLSearchParams(searchParams);
    next.delete('batchId');
    if (periodInput.trim()) next.set('period', periodInput.trim());
    else next.delete('period');
    if (typeInput) next.set('payrollType', typeInput);
    else next.delete('payrollType');
    if (statusInput) next.set('status', statusInput);
    else next.delete('status');
    setSelectedBatchId(undefined);
    setSearchParams(next);
  }, [periodInput, searchParams, setSearchParams, statusInput, typeInput]);

  const resetFilters = useCallback(() => {
    setPeriodInput('');
    setTypeInput('');
    setStatusInput('');
    setSelectedBatchId(undefined);
    setSearchParams({});
  }, [setSearchParams]);

  const selectBatch = useCallback(
    (record: PayrollBatchSummaryDto) => {
      const batchId = getBatchId(record);
      if (!batchId) return;
      setSelectedBatchId(batchId);
      const next = new URLSearchParams(searchParams);
      next.set('batchId', String(batchId));
      setSearchParams(next, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const openAction = useCallback(
    (action: PayrollOperationsAction) => {
      if (!activeBatchId) return;
      switch (action) {
        case 'entry':
          navigate(`/payroll/batches/${activeBatchId}/entry`);
          break;
        case 'ledger':
          navigate(`/payroll/batches/${activeBatchId}/ledger`);
          break;
        case 'confirmation':
          navigate(`/payroll/confirmations?batchId=${activeBatchId}`);
          break;
        case 'approval':
          navigate(
            activeBatch?.approvalWorkflowId
              ? `/approval/workflows?keyword=${activeBatch.approvalWorkflowId}`
              : '/approval/workflows',
          );
          break;
        case 'distribution':
          navigate(`/payroll/distributions?batchId=${activeBatchId}&batchRevision=${revision}`);
          break;
        case 'reconciliation':
          navigate(`/payroll/reconciliations?batchId=${activeBatchId}&batchRevision=${revision}`);
          break;
      }
    },
    [activeBatch?.approvalWorkflowId, activeBatchId, navigate, revision],
  );

  const isDetailLoading = Boolean(
    activeBatchId &&
    (detailQuery.isLoading ||
      importItemsQuery.isLoading ||
      ledgerQuery.isLoading ||
      confirmationQuery.isLoading ||
      distributionsQuery.isLoading ||
      reconciliationsQuery.isLoading),
  );

  const summaryMetrics = [
    {
      label: '员工数',
      value: activeBatch?.totalEmployees ?? ledgerQuery.data?.totalEmployees ?? '—',
    },
    {
      label: '实发金额',
      value: formatCurrency(
        activeBatch?.netTotal ?? ledgerQuery.data?.netTotal,
        activeBatch?.currency,
      ),
    },
    { label: '待处理事项', value: queue.reduce((total, item) => total + item.count, 0) },
    { label: '计算证据', value: evidenceExpected ? (evidenceMeta?.text ?? '—') : '待计算' },
  ];

  return (
    <PageContainer
      className="payroll-page-shell payroll-operations-page"
      header={{
        title: '薪资运营',
        subTitle: '围绕一个批次推进录入、核算、确认、审批、发放与对账',
        extra: [
          <Button
            key="compliance"
            icon={<FileProtectOutlined />}
            onClick={() => navigate('/payroll/compliance')}
          >
            合规核算
          </Button>,
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => batchesQuery.refetch()}>
            刷新
          </Button>,
        ],
      }}
    >
      <div className="payroll-operations-layout">
        <aside className="payroll-operations-sidebar" aria-label="薪资批次选择">
          <section className="payroll-operations-filter">
            <div className="payroll-operations-panel-heading">
              <div>
                <Title level={5}>批次队列</Title>
                <Text type="secondary">按期间和流程状态定位当前运行</Text>
              </div>
              <Badge count={batchesQuery.data?.total ?? 0} overflowCount={999} />
            </div>
            <Space direction="vertical" size={10} style={{ width: '100%' }}>
              <Input
                value={periodInput}
                placeholder="期间，例如 2026-06"
                onChange={(event) => setPeriodInput(event.target.value)}
                onPressEnter={applyFilters}
              />
              <Select
                value={typeInput}
                options={payrollTypeOptions}
                onChange={setTypeInput}
                style={{ width: '100%' }}
              />
              <Select
                value={statusInput}
                options={statusOptions}
                onChange={setStatusInput}
                style={{ width: '100%' }}
              />
              <Space style={{ width: '100%' }}>
                <Button type="primary" block onClick={applyFilters}>
                  应用筛选
                </Button>
                <Button block onClick={resetFilters}>
                  重置
                </Button>
              </Space>
            </Space>
          </section>

          <section className="payroll-operations-batch-list" aria-label="批次列表">
            {batchesQuery.isLoading ? (
              <div className="payroll-operations-empty">
                <Spin />
              </div>
            ) : batchRecords.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无匹配批次" />
            ) : (
              batchRecords.map((record) => {
                const batchId = getBatchId(record);
                const selected = batchId === activeBatchId;
                const evidence = getCalculationEvidenceMeta(record);
                const hasWarnings = (record.warnings?.length ?? 0) > 0;
                return (
                  <button
                    type="button"
                    className={`payroll-operations-batch-row${selected ? ' is-selected' : ''}`}
                    key={batchId ?? `${record.periodLabel}-${record.updatedAt}`}
                    onClick={() => selectBatch(record)}
                  >
                    <span className="payroll-operations-batch-row-main">
                      <span className="payroll-operations-batch-row-title">
                        <strong>
                          {record.periodLabel || record.batchNo || `批次 #${batchId ?? '—'}`}
                        </strong>
                        {record.batchRevision != null && (
                          <Tag>{getBatchRevisionText(record.batchRevision)}</Tag>
                        )}
                      </span>
                      <span className="payroll-operations-batch-row-meta">
                        {getPayrollTypeLabel(record.payrollType)} · {record.totalEmployees ?? '—'}{' '}
                        人 · {formatCurrency(record.netTotal, record.currency)}
                      </span>
                    </span>
                    <span className="payroll-operations-batch-row-status">
                      {statusTag(record)}
                      {hasWarnings ? (
                        <WarningOutlined className="is-warning" />
                      ) : (
                        <Tooltip title={evidence.text}>
                          <FileProtectOutlined />
                        </Tooltip>
                      )}
                    </span>
                  </button>
                );
              })
            )}
          </section>
        </aside>

        <main className="payroll-operations-main">
          {!activeBatch || !snapshot ? (
            <section className="payroll-operations-panel payroll-operations-empty-main">
              <Empty description="请选择一个薪资批次" />
            </section>
          ) : (
            <>
              <section className="payroll-operations-panel payroll-operations-identity">
                <div className="payroll-operations-identity-copy">
                  <Space size={8} wrap>
                    <Title level={3}>
                      {activeBatch.periodLabel || activeBatch.batchNo || `批次 #${activeBatchId}`}
                    </Title>
                    {statusTag(activeBatch)}
                    <Tag>{getBatchRevisionText(activeBatch.batchRevision)}</Tag>
                  </Space>
                  <div className="payroll-operations-identity-meta">
                    <span>批次 ID：{activeBatchId}</span>
                    <span>用工类型：{getPayrollTypeLabel(activeBatch.payrollType)}</span>
                    <span>币种：{activeBatch.currency || 'CNY'}</span>
                    {activeBatch.batchNo && <span>批次号：{activeBatch.batchNo}</span>}
                  </div>
                </div>
                <Space wrap>
                  <Button
                    type="primary"
                    icon={<ArrowRightOutlined />}
                    onClick={() => openAction(nextAction)}
                  >
                    {actionLabel[nextAction]}
                  </Button>
                  <Button icon={<FileSearchOutlined />} onClick={() => openAction('ledger')}>
                    台账
                  </Button>
                </Space>
              </section>

              <section className="payroll-operations-metrics">
                {summaryMetrics.map((metric) => (
                  <div className="payroll-operations-metric" key={metric.label}>
                    <Text type="secondary">{metric.label}</Text>
                    <strong>{metric.value}</strong>
                  </div>
                ))}
              </section>

              <section className="payroll-operations-panel payroll-operations-stage-panel">
                <div className="payroll-operations-panel-heading">
                  <div>
                    <Title level={5}>运行阶段</Title>
                    <Text type="secondary">当前批次的状态和下一步动作集中在这里</Text>
                  </div>
                  <Tag color={queue.length > 0 ? 'warning' : 'success'}>
                    {queue.length > 0 ? `${queue.length} 类待处理事项` : '无阻断事项'}
                  </Tag>
                </div>
                <div className="payroll-operations-stage-rail">
                  {stages.map((stage, index) => (
                    <React.Fragment key={stage.key}>
                      <button
                        type="button"
                        className={`payroll-operations-stage is-${stage.state}`}
                        onClick={() => openAction(stage.key)}
                      >
                        <span className="payroll-operations-stage-icon">
                          {stageStateIcon[stage.state]}
                        </span>
                        <span className="payroll-operations-stage-label">{stage.label}</span>
                        <span className="payroll-operations-stage-description">
                          {stage.description}
                        </span>
                      </button>
                      {index < stages.length - 1 && (
                        <ArrowRightOutlined className="payroll-operations-stage-arrow" />
                      )}
                    </React.Fragment>
                  ))}
                </div>
              </section>

              <div className="payroll-operations-content-grid">
                <section className="payroll-operations-panel payroll-operations-queue-panel">
                  <div className="payroll-operations-panel-heading">
                    <div>
                      <Title level={5}>待处理事项</Title>
                      <Text type="secondary">只显示影响当前批次继续推进的事项</Text>
                    </div>
                    {isDetailLoading && <Spin size="small" />}
                  </div>
                  {queue.length === 0 ? (
                    <Alert
                      type="success"
                      showIcon
                      icon={<CheckCircleOutlined />}
                      title="当前批次没有检测到阻断事项"
                    />
                  ) : (
                    <div className="payroll-operations-queue-list">
                      {queue.map((item) => (
                        <div className="payroll-operations-queue-item" key={item.key}>
                          <span className={`payroll-operations-queue-icon is-${item.tone}`}>
                            {item.tone === 'error' ? (
                              <ExclamationCircleOutlined />
                            ) : (
                              <ClockCircleOutlined />
                            )}
                          </span>
                          <span className="payroll-operations-queue-copy">
                            <strong>{item.title}</strong>
                            <Text type="secondary">{item.description}</Text>
                          </span>
                          <span className="payroll-operations-queue-action">
                            <Tag color={item.tone === 'default' ? undefined : item.tone}>
                              {item.count}
                            </Tag>
                            {item.action && (
                              <Button
                                type="link"
                                size="small"
                                onClick={() => openAction(item.action!)}
                              >
                                处理 <ArrowRightOutlined />
                              </Button>
                            )}
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </section>

                <section className="payroll-operations-panel payroll-operations-evidence-panel">
                  <div className="payroll-operations-panel-heading">
                    <div>
                      <Title level={5}>计算证据</Title>
                      <Text type="secondary">用于确认当前 revision 对应的输入和规则</Text>
                    </div>
                    {evidenceMeta && (
                      <Tag color={evidenceMeta.color}>
                        {evidenceExpected ? evidenceMeta.text : '待计算'}
                      </Tag>
                    )}
                  </div>
                  <div className="payroll-operations-evidence-lines">
                    <div>
                      <span>引擎版本</span>
                      <strong>{activeBatch.calculationEngineVersion || '—'}</strong>
                    </div>
                    <div>
                      <span>输入快照</span>
                      <Text
                        copyable={{
                          icon: <CopyOutlined />,
                          text: activeBatch.inputSnapshotHash || '',
                        }}
                      >
                        {getSnapshotHashPreview(activeBatch.inputSnapshotHash)}
                      </Text>
                    </div>
                    <div>
                      <span>规则快照</span>
                      <Text
                        copyable={{
                          icon: <CopyOutlined />,
                          text: activeBatch.ruleSnapshotHash || '',
                        }}
                      >
                        {getSnapshotHashPreview(activeBatch.ruleSnapshotHash)}
                      </Text>
                    </div>
                  </div>
                  {evidenceExpected && evidenceMeta?.color !== 'success' && (
                    <Alert
                      className="payroll-operations-inline-alert"
                      type="warning"
                      showIcon
                      title="此批次的可追溯证据尚未完整落盘"
                      description="请先确认计算输入、规则版本和引擎版本，再继续确认或发放。"
                    />
                  )}
                </section>
              </div>

              <section className="payroll-operations-panel payroll-operations-detail-panel">
                <div className="payroll-operations-panel-heading">
                  <div>
                    <Title level={5}>批次工作区</Title>
                    <Text type="secondary">
                      具体执行仍保留在原有阶段页面，入口带入当前批次上下文
                    </Text>
                  </div>
                  <Select
                    value={view}
                    onChange={setView}
                    options={[
                      { label: '运行摘要', value: 'summary' },
                      { label: '计算证据', value: 'evidence' },
                      { label: '阶段入口', value: 'stages' },
                    ]}
                    style={{ width: 124 }}
                  />
                </div>
                {view === 'summary' && (
                  <div className="payroll-operations-summary-grid">
                    <div>
                      <span>确认模式</span>
                      <strong>
                        {activeBatch.confirmationRequired === false
                          ? '无需确认'
                          : activeBatch.confirmationMode || '待定'}
                      </strong>
                    </div>
                    <div>
                      <span>待确认人数</span>
                      <strong>{confirmationQuery.data?.pendingCount ?? '—'}</strong>
                    </div>
                    <div>
                      <span>发放单</span>
                      <strong>{distributions.length || '—'}</strong>
                    </div>
                    <div>
                      <span>对账任务</span>
                      <strong>{reconciliations.length || '—'}</strong>
                    </div>
                  </div>
                )}
                {view === 'evidence' && (
                  <div className="payroll-operations-evidence-table">
                    <div>
                      <span>批次 revision</span>
                      <strong>{getBatchRevisionText(activeBatch.batchRevision)}</strong>
                    </div>
                    <div>
                      <span>输入快照摘要</span>
                      <strong>{activeBatch.inputSnapshotHash || '—'}</strong>
                    </div>
                    <div>
                      <span>规则快照摘要</span>
                      <strong>{activeBatch.ruleSnapshotHash || '—'}</strong>
                    </div>
                    <div>
                      <span>计算引擎版本</span>
                      <strong>{activeBatch.calculationEngineVersion || '—'}</strong>
                    </div>
                  </div>
                )}
                {view === 'stages' && (
                  <div className="payroll-operations-entry-list">
                    {stages.map((stage) => (
                      <button type="button" key={stage.key} onClick={() => openAction(stage.key)}>
                        <span className="payroll-operations-entry-icon">
                          {stageIcon[stage.key]}
                        </span>
                        <span>
                          <strong>{stage.label}</strong>
                          <Text type="secondary">{stage.description}</Text>
                        </span>
                        <ArrowRightOutlined />
                      </button>
                    ))}
                  </div>
                )}
              </section>
            </>
          )}
        </main>
      </div>
    </PageContainer>
  );
};

export default PayrollOperations;
