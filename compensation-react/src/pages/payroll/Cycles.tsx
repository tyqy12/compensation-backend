import React, { useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
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
  Col,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Tag,
} from 'antd';
import {
  CalendarOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  FieldTimeOutlined,
  PlusOutlined,
  ScheduleOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import {
  fetchPayrollCycles,
  type PayrollCycleListParams,
  type PayCycleCreateParams,
  type PayCycleUpdateParams,
  useCreatePayrollCycleMutation,
  useDeletePayrollCycleMutation,
  useUpdatePayrollCycleMutation,
} from '@services/queries/payroll';
import { getPagedRecords } from '@services/api';
import type { PagedResponse, PayrollCycleDto } from '../../types/openapi';

const cycleMainTypeEnum: Record<string, { text: string }> = {
  monthly: { text: '标准月度' },
  custom: { text: '自定义' },
  full_time: { text: '全职' },
  part_time: { text: '兼职' },
  contractor: { text: '合同工' },
};

const cycleTypeEnum: Record<string, { text: string }> = {
  monthly: { text: '月度' },
  semi_monthly: { text: '半月' },
  weekly: { text: '周度' },
  biweekly: { text: '双周' },
  custom: { text: '自定义' },
};

const cycleStatusEnum: Record<string, { text: string; color: string }> = {
  draft: { text: '草稿', color: 'default' },
  open: { text: '开放', color: 'processing' },
  closed: { text: '停用', color: 'warning' },
  archived: { text: '归档', color: 'default' },
};

const statusTransitions: Record<string, string[]> = {
  draft: ['open', 'archived'],
  open: ['closed'],
  closed: ['open', 'archived'],
  archived: [],
};

const statusAlias: Record<string, string> = {
  active: 'open',
  inactive: 'closed',
};

type CycleFormValues = {
  type: string;
  periodLabel: string;
  cycleCode?: string;
  cycleName: string;
  cycleType: string;
  startDate?: Dayjs;
  endDate?: Dayjs;
  cutoffDate?: Dayjs;
  payDay?: number;
  leadDays?: number;
  graceDays?: number;
  timezone?: string;
  description?: string;
  status: string;
};

const normalizeStatus = (status?: string) => {
  const raw = status?.trim().toLowerCase();
  if (!raw) return undefined;
  return statusAlias[raw] ?? raw;
};

const normalizeText = (value?: string) => {
  const normalized = value?.trim();
  return normalized ? normalized : undefined;
};

const getCycleDisplayName = (cycle?: PayrollCycleDto): string =>
  cycle?.cycleName || cycle?.periodLabel || '未命名周期';

const getCycleTypeText = (cycle?: PayrollCycleDto): string => {
  const cycleType = (cycle?.cycleType || '').trim().toLowerCase();
  if (cycleTypeEnum[cycleType]) return cycleTypeEnum[cycleType].text;
  const mainType = (cycle?.type || '').trim().toLowerCase();
  if (cycleMainTypeEnum[mainType]) return cycleMainTypeEnum[mainType].text;
  return cycle?.cycleType || cycle?.type || '未配置';
};

const getMainTypeText = (type?: string) => {
  const key = (type || '').trim().toLowerCase();
  return cycleMainTypeEnum[key]?.text || type || '未配置';
};

const getStatusMeta = (status?: string) => cycleStatusEnum[normalizeStatus(status) ?? ''];

const formatDateTime = (value?: string) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '未记录';

const formatDay = (value?: number | string, emptyText = '未配置') => {
  if (value === undefined || value === null || value === '') return emptyText;
  if (typeof value === 'number') return `${value}`;
  const parsed = dayjs(value);
  if (parsed.isValid()) return parsed.format('MM-DD');
  return String(value);
};

const formatPeriodRange = (startDate?: string, endDate?: string): string => {
  if (!startDate && !endDate) return '未配置';
  const start = startDate ? dayjs(startDate).format('YYYY-MM-DD') : '未配置';
  const end = endDate ? dayjs(endDate).format('YYYY-MM-DD') : '未配置';
  return `${start} ~ ${end}`;
};

const formatExecutionTimeFriendly = (value?: string, status?: string): string => {
  if (value) return formatDateTime(value);
  const normalized = normalizeStatus(status);
  if (normalized === 'open') return '待调度';
  if (normalized === 'closed' || normalized === 'draft') return '未启用';
  return '未配置';
};

const CyclesPage: React.FC = () => {
  const { message } = AntdApp.useApp();
  const [searchParams, setSearchParams] = useSearchParams();
  const [queryParams, setQueryParams] = useState<PayrollCycleListParams>(() => ({
    current: Number(searchParams.get('page') || '1'),
    pageSize: Number(searchParams.get('size') || '10'),
    periodLabel: searchParams.get('periodLabel') || searchParams.get('keyword') || undefined,
    status: searchParams.get('status') || undefined,
  }));
  const actionRef = useRef<ActionType>();
  const [latestData, setLatestData] = useState<PagedResponse<PayrollCycleDto>>();
  const [detail, setDetail] = useState<PayrollCycleDto | undefined>();

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<PayrollCycleDto | null>(null);
  const [modalType, setModalType] = useState<'create' | 'edit'>('create');
  const [form] = Form.useForm<CycleFormValues>();

  const createMutation = useCreatePayrollCycleMutation();
  const updateMutation = useUpdatePayrollCycleMutation();
  const deleteMutation = useDeletePayrollCycleMutation();

  const updateUrlParams = (params: PayrollCycleListParams) => {
    const next = new URLSearchParams();
    if (params.current) next.set('page', String(params.current));
    if (params.pageSize) next.set('size', String(params.pageSize));
    if (params.periodLabel) next.set('periodLabel', params.periodLabel);
    if (params.status) next.set('status', params.status);
    setSearchParams(next);
  };

  const handleCreate = () => {
    setModalType('create');
    setEditingRecord(null);
    form.resetFields();
    const defaultPeriod = dayjs().format('YYYY-MM');
    form.setFieldsValue({
      type: 'monthly',
      cycleType: 'monthly',
      status: 'draft',
      timezone: 'UTC+8',
      periodLabel: defaultPeriod,
      cycleName: `${dayjs().format('YYYY年MM月')}发薪周期`,
    });
    setIsModalOpen(true);
  };

  const handleEdit = (record: PayrollCycleDto) => {
    setModalType('edit');
    setEditingRecord(record);
    const normalizedCycleType = (record.cycleType || '').toLowerCase();
    form.setFieldsValue({
      type: record.type || 'monthly',
      periodLabel: record.periodLabel || '',
      cycleCode: record.cycleCode,
      cycleName: record.cycleName || record.periodLabel || '',
      cycleType: cycleTypeEnum[normalizedCycleType] ? normalizedCycleType : 'monthly',
      startDate: record.startDate ? dayjs(record.startDate) : undefined,
      endDate: record.endDate ? dayjs(record.endDate) : undefined,
      cutoffDate: record.cutoffDate ? dayjs(record.cutoffDate) : undefined,
      payDay: record.payDay,
      leadDays: record.leadDays,
      graceDays: record.graceDays,
      timezone: record.timezone || 'UTC+8',
      description: record.description || undefined,
      status: normalizeStatus(record.status) || 'draft',
    });
    setIsModalOpen(true);
  };

  const handleDelete = (record: PayrollCycleDto) => {
    if (!record.id) return;

    Modal.confirm({
      title: '确认删除',
      content: `确定删除发薪周期「${getCycleDisplayName(record)}」吗？仅草稿状态可删除。`,
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

  const validateDateRange = (values: CycleFormValues) => {
    if (values.startDate && values.endDate && values.startDate.isAfter(values.endDate, 'day')) {
      throw new Error('开始日期不能晚于结束日期');
    }
    if (
      values.cutoffDate &&
      values.startDate &&
      values.cutoffDate.isBefore(values.startDate, 'day')
    ) {
      throw new Error('截数日不能早于开始日期');
    }
    if (values.cutoffDate && values.endDate && values.cutoffDate.isAfter(values.endDate, 'day')) {
      throw new Error('截数日不能晚于结束日期');
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      validateDateRange(values);

      const params: PayCycleCreateParams = {
        type: values.type,
        periodLabel: values.periodLabel,
        cycleCode: normalizeText(values.cycleCode),
        cycleName: normalizeText(values.cycleName),
        cycleType: values.cycleType,
        startDate: values.startDate?.format('YYYY-MM-DD'),
        endDate: values.endDate?.format('YYYY-MM-DD'),
        cutoffDate: values.cutoffDate?.format('YYYY-MM-DD'),
        payDay: values.payDay,
        leadDays: values.leadDays,
        graceDays: values.graceDays,
        timezone: normalizeText(values.timezone),
        description: normalizeText(values.description),
        status: values.status || 'draft',
      };

      if (modalType === 'create') {
        await createMutation.mutateAsync(params);
        message.success('创建成功');
      } else if (editingRecord?.id) {
        const updateParams: PayCycleUpdateParams = {
          ...params,
          id: editingRecord.id,
        };
        await updateMutation.mutateAsync({ id: editingRecord.id, params: updateParams });
        message.success('更新成功');
      }

      setIsModalOpen(false);
      actionRef.current?.reload();
    } catch (error: any) {
      if (error?.errorFields) return;
      const msg = error?.message || error?.response?.data?.message || '操作失败';
      message.error(msg);
    }
  };

  const summary = useMemo(() => {
    const records = getPagedRecords(latestData);
    const total = latestData?.total ?? 0;
    const active = records.filter((item) => normalizeStatus(item.status) === 'open').length;
    const inactive = records.filter((item) => normalizeStatus(item.status) === 'closed').length;
    const nextExecutions = records
      .map((item) => item.nextExecutionTime ?? null)
      .filter((value): value is string => Boolean(value))
      .map((value) => dayjs(value))
      .filter((dateValue) => dateValue.isValid())
      .sort((a, b) => (a.isBefore(b) ? -1 : 1));
    const nextExecutionLabel = nextExecutions[0]?.format('YYYY-MM-DD HH:mm') ?? '未配置';
    return { total, active, inactive, nextExecutionLabel };
  }, [latestData]);

  const summaryCards = useMemo(
    () => [
      {
        key: 'total',
        title: '周期数量',
        value: summary.total,
        prefix: <CalendarOutlined />,
      },
      {
        key: 'active',
        title: '启用周期',
        value: summary.active,
        prefix: <ClockCircleOutlined />,
        valueStyle: { color: '#52c41a' },
      },
      {
        key: 'inactive',
        title: '停用周期',
        value: summary.inactive,
        prefix: <WarningOutlined />,
        valueStyle: { color: '#faad14' },
      },
      {
        key: 'next',
        title: '最近执行时间',
        value: summary.nextExecutionLabel,
        prefix: <FieldTimeOutlined />,
        valueStyle: { fontSize: 13 },
      },
    ],
    [summary],
  );

  const statusOptions = useMemo(() => {
    if (modalType === 'create') {
      return ['draft', 'open'].map((value) => ({
        label: cycleStatusEnum[value].text,
        value,
      }));
    }
    const current = normalizeStatus(editingRecord?.status) || 'draft';
    const options = [current, ...(statusTransitions[current] || [])];
    return Array.from(new Set(options)).map((value) => ({
      label: cycleStatusEnum[value]?.text || value,
      value,
    }));
  }, [modalType, editingRecord]);

  const columns: ProColumns<PayrollCycleDto>[] = [
    {
      title: '周期标签',
      dataIndex: 'periodLabel',
      hideInTable: true,
      fieldProps: { placeholder: '如：2025-01' },
    },
    {
      title: '周期名称',
      dataIndex: 'cycleName',
      width: 240,
      render: (_, record) => (
        <Space orientation="vertical" size={2} style={{ width: '100%' }}>
          <Space>
            <ScheduleOutlined />
            <span style={{ fontWeight: 600 }}>{getCycleDisplayName(record)}</span>
          </Space>
          <Space size={6}>
            {record.periodLabel && <Tag>{record.periodLabel}</Tag>}
            {record.cycleCode && <Tag color="blue">{record.cycleCode}</Tag>}
          </Space>
        </Space>
      ),
    },
    {
      title: '周期类型',
      dataIndex: 'cycleType',
      width: 120,
      hideInSearch: true,
      render: (_, record) => getCycleTypeText(record),
    },
    {
      title: '期间范围',
      dataIndex: 'startDate',
      width: 220,
      search: false,
      render: (_, record) => formatPeriodRange(record.startDate, record.endDate),
    },
    {
      title: '截数日',
      dataIndex: 'cutoffDay',
      width: 110,
      search: false,
      render: (_, record) => formatDay(record.cutoffDay ?? record.cutoffDate),
    },
    {
      title: '发薪日',
      dataIndex: 'payDay',
      width: 110,
      search: false,
      render: (_, record) => formatDay(record.payDay),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(cycleStatusEnum).map(([key, meta]) => [key, { text: meta.text }]),
      ),
      render: (_, record) => {
        const meta = getStatusMeta(record.status);
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : record.status || '未配置';
      },
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 170,
      search: false,
      render: (_, record) => formatDateTime(record.updatedAt),
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
        normalizeStatus(record.status) === 'draft' && (
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
      }}
      content={
        <Row gutter={[16, 16]}>
          {summaryCards.map((item) => (
            <Col key={item.key} xs={24} sm={12} md={12} lg={6}>
              <Card size="small">
                <Statistic
                  title={item.title}
                  value={item.value}
                  prefix={item.prefix}
                  styles={{ content: item.valueStyle }}
                />
              </Card>
            </Col>
          ))}
        </Row>
      }
    >
      <ProTable<PayrollCycleDto>
        cardBordered
        headerTitle="周期列表"
        columns={columns}
        actionRef={actionRef}
        scroll={{ x: 1280 }}
        rowKey={(record) =>
          String(
            record.id ??
              `${record.type ?? record.cycleType ?? 'cycle'}-${record.periodLabel ?? ''}`,
          )
        }
        request={async (params) => {
          const nextParams: PayrollCycleListParams = {
            current: params.current || 1,
            pageSize: params.pageSize || 10,
            periodLabel: params.periodLabel,
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
        pagination={{
          current: queryParams.current,
          pageSize: queryParams.pageSize,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条/共 ${total} 条`,
        }}
        locale={{ emptyText: '暂无发薪周期' }}
        toolBarRender={() => [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            新增周期
          </Button>,
        ]}
        options={{ reload: true, density: true, setting: true }}
      />

      <Modal
        title={modalType === 'create' ? '新增发薪周期' : '编辑发薪周期'}
        open={isModalOpen}
        onOk={handleSubmit}
        onCancel={() => setIsModalOpen(false)}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        width={760}
        forceRender
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={12}>
            <Col xs={24} sm={12}>
              <Form.Item
                name="type"
                label="主类型"
                rules={[{ required: true, message: '请选择主类型' }]}
              >
                <Select
                  placeholder="请选择主类型"
                  options={Object.entries(cycleMainTypeEnum).map(([key, value]) => ({
                    label: value.text,
                    value: key,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="cycleType"
                label="周期类型"
                rules={[{ required: true, message: '请选择周期类型' }]}
              >
                <Select
                  placeholder="请选择周期类型"
                  options={Object.entries(cycleTypeEnum).map(([key, value]) => ({
                    label: value.text,
                    value: key,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="periodLabel"
                label="周期标签"
                rules={[
                  { required: true, message: '请输入周期标签' },
                  { pattern: /^\d{4}-\d{2}$/, message: '格式需为 YYYY-MM' },
                ]}
              >
                <Input placeholder="如：2025-01" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="cycleCode"
                label="周期编码"
                tooltip="不填将自动生成，建议仅使用字母、数字、下划线"
              >
                <Input placeholder="如：CN_MAIN_2025_01" />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item
                name="cycleName"
                label="周期名称"
                rules={[{ required: true, message: '请输入周期名称' }]}
              >
                <Input placeholder="如：2025年1月全员薪资周期" />
              </Form.Item>
            </Col>
          </Row>

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

          <Row gutter={12}>
            <Col xs={24} sm={12} md={8}>
              <Form.Item name="cutoffDate" label="截数日">
                <DatePicker placeholder="选择截数日" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} md={8}>
              <Form.Item name="payDay" label="发薪日">
                <InputNumber min={1} max={31} style={{ width: '100%' }} placeholder="1-31" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} md={8}>
              <Form.Item name="timezone" label="时区">
                <Input placeholder="如：UTC+8" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} md={8}>
              <Form.Item name="leadDays" label="提前天数">
                <InputNumber min={0} max={365} style={{ width: '100%' }} placeholder="0-365" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} md={8}>
              <Form.Item name="graceDays" label="宽限天数">
                <InputNumber min={0} max={365} style={{ width: '100%' }} placeholder="0-365" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} md={8}>
              <Form.Item
                name="status"
                label="状态"
                rules={[{ required: true, message: '请选择状态' }]}
              >
                <Select placeholder="请选择状态" options={statusOptions} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="description" label="描述">
                <Input.TextArea
                  rows={3}
                  maxLength={500}
                  placeholder="可填写本周期的规则说明、适用范围等"
                />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      <Drawer
        size={620}
        title={detail ? `周期详情 · ${getCycleDisplayName(detail)}` : '周期详情'}
        extra={detail?.cycleCode ? <Tag color="blue">{detail.cycleCode}</Tag> : undefined}
        open={Boolean(detail)}
        onClose={() => setDetail(undefined)}
        destroyOnHidden
      >
        {detail ? (
          <Space orientation="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="周期名称" span={2}>
                {getCycleDisplayName(detail)}
              </Descriptions.Item>
              <Descriptions.Item label="周期标签">
                {detail.periodLabel || '未配置'}
              </Descriptions.Item>
              <Descriptions.Item label="周期编码">{detail.cycleCode || '未配置'}</Descriptions.Item>
              <Descriptions.Item label="主类型">{getMainTypeText(detail.type)}</Descriptions.Item>
              <Descriptions.Item label="周期类型">{getCycleTypeText(detail)}</Descriptions.Item>
              <Descriptions.Item label="状态">
                {getStatusMeta(detail.status)?.text || detail.status || '未配置'}
              </Descriptions.Item>
              <Descriptions.Item label="期间范围" span={2}>
                {formatPeriodRange(detail.startDate, detail.endDate)}
              </Descriptions.Item>
              <Descriptions.Item label="截数日">
                {formatDay(detail.cutoffDay ?? detail.cutoffDate)}
              </Descriptions.Item>
              <Descriptions.Item label="发薪日">{formatDay(detail.payDay)}</Descriptions.Item>
              <Descriptions.Item label="提前天数">{formatDay(detail.leadDays)}</Descriptions.Item>
              <Descriptions.Item label="宽限天数">{formatDay(detail.graceDays)}</Descriptions.Item>
              <Descriptions.Item label="时区">{detail.timezone || '未配置'}</Descriptions.Item>
              <Descriptions.Item label="下次执行">
                {formatExecutionTimeFriendly(detail.nextExecutionTime, detail.status)}
              </Descriptions.Item>
              <Descriptions.Item label="最近执行">
                {formatExecutionTimeFriendly(detail.lastExecutionTime, detail.status)}
              </Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>
                {detail.description || '未配置'}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {formatDateTime(detail.createdAt)}
              </Descriptions.Item>
              <Descriptions.Item label="更新时间">
                {formatDateTime(detail.updatedAt)}
              </Descriptions.Item>
            </Descriptions>

            <Alert
              type="info"
              showIcon
              title="执行提示"
              description="推荐流程：草稿配置完成后再启用。启用中的周期如需修改，请先停用再编辑，避免影响调度一致性。"
            />
          </Space>
        ) : (
          <Alert type="info" title="请选择一条周期记录" showIcon />
        )}
      </Drawer>
    </PageContainer>
  );
};

export default CyclesPage;
