import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  App as AntdApp,
  Badge,
  Button,
  Descriptions,
  Divider,
  Empty,
  Form,
  Input,
  Segmented,
  Select,
  Space,
  Spin,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import {
  AuditOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  CopyOutlined,
  EditOutlined,
  FileProtectOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  SettingOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  useCreateSalaryTemplateMutation,
  useSalaryTemplateQuery,
  useSalaryTemplatesQuery,
  useUpdateSalaryTemplateMutation,
  type SalaryTemplateCreateParams,
  type SalaryTemplateDto,
  type SalaryTemplateListParams,
} from '@services/queries/payroll';
import { getPagedRecords } from '@services/api';
import SalaryItemsConfig from '@components/SalaryConfig/SalaryItemsConfig';
import TaxRulesConfig from '@components/SalaryConfig/TaxRulesConfig';
import './PayrollPages.less';
import './Rules.less';

const { Text, Title } = Typography;

export interface RuleSalaryItem {
  code: string;
  name: string;
  type: 'earning' | 'deduction';
  required: boolean;
  min: number;
  max?: number;
  description?: string;
}

export interface RuleTaxItem {
  ruleCode: string;
  ruleName?: string;
  rate?: number;
  threshold?: number;
  applyOn?: string;
  mode?: string;
  calculationMode?: string;
  scale?: number;
}

const payrollTypeOptions = [
  { label: '全职', value: 'full_time', color: 'blue' },
  { label: '兼职', value: 'part_time', color: 'gold' },
  { label: '外包', value: 'contractor', color: 'purple' },
];

const statusOptions = [
  { label: '启用', value: 'enabled', color: 'success' },
  { label: '停用', value: 'disabled', color: 'default' },
];

const DEFAULT_ITEMS: RuleSalaryItem[] = [
  {
    code: 'base_salary',
    name: '基本工资',
    type: 'earning',
    required: true,
    min: 0,
    description: '员工的基本薪资',
  },
  {
    code: 'bonus',
    name: '奖金',
    type: 'earning',
    required: false,
    min: 0,
    description: '绩效奖金、年终奖等',
  },
  {
    code: 'allowance',
    name: '津贴补贴',
    type: 'earning',
    required: false,
    min: 0,
    description: '餐补、交通补、住房补等',
  },
  {
    code: 'tax',
    name: '个人所得税',
    type: 'deduction',
    required: true,
    min: 0,
    description: '工资薪金所得个人所得税',
  },
  {
    code: 'social_security',
    name: '社保扣款',
    type: 'deduction',
    required: true,
    min: 0,
    description: '个人缴纳社保部分',
  },
  {
    code: 'housing_fund',
    name: '公积金扣款',
    type: 'deduction',
    required: true,
    min: 0,
    description: '个人缴纳公积金部分',
  },
];

const DEFAULT_TAX_RULES: RuleTaxItem[] = [
  {
    ruleCode: 'income_tax',
    ruleName: '个人所得税',
    applyOn: 'TAXABLE_EARNINGS',
    mode: 'HALF_UP',
    calculationMode: 'cumulative_withholding',
    scale: 2,
  },
  {
    ruleCode: 'social_security',
    ruleName: '社保个人缴费',
    rate: 0.1,
    applyOn: 'TAXABLE_EARNINGS',
    mode: 'HALF_UP',
    scale: 2,
  },
  {
    ruleCode: 'housing_fund',
    ruleName: '公积金个人缴费',
    rate: 0.12,
    applyOn: 'TAXABLE_EARNINGS',
    mode: 'HALF_UP',
    scale: 2,
  },
];

const parseJson = <T,>(value: string | undefined, fallback: T): T => {
  if (!value) return fallback;
  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
};

export const parseRuleItems = (value?: string): RuleSalaryItem[] => {
  const parsed = parseJson<unknown>(value, []);
  if (!Array.isArray(parsed)) return [...DEFAULT_ITEMS];
  return parsed.map((item, index) => {
    const source = item as Partial<RuleSalaryItem>;
    return {
      code: source.code || `item_${index + 1}`,
      name: source.name || source.code || `薪资项目 ${index + 1}`,
      type: source.type === 'deduction' ? 'deduction' : 'earning',
      required: Boolean(source.required),
      min: Number(source.min) || 0,
      max: source.max === undefined || source.max === null ? undefined : Number(source.max),
      description: source.description,
    };
  });
};

