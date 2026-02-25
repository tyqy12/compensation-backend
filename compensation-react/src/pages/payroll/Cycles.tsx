import React, { useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
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
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
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
  DeleteOutlined,
  EditOutlined,
  FieldTimeOutlined,
  PlusOutlined,
  ReloadOutlined,
  ScheduleOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  fetchPayrollCycles,
  type PayrollCycleListParams,
  type PayCycleCreateParams,
  type PayCycleUpdateParams,
  useCreatePayrollCycleMutation,
  useUpdatePayrollCycleMutation,
  useDeletePayrollCycleMutation,
} from '@services/queries/payroll';
import type { PagedResponse, PayrollCycleDto } from '../../types/openapi';
import { getPagedRecords } from '@services/api';

const payrollTypeEnum: Record<string, { text: string; color: string }> = {
  full_time: { text: '全职', color: 'blue' },
  part_time: { text: '兼职', color: 'gold' },
  contractor: { text: '外包', color: 'purple' },
};

const cycleTypeEnum: Record<string, { text: string }> = {
  monthly: { text: '月度' },
  semi_monthly: { text: '半月' },
  weekly: { text: '周度' },
  biweekly: { text: '双周' },
};

const cycleStatusEnum: Record<string, { text: string; color: string }> = {
  active: { text: '启用', color: 'success' },
  inactive: { text: '停用', color: 'default' },
  archived: { text: '归档', color: 'default' },
  draft: { text: '草稿', color: 'warning' },
  open: { text: '开放', color: 'processing' },
  closed: { text: '关闭', color: 'default' },
};

