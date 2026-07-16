import React, { useState, useCallback } from 'react';
import {
  Card,
  Table,
  Button,
  Input,
  InputNumber,
  Select,
  Switch,
  Space,
  Popconfirm,
  Tag,
  Tooltip,
  message,
  Divider,
} from 'antd';
import { PlusOutlined, DeleteOutlined, InfoCircleOutlined } from '@ant-design/icons';

interface TaxRuleItem {
  ruleCode: string;
  ruleName?: string;
  rate?: number;
  threshold?: number;
  applyOn?: string;
  mode?: string;
  scale?: number;
}

interface TaxRulesConfigProps {
  value?: TaxRuleItem[];
  onChange?: (value: TaxRuleItem[]) => void;
}

// 适用对象枚举
const applyOnOptions = [
  { label: '应税收入', value: 'TAXABLE_EARNINGS' },
  { label: '税前收入', value: 'GROSS_EARNINGS' },
  { label: '所有收入', value: 'TOTAL_EARNINGS' },
];

// 舍入方式枚举
const roundingModeOptions = [
  { label: '四舍五入', value: 'HALF_UP' },
  { label: '向上取整', value: 'CEILING' },
  { label: '向下取整', value: 'FLOOR' },
  { label: '截断', value: 'TRUNCATE' },
];

