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
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
  type DescriptionsProps,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { FileSearchOutlined, ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  usePayrollDistributionDetailQuery,
  usePayrollReconciliationDetailQuery,
  usePayrollReconciliationsQuery,
  type PayrollReconciliationListParams,
} from '@services/queries/payroll';
import type { PayrollDistributionDto, PayrollReconciliationTaskDto } from '@types/openapi';
import { getBatchRevisionText, getDistributionStatusMeta } from './components/payrollFlow';

const { Text, Paragraph } = Typography;

const taskStatusMeta: Record<string, { text: string; color: string }> = {
  pending: { text: '待对账', color: 'default' },
  processing: { text: '对账中', color: 'processing' },
  completed: { text: '已完成', color: 'success' },
  failed: { text: '失败', color: 'error' },
};

const resultMeta: Record<string, { text: string; color: string }> = {
  matched: { text: '一致', color: 'success' },
  mismatch: { text: '不一致', color: 'error' },
  resolved: { text: '已解决', color: 'processing' },
};

const resultOptions = [
  { label: '一致', value: 'matched' },
  { label: '不一致', value: 'mismatch' },
  { label: '已解决', value: 'resolved' },
];

const taskStatusOptions = [
  { label: '待对账', value: 'pending' },
  { label: '对账中', value: 'processing' },
  { label: '已完成', value: 'completed' },
  { label: '失败', value: 'failed' },
];

const normalizeStatus = (value?: string) =>
  String(value ?? '')
    .trim()
    .toLowerCase();

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

