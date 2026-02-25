import React, { useMemo, useRef, useState, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  PageContainer,
  ProTable,
  type ProColumns,
  type ActionType,
  type ProFormInstance,
} from '@ant-design/pro-components';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Dropdown,
  Form,
  Input,
  MenuProps,
  Modal,
  Select,
  Space,
  Statistic,
  Tag,
  Tooltip,
} from 'antd';
import {
  CalendarOutlined,
  ClockCircleOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  FileSearchOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  TeamOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  fetchPayrollBatches,
  useCreatePayrollBatchMutation,
  useUpdatePayrollBatchMutation,
  type PayrollBatchListParams,
} from '@services/queries/payroll';
import type { PagedResponse, PayrollBatchSummaryDto } from '../../types/openapi';
import { getPagedRecords } from '@services/api';

// ==================== 枚举定义 ====================
const payrollTypeEnum: Record<string, { text: string; color: string }> = {
  full_time: { text: '全职', color: 'blue' },
  part_time: { text: '兼职', color: 'gold' },
  contractor: { text: '外包', color: 'purple' },
};

const statusEnum: Record<string, { text: string; color: string }> = {
  draft: { text: '草稿', color: 'default' },
  locked: { text: '已锁定', color: 'warning' },
  computed: { text: '已计算', color: 'processing' },
  approved: { text: '已审批', color: 'success' },
  released: { text: '已发薪', color: 'success' },
  archived: { text: '已归档', color: 'default' },
  pay_pending: { text: '待发薪', color: 'default' },
  pay_processing: { text: '发薪中', color: 'processing' },
  pay_success: { text: '发薪成功', color: 'success' },
  pay_completed: { text: '发薪完成', color: 'success' },
  pay_failed: { text: '发薪失败', color: 'error' },
};

const computeEnum: Record<string, { text: string; color: string }> = {
  pending: { text: '待计算', color: 'default' },
  running: { text: '计算中', color: 'processing' },
  completed: { text: '已完成', color: 'success' },
  failed: { text: '失败', color: 'error' },
  pay_processing: { text: '发薪中', color: 'processing' },
};

// ==================== 工具函数 ====================
const formatCurrency = (value?: number, currency = 'CNY'): string => {
  if (value === undefined || value === null) return '—';
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(value);
};

const formatDateTime = (value?: string): string =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—';

const getBatchId = (record: PayrollBatchSummaryDto): string | undefined =>
  record.batchId ?? record.batchNo ?? (record as any).id?.toString();