// 预设税务规则
const PRESET_TAX_RULES: Partial<TaxRuleItem>[] = [
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

const TaxRulesConfig: React.FC<TaxRulesConfigProps> = ({ value, onChange }) => {
  // 确保 value 始终是数组
  const rules = Array.isArray(value) ? value : [];

  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [editForm, setEditForm] = useState<TaxRuleItem | null>(null);
  const [showCustomForm, setShowCustomForm] = useState(false);
  const [customForm, setCustomForm] = useState<TaxRuleItem>({
    ruleCode: '',
    ruleName: '',
    rate: 0,
  });

  // 生成唯一编码
  const generateCode = useCallback((name: string) => {
    return (
      name
        .replace(/[^a-zA-Z0-9\u4e00-\u9fa5]/g, '')
        .toLowerCase()
        .slice(0, 30) +
      '_' +
      Date.now().toString(36)
    );
  }, []);

  // 添加预设规则
  const handleAddPreset = useCallback(
    (preset: Partial<TaxRuleItem>) => {
      const newItem: TaxRuleItem = {
        ruleCode: preset.ruleCode || generateCode(preset.ruleName || 'rule'),
        ruleName: preset.ruleName || '新规则',
        rate: preset.rate ?? 0,
        threshold: preset.threshold,
        applyOn: preset.applyOn || 'TAXABLE_EARNINGS',
        mode: preset.mode || 'HALF_UP',
        scale: preset.scale ?? 2,
      };
      const newValue = [...rules, newItem];
      onChange?.(newValue);
      message.success(`已添加「${newItem.ruleName}」`);
    },
    [rules, onChange, generateCode],
  );

  // 添加自定义规则
  const handleAddCustom = useCallback(() => {
    if (!customForm.ruleName || !customForm.ruleCode) {
      message.warning('请填写规则名称和编码');
      return;
    }
    const newItem: TaxRuleItem = {
      ruleCode: customForm.ruleCode,
      ruleName: customForm.ruleName,
      rate: customForm.rate ?? 0,
      threshold: customForm.threshold,
      applyOn: customForm.applyOn || 'TAXABLE_EARNINGS',
      mode: customForm.mode || 'HALF_UP',
      scale: customForm.scale ?? 2,
    };
    const newValue = [...rules, newItem];
    onChange?.(newValue);
    setCustomForm({ ruleCode: '', ruleName: '', rate: 0 });
    setShowCustomForm(false);
    message.success('已添加规则');
  }, [rules, onChange, customForm]);

  // 删除规则
  const handleDelete = useCallback(
    (index: number) => {
      const newValue = rules.filter((_, i) => i !== index);
      onChange?.(newValue);
      message.success('已删除');
    },
    [rules, onChange],
  );

  // 开始编辑
  const handleEditStart = useCallback(
    (index: number) => {
      setEditingIndex(index);
      setEditForm({ ...rules[index] });
    },
    [rules],
  );

  // 取消编辑
  const handleEditCancel = useCallback(() => {
    setEditingIndex(null);
    setEditForm(null);
  }, []);

  // 保存编辑
  const handleEditSave = useCallback(() => {
    if (editingIndex === null || !editForm) return;
    const newValue = [...rules];
    newValue[editingIndex] = editForm;
    onChange?.(newValue);
    setEditingIndex(null);
    setEditForm(null);
    message.success('已保存');
  }, [editingIndex, editForm, rules, onChange]);

  // 表格列定义
  const columns = [
    {
      title: '序号',
      key: 'order',
      width: 60,
      render: (_: any, __: any, index: number) => index + 1,
    },
    {
      title: '规则名称',
      dataIndex: 'ruleName',
      key: 'ruleName',
      width: 140,
    },
    {
      title: '规则编码',
      dataIndex: 'ruleCode',
      key: 'ruleCode',
      width: 140,
      render: (code: string) => <code style={{ fontSize: 11 }}>{code}</code>,
    },
    {
      title: '税率',
      dataIndex: 'rate',
      key: 'rate',
      width: 100,
      render: (rate: number) => (rate !== undefined ? `${(rate * 100).toFixed(2)}%` : '—'),
    },
    {
      title: '起征点',
      dataIndex: 'threshold',
      key: 'threshold',
      width: 100,
      render: (threshold: number) =>
        threshold !== undefined ? `¥${threshold.toLocaleString()}` : '—',
    },
    {
      title: '适用对象',
      dataIndex: 'applyOn',
      key: 'applyOn',
      width: 120,
      render: (applyOn: string) => {
        const config = applyOnOptions.find((o) => o.value === applyOn);
        return config?.label || applyOn || '—';
      },
    },
    {
      title: '舍入方式',
      dataIndex: 'mode',
      key: 'mode',
      width: 100,
      render: (mode: string) => {
        const config = roundingModeOptions.find((o) => o.value === mode);
        return config?.label || mode || '—';
      },
    },
    {
      title: '舍入精度',
      dataIndex: 'scale',
      key: 'scale',
      width: 90,
      render: (scale: number) => scale ?? 2,
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_: any, __: TaxRuleItem, index: number) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => handleEditStart(index)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除"
            description={`确定要删除「${__.ruleName}」吗？`}
            onConfirm={() => handleDelete(index)}
            okText="确认"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      {/* 工具栏 */}
      <Card
        size="small"
        style={{ marginBottom: 16 }}
        extra={
          <Space>
            <Button icon={<PlusOutlined />} onClick={() => setShowCustomForm(true)}>
              自定义规则
            </Button>
          </Space>
        }
      >
        <Space orientation="vertical" size={4}>
          <div>
            <InfoCircleOutlined style={{ marginRight: 8, color: '#1890ff' }} />
            <span style={{ color: '#666' }}>
              共配置 <strong>{rules.length}</strong> 条税务/扣款规则
            </span>
          </div>
          <div style={{ fontSize: 12, color: '#999' }}>
            提示：可快速添加预设规则，或自定义新的税务/社保/公积金计算规则
          </div>
        </Space>
      </Card>

      {/* 预设规则快速添加 */}
      <Card size="small" title="快速添加预设规则" style={{ marginBottom: 16 }}>
        <Space wrap>
          {PRESET_TAX_RULES.map((preset, index) => (
            <Button key={index} onClick={() => handleAddPreset(preset)}>
              + {preset.ruleName} ({(preset.rate! * 100).toFixed(0)}%)
            </Button>
          ))}
        </Space>
      </Card>

      {/* 自定义规则表单 */}
      {showCustomForm && (
        <Card size="small" title="添加自定义规则" style={{ marginBottom: 16 }}>
          <Space wrap size={16}>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>规则名称 *</div>
              <Input
                value={customForm.ruleName}
                onChange={(e) => setCustomForm((prev) => ({ ...prev, ruleName: e.target.value }))}
                placeholder="如：商业保险扣款"
                style={{ width: 160 }}
              />
            </div>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>规则编码 *</div>
              <Input
                value={customForm.ruleCode}
                onChange={(e) => setCustomForm((prev) => ({ ...prev, ruleCode: e.target.value }))}
                placeholder="如：insurance_deduction"
                style={{ width: 160 }}
              />
            </div>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>税率/比例</div>
              <InputNumber
                value={customForm.rate}
                onChange={(rate) => setCustomForm((prev) => ({ ...prev, rate: rate ?? 0 }))}
                min={0}
                max={1}
                step={0.001}
                style={{ width: 120 }}
                addonAfter="%"
                precision={3}
              />
            </div>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>起征点（可选）</div>
              <InputNumber
                value={customForm.threshold}
                onChange={(threshold) =>
                  setCustomForm((prev) => ({ ...prev, threshold: threshold ?? undefined }))
                }
                min={0}
                placeholder="无起征点"
                style={{ width: 120 }}
                addonAfter="元"
              />
            </div>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>舍入精度</div>
              <Select
                value={customForm.scale ?? 2}
                onChange={(scale) => setCustomForm((prev) => ({ ...prev, scale }))}
                options={[
                  { label: '0 位（整数）', value: 0 },
                  { label: '1 位', value: 1 },
                  { label: '2 位', value: 2 },
                  { label: '3 位', value: 3 },
                ]}
                style={{ width: 120 }}
              />
            </div>
          </Space>
          <Divider style={{ margin: '12px 0' }} />
          <Space>
            <Button type="primary" onClick={handleAddCustom}>
              添加
            </Button>
            <Button onClick={() => setShowCustomForm(false)}>取消</Button>
          </Space>
        </Card>
      )}

      {/* 规则列表 */}
      <Table
        dataSource={rules}
        columns={columns}
        rowKey="ruleCode"
        pagination={false}
        size="small"
        scroll={{ x: 1000 }}
        locale={{ emptyText: '暂无规则，点击上方按钮添加' }}
      />

      {/* 编辑弹窗 */}
      <Card size="small" title="编辑规则" style={{ marginTop: 16 }} hidden={editingIndex === null}>
        <Space orientation="vertical" style={{ width: '100%' }} size={16}>
          <Space wrap>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>规则名称 *</div>
              <Input
                value={editForm?.ruleName}
                onChange={(e) =>
                  setEditForm((prev) => (prev ? { ...prev, ruleName: e.target.value } : null))
                }
                style={{ width: 160 }}
              />
            </div>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>规则编码 *</div>
              <Input
                value={editForm?.ruleCode}
                onChange={(e) =>
                  setEditForm((prev) => (prev ? { ...prev, ruleCode: e.target.value } : null))
                }
                style={{ width: 160 }}
              />
            </div>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>税率/比例 *</div>
              <InputNumber
                value={editForm?.rate}
                onChange={(rate) =>
                  setEditForm((prev) => (prev ? { ...prev, rate: rate ?? 0 } : null))
                }
                min={0}
                max={1}
                step={0.001}
                style={{ width: 120 }}
                addonAfter="%"
                precision={3}
              />
            </div>
          </Space>

          <Space wrap>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>起征点（可选）</div>
              <InputNumber
                value={editForm?.threshold}
                onChange={(threshold) =>
                  setEditForm((prev) =>
                    prev ? { ...prev, threshold: threshold ?? undefined } : null,
                  )
                }
                min={0}
                style={{ width: 140 }}
                addonAfter="元"
              />
            </div>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>适用对象</div>
              <Select
                value={editForm?.applyOn || 'TAXABLE_EARNINGS'}
                onChange={(applyOn) => setEditForm((prev) => (prev ? { ...prev, applyOn } : null))}
                options={applyOnOptions}
                style={{ width: 140 }}
              />
            </div>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>舍入方式</div>
              <Select
                value={editForm?.mode || 'HALF_UP'}
                onChange={(mode) => setEditForm((prev) => (prev ? { ...prev, mode } : null))}
                options={roundingModeOptions}
                style={{ width: 120 }}
              />
            </div>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>舍入精度</div>
              <Select
                value={editForm?.scale ?? 2}
                onChange={(scale) => setEditForm((prev) => (prev ? { ...prev, scale } : null))}
                options={[
                  { label: '0 位（整数）', value: 0 },
                  { label: '1 位', value: 1 },
                  { label: '2 位', value: 2 },
                  { label: '3 位', value: 3 },
                ]}
                style={{ width: 120 }}
              />
            </div>
          </Space>

          <Space>
            <Button type="primary" onClick={handleEditSave}>
              保存
            </Button>
            <Button onClick={handleEditCancel}>取消</Button>
          </Space>
        </Space>
      </Card>
    </div>
  );
};

export default TaxRulesConfig;
