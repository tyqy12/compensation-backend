import React, { useState, useCallback } from 'react';
import {
  Card,
  Table,
  Button,
  Input,
  Select,
  InputNumber,
  Switch,
  Space,
  Popconfirm,
  Tag,
  Tooltip,
  message,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  UpOutlined,
  DownOutlined,
  CopyOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';

interface SalaryItem {
  code: string;
  name: string;
  type: 'earning' | 'deduction';
  required: boolean;
  min: number;
  max?: number;
  description?: string;
}

interface SalaryItemsConfigProps {
  value?: SalaryItem[];
  onChange?: (value: SalaryItem[]) => void;
}

// 薪资类型枚举
const itemTypeEnum = [
  { label: '收入', value: 'earning', color: 'green' },
  { label: '扣款', value: 'deduction', color: 'red' },
];

// 预设的常用薪资项目
const PRESET_ITEMS: Partial<SalaryItem>[] = [
  { code: 'base_salary', name: '基本工资', type: 'earning', required: true, min: 0, description: '员工的基本薪资' },
  { code: 'bonus', name: '奖金', type: 'earning', required: false, min: 0, description: '绩效奖金、年终奖等' },
  { code: 'allowance', name: '津贴补贴', type: 'earning', required: false, min: 0, description: '餐补、交通补、住房补等' },
  { code: 'overtime_pay', name: '加班费', type: 'earning', required: false, min: 0, description: '加班产生的额外费用' },
  { code: 'commission', name: '提成', type: 'earning', required: false, min: 0, description: '销售业绩提成' },
  { code: 'severance', name: '离职补偿金', type: 'earning', required: false, min: 0, description: '经济补偿金' },
  { code: 'tax', name: '个人所得税', type: 'deduction', required: true, min: 0, description: '工资薪金所得个人所得税' },
  { code: 'social_security', name: '社保扣款', type: 'deduction', required: true, min: 0, description: '个人缴纳社保部分' },
  { code: 'housing_fund', name: '公积金扣款', type: 'deduction', required: true, min: 0, description: '个人缴纳公积金部分' },
  { code: 'late_fee', name: '迟到扣款', type: 'deduction', required: false, min: 0, description: '迟到、早退等扣款' },
  { code: 'other_deduction', name: '其他扣款', type: 'deduction', required: false, min: 0, description: '其他原因扣款' },
  { code: 'advance', name: '预支工资', type: 'deduction', required: false, min: 0, description: '提前预支的工资' },
];

const SalaryItemsConfig: React.FC<SalaryItemsConfigProps> = ({ value, onChange }) => {
  // 确保 value 始终是数组
  const items = Array.isArray(value) ? value : [];
  
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [editForm, setEditForm] = useState<SalaryItem | null>(null);

  // 生成唯一编码
  const generateCode = useCallback((name: string) => {
    return name
      .replace(/[^a-zA-Z0-9\u4e00-\u9fa5]/g, '')
      .toLowerCase()
      .slice(0, 30) + '_' + Date.now().toString(36);
  }, []);

  // 添加新项目
  const handleAdd = useCallback((preset?: Partial<SalaryItem>) => {
    const newItem: SalaryItem = {
      code: preset?.code || generateCode(preset?.name || 'item'),
      name: preset?.name || '新项目',
      type: preset?.type || 'earning',
      required: preset?.required ?? false,
      min: preset?.min ?? 0,
      max: preset?.max,
      description: preset?.description,
    };
    const newValue = [...items, newItem];
    onChange?.(newValue);
    message.success(`已添加「${newItem.name}」`);
  }, [items, onChange, generateCode]);

  // 删除项目
  const handleDelete = useCallback((index: number) => {
    const newValue = items.filter((_, i) => i !== index);
    onChange?.(newValue);
    message.success('已删除');
  }, [items, onChange]);

  // 移动项目位置
  const handleMove = useCallback((index: number, direction: 'up' | 'down') => {
    if (
      (direction === 'up' && index === 0) ||
      (direction === 'down' && index === items.length - 1)
    ) {
      return;
    }
    const newValue = [...items];
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    [newValue[index], newValue[targetIndex]] = [newValue[targetIndex], newValue[index]];
    onChange?.(newValue);
  }, [items, onChange]);

  // 复制项目
  const handleCopy = useCallback((item: SalaryItem, index: number) => {
    const newItem = { ...item, code: generateCode(item.name), name: `${item.name} (副本)` };
    const newValue = [...items];
    newValue.splice(index + 1, 0, newItem);
    onChange?.(newValue);
    message.success('已复制');
  }, [items, onChange, generateCode]);

  // 开始编辑
  const handleEditStart = useCallback((index: number) => {
    setEditingIndex(index);
    setEditForm({ ...items[index] });
  }, [items]);

  // 取消编辑
  const handleEditCancel = useCallback(() => {
    setEditingIndex(null);
    setEditForm(null);
  }, []);

  // 保存编辑
  const handleEditSave = useCallback(() => {
    if (editingIndex === null || !editForm) return;
    const newValue = [...items];
    newValue[editingIndex] = editForm;
    onChange?.(newValue);
    setEditingIndex(null);
    setEditForm(null);
    message.success('已保存');
  }, [editingIndex, editForm, items, onChange]);

  // 表格列定义
  const columns = [
    {
      title: '序号',
      key: 'order',
      width: 60,
      render: (_: any, __: any, index: number) => index + 1,
    },
    {
      title: '项目名称',
      dataIndex: 'name',
      key: 'name',
      width: 120,
    },
    {
      title: '项目编码',
      dataIndex: 'code',
      key: 'code',
      width: 140,
      render: (code: string) => <code style={{ fontSize: 11 }}>{code}</code>,
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 80,
      render: (type: 'earning' | 'deduction') => {
        const config = itemTypeEnum.find((t) => t.value === type);
        return <Tag color={config?.color}>{config?.label}</Tag>;
      },
    },
    {
      title: '必填',
      dataIndex: 'required',
      key: 'required',
      width: 70,
      render: (required: boolean) => (
        <Switch
          checked={required}
          size="small"
          disabled
        />
      ),
    },
    {
      title: '最小值',
      dataIndex: 'min',
      key: 'min',
      width: 80,
      render: (min: number) => min?.toLocaleString() ?? '0',
    },
    {
      title: '说明',
      dataIndex: 'description',
      key: 'description',
      width: 150,
      ellipsis: true,
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: (_: any, record: SalaryItem, index: number) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<UpOutlined />}
            onClick={() => handleMove(index, 'up')}
            disabled={index === 0}
          />
          <Button
            type="link"
            size="small"
            icon={<DownOutlined />}
            onClick={() => handleMove(index, 'down')}
            disabled={index === items.length - 1}
          />
          <Button
            type="link"
            size="small"
            onClick={() => handleEditStart(index)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认删除"
            description={`确定要删除「${record.name}」吗？`}
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
            <Select
              placeholder="快速添加"
              style={{ width: 160 }}
              dropdownMatchSelectWidth={false}
              onChange={(index) => {
                if (index !== undefined && index !== null) {
                  handleAdd(PRESET_ITEMS[index]);
                }
              }}
              value={undefined}
              options={PRESET_ITEMS.map((item, index) => ({
                label: item.name,
                value: index,
              }))}
            />
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => handleAdd()}
            >
              手动添加
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" size={4}>
          <div>
            <InfoCircleOutlined style={{ marginRight: 8, color: '#1890ff' }} />
            <span style={{ color: '#666' }}>
              共配置 <strong>{items.length}</strong> 个薪资项目，
              其中收入项 <strong>{items.filter((i) => i.type === 'earning').length}</strong> 个，
              扣款项 <strong>{items.filter((i) => i.type === 'deduction').length}</strong> 个
            </span>
          </div>
          <div style={{ fontSize: 12, color: '#999' }}>
            提示：点击"编辑"可修改项目详情，拖动上下箭头可调整顺序
          </div>
        </Space>
      </Card>

      {/* 项目列表 */}
      <Table
        dataSource={items}
        columns={columns}
        rowKey="code"
        pagination={false}
        size="small"
        scroll={{ y: 400 }}
        locale={{ emptyText: '暂无薪资项目，点击上方按钮添加' }}
      />

      {/* 编辑弹窗 */}
      <Card
        size="small"
        title="编辑薪资项目"
        style={{ marginTop: 16 }}
        hidden={editingIndex === null}
      >
        <Space direction="vertical" style={{ width: '100%' }} size={16}>
          <Space wrap>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>项目名称 *</div>
              <Input
                value={editForm?.name}
                onChange={(e) => setEditForm((prev) => prev ? { ...prev, name: e.target.value } : null)}
                placeholder="如：基本工资"
                style={{ width: 180 }}
              />
            </div>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>项目编码 *</div>
              <Input
                value={editForm?.code}
                onChange={(e) => setEditForm((prev) => prev ? { ...prev, code: e.target.value } : null)}
                placeholder="如：base_salary"
                style={{ width: 180 }}
              />
            </div>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>类型 *</div>
              <Select
                value={editForm?.type}
                onChange={(type) => setEditForm((prev) => prev ? { ...prev, type } : null)}
                options={itemTypeEnum}
                style={{ width: 120 }}
              />
            </div>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>是否必填</div>
              <Switch
                checked={editForm?.required}
                onChange={(required) => setEditForm((prev) => prev ? { ...prev, required } : null)}
              />
            </div>
          </Space>

          <Space wrap>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>最小值</div>
              <InputNumber
                value={editForm?.min}
                onChange={(min) => setEditForm((prev) => prev ? { ...prev, min: min ?? 0 } : null)}
                min={0}
                style={{ width: 120 }}
                addonAfter="元"
              />
            </div>
            <div>
              <div style={{ marginBottom: 4, color: '#666' }}>最大值（可选）</div>
              <InputNumber
                value={editForm?.max}
                onChange={(max) => setEditForm((prev) => prev ? { ...prev, max: max ?? undefined } : null)}
                min={0}
                placeholder="无限制"
                style={{ width: 120 }}
                addonAfter="元"
              />
            </div>
          </Space>

          <div>
            <div style={{ marginBottom: 4, color: '#666' }}>说明（可选）</div>
            <Input.TextArea
              value={editForm?.description}
              onChange={(e) => setEditForm((prev) => prev ? { ...prev, description: e.target.value } : null)}
              placeholder="描述该薪资项目的用途和计算方式"
              rows={2}
              style={{ width: '100%', maxWidth: 500 }}
            />
          </div>

          <Space>
            <Button type="primary" onClick={handleEditSave}>
              保存
            </Button>
            <Button onClick={handleEditCancel}>
              取消
            </Button>
          </Space>
        </Space>
      </Card>
    </div>
  );
};

export default SalaryItemsConfig;