// ==================== 主组件 ====================
const PayrollBatches: React.FC = () => {
  const { message } = AntdApp.useApp();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  // URL 参数状态
  const [queryParams, setQueryParams] = useState<PayrollBatchListParams>(() => ({
    current: Number(searchParams.get('page') || '1'),
    pageSize: Number(searchParams.get('size') || '10'),
    payrollType: searchParams.get('payrollType') || undefined,
    cycleType: searchParams.get('cycleType') || undefined,
    status: searchParams.get('status') || undefined,
    computeStatus: searchParams.get('computeStatus') || undefined,
    keyword: searchParams.get('keyword') || undefined,
    period: searchParams.get('period') || undefined,
    sortBy: searchParams.get('sortBy') || undefined,
    order: (searchParams.get('order') as 'asc' | 'desc' | null) || undefined,
  }));

  // 核心引用
  const actionRef = useRef<ActionType>();
  const formRef = useRef<ProFormInstance>();

  // 数据状态
  const [latestData, setLatestData] = useState<PagedResponse<PayrollBatchSummaryDto>>();

  // Modal 状态
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<PayrollBatchSummaryDto | null>(null);
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();

  // Mutations
  const createMutation = useCreatePayrollBatchMutation();
  const updateMutation = useUpdatePayrollBatchMutation();

  // ==================== URL 同步 ====================
  const updateUrlParams = useCallback((params: PayrollBatchListParams) => {
    const next = new URLSearchParams();
    if (params.current) next.set('page', String(params.current));
    if (params.pageSize) next.set('size', String(params.pageSize));
    if (params.payrollType) next.set('payrollType', params.payrollType);
    if (params.cycleType) next.set('cycleType', params.cycleType);
    if (params.status) next.set('status', params.status);
    if (params.computeStatus) next.set('computeStatus', params.computeStatus);
    if (params.keyword) next.set('keyword', params.keyword);
    if (params.period) next.set('period', params.period);
    if (params.sortBy) next.set('sortBy', params.sortBy);
    if (params.order) next.set('order', params.order);
    setSearchParams(next);
  }, [setSearchParams]);

  // ==================== 统计数据 ====================
  const summary = useMemo(() => {
    const records = getPagedRecords(latestData);
    const total = latestData?.total ?? 0;
    const draft = records.filter((item) => (item.status ?? '').toLowerCase() === 'draft').length;
    const awaitingCompute = records.filter((item) => {
      const state = (item.computeStatus ?? item.status ?? '').toLowerCase();
      return state.includes('pending') || state === 'locked';
    }).length;
    const payProcessing = records.filter((item) => (item.status ?? '').toLowerCase().includes('processing')).length;
    const withWarnings = records.filter((item) => (item.warnings?.length ?? 0) > 0).length;
    const totalEmployees = records.reduce((acc, item) => acc + (item.totalEmployees ?? 0), 0);
    const totalNet = records.reduce((acc, item) => acc + (item.netTotal ?? 0), 0);
    return { total, draft, awaitingCompute, payProcessing, withWarnings, totalEmployees, totalNet };
  }, [latestData]);

  // ==================== 操作处理 ====================
  const handleCreate = useCallback(() => {
    createForm.resetFields();
    createForm.setFieldsValue({ type: 'full_time', currency: 'CNY' });
    setIsCreateModalOpen(true);
  }, [createForm]);

  const handleCreateSubmit = useCallback(async () => {
    try {
      const values = await createForm.validateFields();
      await createMutation.mutateAsync({
        periodLabel: values.periodLabel,
        type: values.type,
        remark: values.remark,
        currency: values.currency || 'CNY',
      });
      message.success('创建成功');
      setIsCreateModalOpen(false);
      actionRef.current?.reload();
    } catch (error: any) {
      if (error?.errorFields) return;
      const msg = error?.response?.data?.message || error?.message || '创建失败';
      message.error(msg);
    }
  }, [createForm, createMutation, message]);

  const handleEdit = useCallback((record: PayrollBatchSummaryDto) => {
    // 只有草稿状态才能编辑
    if ((record.status ?? '').toLowerCase() !== 'draft') {
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
  }, [editForm, message]);

  const handleEditSubmit = useCallback(async () => {
    if (!editingRecord?.batchId) return;
    try {
      const values = await editForm.validateFields();
      await updateMutation.mutateAsync({
        id: editingRecord.batchId!,
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
  }, [editForm, editingRecord, updateMutation, message]);

  const handleViewLedger = useCallback((record: PayrollBatchSummaryDto) => {
    const batchKey = getBatchId(record);
    if (!batchKey) {
      message.warning('缺少批次标识，无法打开财务台账');
      return;
    }
    navigate(`/payroll/batches/${batchKey}/ledger`);
  }, [navigate, message]);

  const handleViewManager = useCallback((record: PayrollBatchSummaryDto) => {
    const batchKey = getBatchId(record);
    if (!batchKey) {
      message.warning('缺少批次标识，无法打开经理核对视图');
      return;
    }
    navigate(`/payroll/batches/${batchKey}/manager-review`);
  }, [navigate, message]);

  // ==================== 表格列定义 ====================
  const columns: ProColumns<PayrollBatchSummaryDto>[] = [
    {
      title: '关键字',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '批次号 / 周期 / 备注' },
    },
    {
      title: '批次号',
      dataIndex: 'batchNo',
      width: 180,
      copyable: true,
      render: (_, record) => getBatchId(record) || '—',
    },
    {
      title: '周期',
      dataIndex: 'periodLabel',
      width: 140,
      render: (_, record) => record.periodLabel || '—',
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
      title: '批次状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(statusEnum).map(([key, value]) => [key, { text: value.text }]),
      ),
      render: (_, record) => {
        const meta = statusEnum[record.status ?? ''];
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : record.status || '—';
      },
    },
    {
      title: '计算状态',
      dataIndex: 'computeStatus',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(computeEnum).map(([key, value]) => [key, { text: value.text }]),
      ),
      render: (_, record) => {
        const meta = computeEnum[record.computeStatus ?? ''];
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : record.computeStatus || '—';
      },
    },
    {
      title: '员工数',
      dataIndex: 'totalEmployees',
      width: 90,
      sorter: true,
      render: (value) => value ?? '—',
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
      width: 120,
      render: (_, record) =>
        record.warnings && record.warnings.length > 0 ? (
          <Space size={2} wrap>
            {record.warnings.slice(0, 2).map((warning, index) => (
              <Tag color="orange" key={index}>
                <ExclamationCircleOutlined style={{ marginRight: 2 }} />
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
      width: 180,
      render: (_, record) => {
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
        ];

        // 只有草稿状态可以编辑
        if ((record.status ?? '').toLowerCase() === 'draft') {
          items.unshift({
            key: 'edit',
            label: '编辑',
            icon: <EditOutlined />,
            onClick: () => handleEdit(record),
          });
        }

        return [
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
  ];

  // ==================== 渲染 ====================
  return (
    <PageContainer
      header={{
        title: '薪酬批次管理',
        breadcrumb: {},
        extra: [
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => actionRef.current?.reload()}>
            刷新
          </Button>,
        ],
      }}
    >
      {/* 统计卡片 - 单行显示 */}
      <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 4, marginBottom: 16 }}>
        <Card size="small" style={{ flex: '0 0 auto', width: 120 }}>
          <Statistic title="批次数" value={summary.total} prefix={<CalendarOutlined />} />
        </Card>
        <Card size="small" style={{ flex: '0 0 auto', width: 120 }}>
          <Statistic title="草稿" value={summary.draft} valueStyle={{ color: '#faad14' }} />
        </Card>
        <Card size="small" style={{ flex: '0 0 auto', width: 120 }}>
          <Statistic title="待计算" value={summary.awaitingCompute} prefix={<ClockCircleOutlined />} />
        </Card>
        <Card size="small" style={{ flex: '0 0 auto', width: 120 }}>
          <Statistic title="发薪中" value={summary.payProcessing} prefix={<ThunderboltOutlined />} valueStyle={{ color: '#1890ff' }} />
        </Card>
        <Card size="small" style={{ flex: '0 0 auto', width: 120 }}>
          <Statistic title="存在预警" value={summary.withWarnings} prefix={<ExclamationCircleOutlined />} valueStyle={{ color: '#fa541c' }} />
        </Card>
        <Card size="small" style={{ flex: '0 0 auto', width: 120 }}>
          <Statistic title="员工总数" value={summary.totalEmployees} prefix={<TeamOutlined />} />
        </Card>
        <Card size="small" style={{ flex: '0 0 auto', width: 140 }}>
          <Statistic title="实发金额" value={formatCurrency(summary.totalNet)} />
        </Card>
      </div>

      <ProTable<PayrollBatchSummaryDto>
        columns={columns}
        actionRef={actionRef}
        formRef={formRef}
        scroll={{ x: 1400 }}
        request={async (params, sort) => {
          const nextParams: PayrollBatchListParams = {
            current: params.current || 1,
            pageSize: params.pageSize || 10,
            keyword: params.keyword,
            payrollType: params.payrollType,
            cycleType: params.cycleType,
            status: params.status,
            computeStatus: params.computeStatus,
            sortBy: undefined,
            order: undefined,
          };

          const rawPeriod = (params as any).period;
          if (rawPeriod) {
            nextParams.period = dayjs(rawPeriod).format('YYYY-MM');
          }

          if (sort && Object.keys(sort).length > 0) {
            const [field, order] = Object.entries(sort)[0];
            nextParams.sortBy = field;
            nextParams.order = order === 'ascend' ? 'asc' : 'desc';
          }

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
        rowKey={(record) => getBatchId(record) ?? Math.random()}
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
        }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            新建批次
          </Button>,
          <Tooltip key="refresh" title="刷新列表">
            <Button icon={<ReloadOutlined />} onClick={() => actionRef.current?.reload()} />
          </Tooltip>,
        ]}
        options={{ density: true, setting: true, reload: false }}
        locale={{ emptyText: '暂无薪酬批次' }}
      />

      {/* 创建批次 Modal */}
      <Modal
        title="新建薪酬批次"
        open={isCreateModalOpen}
        onOk={handleCreateSubmit}
        onCancel={() => setIsCreateModalOpen(false)}
        confirmLoading={createMutation.isPending}
        width={480}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          message="提示"
          description="新建批次默认为草稿状态，可以继续配置后提交审批。"
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
            <Input placeholder="如：2024年1月工资、年终奖补发" />
          </Form.Item>

          <Form.Item
            name="type"
            label="用工类型"
            rules={[{ required: true, message: '请选择用工类型' }]}
          >
            <Select
              placeholder="请选择用工类型"
              options={Object.entries(payrollTypeEnum).map(([key, value]) => ({
                label: value.text,
                value: key,
              }))}
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

      {/* 编辑批次 Modal */}
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
        destroyOnHidden
      >
        <Alert
          type="warning"
          showIcon
          message="编辑限制"
          description="仅草稿状态的批次可以编辑。修改后批次将保留草稿状态，需要重新提交审批。"
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
            <Input placeholder="如：2024年1月工资、年终奖补发" />
          </Form.Item>

          <Form.Item
            name="type"
            label="用工类型"
            rules={[{ required: true, message: '请选择用工类型' }]}
          >
            <Select
              placeholder="请选择用工类型"
              options={Object.entries(payrollTypeEnum).map(([key, value]) => ({
                label: value.text,
                value: key,
              }))}
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
