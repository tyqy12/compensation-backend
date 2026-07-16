import React, { useMemo, useRef, useState, useCallback } from 'react';
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
  Collapse,
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
  message,
  Spin,
  Tabs,
} from 'antd';
import {
  CopyOutlined,
  DiffOutlined,
  EditOutlined,
  FileTextOutlined,
  FormatPainterOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
  DollarOutlined,
  CalculatorOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  useSalaryTemplatesQuery,
  useSalaryTemplateQuery,
  useCreateSalaryTemplateMutation,
  useUpdateSalaryTemplateMutation,
  type SalaryTemplateListParams,
  type SalaryTemplateDto,
  type SalaryTemplateCreateParams,
} from '@services/queries/payroll';
import { getPagedRecords } from '@services/api';
import SalaryItemsConfig from '@components/SalaryConfig/SalaryItemsConfig';
import TaxRulesConfig from '@components/SalaryConfig/TaxRulesConfig';

// ==================== 枚举定义 ====================
const payrollTypeEnum: Record<string, { text: string; color: string }> = {
  full_time: { text: '全职', color: 'blue' },
  part_time: { text: '兼职', color: 'gold' },
  contractor: { text: '外包', color: 'purple' },
};

const templateStatusEnum: Record<string, { text: string; color: string }> = {
  enabled: { text: '启用', color: 'success' },
  disabled: { text: '停用', color: 'default' },
  draft: { text: '草稿', color: 'warning' },
  archived: { text: '归档', color: 'default' },
};

// ==================== 类型定义 ====================
interface SalaryItem {
  code: string;
  name: string;
  type: 'earning' | 'deduction';
  required: boolean;
  min: number;
  max?: number;
  description?: string;
}

interface TaxRuleItem {
  ruleCode: string;
  ruleName?: string;
  rate?: number;
  threshold?: number;
  applyOn?: string;
  mode?: string;
  scale?: number;
}

// ==================== 工具函数 ====================
const formatDateTime = (value?: string): string =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—';

const formatJson = (jsonStr: string): string => {
  try {
    return JSON.stringify(JSON.parse(jsonStr), null, 2);
  } catch {
    return jsonStr;
  }
};

const parseJsonSafe = <T,>(jsonStr: string, defaultValue: T): T => {
  try {
    return jsonStr ? JSON.parse(jsonStr) : defaultValue;
  } catch {
    return defaultValue;
  }
};

