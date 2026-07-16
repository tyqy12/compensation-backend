import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  App as AntdApp,
  Button,
  Calendar as AntdCalendar,
  DatePicker,
  Descriptions,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import {
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  FileProtectOutlined,
  FieldTimeOutlined,
  PlusOutlined,
  ReloadOutlined,
  ScheduleOutlined,
  SettingOutlined,
  TeamOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { getPagedRecords } from '@services/api';
import {
  useCreatePayrollCycleMutation,
  useDeletePayrollCycleMutation,
  usePayrollCyclesQuery,
  useUpdatePayrollCycleMutation,
  type PayCycleCreateParams,
  type PayCycleUpdateParams,
  type PayrollCycleListParams,
} from '@services/queries/payroll';
import type { PayrollCycleDto } from '../../types/openapi';
import './PayrollPages.less';
import './Calendar.less';

const { Text, Title } = Typography;

const payrollTypeOptions = [
  { label: '全职', value: 'full_time', color: 'blue' },
  { label: '兼职', value: 'part_time', color: 'gold' },
  { label: '外包', value: 'contractor', color: 'purple' },
];

const cycleTypeOptions = [
  { label: '月度', value: 'monthly' },
  { label: '半月', value: 'semi_monthly' },
  { label: '周度', value: 'weekly' },
  { label: '双周', value: 'biweekly' },
  { label: '自定义', value: 'custom' },
];

const cycleStatusMeta: Record<string, { label: string; color: string }> = {
  draft: { label: '草稿', color: 'default' },
  open: { label: '开放', color: 'processing' },
  closed: { label: '停用', color: 'warning' },
  archived: { label: '归档', color: 'default' },
};

const statusTransitions: Record<string, string[]> = {
  draft: ['open', 'archived'],
  open: ['closed'],
  closed: ['open', 'archived'],
  archived: [],
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

type CalendarEvent = {
  key: string;
  label: string;
  tone: 'period' | 'cutoff' | 'pay';
};

const normalizeStatus = (value?: string) => String(value || 'draft').toLowerCase();

const getTypeMeta = (value?: string) =>
  payrollTypeOptions.find((item) => item.value === value) || {
    label: value || '未配置',
    value: value || '',
    color: 'default',
  };

const getCycleTypeLabel = (value?: string) =>
  cycleTypeOptions.find((item) => item.value === value)?.label || value || '未配置';

const getStatusMeta = (value?: string) =>
  cycleStatusMeta[normalizeStatus(value)] || { label: value || '未配置', color: 'default' };

const formatDate = (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD') : '未配置');

const formatDateTime = (value?: string) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '未记录';

const getCycleName = (cycle: PayrollCycleDto) =>
  cycle.cycleName || `${cycle.periodLabel || '未命名'}发薪日历`;

const getEventsForDate = (cycle: PayrollCycleDto, date: Dayjs): CalendarEvent[] => {
  const dateKey = date.format('YYYY-MM-DD');
  const events: CalendarEvent[] = [];
  if (cycle.startDate === dateKey) events.push({ key: 'period-start', label: '期间开始', tone: 'period' });
  if (cycle.endDate === dateKey) events.push({ key: 'period-end', label: '期间结束', tone: 'period' });
  if (cycle.cutoffDate === dateKey) events.push({ key: 'cutoff', label: '截数日', tone: 'cutoff' });
  if (cycle.payDate === dateKey) events.push({ key: 'pay-date', label: '发薪日', tone: 'pay' });
  if (cycle.payDay && date.date() === Number(cycle.payDay) && date.format('YYYY-MM') === cycle.periodLabel) {
    events.push({ key: 'pay-day', label: `发薪日 ${cycle.payDay} 日`, tone: 'pay' });
  }
  return events;
};

const getNextStatusOptions = (record?: PayrollCycleDto) => {
  const current = normalizeStatus(record?.status);
  return Array.from(new Set([current, ...(statusTransitions[current] || [])])).map((value) => ({
    label: getStatusMeta(value).label,
    value,
  }));
};

const CalendarPage: React.FC = () => {
  const { message, modal } = AntdApp.useApp();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [month, setMonth] = useState<Dayjs>(() => {
    const value = dayjs(searchParams.get('period') || undefined);
    return value.isValid() ? value.startOf('month') : dayjs().startOf('month');
  });
  const [status, setStatus] = useState(searchParams.get('status') || '');
  const [activeCycleId, setActiveCycleId] = useState<number | undefined>(() => {
    const value = Number(searchParams.get('cycleId'));
    return Number.isFinite(value) && value > 0 ? value : undefined;
  });
  const [modalOpen, setModalOpen] = useState(false);
  const [modalType, setModalType] = useState<'create' | 'edit'>('create');
  const [editingRecord, setEditingRecord] = useState<PayrollCycleDto | null>(null);
  const [form] = Form.useForm<CycleFormValues>();

  const queryParams = useMemo<PayrollCycleListParams>(
    () => ({
      current: 1,
      pageSize: 200,
      periodLabel: month.format('YYYY-MM'),
      status: status || undefined,
    }),
    [month, status],
  );
  const cyclesQuery = usePayrollCyclesQuery(queryParams);
  const createMutation = useCreatePayrollCycleMutation();
  const updateMutation = useUpdatePayrollCycleMutation();
  const deleteMutation = useDeletePayrollCycleMutation();
  const records = useMemo(
    () => getPagedRecords(cyclesQuery.data) as PayrollCycleDto[],
    [cyclesQuery.data],
  );
  const selected = useMemo(
    () => records.find((record) => record.id === activeCycleId) || records[0],
    [activeCycleId, records],
  );

  useEffect(() => {
    if (selected?.id && selected.id !== activeCycleId) {
      setActiveCycleId(selected.id);
      const next = new URLSearchParams(searchParams);
      next.set('cycleId', String(selected.id));
      next.set('period', month.format('YYYY-MM'));
      if (status) next.set('status', status);
      setSearchParams(next, { replace: true });
    }
  }, [activeCycleId, month, searchParams, selected, setSearchParams, status]);

  const updateCalendarContext = useCallback(
    (next: { period?: string; status?: string; cycleId?: string }) => {
      const params = new URLSearchParams(searchParams);
      const period = next.period ?? month.format('YYYY-MM');
      const nextStatus = next.status ?? status;
      params.set('period', period);
      if (nextStatus) params.set('status', nextStatus);
      else params.delete('status');
      if (next.cycleId) params.set('cycleId', next.cycleId);
      else params.delete('cycleId');
      setSearchParams(params, { replace: true });
    },
    [month, searchParams, setSearchParams, status],
  );

  const openCreate = useCallback(() => {
    const period = month.format('YYYY-MM');
    form.resetFields();
    form.setFieldsValue({
      type: searchParams.get('type') || 'full_time',
      periodLabel: period,
      cycleName: `${month.format('YYYY年MM月')}发薪日历`,
      cycleType: 'monthly',
      startDate: month.startOf('month'),
      endDate: month.endOf('month'),
      cutoffDate: month.endOf('month').subtract(5, 'day'),
      payDay: 10,
      leadDays: 5,
      graceDays: 0,
      timezone: 'UTC+8',
      status: 'draft',
    });
    setEditingRecord(null);
    setModalType('create');
    setModalOpen(true);
  }, [form, month, searchParams]);

  const openEdit = useCallback(
    (record: PayrollCycleDto) => {
      setEditingRecord(record);
      setModalType('edit');
      form.setFieldsValue({
        type: record.type || 'full_time',
        periodLabel: record.periodLabel || month.format('YYYY-MM'),
        cycleCode: record.cycleCode,
        cycleName: getCycleName(record),
        cycleType: record.cycleType || 'monthly',
        startDate: record.startDate ? dayjs(record.startDate) : undefined,
        endDate: record.endDate ? dayjs(record.endDate) : undefined,
        cutoffDate: record.cutoffDate ? dayjs(record.cutoffDate) : undefined,
        payDay: record.payDay,
        leadDays: record.leadDays,
        graceDays: record.graceDays,
        timezone: record.timezone || 'UTC+8',
        description: record.description || undefined,
        status: normalizeStatus(record.status),
      });
      setModalOpen(true);
    },
    [form, month],
  );

  const handleSubmit = useCallback(async () => {
    try {
      const values = await form.validateFields();
      const payload: PayCycleCreateParams = {
        type: values.type,
        periodLabel: values.periodLabel.trim(),
        cycleCode: values.cycleCode?.trim() || undefined,
        cycleName: values.cycleName.trim(),
        cycleType: values.cycleType,
        startDate: values.startDate?.format('YYYY-MM-DD'),
        endDate: values.endDate?.format('YYYY-MM-DD'),
        cutoffDate: values.cutoffDate?.format('YYYY-MM-DD'),
        payDay: values.payDay,
        leadDays: values.leadDays,
        graceDays: values.graceDays,
        timezone: values.timezone?.trim() || undefined,
        description: values.description?.trim() || undefined,
        status: values.status,
      };
      let saved: PayrollCycleDto;
      if (modalType === 'create') {
        saved = await createMutation.mutateAsync(payload);
        message.success('发薪日历已创建');
      } else if (editingRecord?.id) {
        saved = await updateMutation.mutateAsync({
          id: editingRecord.id,
          params: { ...payload, id: editingRecord.id } as PayCycleUpdateParams,
        });
        message.success('发薪日历已更新');
      } else {
        return;
      }
      setModalOpen(false);
      await cyclesQuery.refetch();
      if (saved.id) {
        setActiveCycleId(saved.id);
        const nextPeriod = saved.periodLabel || values.periodLabel;
        const nextMonth = dayjs(nextPeriod).startOf('month');
        if (nextMonth.isValid()) setMonth(nextMonth);
        updateCalendarContext({ period: nextPeriod, cycleId: String(saved.id) });
      }
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error?.response?.data?.message || error?.message || '发薪日历保存失败');
    }
  }, [
    createMutation,
    cyclesQuery,
    editingRecord?.id,
    form,
    message,
    modalType,
    updateCalendarContext,
    updateMutation,
  ]);

  const handleDelete = useCallback(
    (record: PayrollCycleDto) => {
      modal.confirm({
        title: '删除发薪日历',
        content: `确认删除「${getCycleName(record)}」吗？仅草稿且未关联批次的日历可以删除。`,
        okText: '删除',
        cancelText: '取消',
        okButtonProps: { danger: true },
        onOk: async () => {
          if (!record.id) return;
          try {
            await deleteMutation.mutateAsync(record.id);
            message.success('发薪日历已删除');
            setActiveCycleId(undefined);
            await cyclesQuery.refetch();
          } catch (error: any) {
            message.error(error?.response?.data?.message || error?.message || '发薪日历删除失败');
          }
        },
      });
    },
    [cyclesQuery, deleteMutation, message, modal],
  );

  const readiness = selected
    ? [
        { label: '适用用工类型', ready: Boolean(selected.type), value: getTypeMeta(selected.type).label },
        { label: '期间边界', ready: Boolean(selected.startDate && selected.endDate), value: `${formatDate(selected.startDate)} ~ ${formatDate(selected.endDate)}` },
        { label: '截数日', ready: Boolean(selected.cutoffDate), value: formatDate(selected.cutoffDate) },
        { label: '发薪日', ready: Boolean(selected.payDay || selected.payDate), value: selected.payDate ? formatDate(selected.payDate) : selected.payDay ? `每月 ${selected.payDay} 日` : '未配置' },
      ]
    : [];

  const summary = useMemo(
    () => ({
      total: records.length,
      open: records.filter((record) => normalizeStatus(record.status) === 'open').length,
      draft: records.filter((record) => normalizeStatus(record.status) === 'draft').length,
      ready: records.filter((record) => record.startDate && record.endDate && (record.payDay || record.payDate)).length,
    }),
    [records],
  );

  const renderDateCell = useCallback(
    (date: Dayjs) => {
      const events = records.flatMap((record) =>
        getEventsForDate(record, date).map((event) => ({ ...event, cycle: record })),
      );
      if (events.length === 0) return null;
      return (
        <ul className="calendar-date-events">
          {events.slice(0, 3).map((event) => (
            <li
              key={`${event.cycle.id}-${event.key}`}
              className={`calendar-date-event is-${event.tone}${event.cycle.id === selected?.id ? ' is-selected' : ''}`}
              onClick={(clickEvent) => {
                clickEvent.stopPropagation();
                if (event.cycle.id) {
                  setActiveCycleId(event.cycle.id);
                  updateCalendarContext({ cycleId: String(event.cycle.id) });
                }
              }}
            >
              <span>{event.label}</span>
            </li>
          ))}
        </ul>
      );
    },
    [records, selected?.id, updateCalendarContext],
  );

  const isSaving = createMutation.isPending || updateMutation.isPending;

  return (
    <PageContainer
      className="payroll-page-shell calendar-page"
      header={{
        title: '发薪日历',
        subTitle: '把期间、截数日、发薪日和运行状态放在同一条时间线上',
        extra: [
          <Button key="rules" icon={<FileProtectOutlined />} onClick={() => navigate('/payroll/templates')}>
            查看规则中心
          </Button>,
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建发薪日历
          </Button>,
        ],
      }}
    >
      <Alert
        className="calendar-page-alert"
        type="info"
        showIcon
        icon={<ScheduleOutlined />}
        title="发薪日历是运行计划，不是薪酬模板"
        description="每个日历代表某个用工类型在某个期间的一次发薪安排。创建批次时应引用开放日历，批次计算再读取对应的规则包。"
      />

      <div className="calendar-context-toolbar">
        <Space wrap>
          <DatePicker
            picker="month"
            value={month}
            allowClear={false}
            onChange={(value) => {
              if (!value) return;
              const nextMonth = value.startOf('month');
              setMonth(nextMonth);
              setActiveCycleId(undefined);
              updateCalendarContext({ period: nextMonth.format('YYYY-MM'), cycleId: undefined });
            }}
          />
          <Select
            allowClear
            value={status || undefined}
            placeholder="全部运行状态"
            options={Object.entries(cycleStatusMeta).map(([value, item]) => ({ label: item.label, value }))}
            onChange={(value) => {
              const nextStatus = value || '';
              setStatus(nextStatus);
              setActiveCycleId(undefined);
              updateCalendarContext({ status: nextStatus, cycleId: undefined });
            }}
            style={{ width: 150 }}
          />
          <Button icon={<ReloadOutlined />} loading={cyclesQuery.isFetching} onClick={() => void cyclesQuery.refetch()}>
            刷新日历
          </Button>
        </Space>
        <Space wrap className="calendar-context-actions">
          <Button icon={<FileProtectOutlined />} onClick={() => navigate(`/payroll/templates?type=${searchParams.get('type') || ''}`)}>
            按类型看规则
          </Button>
          <Button icon={<CalendarOutlined />} onClick={() => navigate(`/payroll/batches?period=${month.format('YYYY-MM')}`)}>
            查看本月批次
          </Button>
        </Space>
      </div>

      <div className="calendar-summary-grid">
        <div className="calendar-summary-tile"><Text type="secondary">本月日历</Text><strong>{summary.total}</strong><span>当前期间记录</span></div>
        <div className="calendar-summary-tile"><Text type="secondary">开放运行</Text><strong className="is-success">{summary.open}</strong><span>可被批次引用</span></div>
        <div className="calendar-summary-tile"><Text type="secondary">草稿安排</Text><strong>{summary.draft}</strong><span>等待校验或启用</span></div>
        <div className="calendar-summary-tile"><Text type="secondary">信息完整</Text><strong>{summary.ready}</strong><span>已配置期间与发薪日</span></div>
      </div>

      <div className="calendar-workbench">
        <section className="calendar-main-panel">
          <div className="calendar-panel-heading">
            <div>
              <Title level={4}>{month.format('YYYY年MM月')}发薪安排</Title>
              <Text type="secondary">点击日期标记可定位对应运行计划</Text>
            </div>
            <Tag icon={<ClockCircleOutlined />}>{records.length} 个安排</Tag>
          </div>
          {cyclesQuery.isLoading ? (
            <div className="calendar-loading"><Spin /></div>
          ) : (
            <AntdCalendar
              fullscreen={false}
              value={month}
              onSelect={(value) => {
                if (value.month() !== month.month()) {
                  const nextMonth = value.startOf('month');
                  setMonth(nextMonth);
                  updateCalendarContext({ period: nextMonth.format('YYYY-MM'), cycleId: undefined });
                }
              }}
              onPanelChange={(value) => {
                const nextMonth = value.startOf('month');
                setMonth(nextMonth);
                updateCalendarContext({ period: nextMonth.format('YYYY-MM'), cycleId: undefined });
              }}
              cellRender={renderDateCell}
            />
          )}
        </section>

        <aside className="calendar-detail-panel">
          {!selected ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="本月暂无发薪日历" />
          ) : (
            <>
              <div className="calendar-detail-heading">
                <div className="calendar-detail-identity">
                  <div className="calendar-detail-icon"><CalendarOutlined /></div>
                  <div>
                    <Title level={4}>{getCycleName(selected)}</Title>
                    <Space wrap size={[6, 4]}>
                      <Tag color={getStatusMeta(selected.status).color}>{getStatusMeta(selected.status).label}</Tag>
                      <Tag color={getTypeMeta(selected.type).color}>{getTypeMeta(selected.type).label}</Tag>
                      <Tag>{getCycleTypeLabel(selected.cycleType)}</Tag>
                    </Space>
                  </div>
                </div>
                <Space>
                  <Button type="text" icon={<EditOutlined />} onClick={() => openEdit(selected)} aria-label="编辑日历" />
                  {normalizeStatus(selected.status) === 'draft' && (
                    <Button type="text" danger icon={<DeleteOutlined />} onClick={() => handleDelete(selected)} aria-label="删除日历" />
                  )}
                </Space>
              </div>
              <Descriptions column={1} size="small" className="calendar-detail-descriptions">
                <Descriptions.Item label="期间">{formatDate(selected.startDate)} ~ {formatDate(selected.endDate)}</Descriptions.Item>
                <Descriptions.Item label="截数日">{formatDate(selected.cutoffDate)}</Descriptions.Item>
                <Descriptions.Item label="发薪日">{selected.payDate ? formatDate(selected.payDate) : selected.payDay ? `每月 ${selected.payDay} 日` : '未配置'}</Descriptions.Item>
                <Descriptions.Item label="时区">{selected.timezone || '未配置'}</Descriptions.Item>
                <Descriptions.Item label="最近执行">{formatDateTime(selected.lastExecutionTime)}</Descriptions.Item>
                <Descriptions.Item label="下次执行">{formatDateTime(selected.nextExecutionTime)}</Descriptions.Item>
              </Descriptions>
              <div className="calendar-readiness">
                <div className="calendar-subheading"><Text strong>运行前检查</Text><Text type="secondary">{readiness.filter((item) => item.ready).length}/{readiness.length}</Text></div>
                {readiness.map((item) => (
                  <div className="calendar-readiness-row" key={item.label}>
                    {item.ready ? <CheckCircleOutlined className="is-ready" /> : <WarningOutlined className="is-not-ready" />}
                    <span><strong>{item.label}</strong><Text type="secondary">{item.value}</Text></span>
                  </div>
                ))}
              </div>
              <Space wrap className="calendar-detail-actions">
                <Button icon={<FileProtectOutlined />} onClick={() => navigate(`/payroll/templates?type=${selected.type || ''}`)}>查看规则</Button>
                <Button type="primary" icon={<FieldTimeOutlined />} onClick={() => navigate(`/payroll/batches?period=${selected.periodLabel || ''}`)}>进入批次工作台</Button>
              </Space>
            </>
          )}
        </aside>
      </div>

      <section className="calendar-agenda-panel">
        <div className="calendar-panel-heading">
          <div>
            <Title level={4}>本月运行计划</Title>
            <Text type="secondary">每条记录都是一个可以被批次引用的期间安排</Text>
          </div>
        </div>
        {records.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无运行计划" />
        ) : (
          <div className="calendar-agenda-list">
            {records.map((record) => (
              <button
                type="button"
                key={record.id ?? record.cycleCode}
                className={`calendar-agenda-row${record.id === selected?.id ? ' is-selected' : ''}`}
                onClick={() => {
                  if (!record.id) return;
                  setActiveCycleId(record.id);
                  updateCalendarContext({ cycleId: String(record.id) });
                }}
              >
                <span className="calendar-agenda-row-main">
                  <span className="calendar-agenda-row-title"><ScheduleOutlined /><strong>{getCycleName(record)}</strong></span>
                  <span className="calendar-agenda-row-meta">{getTypeMeta(record.type).label} · {getCycleTypeLabel(record.cycleType)} · {formatDate(record.startDate)} ~ {formatDate(record.endDate)}</span>
                </span>
                <span className="calendar-agenda-row-dates">
                  <span><Text type="secondary">截数</Text><strong>{record.cutoffDate ? dayjs(record.cutoffDate).format('MM-DD') : '—'}</strong></span>
                  <span><Text type="secondary">发薪</Text><strong>{record.payDate ? dayjs(record.payDate).format('MM-DD') : record.payDay ? `${record.payDay}日` : '—'}</strong></span>
                  <Tag color={getStatusMeta(record.status).color}>{getStatusMeta(record.status).label}</Tag>
                </span>
              </button>
            ))}
          </div>
        )}
      </section>

      <Modal
        title={modalType === 'create' ? '新建发薪日历' : '编辑发薪日历'}
        open={modalOpen}
        onOk={() => void handleSubmit()}
        onCancel={() => setModalOpen(false)}
        confirmLoading={isSaving}
        width={760}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          title="日历只描述运行安排"
          description="规则包请在规则中心维护。开放状态的日历才能被新建批次引用。"
          style={{ marginBottom: 16 }}
        />
        <Form form={form} layout="vertical" className="calendar-form">
          <Form.Item name="cycleName" label="日历名称" rules={[{ required: true, message: '请输入日历名称' }]}>
            <Input placeholder="如：2026年7月全职月薪" />
          </Form.Item>
          <Form.Item name="type" label="适用用工类型" rules={[{ required: true, message: '请选择用工类型' }]}>
            <Select options={payrollTypeOptions.map(({ label, value }) => ({ label, value }))} />
          </Form.Item>
          <Form.Item
            name="periodLabel"
            label="期间标签"
            rules={[{ required: true, message: '请输入期间标签' }, { pattern: /^\d{4}-\d{2}$/, message: '格式需为 YYYY-MM' }]}
          >
            <Input placeholder="如：2026-07" />
          </Form.Item>
          <Form.Item name="cycleType" label="运行频率" rules={[{ required: true, message: '请选择运行频率' }]}>
            <Select options={cycleTypeOptions} />
          </Form.Item>
          <Form.Item name="cycleCode" label="日历编码"><Input placeholder="可选，如 CN_FULL_TIME_202607" /></Form.Item>
          <Form.Item name="status" label="运行状态" rules={[{ required: true, message: '请选择运行状态' }]}>
            <Select options={getNextStatusOptions(editingRecord || undefined)} />
          </Form.Item>
          <Form.Item name="startDate" label="期间开始"><DatePicker style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="endDate" label="期间结束"><DatePicker style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="cutoffDate" label="截数日"><DatePicker style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="payDay" label="发薪日（每月几日）"><InputNumber min={1} max={31} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="leadDays" label="提前天数"><InputNumber min={0} max={365} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="graceDays" label="宽限天数"><InputNumber min={0} max={365} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="timezone" label="时区"><Input placeholder="如：UTC+8" /></Form.Item>
          <Form.Item name="description" label="运行说明" className="calendar-form-wide"><Input.TextArea rows={3} maxLength={500} /></Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default CalendarPage;
