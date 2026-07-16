import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useSelector } from 'react-redux';
import {
  PageContainer,
  ProTable,
  type ActionType,
  type ProColumns,
} from '@ant-design/pro-components';
import {
  Alert,
  App as AntdApp,
  Button,
  Dropdown,
  Form,
  Space,
  Tag,
  Typography,
  type MenuProps,
} from 'antd';
import {
  AuditOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  FileSearchOutlined,
  MoreOutlined,
  PlusOutlined,
  SendOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { getPagedRecords } from '@services/api';
import {
  fetchPayrollBatches,
  useCreatePayrollBatchMutation,
  usePayrollCyclesQuery,
  useUpdatePayrollBatchMutation,
  type PayrollBatchListParams,
} from '@services/queries/payroll';
import type { RootState } from '@services/stores/authSlice';
import type { PayrollBatchSummaryDto } from '@types/openapi';
import type { PagedResponse } from '@types/api';
import { hasAnyRole } from '@utils/rbac';
import { BatchWorkspaceDrawer } from './components/BatchWorkspaceDrawer';
import PayrollBatchFormModal, {
  type PayrollBatchFormValues,
} from './components/PayrollBatchFormModal';
import { PayrollMetricGrid, PayrollSection } from './components/PayrollPagePrimitives';
import {
  getBatchRevisionText,
  getCalculationEvidenceMeta,
  getCalculationStatusMeta,
  getDistributionStatusMeta,
  getFlowStatusMeta,
} from './components/payrollFlow';
import './PayrollPages.less';

const { Text } = Typography;

type WorkspaceTabKey = 'overview' | 'entry' | 'items';

const payrollTypeEnum: Record<string, { text: string; color: string }> = {
  full_time: { text: '全职', color: 'blue' },
  part_time: { text: '兼职', color: 'gold' },
  contractor: { text: '外包', color: 'purple' },
};

const statusEnum: Record<string, { text: string; color: string }> = {
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

const formatDateTime = (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—');

const normalizeStatus = (status?: string) => (status ?? '').toLowerCase();

const toNumber = (value: unknown): number | undefined => {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    const next = Number(value);
    return Number.isFinite(next) ? next : undefined;
  }
  return undefined;
};

const getBatchId = (record: PayrollBatchSummaryDto): number | undefined =>
  toNumber(record.batchId ?? (record as any).id);

const getPaymentBatchNo = (record: PayrollBatchSummaryDto): string | undefined => {
  if (typeof record.paymentBatchNo === 'string' && record.paymentBatchNo.trim()) {
    return record.paymentBatchNo.trim();
  }
  return undefined;
};

const canImportBatch = (status: string) => ['draft', 'locked'].includes(status);

const PayrollBatches: React.FC = () => {
  const { message, modal } = AntdApp.useApp();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const userRoles = useSelector(
    (state: RootState) => state.auth.user?.roles ?? state.auth.roles ?? [],
  );
  const canManageBatch = useMemo(() => hasAnyRole(userRoles, ['ADMIN', 'FINANCE']), [userRoles]);

  const actionRef = useRef<ActionType>();

  const [queryParams, setQueryParams] = useState<PayrollBatchListParams>(() => ({
    current: Number(searchParams.get('page') || '1'),
    pageSize: Number(searchParams.get('size') || '10'),
    payrollType: searchParams.get('payrollType') || undefined,
    status: searchParams.get('status') || undefined,
    period: searchParams.get('period') || undefined,
  }));
  const [latestData, setLatestData] = useState<PagedResponse<PayrollBatchSummaryDto>>();

  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<PayrollBatchSummaryDto | null>(null);
  const [workspaceRecord, setWorkspaceRecord] = useState<PayrollBatchSummaryDto | null>(null);
  const [workspaceDefaultTab, setWorkspaceDefaultTab] = useState<WorkspaceTabKey>('overview');
  const [pendingWorkspaceBatchId, setPendingWorkspaceBatchId] = useState<number | undefined>(() =>
    toNumber(searchParams.get('batchId')),
  );
  const [pendingWorkspaceTab] = useState<WorkspaceTabKey>(() => {
    const value = searchParams.get('tab');
    return value === 'entry' || value === 'items' ? value : 'overview';
  });

  const [createForm] = Form.useForm<PayrollBatchFormValues>();
  const [editForm] = Form.useForm<PayrollBatchFormValues>();

  const createMutation = useCreatePayrollBatchMutation();
  const updateMutation = useUpdatePayrollBatchMutation();
  const openCyclesQuery = usePayrollCyclesQuery({ current: 1, pageSize: 200 });
  const payCycleOptions = useMemo(
    () =>
      getPagedRecords(openCyclesQuery.data)
        .filter((cycle: any) => Number(cycle.id) > 0)
        .map((cycle: any) => ({
          value: Number(cycle.id),
          type: cycle.type,
          periodLabel: cycle.periodLabel,
          status: cycle.status,
          label: `${cycle.cycleName || cycle.periodLabel || '未命名日历'} · ${cycle.periodLabel || '未配置期间'} · ${cycle.status === 'open' ? '开放' : '非开放'}`,
        })),
    [openCyclesQuery.data],
  );

  const updateUrlParams = useCallback(
    (params: PayrollBatchListParams) => {
      const next = new URLSearchParams();
      if (params.current) next.set('page', String(params.current));
      if (params.pageSize) next.set('size', String(params.pageSize));
      if (params.payrollType) next.set('payrollType', params.payrollType);
      if (params.status) next.set('status', params.status);
      if (params.period) next.set('period', params.period);
      setSearchParams(next);
    },
    [setSearchParams],
  );

  const summary = useMemo(() => {
    const records = getPagedRecords(latestData);
    return {
      total: latestData?.total ?? 0,
      draft: records.filter((item) => normalizeStatus(item.status) === 'draft').length,
      calculating: records.filter(
        (item) => normalizeStatus(item.calculationStatus ?? item.computeStatus) === 'calculating',
      ).length,
      calculated: records.filter(
        (item) => normalizeStatus(item.calculationStatus ?? item.computeStatus) === 'calculated',
      ).length,
      confirming: records.filter((item) =>
        ['confirming', 'dispute_processing'].includes(normalizeStatus(item.status)),
      ).length,
      distributing: records.filter((item) =>
        ['processing', 'submitted', 'created', 'planned', 'pay_processing'].includes(
          normalizeStatus(item.paymentStatus),
        ),
      ).length,
      withWarnings: records.filter((item) => (item.warnings?.length ?? 0) > 0).length,
      totalEmployees: records.reduce((acc, item) => acc + (item.totalEmployees ?? 0), 0),
      totalNet: records.reduce((acc, item) => acc + (item.netTotal ?? 0), 0),
    };
  }, [latestData]);

  const summaryCards = useMemo(
    () => [
      { key: 'total', title: '批次数', value: summary.total, prefix: <CalendarOutlined /> },
      {
        key: 'attention',
        title: '待处理',
        value: summary.draft + summary.confirming,
        prefix: <ClockCircleOutlined />,
        valueStyle: { color: 'var(--warning)' },
        description: `草稿 ${summary.draft} · 待确认 ${summary.confirming}`,
      },
      {
        key: 'processing',
        title: '处理中',
        value: summary.calculating + summary.distributing,
        prefix: <ThunderboltOutlined />,
        valueStyle: { color: 'var(--primary)' },
        description: `核算 ${summary.calculating} · 发放 ${summary.distributing}`,
      },
      {
        key: 'warnings',
        title: '预警批次',
        value: summary.withWarnings,
        prefix: <ExclamationCircleOutlined />,
        valueStyle: { color: 'var(--danger)' },
      },
      {
        key: 'totalNet',
        title: '当前页实发金额',
        value: formatCurrency(summary.totalNet),
        prefix: <WalletOutlined />,
      },
    ],
    [summary],
  );

  const openWorkspace = useCallback(
    (record: PayrollBatchSummaryDto, tab: WorkspaceTabKey = 'overview') => {
      setWorkspaceRecord(record);
      setWorkspaceDefaultTab(tab);
    },
    [],
  );

  useEffect(() => {
    if (!pendingWorkspaceBatchId || !latestData) {
      return;
    }
    const record = getPagedRecords(latestData).find(
      (item) => getBatchId(item) === pendingWorkspaceBatchId,
    );
    if (!record) {
      return;
    }
    openWorkspace(record, pendingWorkspaceTab);
    setPendingWorkspaceBatchId(undefined);
  }, [latestData, openWorkspace, pendingWorkspaceBatchId, pendingWorkspaceTab]);

  const handleCreate = useCallback(() => {
    if (!canManageBatch) {
      message.warning('仅财务或管理员可新建批次');
      return;
    }
    createForm.resetFields();
    const defaultCycle = payCycleOptions.find((cycle) => cycle.type === 'full_time');
    createForm.setFieldsValue({
      type: 'full_time',
      currency: 'CNY',
      payCycleId: defaultCycle?.value,
      periodLabel: defaultCycle?.periodLabel,
    });
    setIsCreateModalOpen(true);
  }, [canManageBatch, createForm, message, payCycleOptions]);

  const handleCreateSubmit = useCallback(async () => {
    try {
      const values = await createForm.validateFields();
      const created = await createMutation.mutateAsync({
        payCycleId: values.payCycleId,
        periodLabel: values.periodLabel,
        type: values.type,
        remark: values.remark,
        currency: values.currency || 'CNY',
      });
      const createdRecord: PayrollBatchSummaryDto = {
        batchId: toNumber(created?.id ?? created?.batchId),
        payCycleId: values.payCycleId,
        periodLabel: values.periodLabel,
        payrollType: values.type,
        currency: values.currency || 'CNY',
        remark: values.remark,
        status: 'draft',
        calculationStatus: 'draft',
        batchRevision: 1,
      };
      message.success('创建成功，已打开批次工作台，请继续录入数据');
      setIsCreateModalOpen(false);
      actionRef.current?.reload();
      if (createdRecord.batchId) {
        openWorkspace(createdRecord, 'entry');
      }
    } catch (error: any) {
      if (error?.errorFields) return;
      const msg = error?.response?.data?.message || error?.message || '创建失败';
      message.error(msg);
    }
  }, [createForm, createMutation, message, openWorkspace]);

  const handleEdit = useCallback(
    (record: PayrollBatchSummaryDto) => {
      if (!canManageBatch) {
        message.warning('仅财务或管理员可编辑批次');
        return;
      }
      if (normalizeStatus(record.status) !== 'draft') {
        message.warning('只有草稿状态的批次可以编辑');
        return;
      }
      setEditingRecord(record);
      editForm.setFieldsValue({
        payCycleId: record.payCycleId,
        periodLabel: record.periodLabel,
        type: record.payrollType,
        currency: record.currency || 'CNY',
        remark: record.remark,
      });
      setIsEditModalOpen(true);
    },
    [canManageBatch, editForm, message],
  );

  const handleEditSubmit = useCallback(async () => {
    const batchId = editingRecord ? getBatchId(editingRecord) : undefined;
    if (!batchId) {
      message.warning('缺少批次 ID，无法更新');
      return;
    }
    try {
      const values = await editForm.validateFields();
      await updateMutation.mutateAsync({
        id: batchId,
        params: {
          payCycleId: values.payCycleId,
          periodLabel: values.periodLabel,
          type: values.type,
          currency: values.currency || 'CNY',
          remark: values.remark,
        },
      });
      message.success('更新成功');
      setIsEditModalOpen(false);
      setEditingRecord(null);
      actionRef.current?.reload();
    } catch (error: any) {
      if (error?.errorFields) return;
      const msg = error?.response?.data?.message || error?.message || '更新失败';
      message.error(msg);
    }
  }, [editForm, editingRecord, message, updateMutation]);

  const handleViewLedger = useCallback(
    (record: PayrollBatchSummaryDto) => {
      const batchId = getBatchId(record);
      if (!batchId) {
        message.warning('缺少批次 ID，无法打开财务台账');
        return;
      }
      navigate(`/payroll/batches/${batchId}/ledger`);
    },
    [message, navigate],
  );

  const handleViewManager = useCallback(
    (record: PayrollBatchSummaryDto) => {
      const batchId = getBatchId(record);
      if (!batchId) {
        message.warning('缺少批次 ID，无法打开经理核对视图');
        return;
      }
      navigate(`/payroll/batches/${batchId}/manager-review`);
    },
    [message, navigate],
  );

  const handleViewApproval = useCallback(
    (record: PayrollBatchSummaryDto) => {
      if (!record.approvalWorkflowId) {
        message.warning('当前批次暂无审批流信息');
        return;
      }
      navigate(`/approval/workflows?keyword=${record.approvalWorkflowId}`);
    },
    [message, navigate],
  );

  const handleOpenConfirmationWorkbench = useCallback(
    (record?: PayrollBatchSummaryDto) => {
      const batchId = record ? getBatchId(record) : undefined;
      if (batchId) {
        navigate(`/payroll/confirmations?batchId=${batchId}`);
        return;
      }
      navigate('/payroll/confirmations');
    },
    [navigate],
  );

  const handleOpenDistributions = useCallback(
    (record?: PayrollBatchSummaryDto) => {
      const batchId = record ? getBatchId(record) : undefined;
      if (batchId) {
        navigate(`/payroll/distributions?batchId=${batchId}`);
        return;
      }
      navigate('/payroll/distributions');
    },
    [navigate],
  );

  const handleOpenReconciliations = useCallback(
    (record?: PayrollBatchSummaryDto) => {
      const batchId = record ? getBatchId(record) : undefined;
      if (batchId) {
        navigate(`/payroll/reconciliations?batchId=${batchId}`);
        return;
      }
      navigate('/payroll/reconciliations');
    },
    [navigate],
  );

  const buildFlowAction = useCallback(
    (record: PayrollBatchSummaryDto) => {
      const status = normalizeStatus(record.status);
      return {
        key: 'workspace',
        label: canManageBatch ? (canImportBatch(status) ? '录入工作台' : '批次工作台') : '查看流程',
        icon: <FileSearchOutlined />,
        onClick: () =>
          openWorkspace(record, canImportBatch(status) && canManageBatch ? 'entry' : 'overview'),
      };
    },
    [canManageBatch, openWorkspace],
  );

  const columns = useMemo<ProColumns<PayrollBatchSummaryDto>[]>(
    () => [
      {
        title: '期间',
        dataIndex: 'period',
        hideInTable: true,
        valueType: 'dateMonth',
        fieldProps: {
          placeholder: '选择期间',
        },
      },
      {
        title: '用工类型',
        dataIndex: 'payrollType',
        width: 100,
        valueType: 'select',
        valueEnum: Object.fromEntries(
          Object.entries(payrollTypeEnum).map(([key, value]) => [key, { text: value.text }]),
        ),
        render: (_, record) => {
          const meta = payrollTypeEnum[record.payrollType ?? ''];
          return meta ? <Tag color={meta.color}>{meta.text}</Tag> : record.payrollType || '—';
        },
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 190,
        valueType: 'select',
        valueEnum: Object.fromEntries(
          Object.entries(statusEnum).map(([key, value]) => [key, { text: value.text }]),
        ),
        render: (_, record) => {
          const flowMeta =
            getFlowStatusMeta(record.status) ?? statusEnum[normalizeStatus(record.status)];
          const calculationMeta = getCalculationStatusMeta(
            record.calculationStatus ?? record.computeStatus,
          );
          return (
            <div className="payroll-batch-flow">
              {flowMeta ? <Tag color={flowMeta.color}>{flowMeta.text}</Tag> : record.status || '—'}
              {calculationMeta && <Tag color={calculationMeta.color}>{calculationMeta.text}</Tag>}
            </div>
          );
        },
      },
      {
        title: '计算证据',
        key: 'calculationEvidence',
        width: 120,
        render: (_, record) => {
          const flowStatus = normalizeStatus(record.status);
          const calculationStatus = normalizeStatus(
            record.calculationStatus ?? record.computeStatus,
          );
          const evidenceExpected =
            ['calculating', 'calculated'].includes(calculationStatus) ||
            !['draft', 'locked'].includes(flowStatus);
          if (!evidenceExpected) {
            return <Text type="secondary">—</Text>;
          }
          const meta = getCalculationEvidenceMeta(record);
          return <Tag color={meta.color}>{meta.text}</Tag>;
        },
      },
      {
        title: '批次标识',
        dataIndex: 'batchId',
        width: 240,
        render: (_, record) => {
          const batchId = getBatchId(record);
          const paymentBatchNo = getPaymentBatchNo(record);
          return (
            <Space orientation="vertical" size={0}>
              <Space size={8} wrap>
                <Text strong>{batchId ? `#${batchId}` : '—'}</Text>
                {record.batchRevision != null && (
                  <Tag>{getBatchRevisionText(record.batchRevision)}</Tag>
                )}
              </Space>
              {record.periodLabel && <Text type="secondary">{record.periodLabel}</Text>}
              {record.batchNo && <Text type="secondary">批次号：{record.batchNo}</Text>}
              {record.approvalWorkflowId && (
                <Text type="secondary">审批流：#{record.approvalWorkflowId}</Text>
              )}
              {paymentBatchNo && <Text type="secondary">支付批次：{paymentBatchNo}</Text>}
            </Space>
          );
        },
      },
      {
        title: '流程信息',
        key: 'flowInfo',
        width: 220,
        render: (_, record) => {
          const paymentMeta = getDistributionStatusMeta(record.paymentStatus);
          return (
            <Space size={4} wrap>
              <Tag color={record.confirmationRequired === false ? 'default' : 'blue'}>
                {record.confirmationRequired === false ? '免确认' : '需确认'}
              </Tag>
              {(record.confirmationMode || record.confirmationRequired === false) && (
                <Tag>
                  {record.confirmationRequired === false
                    ? '跳过确认'
                    : record.confirmationMode === 'group'
                      ? '组确认'
                      : record.confirmationMode === 'individual'
                        ? '逐人确认'
                        : record.confirmationMode}
                </Tag>
              )}
              {paymentMeta && <Tag color={paymentMeta.color}>{paymentMeta.text}</Tag>}
            </Space>
          );
        },
      },
      {
        title: '员工数',
        dataIndex: 'totalEmployees',
        width: 90,
        render: (_, record) => record.totalEmployees ?? '—',
      },
      {
        title: '实发金额',
        dataIndex: 'netTotal',
        width: 130,
        align: 'right',
        render: (_, record) => formatCurrency(record.netTotal, record.currency ?? 'CNY'),
      },
      {
        title: '预警',
        dataIndex: 'warnings',
        width: 220,
        render: (_, record) =>
          record.warnings && record.warnings.length > 0 ? (
            <Space size={4} wrap>
              {record.warnings.slice(0, 2).map((warning, index) => (
                <Tag color="orange" key={`${warning}-${index}`}>
                  <ExclamationCircleOutlined style={{ marginRight: 4 }} />
                  {warning}
                </Tag>
              ))}
              {record.warnings.length > 2 && (
                <Tag color="orange">+{record.warnings.length - 2}</Tag>
              )}
            </Space>
          ) : (
            '—'
          ),
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        width: 170,
        render: (_, record) => formatDateTime(record.updatedAt),
      },
      {
        title: '操作',
        valueType: 'option',
        width: 220,
        render: (_, record) => {
          const status = normalizeStatus(record.status);
          const flowAction = buildFlowAction(record);
          const items: MenuProps['items'] = [
            {
              key: 'ledger',
              label: '财务台账',
              icon: <FileSearchOutlined />,
              onClick: () => handleViewLedger(record),
            },
            {
              key: 'manager',
              label: '经理核对',
              icon: <TeamOutlined />,
              onClick: () => handleViewManager(record),
            },
            {
              key: 'confirmations',
              label: '确认工作台',
              icon: <CheckCircleOutlined />,
              onClick: () => handleOpenConfirmationWorkbench(record),
            },
            {
              key: 'distributions',
              label: '发放单',
              icon: <SendOutlined />,
              onClick: () => handleOpenDistributions(record),
            },
            {
              key: 'reconciliations',
              label: '对账任务',
              icon: <AuditOutlined />,
              onClick: () => handleOpenReconciliations(record),
            },
          ];

          if (record.approvalWorkflowId) {
            items.push({
              key: 'approval',
              label: '审批流详情',
              icon: <ClockCircleOutlined />,
              onClick: () => handleViewApproval(record),
            });
          }

          if (canManageBatch && status === 'draft') {
            items.unshift({
              key: 'edit',
              label: '编辑',
              icon: <EditOutlined />,
              onClick: () => handleEdit(record),
            });
          }

          return [
            <Button key={flowAction.key} type="link" size="small" onClick={flowAction.onClick}>
              {flowAction.label}
            </Button>,
            <Button key="ledger" type="link" size="small" onClick={() => handleViewLedger(record)}>
              台账
            </Button>,
            <Dropdown key="more" menu={{ items }} trigger={['click']}>
              <Button type="link" size="small">
                更多 <MoreOutlined />
              </Button>
            </Dropdown>,
          ];
        },
      },
    ],
    [
      buildFlowAction,
      canManageBatch,
      handleEdit,
      handleOpenConfirmationWorkbench,
      handleOpenDistributions,
      handleOpenReconciliations,
      handleViewApproval,
      handleViewLedger,
      handleViewManager,
    ],
  );

  return (
    <PageContainer
      className="payroll-page-shell"
      header={{
        title: '薪酬批次',
        subTitle: '集中查看批次状态，并从工作台推进核算、确认、审批和发放',
        extra: [
          canManageBatch && (
            <Button key="create" type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
              新建批次
            </Button>
          ),
          <Button
            key="confirm-workbench"
            icon={<CheckCircleOutlined />}
            onClick={() => handleOpenConfirmationWorkbench()}
          >
            确认工作台
          </Button>,
        ].filter(Boolean),
      }}
    >
      <div className="payroll-page-main">
        {!canManageBatch && (
          <Alert
            type="warning"
            showIcon
            className="payroll-context-alert"
            title="当前为只读视角"
            description="批次录入、锁定、核算、提交审批和发放重试仅对财务或管理员开放。"
          />
        )}

        <PayrollSection
          title="批次概览"
          description={`当前筛选结果：${summary.total} 个批次，统计金额基于当前页数据`}
        >
          <PayrollMetricGrid items={summaryCards} />
        </PayrollSection>

        <PayrollSection
          title="批次列表"
          description="先通过筛选缩小范围，再从每行的工作台或更多菜单进入后续流程"
          className="payroll-pro-table-section payroll-table-section"
        >
          <ProTable<PayrollBatchSummaryDto>
            actionRef={actionRef}
            columns={columns}
            request={async (params) => {
              const nextParams: PayrollBatchListParams = {
                current: params.current,
                pageSize: params.pageSize,
                payrollType:
                  typeof params.payrollType === 'string' ? params.payrollType : undefined,
                status: typeof params.status === 'string' ? params.status : undefined,
                period: params.period
                  ? dayjs(params.period as string | Date).format('YYYY-MM')
                  : undefined,
              };
              setQueryParams(nextParams);
              updateUrlParams(nextParams);
              try {
                const data = await fetchPayrollBatches(nextParams);
                setLatestData(data);
                return {
                  data: getPagedRecords(data),
                  success: true,
                  total: data.total ?? 0,
                };
              } catch (error: any) {
                const msg = error?.response?.data?.message || error?.message || '批次数据加载失败';
                message.error(msg);
                setLatestData(undefined);
                return { data: [], success: false, total: 0 };
              }
            }}
            rowKey={(record) =>
              String(
                getBatchId(record) ??
                  `${record.periodLabel ?? 'batch'}-${record.updatedAt ?? record.createdAt ?? ''}`,
              )
            }
            search={{
              labelWidth: 'auto',
              collapseRender: false,
              optionRender: (_, __, dom) => dom,
            }}
            pagination={{
              current: queryParams.current,
              pageSize: queryParams.pageSize,
              showQuickJumper: true,
              showSizeChanger: true,
              showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条/共 ${total} 条`,
            }}
            toolBarRender={() => []}
            options={{ density: true, setting: true, reload: true }}
            locale={{ emptyText: '暂无薪酬批次' }}
          />
        </PayrollSection>
      </div>

      <BatchWorkspaceDrawer
        open={!!workspaceRecord}
        batch={workspaceRecord}
        defaultTab={workspaceDefaultTab}
        canManageBatch={canManageBatch}
        onClose={() => setWorkspaceRecord(null)}
        onBatchChanged={() => actionRef.current?.reload()}
      />

      <PayrollBatchFormModal
        mode="create"
        open={isCreateModalOpen}
        form={createForm}
        mutation={createMutation}
        payrollTypeOptions={Object.entries(payrollTypeEnum).map(([value, option]) => ({
          value,
          label: option.text,
        }))}
        payCycleOptions={payCycleOptions}
        onSubmit={handleCreateSubmit}
        onCancel={() => setIsCreateModalOpen(false)}
      />

      <PayrollBatchFormModal
        mode="edit"
        open={isEditModalOpen}
        form={editForm}
        mutation={updateMutation}
        payrollTypeOptions={Object.entries(payrollTypeEnum).map(([value, option]) => ({
          value,
          label: option.text,
        }))}
        payCycleOptions={payCycleOptions}
        onSubmit={handleEditSubmit}
        onCancel={() => {
          setIsEditModalOpen(false);
          setEditingRecord(null);
        }}
      />
    </PageContainer>
  );
};

export default PayrollBatches;