export const parseTaxRules = (value?: string): RuleTaxItem[] => {
  const parsed = parseJson<unknown>(value, null);
  if (Array.isArray(parsed)) return parsed as RuleTaxItem[];
  if (!parsed || typeof parsed !== 'object') return [...DEFAULT_TAX_RULES];
  return Object.entries(parsed as Record<string, unknown>).map(([ruleCode, config]) => ({
    ruleCode,
    ruleName:
      ruleCode === 'tax'
        ? '个人所得税'
        : ruleCode === 'social'
          ? '社保个人缴费'
          : ruleCode === 'rounding'
            ? '金额舍入'
            : ruleCode,
    ...(typeof config === 'object' && config ? config : {}),
    ...(ruleCode === 'tax' && typeof config === 'object' && config &&
    ['cumulative_withholding', 'cumulative-withholding'].includes(String((config as Record<string, unknown>).mode))
      ? { calculationMode: String((config as Record<string, unknown>).mode), mode: 'HALF_UP' }
      : {}),
  })) as RuleTaxItem[];
};

export const getRuleMetrics = (record?: SalaryTemplateDto | null) => {
  const items = parseRuleItems(record?.itemsJson);
  const rules = parseTaxRules(record?.taxRuleJson);
  return {
    items,
    rules,
    itemCount: items.length,
    requiredCount: items.filter((item) => item.required).length,
    earningCount: items.filter((item) => item.type === 'earning').length,
    deductionCount: items.filter((item) => item.type === 'deduction').length,
    ruleCount: rules.length,
  };
};

const formatDateTime = (value?: string) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '尚未更新';

const getTypeMeta = (type?: string) =>
  payrollTypeOptions.find((item) => item.value === type) || {
    label: type || '未配置',
    value: type || '',
    color: 'default',
  };

const getStatusMeta = (status?: string) =>
  statusOptions.find((item) => item.value === status) || {
    label: status || '未配置',
    value: status || '',
    color: 'default',
  };

const normalizeRuleParams = (value: Record<string, string | undefined>) => {
  const next = new URLSearchParams();
  Object.entries(value).forEach(([key, item]) => {
    if (item) next.set(key, item);
  });
  return next;
};

