import React, { useCallback, useMemo, useRef, useState } from 'react';
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
  Card,
  Dropdown,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Statistic,
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
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { getPagedRecords } from '@services/api';
import {
  fetchPayrollBatches,
  useCreatePayrollBatchMutation,
  useUpdatePayrollBatchMutation,
  type PayrollBatchListParams,
} from '@services/queries/payroll';
import type { RootState } from '@services/stores/authSlice';
import type { PayrollBatchSummaryDto } from '@types/openapi';
import type { PagedResponse } from '@types/api';
import { hasAnyRole } from '@utils/rbac';
import { BatchWorkspaceDrawer } from './components/BatchWorkspaceDrawer';
import {
  getBatchRevisionText,
  getCalculationStatusMeta,
  getDistributionStatusMeta,
  getFlowStatusMeta,
} from './components/payrollFlow';

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

const calculationStatusEnum: Record<string, { text: string; color: string }> = {
  draft: { text: '未计算', color: 'default' },
  locked: { text: '已锁定', color: 'warning' },
  calculating: { text: '计算中', color: 'processing' },
  calculated: { text: '计算完成', color: 'success' },
  failed: { text: '计算失败', color: 'error' },
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

const getBatchId = (record: PayrollBatchSummaryDto): number | undefined => toNumber(record.batchId ?? (record as any).id);

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
  const userRoles = useSelector((state: RootState) => state.auth.user?.roles ?? state.auth.roles ?? []);
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

  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();

  const createMutation = useCreatePayrollBatchMutation();
  const updateMutation = useUpdatePayrollBatchMutation();

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
      calculating: records.filter((item) => normalizeStatus(item.calculationStatus ?? item.computeStatus) === 'calculating').length,
      calculated: records.filter((item) => normalizeStatus(item.calculationStatus ?? item.computeStatus) === 'calculated').length,
      confirming: records.filter((item) => ['confirming', 'dispute_processing'].includes(normalizeStatus(item.status))).length,
      distributing: records.filter((item) => ['processing', 'submitted', 'created', 'planned', 'pay_processing'].includes(normalizeStatus(item.paymentStatus))).length,
      withWarnings: records.filter((item) => (item.warnings?.length ?? 0) > 0).length,
      totalEmployees: records.reduce((acc, item) => acc + (item.totalEmployees ?? 0), 0),
      totalNet: records.reduce((acc, item) => acc + (item.netTotal ?? 0), 0),
    };
  }, [latestData]);

  const summaryCards = useMemo(
    () => [
      { key: 'total', title: '批次数', value: summary.total, prefix: <CalendarOutlined /> },
      { key: 'draft', title: '草稿', value: summary.draft, valueStyle: { color: '#faad14' } },
      { key: 'calculating', title: '计算中', value: summary.calculating, prefix: <ClockCircleOutlined /> },
      { key: 'calculated', title: '已核算', value: summary.calculated, prefix: <CheckCircleOutlined />, valueStyle: { color: '#52c41a' } },
      { key: 'confirming', title: '待确认', value: summary.confirming, prefix: <TeamOutlined />, valueStyle: { color: '#1677ff' } },
      { key: 'distributing', title: '发放中', value: summary.distributing, prefix: <ThunderboltOutlined />, valueStyle: { color: '#1890ff' } },
      { key: 'withWarnings', title: '存在预警', value: summary.withWarnings, prefix: <ExclamationCircleOutlined />, valueStyle: { color: '#fa541c' } },
      { key: 'totalEmployees', title: '员工总数', value: summary.totalEmployees, prefix: <TeamOutlined /> },
      { key: 'totalNet', title: '实发金额', value: formatCurrency(summary.totalNet) },
    ],
    [summary],
  );

  const openWorkspace = useCallback((record: PayrollBatchSummaryDto, tab: WorkspaceTabKey = 'overview') => {
    setWorkspaceRecord(record);
    setWorkspaceDefaultTab(tab);
  }, []);

  const handleCreate = useCallback(() => {
    if (!canManageBatch) {
      message.warning('仅财务或管理员可新建批次');
      return;
    }
    createForm.resetFields();
    createForm.setFieldsValue({ type: 'full_time', currency: 'CNY' });
    setIsCreateModalOpen(true);
  }, [canManageBatch, createForm, message]);

  const handleCreateSubmit = useCallback(async () => {
    try {
      const values = await createForm.validateFields();
      const created = await createMutation.mutateAsync({
        periodLabel: values.periodLabel,
        type: values.type,
        remark: values.remark,
        currency: values.currency || 'CNY',
      });
      const createdRecord: PayrollBatchSummaryDto = {
        batchId: toNumber(created?.id ?? created?.batchId),
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
        label: canManageBatch
          ? canImportBatch(status)
            ? '录入工作台'
            : '批次工作台'
          : '查看流程',
        icon: <FileSearchOutlined />,
        onClick: () => openWorkspace(record, canImportBatch(status) && canManageBatch ? 'entry' : 'overview'),
      };
    },
    [canManageBatch, openWorkspace],
  );

  const columns = useMemo<ProColumns<PayrollBatchSummaryDto>[]>(() => [
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
      valueEnum: Object.fromEntries(Object.entries(payrollTypeEnum).map(([key, value]) => [key, { text: value.text }])),
      render: (_, record) => {
        const meta = payrollTypeEnum[record.payrollType ?? ''];
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : record.payrollType || '—';
      },
    },
    {
      title: '核算状态',
      dataIndex: 'calculationStatus',
      width: 110,
      hideInSearch: true,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(calculationStatusEnum).map(([key, value]) => [key, { text: value.text }]),
      ),
      render: (_, record) => {
        const meta = getCalculationStatusMeta(record.calculationStatus ?? record.computeStatus);
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : '—';
      },
    },
    {
      title: '流转状态',
      dataIndex: 'status',
      width: 110,
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(statusEnum).map(([key, value]) => [key, { text: value.text }])),
      render: (_, record) => {
        const meta = getFlowStatusMeta(record.status) ?? statusEnum[normalizeStatus(record.status)];
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : record.status || '—';
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
          <Space direction="vertical" size={0}>
            <Space size={8} wrap>
              <Text strong>{batchId ? `#${batchId}` : '—'}</Text>
              {record.batchRevision != null && <Tag>{getBatchRevisionText(record.batchRevision)}</Tag>}
            </Space>
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
      title: '期间/名称',
      dataIndex: 'periodLabel',
      width: 180,
      render: (_, record) => record.periodLabel || '—',
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
      title: '应发金额',
      dataIndex: 'grossTotal',
      width: 130,
      align: 'right',
      render: (_, record) => formatCurrency(record.grossTotal, record.currency ?? 'CNY'),
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
            {record.warnings.length > 2 && <Tag color="orange">+{record.warnings.length - 2}</Tag>}
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
          items.unshift({ key: 'edit', label: '编辑', icon: <EditOutlined />, onClick: () => handleEdit(record) });
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
  ], [
    buildFlowAction,
    canManageBatch,
    handleEdit,
    handleOpenConfirmationWorkbench,
    handleOpenDistributions,
    handleOpenReconciliations,
    handleViewApproval,
    handleViewLedger,
    handleViewManager,
  ]);

  const toolbarButtons = useMemo(
    () => [
      canManageBatch ? (
        <Button key="create" type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
          新建批次
        </Button>
      ) : null,
      <Button key="confirm-workbench" icon={<CheckCircleOutlined />} onClick={() => handleOpenConfirmationWorkbench()}>
        确认工作台
      </Button>,
    ].filter(Boolean),
    [canManageBatch, handleCreate, handleOpenConfirmationWorkbench],
  );

  return (
    <PageContainer
      title="薪酬批次"
      subTitle="通过批次工作台统一管理录入、核算、确认、审批与发放链路"
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Alert
          type={canManageBatch ? 'info' : 'warning'}
          showIcon
          message={canManageBatch ? '批次操作流程' : '当前为只读视角'}
          description={
            canManageBatch
              ? '建议按“新建批次 → 打开工作台 → 导入或补录 → 锁定并核算 → 员工确认 → 提交审批 → 跟踪发放”的顺序操作；若发放失败，可在工作台重试失败子集。'
              : '当前账号可查看工作台、台账、经理核对与确认工作台；新建、录入、锁定、核算、提交审批、重试发放仅对财务或管理员开放。'
          }
        />

        <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 4 }}>
          {summaryCards.map((card) => (
            <Card key={card.key} size="small" style={{ flex: '0 0 auto', width: 138 }}>
              <Statistic title={card.title} value={card.value} prefix={card.prefix} valueStyle={card.valueStyle} />
            </Card>
          ))}
        </div>

        <ProTable<PayrollBatchSummaryDto>
          actionRef={actionRef}
          columns={columns}
          request={async (params) => {
            const nextParams: PayrollBatchListParams = {
              current: params.current,
              pageSize: params.pageSize,
              payrollType: typeof params.payrollType === 'string' ? params.payrollType : undefined,
              status: typeof params.status === 'string' ? params.status : undefined,
              period: params.period ? dayjs(params.period as string | Date).format('YYYY-MM') : undefined,
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
          rowKey={(record) => String(getBatchId(record) ?? `${record.periodLabel ?? 'batch'}-${record.updatedAt ?? record.createdAt ?? ''}`)}
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
          toolBarRender={() => toolbarButtons as React.ReactNode[]}
          options={{ density: true, setting: true, reload: true }}
          locale={{ emptyText: '暂无薪酬批次' }}
        />
      </Space>

      <BatchWorkspaceDrawer
        open={!!workspaceRecord}
        batch={workspaceRecord}
        defaultTab={workspaceDefaultTab}
        canManageBatch={canManageBatch}
        onClose={() => setWorkspaceRecord(null)}
        onBatchChanged={() => actionRef.current?.reload()}
      />

      <Modal
        title="新建薪酬批次"
        open={isCreateModalOpen}
        onOk={handleCreateSubmit}
        onCancel={() => setIsCreateModalOpen(false)}
        confirmLoading={createMutation.isPending}
        width={480}
        forceRender
      >
        <Alert
          type="info"
          showIcon
          message="创建说明"
          description="新建后系统会自动打开批次工作台；你可以继续上传 CSV 或直接手动录入薪资项。"
          style={{ marginBottom: 16 }}
        />
        <Form form={createForm} layout="vertical">
          <Form.Item
            name="periodLabel"
            label="批次名称/期间"
            rules={[
              { required: true, message: '请输入批次名称' },
              { min: 2, max: 50, message: '名称长度应为 2-50 个字符' },
            ]}
          >
            <Input placeholder="如：2026年3月工资、年终奖补发" />
          </Form.Item>
          <Form.Item name="type" label="用工类型" rules={[{ required: true, message: '请选择用工类型' }]}> 
            <Select
              placeholder="请选择用工类型"
              options={Object.entries(payrollTypeEnum).map(([key, value]) => ({ label: value.text, value: key }))}
            />
          </Form.Item>
          <Form.Item name="currency" label="币种">
            <Select
              options={[
                { label: '人民币 (CNY)', value: 'CNY' },
                { label: '美元 (USD)', value: 'USD' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} placeholder="可选，添加批次备注信息" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="编辑薪酬批次"
        open={isEditModalOpen}
        onOk={handleEditSubmit}
        onCancel={() => {
          setIsEditModalOpen(false);
          setEditingRecord(null);
        }}
        confirmLoading={updateMutation.isPending}
        width={480}
        forceRender
      >
        <Alert
          type="warning"
          showIcon
          message="编辑限制"
          description="仅草稿状态的批次可以编辑。修改后建议回到批次工作台核对录入数据，再继续后续流转。"
          style={{ marginBottom: 16 }}
        />
        <Form form={editForm} layout="vertical">
          <Form.Item
            name="periodLabel"
            label="批次名称/期间"
            rules={[
              { required: true, message: '请输入批次名称' },
              { min: 2, max: 50, message: '名称长度应为 2-50 个字符' },
            ]}
          >
            <Input placeholder="如：2026年3月工资、年终奖补发" />
          </Form.Item>
          <Form.Item name="type" label="用工类型" rules={[{ required: true, message: '请选择用工类型' }]}> 
            <Select
              placeholder="请选择用工类型"
              options={Object.entries(payrollTypeEnum).map(([key, value]) => ({ label: value.text, value: key }))}
            />
          </Form.Item>
          <Form.Item name="currency" label="币种">
            <Select
              options={[
                { label: '人民币 (CNY)', value: 'CNY' },
                { label: '美元 (USD)', value: 'USD' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} placeholder="可选，添加批次备注信息" />
          </Form.Item>
        </Form>
      </Modal>

    </PageContainer>
  );
};

export default PayrollBatches;