// 默认薪资项目配置
const DEFAULT_ITEMS: SalaryItem[] = [
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

// 默认税务规则配置
const DEFAULT_TAX_RULES: TaxRuleItem[] = [
  {
    ruleCode: 'income_tax',
    ruleName: '个人所得税',
    rate: 0.03,
    applyOn: 'TAXABLE_EARNINGS',
    mode: 'HALF_UP',
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

// ==================== 主组件 ====================
const TemplatesPage: React.FC = () => {
  const { message } = AntdApp.useApp();
  const [searchParams, setSearchParams] = useSearchParams();

  // URL 参数状态
  const [queryParams, setQueryParams] = useState<SalaryTemplateListParams>(() => ({
    current: Number(searchParams.get('page') || '1'),
    pageSize: Number(searchParams.get('size') || '10'),
    type: searchParams.get('type') || undefined,
    status: searchParams.get('status') || undefined,
  }));

  // 核心引用
  const actionRef = useRef<ActionType>();
  const formRef = useRef<ProFormInstance>();

  // 数据状态
  const [latestData, setLatestData] = useState<any>();
  const [detailRecord, setDetailRecord] = useState<SalaryTemplateDto | null>(null);

  // Modal 状态
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<SalaryTemplateDto | null>(null);
  const [modalType, setModalType] = useState<'create' | 'edit'>('create');
  const [form] = Form.useForm();

  // 可视化配置状态
  const [salaryItems, setSalaryItems] = useState<SalaryItem[]>(DEFAULT_ITEMS);
  const [taxRules, setTaxRules] = useState<TaxRuleItem[]>(DEFAULT_TAX_RULES);
  const [activeTab, setActiveTab] = useState('items');

  // 查询 Mutations
  const templatesQuery = useSalaryTemplatesQuery(queryParams);
  const detailQuery = useSalaryTemplateQuery(detailRecord?.id ?? 0, {
    enabled: !!detailRecord?.id,
  });

  // 操作 Mutations
  const createMutation = useCreateSalaryTemplateMutation();
  const updateMutation = useUpdateSalaryTemplateMutation();

  // ==================== URL 同步 ====================
  const updateUrlParams = useCallback(
    (params: SalaryTemplateListParams) => {
      const next = new URLSearchParams();
      if (params.current) next.set('page', String(params.current));
      if (params.pageSize) next.set('size', String(params.pageSize));
      if (params.type) next.set('type', params.type);
      if (params.status) next.set('status', params.status);
      setSearchParams(next);
    },
    [setSearchParams],
  );

  // ==================== 统计数据 ====================
  const summary = useMemo(() => {
    const records = getPagedRecords(latestData) as SalaryTemplateDto[];
    const total = latestData?.total ?? 0;
    const enabled = records.filter((item) => item.status === 'enabled').length;
    const disabled = records.filter((item) => item.status === 'disabled').length;
    return { total, enabled, disabled };
  }, [latestData]);

  // ==================== 可视化配置处理 ====================

  // ==================== 操作处理 ====================
  const handleCreate = useCallback(() => {
    setModalType('create');
    setEditingRecord(null);
    form.resetFields();
    setSalaryItems(DEFAULT_ITEMS);
    setTaxRules(DEFAULT_TAX_RULES);
    setActiveTab('items');
    form.setFieldsValue({
      type: 'full_time',
      status: 'enabled',
    });
    setIsModalOpen(true);
  }, [form]);

  const handleEdit = useCallback(
    (record: SalaryTemplateDto) => {
      setModalType('edit');
      setEditingRecord(record);

      // 解析现有的 JSON 配置
      const parsedItems = parseJsonSafe<SalaryItem[]>(record.itemsJson || '[]', DEFAULT_ITEMS);
      const parsedTaxRules = parseJsonSafe<TaxRuleItem[]>(
        record.taxRuleJson || '[]',
        DEFAULT_TAX_RULES,
      );

      setSalaryItems(parsedItems);
      setTaxRules(parsedTaxRules);
      setActiveTab('items');

      form.setFieldsValue({
        name: record.name,
        type: record.type,
        status: record.status,
      });
      setIsModalOpen(true);
    },
    [form],
  );

  const handleCopy = useCallback(
    (record: SalaryTemplateDto) => {
      setModalType('create');
      setEditingRecord(null);
      form.resetFields();

      // 解析现有的 JSON 配置
      const parsedItems = parseJsonSafe<SalaryItem[]>(record.itemsJson || '[]', DEFAULT_ITEMS);
      const parsedTaxRules = parseJsonSafe<TaxRuleItem[]>(
        record.taxRuleJson || '[]',
        DEFAULT_TAX_RULES,
      );

      setSalaryItems(parsedItems);
      setTaxRules(parsedTaxRules);
      setActiveTab('items');

      form.setFieldsValue({
        name: `${record.name} (副本)`,
        type: record.type,
        status: 'disabled',
      });
      setIsModalOpen(true);
      message.info('已复制模板内容，请修改名称后保存');
    },
    [form],
  );

  // 序列化税务规则为后端期望的对象格式
  const serializeTaxRules = useCallback((rules: TaxRuleItem[]): string => {
    const taxRuleObj: Record<string, any> = {};

    for (const rule of rules) {
      if (rule.ruleCode === 'income_tax' || rule.ruleName?.includes('税')) {
        taxRuleObj.tax = {
          rate: rule.rate ?? 0.03,
          applyOn: rule.applyOn || 'TAXABLE_EARNINGS',
        };
      } else if (rule.ruleCode === 'social_security' || rule.ruleName?.includes('社保')) {
        taxRuleObj.social = {
          rate: rule.rate ?? 0.1,
          applyOn: rule.applyOn || 'TAXABLE_EARNINGS',
        };
      }
    }

    // 添加舍入配置
    const firstRuleWithRounding = rules.find((r) => r.mode || r.scale !== undefined);
    taxRuleObj.rounding = {
      scale: firstRuleWithRounding?.scale ?? 2,
      mode: firstRuleWithRounding?.mode || 'HALF_UP',
    };

    return JSON.stringify(taxRuleObj, null, 2);
  }, []);

  const handleSubmit = useCallback(async () => {
    try {
      const values = await form.validateFields();

      // 序列化配置为 JSON，确保所有必要字段都有值
      const sanitizedItems = salaryItems.map((item) => ({
        code: item.code || 'unknown',
        name: item.name || '未命名项目',
        type: item.type || 'earning',
        required: Boolean(item.required),
        min: Number(item.min) || 0,
        max: item.max,
        description: item.description,
      }));
      const itemsJson = JSON.stringify(sanitizedItems, null, 2);
      const taxRuleJson = serializeTaxRules(taxRules);

      if (modalType === 'create') {
        const params: SalaryTemplateCreateParams = {
          name: values.name,
          type: values.type,
          status: values.status || 'enabled',
          itemsJson,
          taxRuleJson,
        };
        await createMutation.mutateAsync(params);
        message.success('创建成功');
      } else if (editingRecord?.id) {
        await updateMutation.mutateAsync({
          id: editingRecord.id,
          params: {
            id: editingRecord.id,
            name: values.name,
            type: values.type,
            status: values.status,
            itemsJson,
            taxRuleJson,
          },
        });
        message.success('更新成功');
      }

      setIsModalOpen(false);
      actionRef.current?.reload();
    } catch (error: any) {
      if (error?.errorFields) return;
      const msg = error?.response?.data?.message || error?.message || '操作失败';
      message.error(msg);
    }
  }, [
    form,
    modalType,
    editingRecord,
    createMutation,
    updateMutation,
    salaryItems,
    taxRules,
    serializeTaxRules,
  ]);

  // ==================== 表格列定义 ====================
  const columns: ProColumns<SalaryTemplateDto>[] = [
    {
      title: '模板名称',
      dataIndex: 'name',
      width: 220,
      render: (_, record) => (
        <Space>
          <FileTextOutlined style={{ color: '#1890ff' }} />
          <span style={{ fontWeight: 600 }}>{record.name}</span>
        </Space>
      ),
    },
    {
      title: '用工类型',
      dataIndex: 'type',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(payrollTypeEnum).map(([k, v]) => [k, { text: v.text }]),
      ),
      render: (_, record) => {
        const meta = payrollTypeEnum[record.type ?? ''];
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : record.type || '—';
      },
    },
    {
      title: '版本',
      dataIndex: 'dataVersion',
      width: 70,
      render: (value) => <Tag>v{value ?? 1}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(templateStatusEnum).map(([k, v]) => [k, { text: v.text }]),
      ),
      render: (_, record) => {
        const meta = templateStatusEnum[record.status ?? ''];
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : record.status || '—';
      },
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 170,
      render: (_, record) => formatDateTime(record.updateTime),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      render: (_, record) => [
        <Button key="detail" type="link" size="small" onClick={() => setDetailRecord(record)}>
          详情
        </Button>,
        <Button
          key="edit"
          type="link"
          size="small"
          icon={<EditOutlined />}
          onClick={() => handleEdit(record)}
        >
          编辑
        </Button>,
        <Button
          key="copy"
          type="link"
          size="small"
          icon={<CopyOutlined />}
          onClick={() => handleCopy(record)}
        >
          复制
        </Button>,
      ],
    },
  ];

  const isLoading = createMutation.isPending || updateMutation.isPending;

  // ==================== 详情展示 ====================
  const renderDetailItems = () => {
    if (!detailRecord?.itemsJson) return null;
    const items = parseJsonSafe(detailRecord.itemsJson, []);
    if (!Array.isArray(items) || items.length === 0) return null;

    return (
      <Collapse
        size="small"
        items={[
          {
            key: 'items',
            label: (
              <Space>
                <FormatPainterOutlined />
                薪资项目配置 ({items.length} 项)
              </Space>
            ),
            children: (
              <div style={{ maxHeight: 300, overflow: 'auto' }}>
                <pre style={{ fontSize: 12, margin: 0 }}>{formatJson(detailRecord.itemsJson)}</pre>
              </div>
            ),
          },
        ]}
      />
    );
  };

  const renderDetailTaxRules = () => {
    if (!detailRecord?.taxRuleJson) return null;
    return (
      <Collapse
        size="small"
        items={[
          {
            key: 'tax',
            label: (
              <Space>
                <DiffOutlined />
                税务规则配置
              </Space>
            ),
            children: (
              <div style={{ maxHeight: 200, overflow: 'auto' }}>
                <pre style={{ fontSize: 12, margin: 0 }}>
                  {formatJson(detailRecord.taxRuleJson)}
                </pre>
              </div>
            ),
          },
        ]}
      />
    );
  };

  // ==================== 渲染 ====================
  return (
    <PageContainer
      header={{
        title: '薪资模板管理',
        breadcrumb: {},
        extra: [
          <Tooltip key="refresh" title="刷新列表">
            <Button icon={<ReloadOutlined />} onClick={() => actionRef.current?.reload()} />
          </Tooltip>,
        ],
      }}
    >
      <Space orientation="vertical" size={16} style={{ width: '100%', padding: 24 }}>
        {/* 统计卡片 */}
        <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 4 }}>
          <Card size="small" style={{ flex: '0 0 auto', width: 140 }}>
            <Statistic
              title="模板数量"
              value={summary.total}
              prefix={<FileTextOutlined />}
              styles={{ content: { fontSize: 20 } }}
            />
          </Card>
          <Card size="small" style={{ flex: '0 0 auto', width: 140 }}>
            <Statistic
              title="启用模板"
              value={summary.enabled}
              prefix={<ThunderboltOutlined />}
              styles={{ content: { fontSize: 20, color: '#52c41a' } }}
            />
          </Card>
          <Card size="small" style={{ flex: '0 0 auto', width: 140 }}>
            <Statistic
              title="停用模板"
              value={summary.disabled}
              prefix={<SafetyCertificateOutlined />}
              styles={{ content: { fontSize: 20 } }}
            />
          </Card>
        </div>

        <ProTable<SalaryTemplateDto>
          columns={columns}
          actionRef={actionRef}
          formRef={formRef}
          scroll={{ x: 1000 }}
          rowKey={(record) => String(record.id ?? Math.random())}
          request={async (params) => {
            const nextParams: SalaryTemplateListParams = {
              current: params.current || 1,
              pageSize: params.pageSize || 10,
              type: params.type,
              status: params.status,
            };
            setQueryParams(nextParams);
            updateUrlParams(nextParams);

            try {
              const data = await templatesQuery.refetch();
              setLatestData(data.data);
              return {
                data: getPagedRecords(data.data),
                success: true,
                total: data.data?.total ?? 0,
              };
            } catch (error: any) {
              message.error(error?.message || '加载失败');
              return { data: [], success: false, total: 0 };
            }
          }}
          search={{
            labelWidth: 'auto',
            collapseRender: false,
            optionRender: (_, __, dom) => dom,
          }}
          pagination={{
            current: queryParams.current,
            pageSize: queryParams.pageSize,
            showSizeChanger: true,
            showQuickJumper: true,
          }}
          toolBarRender={() => [
            <Button key="create" type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
              新增模板
            </Button>,
          ]}
          locale={{ emptyText: '暂无薪资模板' }}
        />
      </Space>

      {/* 新增/编辑 Modal */}
      <Modal
        title={modalType === 'create' ? '新增薪资模板' : '编辑薪资模板'}
        open={isModalOpen}
        onOk={handleSubmit}
        onCancel={() => setIsModalOpen(false)}
        confirmLoading={isLoading}
        width={900}
        forceRender
        okText={modalType === 'create' ? '创建' : '保存'}
        styles={{ body: { maxHeight: '70vh', overflowY: 'auto' } }}
      >
        <Form form={form} layout="vertical">
          <Space style={{ width: '100%' }} size={16}>
            <Form.Item
              name="name"
              label="模板名称"
              rules={[
                { required: true, message: '请输入模板名称' },
                { min: 2, max: 50, message: '名称长度应为 2-50 个字符' },
              ]}
              style={{ flex: 1 }}
            >
              <Input placeholder="如：全职员工标准薪资模板" />
            </Form.Item>

            <Form.Item
              name="type"
              label="用工类型"
              rules={[{ required: true, message: '请选择用工类型' }]}
              style={{ width: 160 }}
            >
              <Select
                placeholder="请选择"
                options={Object.entries(payrollTypeEnum).map(([key, value]) => ({
                  label: value.text,
                  value: key,
                }))}
              />
            </Form.Item>

            <Form.Item
              name="status"
              label="状态"
              rules={[{ required: true, message: '请选择状态' }]}
              style={{ width: 120 }}
            >
              <Select
                placeholder="请选择"
                options={Object.entries(templateStatusEnum).map(([key, value]) => ({
                  label: value.text,
                  value: key,
                }))}
              />
            </Form.Item>
          </Space>

          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={[
              {
                key: 'items',
                label: (
                  <span>
                    <DollarOutlined />
                    薪资项目配置
                  </span>
                ),
                children: <SalaryItemsConfig value={salaryItems} onChange={setSalaryItems} />,
              },
              {
                key: 'tax',
                label: (
                  <span>
                    <CalculatorOutlined />
                    税务/扣款规则
                  </span>
                ),
                children: <TaxRulesConfig value={taxRules} onChange={setTaxRules} />,
              },
            ]}
          />
        </Form>
      </Modal>

      {/* 详情 Drawer */}
      <Drawer
        width={600}
        title="模板详情"
        open={Boolean(detailRecord)}
        onClose={() => setDetailRecord(null)}
        destroyOnHidden
        extra={
          detailRecord && (
            <Space>
              <Button icon={<EditOutlined />} onClick={() => handleEdit(detailRecord)}>
                编辑
              </Button>
              <Button icon={<CopyOutlined />} onClick={() => handleCopy(detailRecord)}>
                复制
              </Button>
            </Space>
          )
        }
      >
        <Spin spinning={detailQuery.isLoading}>
          {detailRecord && (
            <Space orientation="vertical" size={16} style={{ width: '100%' }}>
              <Descriptions column={1} bordered size="small">
                <Descriptions.Item label="模板名称">{detailRecord.name}</Descriptions.Item>
                <Descriptions.Item label="用工类型">
                  {payrollTypeEnum[detailRecord.type ?? '']?.text ?? detailRecord.type ?? '—'}
                </Descriptions.Item>
                <Descriptions.Item label="版本">
                  <Tag>v{detailRecord.dataVersion ?? 1}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="状态">
                  {(() => {
                    const meta = templateStatusEnum[detailRecord.status ?? ''];
                    return meta ? <Tag color={meta.color}>{meta.text}</Tag> : detailRecord.status;
                  })()}
                </Descriptions.Item>
                <Descriptions.Item label="创建时间">
                  {formatDateTime(detailRecord.createTime)}
                </Descriptions.Item>
                <Descriptions.Item label="更新时间">
                  {formatDateTime(detailRecord.updateTime)}
                </Descriptions.Item>
              </Descriptions>

              {renderDetailItems()}
              {renderDetailTaxRules()}

              <Alert
                type="info"
                showIcon
                title="版本说明"
                description="每次修改模板内容（薪资项目或税务规则），版本号会自动递增。历史版本可用于追溯和回滚。"
              />
            </Space>
          )}
        </Spin>
      </Drawer>
    </PageContainer>
  );
};

export default TemplatesPage;