const formatDateTime = (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—');

const formatDay = (value?: number | string) => {
  if (value === undefined || value === null) return '—';
  if (typeof value === 'number') return `${value}`;
  const parsed = dayjs(value);
  if (parsed.isValid()) return parsed.format('MM-DD');
  return value;
};

const CyclesPage: React.FC = () => {
  const { message } = AntdApp.useApp();
  const [searchParams, setSearchParams] = useSearchParams();
  const [queryParams, setQueryParams] = useState<PayrollCycleListParams>(() => ({
    current: Number(searchParams.get('page') || '1'),
    pageSize: Number(searchParams.get('size') || '10'),
    payrollType: searchParams.get('payrollType') || undefined,
    cycleType: searchParams.get('cycleType') || undefined,
    status: searchParams.get('status') || undefined,
    keyword: searchParams.get('keyword') || undefined,
  }));
  const actionRef = useRef<ActionType>();
  const formRef = useRef<ProFormInstance>();
  const [latestData, setLatestData] = useState<PagedResponse<PayrollCycleDto>>();
  const [detail, setDetail] = useState<PayrollCycleDto | undefined>();

  // CRUD 状态
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<PayrollCycleDto | null>(null);
  const [modalType, setModalType] = useState<'create' | 'edit'>('create');
  const [form] = Form.useForm();

  // Mutations
  const createMutation = useCreatePayrollCycleMutation();
  const updateMutation = useUpdatePayrollCycleMutation();
  const deleteMutation = useDeletePayrollCycleMutation();

  const updateUrlParams = (params: PayrollCycleListParams) => {
    const next = new URLSearchParams();
    if (params.current) next.set('page', String(params.current));
    if (params.pageSize) next.set('size', String(params.pageSize));
    if (params.keyword) next.set('keyword', params.keyword);
    if (params.payrollType) next.set('payrollType', params.payrollType);
    if (params.cycleType) next.set('cycleType', params.cycleType);
    if (params.status) next.set('status', params.status);
    setSearchParams(next);
  };

  // 打开新增 Modal
  const handleCreate = () => {
    setModalType('create');
    setEditingRecord(null);
    form.resetFields();
    form.setFieldsValue({ status: 'draft' });
    setIsModalOpen(true);
  };

  // 打开编辑 Modal
  const handleEdit = (record: PayrollCycleDto) => {
    setModalType('edit');
    setEditingRecord(record);
    form.setFieldsValue({
      type: record.payrollType,
      periodLabel: record.cycleName || record.periodLabel,
      startDate: record.startDate ? dayjs(record.startDate) : undefined,
      endDate: record.endDate ? dayjs(record.endDate) : undefined,
      cutoffDate: record.cutoffDate ? dayjs(record.cutoffDate) : undefined,
      status: record.status,
    });
    setIsModalOpen(true);
  };

  // 删除确认
  const handleDelete = (record: PayrollCycleDto) => {
    if (!record.id) return;

    Modal.confirm({
      title: '确认删除',
      content: `确定要删除发薪周期「${record.cycleName || record.periodLabel}」吗？只能删除草稿状态的周期。`,
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      async onOk() {
        try {
          await deleteMutation.mutateAsync(record.id!);
          message.success('删除成功');
          actionRef.current?.reload();
        } catch (error: any) {
          const msg = error?.response?.data?.message || error?.message || '删除失败';
          message.error(msg);
        }
      },
    });
  };

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();

      if (modalType === 'create') {
        const params: PayCycleCreateParams = {
          type: values.type,
          periodLabel: values.periodLabel,
          startDate: values.startDate?.format('YYYY-MM-DD'),
          endDate: values.endDate?.format('YYYY-MM-DD'),
          cutoffDate: values.cutoffDate?.format('YYYY-MM-DD'),
          status: values.status || 'draft',
        };
        await createMutation.mutateAsync(params);
        message.success('创建成功');
      } else if (editingRecord?.id) {
        const params: PayCycleUpdateParams = {
          id: editingRecord.id,
          type: values.type,
          periodLabel: values.periodLabel,
          startDate: values.startDate?.format('YYYY-MM-DD'),
          endDate: values.endDate?.format('YYYY-MM-DD'),
          cutoffDate: values.cutoffDate?.format('YYYY-MM-DD'),
          status: values.status,
        };
        await updateMutation.mutateAsync({ id: editingRecord.id, params });
        message.success('更新成功');
      }

      setIsModalOpen(false);
      actionRef.current?.reload();
    } catch (error: any) {
      if (error?.errorFields) {
        return; // 表单验证错误
      }
      const msg = error?.response?.data?.message || error?.message || '操作失败';
      message.error(msg);
    }
  };

 const summary = useMemo(() => {
    const records = getPagedRecords(latestData);
    const total = latestData?.total ?? 0;
    const active = records.filter((item) => {
      const status = (item.status ?? '').toLowerCase();
      return status === 'active' || status === 'open';
    }).length;
    const inactive = records.filter((item) => {
      const status = (item.status ?? '').toLowerCase();
      return status === 'inactive' || status === 'closed';
    }).length;
    const nextExecutions = records
      .map((item) => item.nextExecutionTime ?? item.startDate ?? null)
      .filter((value): value is string => Boolean(value))
      .map((value) => dayjs(value))
      .filter((d) => d.isValid())
      .sort((a, b) => (a.isBefore(b) ? -1 : 1));
    const nextExecutionLabel = nextExecutions[0]?.format('YYYY-MM-DD HH:mm') ?? '—';
    return { total, active, inactive, nextExecutionLabel };
  }, [latestData]);

  const columns: ProColumns<PayrollCycleDto>[] = [
    {
      title: '关键字',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '周期名称/编码/备注' },
    },
    {
      title: '周期名称',
      dataIndex: 'cycleName',
      width: 200,
      render: (_, record) => (
        <Space direction="vertical" size={2} style={{ width: '100%' }}>
          <Space>
            <ScheduleOutlined />
            <span style={{ fontWeight: 600 }}>{record.cycleName || '未命名周期'}</span>
          </Space>
          {record.cycleCode && <Tag>{record.cycleCode}</Tag>}
        </Space>
      ),
    },
    {
      title: '用工类型',
      dataIndex: 'payrollType',
      width: 120,
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(payrollTypeEnum).map(([k, v]) => [k, { text: v.text }])),
      render: (_, record) => {
        const meta = payrollTypeEnum[record.payrollType ?? ''];
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : record.payrollType || '—';
      },
    },
    {
      title: '期间范围',
      dataIndex: 'startDate',
      width: 220,
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      search: false,
      render: (_, record) => {
        const start = record.startDate ? dayjs(record.startDate).format('YYYY-MM-DD') : '—';
        const end = record.endDate ? dayjs(record.endDate).format('YYYY-MM-DD') : '—';
        return `${start} ~ ${end}`;
      },
    },
    {
      title: '周期类型',
      dataIndex: 'cycleType',
      width: 120,
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(cycleTypeEnum).map(([k, v]) => [k, { text: v.text }])),
      render: (_, record) => cycleTypeEnum[record.cycleType ?? '']?.text ?? record.cycleType ?? '—',
    },
    {
      title: '截数日',
      dataIndex: 'cutoffDay',
      width: 100,
      render: (_, record) => formatDay(record.cutoffDay ?? record.cutoffDate),
    },
    {
      title: '发薪日',
      dataIndex: 'payDay',
      width: 100,
      render: (_, record) => formatDay(record.payDay ?? record.payDate),
    },
    {
      title: '时区',
      dataIndex: 'timezone',
      width: 140,
      render: (value) => value || 'UTC+8',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(cycleStatusEnum).map(([k, v]) => [k, { text: v.text }])),
      render: (_, record) => {
        const meta = cycleStatusEnum[record.status ?? ''];
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : record.status || '—';
      },
    },
    {
      title: '下次执行',
      dataIndex: 'nextExecutionTime',
      width: 180,
      render: (_, record) => formatDateTime(record.nextExecutionTime),
    },
    {
      title: '最近执行',
      dataIndex: 'lastExecutionTime',
      width: 180,
      render: (_, record) => formatDateTime(record.lastExecutionTime),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 180,
      render: (_, record) => [
        <Button key="detail" type="link" onClick={() => setDetail(record)}>
          查看详情
        </Button>,
        <Button key="edit" type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
          编辑
        </Button>,
        record.status === 'draft' && (
          <Button
            key="delete"
            type="link"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record)}
          >
            删除
          </Button>
        ),
      ],
    },
  ];

  return (
    <PageContainer
      header={{
        title: '发薪周期管理',
        breadcrumb: {},
        extra: [
          <Tooltip key="refresh" title="刷新列表">
            <Button icon={<ReloadOutlined />} onClick={() => actionRef.current?.reload()} />
          </Tooltip>,
        ],
      }}
    >
      <Space direction="vertical" size={16} style={{ width: '100%', padding: 24 }}>
        {/* 统计卡片 - 单行显示 */}
        <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 4 }}>
          <Card size="small" style={{ flex: '0 0 auto', width: 160 }}>
            <Statistic title="周期数量" value={summary.total} prefix={<CalendarOutlined />} />
          </Card>
          <Card size="small" style={{ flex: '0 0 auto', width: 160 }}>
            <Statistic title="启用周期" value={summary.active} prefix={<ClockCircleOutlined />} valueStyle={{ color: '#52c41a' }} />
          </Card>
          <Card size="small" style={{ flex: '0 0 auto', width: 160 }}>
            <Statistic title="停用周期" value={summary.inactive} prefix={<WarningOutlined />} valueStyle={{ color: '#faad14' }} />
          </Card>
          <Card size="small" style={{ flex: '0 0 auto', width: 180 }}>
            <Statistic title="最近执行时间" value={summary.nextExecutionLabel} prefix={<FieldTimeOutlined />} valueStyle={{ fontSize: 13 }} />
          </Card>
        </div>

        <ProTable<PayrollCycleDto>
          columns={columns}
          actionRef={actionRef}
          formRef={formRef}
          scroll={{ x: 1200 }}
          rowKey={(record) => String(record.id ?? record.cycleCode ?? Math.random())}
          request={async (params) => {
            const nextParams: PayrollCycleListParams = {
              current: params.current || 1,
              pageSize: params.pageSize || 10,
              keyword: params.keyword,
              payrollType: params.payrollType,
              cycleType: params.cycleType,
              status: params.status,
            };
            setQueryParams(nextParams);
            updateUrlParams(nextParams);

            try {
              const data = await fetchPayrollCycles(nextParams);
              setLatestData(data);
              return {
                data: getPagedRecords(data),
                success: true,
                total: data.total ?? 0,
              };
            } catch (error: any) {
              const msg = error?.response?.data?.message || error?.message || '发薪周期加载失败';
              message.error(msg);
              setLatestData(undefined);
              return {
                data: [],
                success: false,
                total: 0,
              };
            }
          }}
          search={{ labelWidth: 'auto', collapseRender: false, optionRender: (_, __, dom) => dom }}
          pagination={{ current: queryParams.current, pageSize: queryParams.pageSize, showSizeChanger: true, showQuickJumper: true }}
          locale={{ emptyText: '暂无发薪周期' }}
          toolBarRender={() => [
            <Button key="create" type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
              新增周期
            </Button>,
          ]}
        />
      </Space>

      {/* 新增/编辑 Modal */}
      <Modal
        title={modalType === 'create' ? '新增发薪周期' : '编辑发薪周期'}
        open={isModalOpen}
        onOk={handleSubmit}
        onCancel={() => setIsModalOpen(false)}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        width={560}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
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

          <Form.Item
            name="periodLabel"
            label="周期名称"
            rules={[{ required: true, message: '请输入周期名称' }]}
          >
            <Input placeholder="如：2024年1月、2024年第一季度" />
          </Form.Item>

          <Form.Item label="期间范围">
            <Space style={{ width: '100%' }}>
              <Form.Item name="startDate" noStyle>
                <DatePicker placeholder="开始日期" style={{ width: '100%' }} />
              </Form.Item>
              <span>~</span>
              <Form.Item name="endDate" noStyle>
                <DatePicker placeholder="结束日期" style={{ width: '100%' }} />
              </Form.Item>
            </Space>
          </Form.Item>

          <Form.Item name="cutoffDate" label="截数日">
            <DatePicker placeholder="选择截数日" style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="status" label="状态">
            <Select
              placeholder="请选择状态"
              options={Object.entries(cycleStatusEnum).map(([key, value]) => ({
                label: value.text,
                value: key,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        width={520}
        title="周期详情"
        open={Boolean(detail)}
        onClose={() => setDetail(undefined)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="周期名称">{detail.cycleName || '—'}</Descriptions.Item>
              <Descriptions.Item label="周期编码">{detail.cycleCode || '—'}</Descriptions.Item>
              <Descriptions.Item label="用工类型">
                {payrollTypeEnum[detail.payrollType ?? '']?.text ?? detail.payrollType ?? '—'}
              </Descriptions.Item>
              <Descriptions.Item label="周期类型">
                {cycleTypeEnum[detail.cycleType ?? '']?.text ?? detail.cycleType ?? '—'}
              </Descriptions.Item>
              <Descriptions.Item label="截数日">{formatDay(detail.cutoffDay ?? detail.cutoffDate)}</Descriptions.Item>
              <Descriptions.Item label="发薪日">{formatDay(detail.payDay ?? detail.payDate)}</Descriptions.Item>
              <Descriptions.Item label="期间范围">
                {detail.startDate || detail.endDate
                  ? `${detail.startDate ? dayjs(detail.startDate).format('YYYY-MM-DD') : '—'} ~ ${
                      detail.endDate ? dayjs(detail.endDate).format('YYYY-MM-DD') : '—'
                    }`
                  : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="提前天数">{formatDay(detail.leadDays)}</Descriptions.Item>
              <Descriptions.Item label="宽限天数">{formatDay(detail.graceDays)}</Descriptions.Item>
              <Descriptions.Item label="时区">{detail.timezone || 'UTC+8'}</Descriptions.Item>
              <Descriptions.Item label="状态">
                {cycleStatusEnum[detail.status ?? '']?.text ?? detail.status ?? '—'}
              </Descriptions.Item>
              <Descriptions.Item label="下次执行">{formatDateTime(detail.nextExecutionTime)}</Descriptions.Item>
              <Descriptions.Item label="最近执行">{formatDateTime(detail.lastExecutionTime)}</Descriptions.Item>
              <Descriptions.Item label="描述">{detail.description || '—'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatDateTime(detail.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{formatDateTime(detail.updatedAt)}</Descriptions.Item>
            </Descriptions>

            <Alert
              type="info"
              showIcon
              message="提示"
              description="发薪周期的执行计划由后台批处理触发，若需调整，请通过“停用 + 编辑”后重新启用。"
            />
          </Space>
        ) : (
          <Alert type="info" message="请选择一条周期记录" showIcon />
        )}
      </Drawer>
    </PageContainer>
  );
};

export default CyclesPage;
