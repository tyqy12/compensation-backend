import React, { useCallback, useMemo, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Spin,
  Statistic,
  Steps,
  Table,
  Tag,
  Tooltip,
  Typography,
  type DescriptionsProps,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import {
  CheckCircleOutlined,
  FileSearchOutlined,
  RedoOutlined,
  ReloadOutlined,
  SendOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  usePayrollDistributionDetailQuery,
  usePayrollDistributionItemsQuery,
  usePayrollDistributionReconciliationQuery,
  usePayrollDistributionsQuery,
  usePayrollBatchDetailQuery,
  useRetryPayrollDistributionMutation,
  type PayrollDistributionListParams,
} from '@services/queries/payroll';
import { useHasAction } from '@services/queries/rbac';
import { withActionPrefix } from '@utils/error';
import type {
  PayrollDistributionDto,
  PayrollDistributionItemDto,
  PayrollReconciliationTaskDto,
} from '@types/openapi';
import {
  getBatchRevisionText,
  getDistributionStatusMeta,
  getSettlementRouteFailureMeta,
  isSettlementRouteBlocked,
} from './components/payrollFlow';

const { Text, Paragraph } = Typography;

const payrollTypeMeta: Record<string, { text: string; color: string }> = {
  full_time: { text: '全职', color: 'blue' },
  part_time: { text: '兼职', color: 'gold' },
  contractor: { text: '外包', color: 'purple' },
};

const approvalStatusMeta: Record<string, { text: string; color: string }> = {
  pending: { text: '待提交', color: 'default' },
  in_progress: { text: '审批中', color: 'processing' },
  approved: { text: '审批通过', color: 'success' },
  rejected: { text: '审批驳回', color: 'error' },
  cancelled: { text: '已取消', color: 'default' },
};

const itemStatusMeta: Record<string, { text: string; color: string }> = {
  pending: { text: '待发放', color: 'default' },
  retrying: { text: '重试中', color: 'processing' },
  success: { text: '成功', color: 'success' },
  failed: { text: '失败', color: 'error' },
};

const paymentRecordStatusMeta: Record<string, { text: string; color: string }> = {
  pending: { text: '待处理', color: 'default' },
  processing: { text: '处理中', color: 'processing' },
  success: { text: '成功', color: 'success' },
  failed: { text: '失败', color: 'error' },
  cancelled: { text: '已取消', color: 'default' },
};

const reconciliationTaskStatusMeta: Record<string, { text: string; color: string }> = {
  pending: { text: '待对账', color: 'default' },
  processing: { text: '对账中', color: 'processing' },
  completed: { text: '已完成', color: 'success' },
  failed: { text: '失败', color: 'error' },
};

const reconciliationResultMeta: Record<string, { text: string; color: string }> = {
  matched: { text: '一致', color: 'success' },
  mismatch: { text: '不一致', color: 'error' },
  resolved: { text: '已解决', color: 'processing' },
};

const distributionStatusOptions = [
  { label: '计划中', value: 'planned' },
  { label: '提交中', value: 'submitting' },
  { label: '处理中', value: 'processing' },
  { label: '全部完成', value: 'completed' },
  { label: '部分完成', value: 'partially_completed' },
  { label: '发放失败', value: 'failed' },
  { label: '已取消', value: 'cancelled' },
  { label: '已作废', value: 'superseded' },
];

const normalizeStatus = (value?: string) =>
  String(value ?? '')
    .trim()
    .toLowerCase();

const isRetryableDistribution = (status?: string) =>
  ['failed', 'partially_completed'].includes(normalizeStatus(status));

const parsePositiveInt = (value: string | null | undefined, fallback?: number) => {
  const next = Number.parseInt(String(value ?? ''), 10);
  if (!Number.isFinite(next) || next <= 0) {
    return fallback;
  }
  return next;
};

const formatCurrency = (value?: number | null, currency = 'CNY') => {
  if (value === undefined || value === null) {
    return '—';
  }
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(value);
};

const formatDate = (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD') : '—');

const formatDateTime = (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—');

const renderTag = (meta: { text: string; color: string } | undefined, fallback?: string) =>
  meta ? <Tag color={meta.color}>{meta.text}</Tag> : fallback || '—';

const getApprovalTag = (status?: string) =>
  renderTag(approvalStatusMeta[normalizeStatus(status)], status || '—');

const getReconciliationTag = (task?: PayrollReconciliationTaskDto | null) => {
  if (!task) {
    return <Tag>未生成</Tag>;
  }
  const resultMeta = reconciliationResultMeta[normalizeStatus(task.result)];
  if (resultMeta) {
    return <Tag color={resultMeta.color}>{resultMeta.text}</Tag>;
  }
  const taskMeta = reconciliationTaskStatusMeta[normalizeStatus(task.taskStatus)];
  return renderTag(taskMeta, task.taskStatus || '—');
};

const getDistributionCurrentStep = (
  detail?: PayrollDistributionDto,
  reconciliation?: PayrollReconciliationTaskDto | null,
) => {
  const distributionStatus = normalizeStatus(detail?.distributionStatus);
  const approvalStatus = normalizeStatus(detail?.approvalStatus);
  if (reconciliation?.id) {
    return 4;
  }
  if (
    ['completed', 'partially_completed', 'failed', 'cancelled', 'superseded'].includes(
      distributionStatus,
    )
  ) {
    return 3;
  }
  if (['planned', 'submitting', 'processing'].includes(distributionStatus)) {
    return 2;
  }
  if (approvalStatus) {
    return 1;
  }
  return 0;
};

const buildSearchParams = (filters: PayrollDistributionListParams, distributionId?: number) => {
  const params = new URLSearchParams();
  if ((filters.current ?? 1) > 1) {
    params.set('current', String(filters.current));
  }
  if ((filters.pageSize ?? 10) !== 10) {
    params.set('pageSize', String(filters.pageSize));
  }
  if (filters.batchId) {
    params.set('batchId', String(filters.batchId));
  }
  if (filters.batchRevision) {
    params.set('batchRevision', String(filters.batchRevision));
  }
  if (filters.status) {
    params.set('status', filters.status);
  }
  if (distributionId) {
    params.set('distributionId', String(distributionId));
  }
  return params;
};

const Distributions: React.FC = () => {
  const navigate = useNavigate();
  const { message } = AntdApp.useApp();
  const [searchParams, setSearchParams] = useSearchParams();
  const canRetry = useHasAction('api.payroll.distributions.retry');
  const retryDistributionMutation = useRetryPayrollDistributionMutation();
  const [retryingDistributionId, setRetryingDistributionId] = useState<number>();

  const initialFilters = useMemo<PayrollDistributionListParams>(
    () => ({
      current: parsePositiveInt(searchParams.get('current'), 1) ?? 1,
      pageSize: parsePositiveInt(searchParams.get('pageSize'), 10) ?? 10,
      batchId: parsePositiveInt(searchParams.get('batchId')),
      batchRevision: parsePositiveInt(searchParams.get('batchRevision')),
      status: searchParams.get('status') || undefined,
    }),
    [searchParams],
  );

  const initialDistributionId = parsePositiveInt(searchParams.get('distributionId'));

  const [filters, setFilters] = useState<PayrollDistributionListParams>(initialFilters);
  const [batchIdInput, setBatchIdInput] = useState<number | undefined>(initialFilters.batchId);
  const [batchRevisionInput, setBatchRevisionInput] = useState<number | undefined>(
    initialFilters.batchRevision,
  );
  const [statusInput, setStatusInput] = useState<string | undefined>(initialFilters.status);
  const [selectedDistributionId, setSelectedDistributionId] = useState<number | undefined>(
    initialDistributionId,
  );

  const distributionsQuery = usePayrollDistributionsQuery(filters);
  const distributionDetailQuery = usePayrollDistributionDetailQuery(selectedDistributionId ?? 0, {
    enabled: Boolean(selectedDistributionId),
  });
  const distributionItemsQuery = usePayrollDistributionItemsQuery(selectedDistributionId ?? 0, {
    enabled: Boolean(selectedDistributionId),
  });
  const distributionReconciliationQuery = usePayrollDistributionReconciliationQuery(
    selectedDistributionId ?? 0,
    {
      enabled: Boolean(selectedDistributionId),
    },
  );
  const records = useMemo(
    () => distributionsQuery.data?.records ?? [],
    [distributionsQuery.data?.records],
  );
  const selectedRecord = useMemo(
    () => records.find((item) => item.id === selectedDistributionId),
    [records, selectedDistributionId],
  );
  const detail = distributionDetailQuery.data ?? selectedRecord;
  const reconciliation = distributionReconciliationQuery.data ?? null;
  const currentBatchQuery = usePayrollBatchDetailQuery(detail?.batchId ?? 0, {
    enabled: Boolean(detail?.batchId),
  });
  const drawerOpen = Boolean(selectedDistributionId);
  const drawerLoading =
    distributionDetailQuery.isLoading ||
    distributionItemsQuery.isLoading ||
    distributionReconciliationQuery.isLoading ||
    currentBatchQuery.isLoading;
  const routeBlockedItems = useMemo(
    () => (distributionItemsQuery.data ?? []).filter(isSettlementRouteBlocked),
    [distributionItemsQuery.data],
  );
  const currentBatchLoaded = Boolean(currentBatchQuery.data);
  const currentBatchRevision = currentBatchQuery.data?.batchRevision ?? 1;
  const distributionRevision = detail?.batchRevision ?? 1;
  const distributionIsStale =
    normalizeStatus(detail?.distributionStatus) === 'superseded' ||
    (currentBatchLoaded && currentBatchRevision !== distributionRevision);

  const retryDistribution = useCallback(
    async (distributionId?: number) => {
      if (!distributionId) {
        message.warning('缺少发放单 ID，无法重试');
        return;
      }
      setRetryingDistributionId(distributionId);
      try {
        await retryDistributionMutation.mutateAsync(distributionId);
        message.success('发放单重试已提交');
      } catch (error) {
        message.error(withActionPrefix('发放单重试失败', error));
      } finally {
        setRetryingDistributionId(undefined);
      }
    },
    [message, retryDistributionMutation],
  );

  const updateRoute = useCallback(
    (nextFilters: PayrollDistributionListParams, distributionId?: number) => {
      setSearchParams(buildSearchParams(nextFilters, distributionId));
    },
    [setSearchParams],
  );

  const handleSearch = useCallback(() => {
    const nextFilters: PayrollDistributionListParams = {
      ...filters,
      current: 1,
      batchId: batchIdInput,
      batchRevision: batchRevisionInput,
      status: statusInput,
    };
    setFilters(nextFilters);
    updateRoute(nextFilters, selectedDistributionId);
  }, [batchIdInput, batchRevisionInput, filters, selectedDistributionId, statusInput, updateRoute]);

  const handleReset = useCallback(() => {
    const nextFilters: PayrollDistributionListParams = {
      current: 1,
      pageSize: 10,
    };
    setBatchIdInput(undefined);
    setBatchRevisionInput(undefined);
    setStatusInput(undefined);
    setFilters(nextFilters);
    updateRoute(nextFilters, selectedDistributionId);
  }, [selectedDistributionId, updateRoute]);

  const handlePaginationChange = useCallback(
    (pagination: TablePaginationConfig) => {
      const nextFilters: PayrollDistributionListParams = {
        ...filters,
        current: pagination.current ?? filters.current ?? 1,
        pageSize: pagination.pageSize ?? filters.pageSize ?? 10,
      };
      setFilters(nextFilters);
      updateRoute(nextFilters, selectedDistributionId);
    },
    [filters, selectedDistributionId, updateRoute],
  );

  const openDistribution = useCallback(
    (distributionId?: number) => {
      if (!distributionId) {
        message.warning('缺少发放单 ID，无法查看详情');
        return;
      }
      setSelectedDistributionId(distributionId);
      updateRoute(filters, distributionId);
    },
    [filters, message, updateRoute],
  );

  const closeDrawer = useCallback(() => {
    setSelectedDistributionId(undefined);
    updateRoute(filters, undefined);
  }, [filters, updateRoute]);

  const summary = useMemo(() => {
    const processing = records.filter((item) =>
      ['submitting', 'processing'].includes(normalizeStatus(item.distributionStatus)),
    ).length;
    const completed = records.filter(
      (item) => normalizeStatus(item.distributionStatus) === 'completed',
    ).length;
    const abnormal = records.filter((item) =>
      ['failed', 'partially_completed', 'cancelled', 'superseded'].includes(
        normalizeStatus(item.distributionStatus),
      ),
    ).length;
    const totalAmount = records.reduce((sum, item) => sum + (item.totalAmount ?? 0), 0);
    const actualAmount = records.reduce((sum, item) => sum + (item.actualAmount ?? 0), 0);
    return {
      total: distributionsQuery.data?.total ?? 0,
      processing,
      completed,
      abnormal,
      totalAmount,
      actualAmount,
    };
  }, [distributionsQuery.data?.total, records]);

  const columns = useMemo<ColumnsType<PayrollDistributionDto>>(
    () => [
      {
        title: '发放单',
        dataIndex: 'distributionNo',
        width: 240,
        render: (_, record) => (
          <Space orientation="vertical" size={0}>
            <Space size={8} wrap>
              <Text strong>{record.distributionNo || '—'}</Text>
              {record.batchRevision != null && (
                <Tag>{getBatchRevisionText(record.batchRevision)}</Tag>
              )}
            </Space>
            <Text type="secondary">批次：#{record.batchId ?? '—'}</Text>
            {record.paymentBatchNo && (
              <Text type="secondary">支付批次：{record.paymentBatchNo}</Text>
            )}
          </Space>
        ),
      },
      {
        title: '期间/用工',
        dataIndex: 'periodLabel',
        width: 180,
        render: (_, record) => (
          <Space orientation="vertical" size={4}>
            <Text>{record.periodLabel || '—'}</Text>
            {record.payrollType
              ? renderTag(payrollTypeMeta[normalizeStatus(record.payrollType)], record.payrollType)
              : '—'}
          </Space>
        ),
      },
      {
        title: '发放状态',
        dataIndex: 'distributionStatus',
        width: 130,
        render: (_, record) =>
          renderTag(
            getDistributionStatusMeta(record.distributionStatus),
            record.distributionStatus || '—',
          ),
      },
      {
        title: '审批/对账',
        key: 'flowStatus',
        width: 210,
        render: (_, record) => (
          <Space orientation="vertical" size={4}>
            <Space size={8} wrap>
              <Text type="secondary">审批</Text>
              {getApprovalTag(record.approvalStatus)}
            </Space>
            <Space size={8} wrap>
              <Text type="secondary">对账</Text>
              {getReconciliationTag(
                record.reconciliationTaskId
                  ? {
                      id: record.reconciliationTaskId,
                      taskStatus: record.reconciliationTaskStatus,
                      result: record.reconciliationResult,
                    }
                  : null,
              )}
            </Space>
          </Space>
        ),
      },
      {
        title: '金额/人数',
        key: 'amountInfo',
        width: 220,
        render: (_, record) => (
          <Space orientation="vertical" size={2}>
            <Text>应发：{formatCurrency(record.totalAmount)}</Text>
            <Text>实发：{formatCurrency(record.actualAmount)}</Text>
            <Text type="secondary">
              人数：{record.totalCount ?? 0}，尝试：{record.currentAttempt ?? 0}/
              {record.retryLimit ?? 0}
            </Text>
          </Space>
        ),
      },
      {
        title: '计划/更新时间',
        key: 'timeInfo',
        width: 180,
        render: (_, record) => (
          <Space orientation="vertical" size={2}>
            <Text>计划：{formatDate(record.scheduledDate)}</Text>
            <Text type="secondary">更新：{formatDateTime(record.updateTime)}</Text>
          </Space>
        ),
      },
      {
        title: '操作',
        key: 'actions',
        width: 220,
        render: (_, record) => (
          <Space size={0} wrap>
            <Button type="link" size="small" onClick={() => openDistribution(record.id)}>
              详情
            </Button>
            <Button
              type="link"
              size="small"
              disabled={!record.approvalWorkflowId}
              onClick={() => navigate(`/approval/workflows?keyword=${record.approvalWorkflowId}`)}
            >
              审批流
            </Button>
            <Button
              type="link"
              size="small"
              disabled={!record.paymentBatchNo}
              onClick={() => navigate(`/payments/batches/${record.paymentBatchNo}`)}
            >
              支付批次
            </Button>
            <Button
              type="link"
              size="small"
              disabled={!record.reconciliationTaskId}
              onClick={() =>
                navigate(
                  `/payroll/reconciliations?batchId=${record.batchId ?? ''}&taskId=${record.reconciliationTaskId ?? ''}`,
                )
              }
            >
              对账任务
            </Button>
          </Space>
        ),
      },
    ],
    [navigate, openDistribution],
  );

  const itemColumns = useMemo<ColumnsType<PayrollDistributionItemDto>>(
    () => [
      {
        title: '员工/账户',
        key: 'employee',
        width: 220,
        render: (_, record) => (
          <Space orientation="vertical" size={0}>
            <Text strong>{record.employeeName || '—'}</Text>
            <Text type="secondary">员工：#{record.employeeId ?? '—'}</Text>
            <Text type="secondary">收款：{record.accountNoMasked || '—'}</Text>
          </Space>
        ),
      },
      {
        title: '收款信息',
        key: 'account',
        width: 220,
        render: (_, record) => (
          <Space orientation="vertical" size={0}>
            <Text>{record.recipientName || '—'}</Text>
            <Text type="secondary">账户类型：{record.accountType || '—'}</Text>
            <Text type="secondary">
              方式：{record.paymentMethod || '—'} / 渠道：{record.providerCode || '—'}
            </Text>
          </Space>
        ),
      },
      {
        title: '金额',
        dataIndex: 'amount',
        width: 120,
        align: 'right',
        render: (_, record) => formatCurrency(record.amount),
      },
      {
        title: '明细状态',
        dataIndex: 'itemStatus',
        width: 120,
        render: (_, record) =>
          isSettlementRouteBlocked(record) ? (
            <Tag color="error">收款路由阻断</Tag>
          ) : (
            renderTag(itemStatusMeta[normalizeStatus(record.itemStatus)], record.itemStatus || '—')
          ),
      },
      {
        title: '支付记录',
        key: 'paymentRecord',
        width: 220,
        render: (_, record) => (
          <Space orientation="vertical" size={0}>
            <Text>{record.paymentRecordId ? `#${record.paymentRecordId}` : '—'}</Text>
            {renderTag(
              paymentRecordStatusMeta[normalizeStatus(record.paymentRecordStatus)],
              record.paymentRecordStatus || '—',
            )}
            {record.paymentTime && (
              <Text type="secondary">支付：{formatDateTime(record.paymentTime)}</Text>
            )}
          </Space>
        ),
      },
      {
        title: '重试/失败原因',
        key: 'retryInfo',
        width: 240,
        render: (_, record) => {
          const routeBlocked = isSettlementRouteBlocked(record);
          const routeMeta = getSettlementRouteFailureMeta(record.errorCode);
          const reason = record.failureReason || record.errorMsg || '—';
          return (
            <Space orientation="vertical" size={0}>
              <Text>重试次数：{record.retryCount ?? 0}</Text>
              {routeBlocked && <Tag color={routeMeta.color}>{routeMeta.text}</Tag>}
              <Tooltip title={reason}>
                <Text type={routeBlocked ? 'danger' : 'secondary'} ellipsis>
                  {reason}
                </Text>
              </Tooltip>
              {routeBlocked && <Text type="secondary">修正收款信息后再重试</Text>}
            </Space>
          );
        },
      },
    ],
    [],
  );

  const detailItems = useMemo<DescriptionsProps['items']>(() => {
    if (!detail) {
      return [];
    }
    return [
      {
        key: 'distributionNo',
        label: '发放单号',
        children: detail.distributionNo || '—',
      },
      {
        key: 'batch',
        label: '关联批次',
        children: detail.batchId ? (
          <Space size={8} wrap>
            <Text>{`#${detail.batchId} ${getBatchRevisionText(detail.batchRevision)}`}</Text>
            {distributionIsStale ? (
              <Tag color="error">旧版本</Tag>
            ) : currentBatchLoaded ? (
              <Tag color="success">版本一致</Tag>
            ) : (
              <Tag>待校验</Tag>
            )}
          </Space>
        ) : (
          '—'
        ),
      },
      {
        key: 'period',
        label: '计薪期间',
        children: detail.periodLabel || '—',
      },
      {
        key: 'payrollType',
        label: '用工类型',
        children: detail.payrollType
          ? renderTag(payrollTypeMeta[normalizeStatus(detail.payrollType)], detail.payrollType)
          : '—',
      },
      {
        key: 'status',
        label: '发放状态',
        children: renderTag(
          getDistributionStatusMeta(detail.distributionStatus),
          detail.distributionStatus || '—',
        ),
      },
      {
        key: 'scheduledDate',
        label: '计划发放日',
        children: formatDate(detail.scheduledDate),
      },
      {
        key: 'amount',
        label: '应发总额',
        children: formatCurrency(detail.totalAmount),
      },
      {
        key: 'actualAmount',
        label: '实发总额',
        children: formatCurrency(detail.actualAmount),
      },
      {
        key: 'count',
        label: '应发人数',
        children: detail.totalCount ?? '—',
      },
      {
        key: 'result',
        label: '成功/失败',
        children: `${detail.successCount ?? 0} / ${detail.failedCount ?? 0}`,
      },
      {
        key: 'attempt',
        label: '重试配置',
        children: `${detail.currentAttempt ?? 0} / ${detail.retryLimit ?? 0}`,
      },
      {
        key: 'allowPartial',
        label: '允许部分发放',
        children: detail.allowPartial ? '允许' : '不允许',
      },
      {
        key: 'approval',
        label: '审批状态',
        children: getApprovalTag(detail.approvalStatus),
      },
      {
        key: 'approvalWorkflowId',
        label: '审批流 ID',
        children: detail.approvalWorkflowId ? `#${detail.approvalWorkflowId}` : '—',
      },
      {
        key: 'paymentBatchNo',
        label: '支付批次',
        children: detail.paymentBatchNo || '—',
      },
      {
        key: 'provider',
        label: '发放渠道',
        children: detail.settlementProviderCode || '—',
      },
      {
        key: 'createTime',
        label: '创建时间',
        children: formatDateTime(detail.createTime),
      },
      {
        key: 'updateTime',
        label: '更新时间',
        children: formatDateTime(detail.updateTime),
      },
    ];
  }, [currentBatchLoaded, detail, distributionIsStale]);

  const stepItems = useMemo(() => {
    const distributionMeta = getDistributionStatusMeta(detail?.distributionStatus);
    const approvalMeta = approvalStatusMeta[normalizeStatus(detail?.approvalStatus)];
    const reconciliationMeta =
      reconciliationResultMeta[normalizeStatus(reconciliation?.result)] ??
      reconciliationTaskStatusMeta[normalizeStatus(reconciliation?.taskStatus)];
    return [
      {
        title: '创建发放单',
        description: detail?.distributionNo || '待生成',
      },
      {
        title: '审批流',
        description: approvalMeta?.text ?? '待启动',
      },
      {
        title: '渠道提交',
        description: detail?.paymentBatchNo
          ? `支付批次 ${detail.paymentBatchNo}`
          : '待创建支付批次',
      },
      {
        title: '执行结果',
        description: distributionMeta?.text ?? '待执行',
      },
      {
        title: '对账收口',
        description: reconciliationMeta?.text ?? '待生成对账任务',
      },
    ];
  }, [detail, reconciliation]);

  return (
    <PageContainer
      title="发放单"
      subTitle="按批次版本查看真实发放链路、支付批次与失败子集重试结果。"
      extra={
        <Button icon={<ReloadOutlined />} onClick={() => distributionsQuery.refetch()}>
          刷新
        </Button>
      }
    >
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        <Card>
          <Space wrap>
            <InputNumber
              placeholder="批次 ID"
              min={1}
              value={batchIdInput}
              onChange={(value) => setBatchIdInput(value ?? undefined)}
            />
            <InputNumber
              placeholder="批次版本"
              min={1}
              value={batchRevisionInput}
              onChange={(value) => setBatchRevisionInput(value ?? undefined)}
            />
            <Select
              allowClear
              placeholder="发放状态"
              options={distributionStatusOptions}
              style={{ width: 160 }}
              value={statusInput}
              onChange={(value) => setStatusInput(value)}
            />
            <Button type="primary" onClick={handleSearch}>
              查询
            </Button>
            <Button onClick={handleReset}>重置</Button>
          </Space>
        </Card>

        <Space size={16} wrap>
          <Card size="small">
            <Statistic title="发放单总数" value={summary.total} />
          </Card>
          <Card size="small">
            <Statistic
              title="处理中"
              value={summary.processing}
              styles={{ content: { color: '#1677ff' } }}
            />
          </Card>
          <Card size="small">
            <Statistic
              title="全部完成"
              value={summary.completed}
              styles={{ content: { color: '#52c41a' } }}
            />
          </Card>
          <Card size="small">
            <Statistic
              title="异常单数"
              value={summary.abnormal}
              styles={{ content: { color: '#ff4d4f' } }}
            />
          </Card>
          <Card size="small">
            <Statistic title="应发总额" value={summary.totalAmount} precision={2} prefix="¥" />
          </Card>
          <Card size="small">
            <Statistic title="实发总额" value={summary.actualAmount} precision={2} prefix="¥" />
          </Card>
        </Space>

        <Card styles={{ body: { padding: 0 } }}>
          <Table<PayrollDistributionDto>
            rowKey={(record) =>
              record.id ??
              `${record.distributionNo ?? 'distribution'}-${record.batchId ?? 'unknown'}`
            }
            loading={distributionsQuery.isLoading}
            columns={columns}
            dataSource={records}
            scroll={{ x: 1400 }}
            pagination={{
              current: filters.current,
              pageSize: filters.pageSize,
              total: distributionsQuery.data?.total ?? 0,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条`,
            }}
            onChange={handlePaginationChange}
          />
        </Card>
      </Space>

      <Drawer
        title={detail?.distributionNo ? `发放单详情 · ${detail.distributionNo}` : '发放单详情'}
        size={1080}
        open={drawerOpen}
        onClose={closeDrawer}
        extra={
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => {
                distributionDetailQuery.refetch();
                distributionItemsQuery.refetch();
                distributionReconciliationQuery.refetch();
              }}
            >
              刷新
            </Button>
            {detail?.approvalWorkflowId && (
              <Button
                icon={<CheckCircleOutlined />}
                onClick={() => navigate(`/approval/workflows?keyword=${detail.approvalWorkflowId}`)}
              >
                审批流
              </Button>
            )}
            {detail?.paymentBatchNo && (
              <Button
                icon={<ThunderboltOutlined />}
                onClick={() => navigate(`/payments/batches/${detail.paymentBatchNo}`)}
              >
                支付批次
              </Button>
            )}
            {detail?.reconciliationTaskId && (
              <Button
                icon={<FileSearchOutlined />}
                onClick={() =>
                  navigate(
                    `/payroll/reconciliations?batchId=${detail.batchId ?? ''}&taskId=${detail.reconciliationTaskId}`,
                  )
                }
              >
                对账任务
              </Button>
            )}
            {canRetry &&
              detail &&
              isRetryableDistribution(detail.distributionStatus) &&
              !distributionIsStale && (
                <Popconfirm
                  title="确认重试该发放单？"
                  description="仅会重试未产生渠道订单的失败明细，已提交渠道的记录仍需先完成对账。"
                  okText="确认重试"
                  cancelText="取消"
                  onConfirm={() => retryDistribution(detail.id)}
                >
                  <Button icon={<RedoOutlined />} loading={retryingDistributionId === detail.id}>
                    重试发放
                  </Button>
                </Popconfirm>
              )}
          </Space>
        }
      >
        <Spin spinning={drawerLoading}>
          {detail ? (
            <Space orientation="vertical" size={16} style={{ width: '100%' }}>
              <Card size="small" title="流程进度">
                <Steps
                  current={getDistributionCurrentStep(detail, reconciliation)}
                  items={stepItems}
                />
              </Card>

              {(normalizeStatus(detail.distributionStatus) === 'failed' ||
                (detail.failedCount ?? 0) > 0) && (
                <Alert
                  type="warning"
                  showIcon
                  title="当前存在失败明细"
                  description="请重点检查失败原因、收款账户信息与渠道回执，必要时发起失败子集重试。"
                />
              )}

              {distributionIsStale && (
                <Alert
                  type="error"
                  showIcon
                  title="当前发放单不是批次的有效版本"
                  description={
                    currentBatchLoaded
                      ? `该发放单为 ${getBatchRevisionText(detail.batchRevision)}，当前批次为 ${getBatchRevisionText(currentBatchRevision)}。旧版本禁止继续发放，请返回批次工作台重新生成发放单。`
                      : '该发放单已被标记为旧版本，禁止继续发放，请返回批次工作台重新生成发放单。'
                  }
                />
              )}

              {routeBlockedItems.length > 0 && (
                <Alert
                  type="error"
                  showIcon
                  title={`${routeBlockedItems.length} 条明细被收款路由阻断`}
                  description="这些明细没有生成可用渠道或收款账号，不会进入支付渠道；修正员工收款信息后再重试失败明细。"
                />
              )}

              {reconciliation && normalizeStatus(reconciliation.result) === 'mismatch' && (
                <Alert
                  type="error"
                  showIcon
                  title="对账不一致"
                  description={`差异金额：${formatCurrency(reconciliation.difference)}`}
                />
              )}

              <Card size="small" title="发放概要">
                <Descriptions column={2} size="small" items={detailItems} />
              </Card>

              <Card size="small" title="对账结果">
                {reconciliation ? (
                  <Descriptions
                    column={2}
                    size="small"
                    items={[
                      {
                        key: 'taskId',
                        label: '对账任务',
                        children: reconciliation.id ? `#${reconciliation.id}` : '—',
                      },
                      {
                        key: 'taskStatus',
                        label: '任务状态',
                        children: getReconciliationTag(reconciliation),
                      },
                      {
                        key: 'expectedAmount',
                        label: '应发金额',
                        children: formatCurrency(reconciliation.expectedAmount),
                      },
                      {
                        key: 'actualAmount',
                        label: '实发金额',
                        children: formatCurrency(reconciliation.actualAmount),
                      },
                      {
                        key: 'difference',
                        label: '差异金额',
                        children: formatCurrency(reconciliation.difference),
                      },
                      {
                        key: 'updateTime',
                        label: '更新时间',
                        children: formatDateTime(reconciliation.updateTime),
                      },
                    ]}
                  />
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无对账任务" />
                )}
              </Card>

              <Card size="small" title="发放明细">
                <Table<PayrollDistributionItemDto>
                  rowKey={(record) =>
                    record.id ?? `${record.distributionId}-${record.employeeId}-${record.lineId}`
                  }
                  columns={itemColumns}
                  dataSource={distributionItemsQuery.data ?? []}
                  pagination={false}
                  scroll={{ x: 1200 }}
                  locale={{
                    emptyText: (
                      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无发放明细" />
                    ),
                  }}
                />
              </Card>

              {reconciliation?.differenceDetail && (
                <Card size="small" title="差异明细原文">
                  <Paragraph style={{ marginBottom: 0 }}>
                    <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                      {reconciliation.differenceDetail}
                    </pre>
                  </Paragraph>
                </Card>
              )}
            </Space>
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未找到发放单详情" />
          )}
        </Spin>
      </Drawer>
    </PageContainer>
  );
};

export default Distributions;