const RulesPage: React.FC = () => {
  const { message } = AntdApp.useApp();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [keyword, setKeyword] = useState(searchParams.get('q') || '');
  const [type, setType] = useState(searchParams.get('type') || '');
  const [status, setStatus] = useState(searchParams.get('status') || '');
  const [selectedId, setSelectedId] = useState<number | undefined>(() => {
    const value = Number(searchParams.get('templateId'));
    return Number.isFinite(value) && value > 0 ? value : undefined;
  });
  const [editorMode, setEditorMode] = useState<'view' | 'edit' | 'create'>('view');
  const [form] = Form.useForm<{ name: string; type: string; status: string }>();
  const [salaryItems, setSalaryItems] = useState<RuleSalaryItem[]>(DEFAULT_ITEMS);
  const [taxRules, setTaxRules] = useState<RuleTaxItem[]>(DEFAULT_TAX_RULES);

  const queryParams = useMemo<SalaryTemplateListParams>(
    () => ({ current: 1, pageSize: 200, type: type || undefined, status: status || undefined }),
    [status, type],
  );
  const templatesQuery = useSalaryTemplatesQuery(queryParams);
  const detailQuery = useSalaryTemplateQuery(selectedId ?? 0, { enabled: Boolean(selectedId) });
  const createMutation = useCreateSalaryTemplateMutation();
  const updateMutation = useUpdateSalaryTemplateMutation();

  const records = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    return (getPagedRecords(templatesQuery.data) as SalaryTemplateDto[]).filter((record) => {
      if (!normalizedKeyword) return true;
      return `${record.name} ${record.type}`.toLowerCase().includes(normalizedKeyword);
    });
  }, [keyword, templatesQuery.data]);

  const selectedSummary = useMemo(
    () => records.find((record) => record.id === selectedId) || records[0],
    [records, selectedId],
  );
  const selected = detailQuery.data || selectedSummary;
  const selectedMetrics = useMemo(() => getRuleMetrics(selected), [selected]);
  const requiresTaxMigration = selectedMetrics.rules.some(
    (rule) => (rule.ruleCode === 'tax' || rule.ruleCode === 'income_tax' || rule.ruleName?.includes('税')) &&
      rule.calculationMode !== 'cumulative_withholding' && rule.calculationMode !== 'cumulative-withholding',
  );

  useEffect(() => {
    if (!selectedId && records[0]?.id) {
      setSelectedId(records[0].id);
      setSearchParams(
        normalizeRuleParams({
          q: keyword || undefined,
          type: type || undefined,
          status: status || undefined,
          templateId: String(records[0].id),
        }),
        { replace: true },
      );
    }
  }, [keyword, records, selectedId, setSearchParams, status, type]);

  const syncFilters = useCallback(
    (next: { q?: string; type?: string; status?: string; templateId?: string }) => {
      const hasTemplateId = Object.prototype.hasOwnProperty.call(next, 'templateId');
      setSearchParams(
        normalizeRuleParams({
          q: next.q !== undefined ? next.q || undefined : keyword || undefined,
          type: next.type !== undefined ? next.type || undefined : type || undefined,
          status: next.status !== undefined ? next.status || undefined : status || undefined,
          templateId: hasTemplateId
            ? next.templateId || undefined
            : selectedId
              ? String(selectedId)
              : undefined,
        }),
        { replace: true },
      );
    },
    [keyword, selectedId, setSearchParams, status, type],
  );

  const selectRecord = useCallback(
    (record: SalaryTemplateDto) => {
      if (!record.id) return;
      setSelectedId(record.id);
      setEditorMode('view');
      syncFilters({ templateId: String(record.id) });
    },
    [syncFilters],
  );

  const loadEditor = useCallback(
    (record: SalaryTemplateDto, mode: 'edit' | 'create') => {
      const metrics = getRuleMetrics(record);
      setSalaryItems(metrics.items);
      setTaxRules(metrics.rules);
      form.setFieldsValue({
        name: mode === 'create' ? `${record.name}（副本）` : record.name,
        type: record.type,
        status: mode === 'create' ? 'disabled' : record.status,
      });
      setEditorMode(mode);
    },
    [form],
  );

  const handleCreate = useCallback(() => {
    form.resetFields();
    form.setFieldsValue({ name: '', type: type || 'full_time', status: 'disabled' });
    setSalaryItems([...DEFAULT_ITEMS]);
    setTaxRules([...DEFAULT_TAX_RULES]);
    setEditorMode('create');
  }, [form, type]);

  const serializeTaxRules = useCallback((rules: RuleTaxItem[]) => {
    const result: Record<string, Record<string, unknown>> = {};
    rules.forEach((rule) => {
      const code = rule.ruleCode || '';
      if (code === 'income_tax' || code === 'tax' || rule.ruleName?.includes('税')) {
        result.tax = {
          mode: 'cumulative_withholding',
          applyOn: rule.applyOn || 'TAXABLE_EARNINGS',
        };
      } else if (code === 'social_security' || code === 'social' || rule.ruleName?.includes('社保')) {
        result.social = { rate: rule.rate ?? 0.1, applyOn: rule.applyOn || 'TAXABLE_EARNINGS' };
      }
    });
    const rounding = rules.find((rule) => rule.mode || rule.scale !== undefined);
    result.rounding = { scale: rounding?.scale ?? 2, mode: rounding?.mode || 'HALF_UP' };
    return JSON.stringify(result, null, 2);
  }, []);

  const handleSave = useCallback(async () => {
    try {
      const values = await form.validateFields();
      const itemsJson = JSON.stringify(
        salaryItems.map((item) => ({
          ...item,
          min: Number(item.min) || 0,
          max: item.max === undefined ? undefined : Number(item.max),
        })),
        null,
        2,
      );
      const taxRuleJson = serializeTaxRules(taxRules);
      const payload: SalaryTemplateCreateParams = {
        name: values.name.trim(),
        type: values.type,
        status: values.status,
        itemsJson,
        taxRuleJson,
      };
      let saved: SalaryTemplateDto;
      if (editorMode === 'create') {
        saved = await createMutation.mutateAsync(payload);
        message.success('规则包已创建，当前为停用状态');
      } else if (selected?.id) {
        saved = await updateMutation.mutateAsync({
          id: selected.id,
          params: { ...payload, id: selected.id },
        });
        message.success('规则包当前版本已更新');
      } else {
        return;
      }
      await templatesQuery.refetch();
      setEditorMode('view');
      if (saved.id) {
        setSelectedId(saved.id);
        syncFilters({ templateId: String(saved.id), type: type || undefined });
      }
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error?.response?.data?.message || error?.message || '规则包保存失败');
    }
  }, [
    createMutation,
    editorMode,
    form,
    message,
    salaryItems,
    selected?.id,
    serializeTaxRules,
    syncFilters,
    taxRules,
    templatesQuery,
    type,
    updateMutation,
  ]);

  const handleStatusAction = useCallback(async () => {
    if (!selected?.id) return;
    try {
      const nextStatus = selected.status === 'enabled' ? 'disabled' : 'enabled';
      await updateMutation.mutateAsync({
        id: selected.id,
        params: {
          id: selected.id,
          name: selected.name,
          type: selected.type,
          status: nextStatus,
          itemsJson: selected.itemsJson || JSON.stringify(selectedMetrics.items),
          taxRuleJson: selected.taxRuleJson || serializeTaxRules(selectedMetrics.rules),
        },
      });
      message.success(nextStatus === 'enabled' ? '规则包已发布并启用' : '规则包已停用');
      await templatesQuery.refetch();
      await detailQuery.refetch();
    } catch (error: any) {
      message.error(error?.response?.data?.message || error?.message || '规则包状态更新失败');
    }
  }, [detailQuery, message, selected, selectedMetrics.items, selectedMetrics.rules, serializeTaxRules, templatesQuery, updateMutation]);

  const isSaving = createMutation.isPending || updateMutation.isPending;

  return (
    <PageContainer
      className="payroll-page-shell rules-page"
      header={{
        title: '规则中心',
        subTitle: '管理可被发薪日历引用的薪酬规则包与当前版本',
        extra: [
          <Button key="compliance" icon={<AuditOutlined />} onClick={() => navigate('/payroll/compliance')}>
            合规核算
          </Button>,
          <Button key="calendar" icon={<CalendarOutlined />} onClick={() => navigate('/payroll/calendar')}>
            查看发薪日历
          </Button>,
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            新建规则包
          </Button>,
        ],
      }}
    >
      <Alert
        className="rules-page-alert"
        type="info"
        showIcon
        icon={<FileProtectOutlined />}
        title="这里管理的是规则，不是发薪期间"
        description="规则包描述薪资项目、扣款与税务参数；发薪期间、截数日和发薪日请在发薪日历配置。新规则包默认停用，启用前请完成校验。"
      />

      <div className="rules-toolbar">
        <Input.Search
          allowClear
          value={keyword}
          placeholder="搜索规则包名称或类型"
          onChange={(event) => {
            const value = event.target.value;
            setKeyword(value);
            syncFilters({ q: value, templateId: '' });
          }}
          className="rules-search"
        />
        <Segmented
          value={type || 'all'}
          options={[{ label: '全部用工类型', value: 'all' }, ...payrollTypeOptions]}
          onChange={(value) => {
            const nextType = value === 'all' ? '' : String(value);
            setType(nextType);
            setSelectedId(undefined);
            syncFilters({ type: nextType, templateId: '' });
          }}
        />
        <Select
          allowClear
          value={status || undefined}
          placeholder="全部状态"
          options={statusOptions.map(({ label, value }) => ({ label, value }))}
          onChange={(value) => {
            const nextStatus = value || '';
            setStatus(nextStatus);
            setSelectedId(undefined);
            syncFilters({ status: nextStatus, templateId: '' });
          }}
          className="rules-status-filter"
        />
        <Button
          icon={<ReloadOutlined />}
          loading={templatesQuery.isFetching}
          onClick={() => void templatesQuery.refetch()}
        >
          刷新
        </Button>
      </div>

      <div className="rules-workbench">
        <aside className="rules-index-panel" aria-label="规则包列表">
          <div className="rules-panel-heading">
            <div>
              <Text strong>规则包</Text>
              <Text type="secondary">{records.length} 个当前记录</Text>
            </div>
            <Badge count={records.filter((item) => item.status === 'enabled').length} showZero color="#1677ff" />
          </div>
          <div className="rules-index-list">
            {templatesQuery.isLoading ? (
              <div className="rules-loading"><Spin /></div>
            ) : records.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无匹配规则包" />
            ) : (
              records.map((record) => {
                const metrics = getRuleMetrics(record);
                const typeMeta = getTypeMeta(record.type);
                const statusMeta = getStatusMeta(record.status);
                return (
                  <button
                    type="button"
                    key={record.id ?? record.name}
                    className={`rules-index-row${record.id === selected?.id ? ' is-selected' : ''}`}
                    onClick={() => selectRecord(record)}
                  >
                    <span className="rules-index-row-main">
                      <span className="rules-index-row-title">
                        <SettingOutlined />
                        <strong>{record.name}</strong>
                      </span>
                      <span className="rules-index-row-meta">
                        {typeMeta.label} · {metrics.itemCount} 项 · {metrics.ruleCount} 条规则
                      </span>
                    </span>
                    <Tag color={statusMeta.color}>{statusMeta.label}</Tag>
                  </button>
                );
              })
            )}
          </div>
        </aside>

        <main className="rules-detail-panel">
          {!selected ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择一个规则包" />
          ) : (
            <>
              <div className="rules-detail-header">
                <div className="rules-detail-identity">
                  <div className="rules-detail-icon"><AuditOutlined /></div>
                  <div>
                    <Space wrap size={[8, 4]}>
                      <Title level={3}>{selected.name}</Title>
                      <Tag color={getStatusMeta(selected.status).color}>{getStatusMeta(selected.status).label}</Tag>
                      <Tag color={getTypeMeta(selected.type).color}>{getTypeMeta(selected.type).label}</Tag>
                    </Space>
                    <Text type="secondary">
                      当前版本 v{selected.dataVersion ?? 1} · 最近更新 {formatDateTime(selected.updateTime)}
                    </Text>
                  </div>
                </div>
                <Space wrap>
                  <Button
                    icon={selected.status === 'enabled' ? <SettingOutlined /> : <CheckCircleOutlined />}
                    loading={updateMutation.isPending}
                    onClick={() => void handleStatusAction()}
                  >
                    {selected.status === 'enabled' ? '停用当前规则' : '发布并启用'}
                  </Button>
                  <Button icon={<CalendarOutlined />} onClick={() => navigate(`/payroll/calendar?type=${selected.type || ''}`)}>
                    查看适用日历
                  </Button>
                  <Button icon={<CopyOutlined />} onClick={() => loadEditor(selected, 'create')}>
                    复制为新规则
                  </Button>
                  <Button type="primary" icon={<EditOutlined />} onClick={() => loadEditor(selected, 'edit')}>
                    编辑当前版本
                  </Button>
                </Space>
              </div>

              <div className="rules-metric-grid">
                <div className="rules-metric"><Text type="secondary">薪资项目</Text><strong>{selectedMetrics.itemCount}</strong><span>{selectedMetrics.earningCount} 收入 · {selectedMetrics.deductionCount} 扣款</span></div>
                <div className="rules-metric"><Text type="secondary">必填项目</Text><strong>{selectedMetrics.requiredCount}</strong><span>录入时必须提供</span></div>
                <div className="rules-metric"><Text type="secondary">税务与扣款规则</Text><strong>{selectedMetrics.ruleCount}</strong><span>由规则引擎读取</span></div>
                <div className="rules-metric"><Text type="secondary">数据版本</Text><strong>v{selected.dataVersion ?? 1}</strong><span>用于计算追溯</span></div>
              </div>

              {editorMode === 'view' ? (
                <>
                  <Descriptions className="rules-descriptions" column={{ xs: 1, sm: 2 }} size="small">
                    <Descriptions.Item label="规则包名称">{selected.name}</Descriptions.Item>
                    <Descriptions.Item label="适用用工类型">{getTypeMeta(selected.type).label}</Descriptions.Item>
                    <Descriptions.Item label="创建时间">{formatDateTime(selected.createTime)}</Descriptions.Item>
                    <Descriptions.Item label="更新时间">{formatDateTime(selected.updateTime)}</Descriptions.Item>
                  </Descriptions>
                  <Divider />
                  <Tabs
                    items={[
                      {
                        key: 'items',
                        label: <span><TeamOutlined /> 薪资项目</span>,
                        children: (
                          <div className="rules-readonly-list">
                            {selectedMetrics.items.map((item) => (
                              <div className="rules-readonly-row" key={item.code}>
                                <div><strong>{item.name}</strong><Text code>{item.code}</Text></div>
                                <Space wrap>
                                  <Tag color={item.type === 'earning' ? 'green' : 'red'}>{item.type === 'earning' ? '收入' : '扣款'}</Tag>
                                  {item.required && <Tag color="orange">必填</Tag>}
                                  <Text type="secondary">{item.description || '未填写说明'}</Text>
                                </Space>
                              </div>
                            ))}
                          </div>
                        ),
                      },
                      {
                        key: 'rules',
                        label: <span><CheckCircleOutlined /> 税务规则</span>,
                        children: (
                          <div className="rules-readonly-list">
                            {selectedMetrics.rules.map((rule) => (
                              <div className="rules-readonly-row" key={`${rule.ruleCode}-${rule.ruleName}`}>
                                <div><strong>{rule.ruleName || rule.ruleCode}</strong><Text code>{rule.ruleCode}</Text></div>
                                <Space wrap>
                                  {rule.rate !== undefined && <Tag color="blue">税率 {(rule.rate * 100).toFixed(2)}%</Tag>}
                                  {rule.applyOn && <Text type="secondary">基数 {rule.applyOn}</Text>}
                                  {rule.mode && <Text type="secondary">舍入 {rule.mode}</Text>}
                                </Space>
                              </div>
                            ))}
                          </div>
                        ),
                      },
                    ]}
                  />
                  <Alert
                    type="warning"
                    showIcon
                    title="当前版本说明"
                    description="规则版本、政策包发布、批次输入快照和计算证据链均已纳入受控流程；已锁定或已支付批次不会被覆盖，差异通过调整批次处理。"
                  />
                  {requiresTaxMigration && (
                    <Alert
                      type="error"
                      showIcon
                      title="该规则包使用已下线的固定税率"
                      description="请进入编辑并保存为累计预扣模式；迁移完成前不能发布，也不能用于薪酬核算。"
                      style={{ marginTop: 12 }}
                    />
                  )}
                </>
              ) : (
                <div className="rules-editor">
                  <div className="rules-editor-heading">
                    <div>
                      <Title level={4}>{editorMode === 'create' ? '创建规则包' : '编辑当前版本'}</Title>
                      <Text type="secondary">保存后才会成为可被批次计算读取的规则内容。</Text>
                    </div>
                    <Space>
                      <Button onClick={() => setEditorMode('view')}>取消</Button>
                      <Button type="primary" icon={<SaveOutlined />} loading={isSaving} onClick={() => void handleSave()}>
                        保存规则包
                      </Button>
                    </Space>
                  </div>
                  <Form form={form} layout="vertical" className="rules-editor-form">
                    <Form.Item name="name" label="规则包名称" rules={[{ required: true, message: '请输入规则包名称' }]}>
                      <Input placeholder="如：大陆全职月薪规则 2026" />
                    </Form.Item>
                    <Form.Item name="type" label="适用用工类型" rules={[{ required: true, message: '请选择用工类型' }]}>
                      <Select options={payrollTypeOptions.map(({ label, value }) => ({ label, value }))} />
                    </Form.Item>
                    <Form.Item name="status" label="当前状态" rules={[{ required: true, message: '请选择状态' }]}>
                      <Select options={statusOptions.map(({ label, value }) => ({ label, value }))} />
                    </Form.Item>
                  </Form>
                  <Tabs
                    defaultActiveKey="items"
                    items={[
                      { key: 'items', label: '薪资项目', children: <SalaryItemsConfig value={salaryItems} onChange={setSalaryItems} /> },
                      { key: 'rules', label: '税务与扣款规则', children: <TaxRulesConfig value={taxRules} onChange={setTaxRules} /> },
                    ]}
                  />
                </div>
              )}
            </>
          )}
        </main>
      </div>
    </PageContainer>
  );
};

export default RulesPage;