const formatDateTime = (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—');

const renderTag = (meta: { text: string; color: string } | undefined, fallback?: string) =>
  meta ? <Tag color={meta.color}>{meta.text}</Tag> : fallback || '—';

const parseDifferenceDetail = (value?: string) => {
  if (!value) {
    return undefined;
  }
  try {
    return JSON.parse(value) as unknown;
  } catch {
    return value;
  }
};

const buildSearchParams = (filters: PayrollReconciliationListParams, taskId?: number) => {
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
  if (filters.distributionId) {
    params.set('distributionId', String(filters.distributionId));
  }
  if (filters.taskStatus) {
    params.set('taskStatus', filters.taskStatus);
  }
  if (filters.result) {
    params.set('result', filters.result);
  }
  if (taskId) {
    params.set('taskId', String(taskId));
  }
  return params;
};

const Reconciliations: React.FC = () => {
  const navigate = useNavigate();
  const { message } = AntdApp.useApp();
  const [searchParams, setSearchParams] = useSearchParams();

  const initialFilters = useMemo<PayrollReconciliationListParams>(
    () => ({
      current: parsePositiveInt(searchParams.get('current'), 1) ?? 1,
      pageSize: parsePositiveInt(searchParams.get('pageSize'), 10) ?? 10,
      batchId: parsePositiveInt(searchParams.get('batchId')),
      batchRevision: parsePositiveInt(searchParams.get('batchRevision')),
      distributionId: parsePositiveInt(searchParams.get('distributionId')),
      taskStatus: searchParams.get('taskStatus') || undefined,
      result: searchParams.get('result') || undefined,
    }),
    [searchParams],
  );

  const initialTaskId = parsePositiveInt(searchParams.get('taskId'));

  const [filters, setFilters] = useState<PayrollReconciliationListParams>(initialFilters);
  const [batchIdInput, setBatchIdInput] = useState<number | undefined>(initialFilters.batchId);
  const [batchRevisionInput, setBatchRevisionInput] = useState<number | undefined>(
    initialFilters.batchRevision,
  );
  const [distributionIdInput, setDistributionIdInput] = useState<number | undefined>(
    initialFilters.distributionId,
  );
  const [taskStatusInput, setTaskStatusInput] = useState<string | undefined>(
    initialFilters.taskStatus,
  );
  const [resultInput, setResultInput] = useState<string | undefined>(initialFilters.result);
  const [selectedTaskId, setSelectedTaskId] = useState<number | undefined>(initialTaskId);

  const reconciliationsQuery = usePayrollReconciliationsQuery(filters);
  const reconciliationDetailQuery = usePayrollReconciliationDetailQuery(selectedTaskId ?? 0, {
    enabled: Boolean(selectedTaskId),
  });
  const relatedDistributionId = reconciliationDetailQuery.data?.distributionId;
  const relatedDistributionQuery = usePayrollDistributionDetailQuery(relatedDistributionId ?? 0, {
    enabled: Boolean(relatedDistributionId),
  });

  const records = useMemo(
    () => reconciliationsQuery.data?.records ?? [],
    [reconciliationsQuery.data?.records],
  );
  const selectedRecord = useMemo(
    () => records.find((item) => item.id === selectedTaskId),
    [records, selectedTaskId],
  );
  const detail = reconciliationDetailQuery.data ?? selectedRecord;
  const relatedDistribution = relatedDistributionQuery.data as PayrollDistributionDto | undefined;
  const drawerOpen = Boolean(selectedTaskId);
  const drawerLoading = reconciliationDetailQuery.isLoading || relatedDistributionQuery.isLoading;

  const updateRoute = useCallback(
    (nextFilters: PayrollReconciliationListParams, taskId?: number) => {
      setSearchParams(buildSearchParams(nextFilters, taskId));
    },
    [setSearchParams],
  );

  const handleSearch = useCallback(() => {
    const nextFilters: PayrollReconciliationListParams = {
      ...filters,
      current: 1,
      batchId: batchIdInput,
      batchRevision: batchRevisionInput,
      distributionId: distributionIdInput,
      taskStatus: taskStatusInput,
      result: resultInput,
    };
    setFilters(nextFilters);
    updateRoute(nextFilters, selectedTaskId);
  }, [
    batchIdInput,
    batchRevisionInput,
    distributionIdInput,
    filters,
    resultInput,
    selectedTaskId,
    taskStatusInput,
    updateRoute,
  ]);

  const handleReset = useCallback(() => {
    const nextFilters: PayrollReconciliationListParams = {
      current: 1,
      pageSize: 10,
    };
    setBatchIdInput(undefined);
    setBatchRevisionInput(undefined);
    setDistributionIdInput(undefined);
    setTaskStatusInput(undefined);
    setResultInput(undefined);
    setFilters(nextFilters);
    updateRoute(nextFilters, selectedTaskId);
  }, [selectedTaskId, updateRoute]);

  const handlePaginationChange = useCallback(
    (pagination: TablePaginationConfig) => {
      const nextFilters: PayrollReconciliationListParams = {
        ...filters,
        current: pagination.current ?? filters.current ?? 1,
        pageSize: pagination.pageSize ?? filters.pageSize ?? 10,
      };
      setFilters(nextFilters);
      updateRoute(nextFilters, selectedTaskId);
    },
    [filters, selectedTaskId, updateRoute],
  );

  const openTask = useCallback(
    (taskId?: number) => {
      if (!taskId) {
        message.warning('缺少对账任务 ID，无法查看详情');
        return;
      }
      setSelectedTaskId(taskId);
      updateRoute(filters, taskId);
    },
    [filters, message, updateRoute],
  );

  const closeDrawer = useCallback(() => {
    setSelectedTaskId(undefined);
    updateRoute(filters, undefined);
  }, [filters, updateRoute]);

  const summary = useMemo(() => {
    const matched = records.filter((item) => normalizeStatus(item.result) === 'matched').length;
    const mismatch = records.filter((item) => normalizeStatus(item.result) === 'mismatch').length;
    const processing = records.filter((item) =>
      ['pending', 'processing'].includes(normalizeStatus(item.taskStatus)),
    ).length;
    const totalDifference = records.reduce((sum, item) => sum + Math.abs(item.difference ?? 0), 0);
    return {
      total: reconciliationsQuery.data?.total ?? 0,
      matched,
      mismatch,
      processing,
      totalDifference,
    };
  }, [reconciliationsQuery.data?.total, records]);

  const columns = useMemo<ColumnsType<PayrollReconciliationTaskDto>>(
    () => [
      {
        title: '对账任务',
        dataIndex: 'id',
        width: 220,
        render: (_, record) => (
          <Space orientation="vertical" size={0}>
            <Space size={8} wrap>
              <Text strong>{record.id ? `#${record.id}` : '—'}</Text>
              {record.batchRevision != null && (
                <Tag>{getBatchRevisionText(record.batchRevision)}</Tag>
              )}
            </Space>
            <Text type="secondary">发放单：{record.distributionNo || '—'}</Text>
            <Text type="secondary">批次：#{record.batchId ?? '—'}</Text>
          </Space>
        ),
      },
      {
        title: '期间/状态',
        key: 'status',
        width: 220,
        render: (_, record) => (
          <Space orientation="vertical" size={4}>
            <Text>{record.periodLabel || '—'}</Text>
            <Space size={8} wrap>
              {renderTag(
                taskStatusMeta[normalizeStatus(record.taskStatus)],
                record.taskStatus || '—',
              )}
              {renderTag(resultMeta[normalizeStatus(record.result)], record.result || '—')}
            </Space>
          </Space>
        ),
      },
      {
        title: '金额对比',
        key: 'amounts',
        width: 240,
        render: (_, record) => (
          <Space orientation="vertical" size={2}>
            <Text>应发：{formatCurrency(record.expectedAmount)}</Text>
            <Text>实发：{formatCurrency(record.actualAmount)}</Text>
            <Text type="secondary">差异：{formatCurrency(record.difference)}</Text>
          </Space>
        ),
      },
      {
        title: '发放状态',
        dataIndex: 'distributionStatus',
        width: 120,
        render: (_, record) =>
          renderTag(
            getDistributionStatusMeta(record.distributionStatus),
            record.distributionStatus || '—',
          ),
      },
      {
        title: '更新时间',
        dataIndex: 'updateTime',
        width: 170,
        render: (_, record) => formatDateTime(record.updateTime),
      },
      {
        title: '操作',
        key: 'actions',
        width: 200,
        render: (_, record) => (
          <Space size={0} wrap>
            <Button type="link" size="small" onClick={() => openTask(record.id)}>
              详情
            </Button>
            <Button
              type="link"
              size="small"
              disabled={!record.batchId}
              onClick={() => navigate(`/payroll/distributions?batchId=${record.batchId ?? ''}`)}
            >
              发放单
            </Button>
          </Space>
        ),
      },
    ],
    [message, navigate, openTask],
  );

  const detailItems = useMemo<DescriptionsProps['items']>(() => {
    if (!detail) {
      return [];
    }
    return [
      {
        key: 'taskId',
        label: '任务 ID',
        children: detail.id ? `#${detail.id}` : '—',
      },
      {
        key: 'taskStatus',
        label: '任务状态',
        children: renderTag(
          taskStatusMeta[normalizeStatus(detail.taskStatus)],
          detail.taskStatus || '—',
        ),
      },
      {
        key: 'result',
        label: '对账结果',
        children: renderTag(resultMeta[normalizeStatus(detail.result)], detail.result || '—'),
      },
      {
        key: 'distributionNo',
        label: '发放单号',
        children: detail.distributionNo || '—',
      },
      {
        key: 'distributionStatus',
        label: '发放状态',
        children: renderTag(
          getDistributionStatusMeta(detail.distributionStatus),
          detail.distributionStatus || '—',
        ),
      },
      {
        key: 'batch',
        label: '关联批次',
        children: detail.batchId
          ? `#${detail.batchId} ${getBatchRevisionText(detail.batchRevision)}`
          : '—',
      },
      {
        key: 'period',
        label: '计薪期间',
        children: detail.periodLabel || '—',
      },
      {
        key: 'payrollType',
        label: '用工类型',
        children: detail.payrollType || '—',
      },
      {
        key: 'expectedAmount',
        label: '应发金额',
        children: formatCurrency(detail.expectedAmount),
      },
      {
        key: 'actualAmount',
        label: '实发金额',
        children: formatCurrency(detail.actualAmount),
      },
      {
        key: 'difference',
        label: '差异金额',
        children: formatCurrency(detail.difference),
      },
      {
        key: 'updateTime',
        label: '更新时间',
        children: formatDateTime(detail.updateTime),
      },
    ];
  }, [detail]);

  const parsedDifferenceDetail = useMemo(
    () => parseDifferenceDetail(detail?.differenceDetail),
    [detail?.differenceDetail],
  );

  return (
    <PageContainer
      title="对账任务"
      subTitle="核对应发与实发差异，收口发放链路。"
      extra={
        <Button icon={<ReloadOutlined />} onClick={() => reconciliationsQuery.refetch()}>
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
            <InputNumber
              placeholder="发放单 ID"
              min={1}
              value={distributionIdInput}
              onChange={(value) => setDistributionIdInput(value ?? undefined)}
            />
            <Select
              allowClear
              placeholder="任务状态"
              options={taskStatusOptions}
              style={{ width: 150 }}
              value={taskStatusInput}
              onChange={(value) => setTaskStatusInput(value)}
            />
            <Select
              allowClear
              placeholder="对账结果"
              options={resultOptions}
              style={{ width: 150 }}
              value={resultInput}
              onChange={(value) => setResultInput(value)}
            />
            <Button type="primary" onClick={handleSearch}>
              查询
            </Button>
            <Button onClick={handleReset}>重置</Button>
          </Space>
        </Card>

        <Space size={16} wrap>
          <Card size="small">
            <Statistic title="任务总数" value={summary.total} />
          </Card>
          <Card size="small">
            <Statistic
              title="待处理"
              value={summary.processing}
              styles={{ content: { color: '#1677ff' } }}
            />
          </Card>
          <Card size="small">
            <Statistic
              title="一致"
              value={summary.matched}
              styles={{ content: { color: '#52c41a' } }}
            />
          </Card>
          <Card size="small">
            <Statistic
              title="不一致"
              value={summary.mismatch}
              styles={{ content: { color: '#ff4d4f' } }}
            />
          </Card>
          <Card size="small">
            <Statistic title="累计差异" value={summary.totalDifference} precision={2} prefix="¥" />
          </Card>
        </Space>

        <Card styles={{ body: { padding: 0 } }}>
          <Table<PayrollReconciliationTaskDto>
            rowKey={(record) => record.id ?? `${record.distributionId}-${record.batchId}`}
            loading={reconciliationsQuery.isLoading}
            columns={columns}
            dataSource={records}
            scroll={{ x: 1100 }}
            pagination={{
              current: filters.current,
              pageSize: filters.pageSize,
              total: reconciliationsQuery.data?.total ?? 0,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条`,
            }}
            onChange={handlePaginationChange}
          />
        </Card>
      </Space>

      <Drawer
        title={detail?.id ? `对账任务详情 · #${detail.id}` : '对账任务详情'}
        size={920}
        open={drawerOpen}
        onClose={closeDrawer}
        extra={
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => {
                reconciliationDetailQuery.refetch();
                relatedDistributionQuery.refetch();
              }}
            >
              刷新
            </Button>
            {relatedDistribution?.paymentBatchNo && (
              <Button
                icon={<ThunderboltOutlined />}
                onClick={() => navigate(`/payments/batches/${relatedDistribution.paymentBatchNo}`)}
              >
                支付批次
              </Button>
            )}
            {detail?.batchId && (
              <Button
                icon={<FileSearchOutlined />}
                onClick={() => navigate(`/payroll/distributions?batchId=${detail.batchId}`)}
              >
                发放单
              </Button>
            )}
          </Space>
        }
      >
        <Spin spinning={drawerLoading}>
          {detail ? (
            <Space orientation="vertical" size={16} style={{ width: '100%' }}>
              {normalizeStatus(detail.result) === 'mismatch' && (
                <Alert
                  type="error"
                  showIcon
                  title="对账存在差异"
                  description={`当前差异金额为 ${formatCurrency(detail.difference)}，请结合发放明细与渠道回执排查。`}
                />
              )}

              <Card size="small" title="任务概要">
                <Descriptions column={2} size="small" items={detailItems} />
              </Card>

              {relatedDistribution && (
                <Card size="small" title="关联发放单">
                  <Descriptions
                    column={2}
                    size="small"
                    items={[
                      {
                        key: 'distributionNo',
                        label: '发放单号',
                        children: relatedDistribution.distributionNo || '—',
                      },
                      {
                        key: 'distributionStatus',
                        label: '发放状态',
                        children: renderTag(
                          getDistributionStatusMeta(relatedDistribution.distributionStatus),
                          relatedDistribution.distributionStatus || '—',
                        ),
                      },
                      {
                        key: 'approvalWorkflowId',
                        label: '审批流',
                        children: relatedDistribution.approvalWorkflowId
                          ? `#${relatedDistribution.approvalWorkflowId}`
                          : '—',
                      },
                      {
                        key: 'paymentBatchNo',
                        label: '支付批次',
                        children: relatedDistribution.paymentBatchNo || '—',
                      },
                      {
                        key: 'scheduledDate',
                        label: '计划发放日',
                        children: relatedDistribution.scheduledDate
                          ? dayjs(relatedDistribution.scheduledDate).format('YYYY-MM-DD')
                          : '—',
                      },
                      {
                        key: 'actualAmount',
                        label: '实发金额',
                        children: formatCurrency(relatedDistribution.actualAmount),
                      },
                    ]}
                  />
                </Card>
              )}

              <Card size="small" title="差异明细">
                {detail.differenceDetail ? (
                  <Paragraph style={{ marginBottom: 0 }}>
                    <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                      {typeof parsedDifferenceDetail === 'string'
                        ? parsedDifferenceDetail
                        : JSON.stringify(parsedDifferenceDetail, null, 2)}
                    </pre>
                  </Paragraph>
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无差异明细" />
                )}
              </Card>
            </Space>
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未找到对账任务详情" />
          )}
        </Spin>
      </Drawer>
    </PageContainer>
  );
};

export default Reconciliations;
