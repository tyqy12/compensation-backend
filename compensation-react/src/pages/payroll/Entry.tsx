import React, { useCallback, useMemo, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  App as AntdApp,
  AutoComplete,
  Button,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Segmented,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  FileSearchOutlined,
  InboxOutlined,
  LockOutlined,
  ReloadOutlined,
  SaveOutlined,
  ThunderboltOutlined,
  UploadOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { useSelector } from 'react-redux';
import {
  useAddPayrollManualImportItemMutation,
  useComputePayrollBatchMutation,
  useDeletePayrollImportItemMutation,
  useImportPayrollBatchCsvMutation,
  useLockPayrollBatchMutation,
  usePayrollBatchDetailQuery,
  usePayrollImportItemsQuery,
  usePayrollImportSalaryItemsQuery,
  useUpdatePayrollImportItemMutation,
  type PayrollBatchImportResult,
  type PayrollImportItemDto,
} from '@services/queries/payroll';
import type { PayrollBatchDetailDto } from '@services/queries/payroll';
import { useEmployeesQuery, type Employee } from '@services/queries/employee';
import type { RootState } from '@services/stores/authSlice';
import { hasAnyRole } from '@utils/rbac';
import {
  getBatchRevisionText,
  getCalculationStatusMeta,
  getFlowStatusMeta,
  isPayrollBatchComputable,
} from './components/payrollFlow';
import './PayrollPages.less';
import './Entry.less';

const { Text, Title } = Typography;

type EntryMode = 'csv' | 'manual';
type ManualFormValues = {
  employeeId?: number;
  employeeNo: string;
  itemCode: string;
  amount: number;
  note?: string;
};
type EditFormValues = ManualFormValues & { rowNo: number };

const mutableStatuses = ['draft', 'locked'];

const formatCurrency = (value?: number, currency = 'CNY') => {
  if (value === undefined || value === null) return '—';
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(value);
};

const normalize = (value?: string | null) =>
  String(value ?? '')
    .trim()
    .toLowerCase();

const isManualImport = (item: PayrollImportItemDto) =>
  Boolean(item.manual) || normalize(item.sourceName) === 'manual';

const hasImportError = (item: PayrollImportItemDto) =>
  Boolean(item.errorMsg) || ['invalid', 'error', 'failed'].includes(normalize(item.status));

const getImportErrorText = (item: PayrollImportItemDto) =>
  item.errorMsg || (hasImportError(item) ? '导入项未通过输入校验' : '');

const formatImportSource = (item: PayrollImportItemDto) =>
  isManualImport(item) ? '手动录入' : item.sourceName || 'CSV 导入';

const getImportSourceColor = (item: PayrollImportItemDto) =>
  isManualImport(item) ? 'blue' : 'default';

const parseImportSummary = (result?: PayrollBatchImportResult | null) => {
  if (!result?.importSummary) return undefined;
  try {
    return JSON.parse(result.importSummary) as {
      total?: number;
      valid?: number;
      invalid?: number;
    };
  } catch {
    return undefined;
  }
};

const PayrollBatchEntry: React.FC = () => {
  const { batchId: batchIdParam } = useParams<{ batchId: string }>();
  const batchId = Number(batchIdParam);
  const navigate = useNavigate();
  const { message } = AntdApp.useApp();
  const [entryMode, setEntryMode] = useState<EntryMode>('csv');
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [lastImportResult, setLastImportResult] = useState<PayrollBatchImportResult | null>(null);
  const [employeeKeyword, setEmployeeKeyword] = useState('');
  const [selectedEmployee, setSelectedEmployee] = useState<Employee | null>(null);
  const [editingItem, setEditingItem] = useState<PayrollImportItemDto | null>(null);
  const [showInvalidOnly, setShowInvalidOnly] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [manualForm] = Form.useForm<ManualFormValues>();
  const [editForm] = Form.useForm<EditFormValues>();

  const roles = useSelector((state: RootState) => state.auth.user?.roles ?? state.auth.roles ?? []);
  const canManage = useMemo(() => hasAnyRole(roles, ['ADMIN', 'FINANCE']), [roles]);
  const detailQuery = usePayrollBatchDetailQuery(batchId, {
    enabled: Number.isFinite(batchId) && batchId > 0,
  });
  const itemsQuery = usePayrollImportItemsQuery(batchId, {
    enabled: Number.isFinite(batchId) && batchId > 0,
  });
  const salaryItemsQuery = usePayrollImportSalaryItemsQuery({ enabled: canManage });
  const importMutation = useImportPayrollBatchCsvMutation();
  const addManualMutation = useAddPayrollManualImportItemMutation();
  const updateItemMutation = useUpdatePayrollImportItemMutation();
  const deleteItemMutation = useDeletePayrollImportItemMutation();
  const lockMutation = useLockPayrollBatchMutation();
  const computeMutation = useComputePayrollBatchMutation();

  const detail = detailQuery.data as PayrollBatchDetailDto | undefined;
  const items = useMemo(() => itemsQuery.data ?? [], [itemsQuery.data]);
  const status = normalize(detail?.status);
  const importMutable = canManage && mutableStatuses.includes(status);
  const employeeQuery = useEmployeesQuery(
    { current: 1, pageSize: 10, keyword: employeeKeyword.trim() || undefined },
    { enabled: importMutable && employeeKeyword.trim().length > 0 },
  );
  const employeeOptions = useMemo(
    () =>
      (employeeQuery.data?.records ?? []).map((employee: Employee) => ({
        value: employee.employeeId || String(employee.id || ''),
        label: `${employee.name || employee.employeeId || '未命名'}（${employee.employeeId || '—'}）${employee.department ? ` / ${employee.department}` : ''}`,
        employee,
      })),
    [employeeQuery.data?.records],
  );

  const invalidItems = useMemo(() => items.filter(hasImportError), [items]);
  const filteredItems = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    return items.filter((item) => {
      if (showInvalidOnly && !hasImportError(item)) return false;
      if (!normalizedKeyword) return true;
      return [item.employeeName, item.employeeNo, item.itemName, item.itemCode, item.errorMsg]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalizedKeyword));
    });
  }, [items, keyword, showInvalidOnly]);
  const totalAmount = useMemo(
    () => items.reduce((total, item) => total + (Number(item.amount) || 0), 0),
    [items],
  );
  const employeeCount = useMemo(
    () => new Set(items.map((item) => item.employeeNo).filter(Boolean)).size,
    [items],
  );
  const manualCount = useMemo(() => items.filter(isManualImport).length, [items]);
  const csvCount = items.length - manualCount;
  const importSummary = parseImportSummary(lastImportResult);
  const flowMeta = getFlowStatusMeta(detail?.status);
  const calculationMeta = getCalculationStatusMeta(
    detail?.calculationStatus ?? detail?.computeStatus,
  );

  const getErrorMessage = useCallback((error: any, fallback: string) => {
    return error?.response?.data?.message || error?.message || fallback;
  }, []);

  const refresh = useCallback(async () => {
    await Promise.all([detailQuery.refetch(), itemsQuery.refetch()]);
  }, [detailQuery, itemsQuery]);

  const handleImport = useCallback(async () => {
    const file = fileList[0]?.originFileObj as File | undefined;
    if (!importMutable) {
      message.warning('当前批次已进入只读阶段');
      return;
    }
    if (!file) {
      message.warning('请先选择 CSV 文件');
      return;
    }
    try {
      const result = await importMutation.mutateAsync({ batchId, file });
      setLastImportResult(result);
      setFileList([]);
      const summary = parseImportSummary(result);
      message.success(
        summary
          ? `导入完成：成功 ${summary.valid ?? 0} 行，失败 ${summary.invalid ?? 0} 行`
          : 'CSV 导入完成',
      );
    } catch (error: any) {
      message.error(getErrorMessage(error, 'CSV 导入失败'));
    }
  }, [batchId, fileList, getErrorMessage, importMutable, importMutation, message]);

  const handleManualSubmit = useCallback(async () => {
    try {
      const values = await manualForm.validateFields();
      await addManualMutation.mutateAsync({ batchId, payload: values });
      message.success('薪资项已追加');
      manualForm.resetFields(['employeeId', 'employeeNo', 'amount', 'note']);
      setSelectedEmployee(null);
      setEmployeeKeyword('');
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(getErrorMessage(error, '手动录入失败'));
    }
  }, [addManualMutation, batchId, getErrorMessage, manualForm, message]);

  const handleEdit = useCallback(
    (item: PayrollImportItemDto) => {
      setEditingItem(item);
      editForm.setFieldsValue({
        rowNo: item.rowNo ?? 1,
        employeeNo: item.employeeNo || '',
        itemCode: item.itemCode || '',
        amount: item.amount ?? 0,
        note: item.note,
      });
    },
    [editForm],
  );

  const handleEditSubmit = useCallback(async () => {
    if (!editingItem?.id) return;
    try {
      const values = await editForm.validateFields();
      await updateItemMutation.mutateAsync({ batchId, itemId: editingItem.id, payload: values });
      message.success('导入项已更新');
      setEditingItem(null);
      editForm.resetFields();
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(getErrorMessage(error, '导入项更新失败'));
    }
  }, [batchId, editForm, editingItem, getErrorMessage, message, updateItemMutation]);

  const handleDelete = useCallback(
    async (item: PayrollImportItemDto) => {
      if (!item.id) return;
      try {
        await deleteItemMutation.mutateAsync({ batchId, itemId: item.id });
        message.success('导入项已删除');
      } catch (error: any) {
        message.error(getErrorMessage(error, '导入项删除失败'));
      }
    },
    [batchId, deleteItemMutation, getErrorMessage, message],
  );

  const handleLock = useCallback(async () => {
    if (items.length === 0) {
      message.warning('至少录入一条薪资项后才能锁定');
      return;
    }
    if (invalidItems.length > 0) {
      message.warning('请先处理异常导入项，再锁定批次');
      return;
    }
    try {
      await lockMutation.mutateAsync(batchId);
      message.success('批次已锁定');
      await refresh();
    } catch (error: any) {
      message.error(getErrorMessage(error, '锁定批次失败'));
    }
  }, [batchId, getErrorMessage, invalidItems.length, items.length, lockMutation, message, refresh]);

  const handleCompute = useCallback(async () => {
    try {
      await computeMutation.mutateAsync(batchId);
      message.success('核算已完成，正在打开财务台账');
      navigate(`/payroll/batches/${batchId}/ledger`);
    } catch (error: any) {
      message.error(getErrorMessage(error, '核算失败'));
    }
  }, [batchId, computeMutation, getErrorMessage, message, navigate]);

  const columns = useMemo(
    () => [
      {
        title: '行',
        dataIndex: 'rowNo',
        width: 64,
        render: (value: number | undefined) => value ?? '—',
      },
      {
        title: '员工',
        key: 'employee',
        width: 180,
        render: (_: unknown, item: PayrollImportItemDto) => (
          <Space orientation="vertical" size={0}>
            <Text strong>{item.employeeName || item.employeeNo || '—'}</Text>
            <Text type="secondary">{item.employeeNo || '未匹配工号'}</Text>
          </Space>
        ),
      },
      {
        title: '薪资项',
        key: 'item',
        width: 190,
        render: (_: unknown, item: PayrollImportItemDto) => (
          <Space orientation="vertical" size={0}>
            <Text strong>{item.itemName || item.itemCode || '—'}</Text>
            <Text type="secondary">{item.itemCode || '未指定编码'}</Text>
          </Space>
        ),
      },
      {
        title: '金额',
        dataIndex: 'amount',
        width: 130,
        align: 'right' as const,
        render: (value: number | undefined) => formatCurrency(value, detail?.currency || 'CNY'),
      },
      {
        title: '来源',
        key: 'source',
        width: 110,
        render: (_: unknown, item: PayrollImportItemDto) => (
          <Tag color={getImportSourceColor(item)}>{formatImportSource(item)}</Tag>
        ),
      },
      {
        title: '状态',
        key: 'status',
        width: 110,
        render: (_: unknown, item: PayrollImportItemDto) => {
          const error = getImportErrorText(item);
          return error ? (
            <Tooltip title={error}>
              <Tag color="error">
                <ExclamationCircleOutlined /> 异常
              </Tag>
            </Tooltip>
          ) : (
            <Tag color="success">
              <CheckCircleOutlined /> 有效
            </Tag>
          );
        },
      },
      {
        title: '异常原因',
        key: 'error',
        width: 240,
        ellipsis: true,
        render: (_: unknown, item: PayrollImportItemDto) =>
          getImportErrorText(item) || <Text type="secondary">—</Text>,
      },
      {
        title: '操作',
        key: 'action',
        fixed: 'right' as const,
        width: 130,
        render: (_: unknown, item: PayrollImportItemDto) =>
          importMutable ? (
            <Space size={2}>
              <Button
                type="link"
                size="small"
                icon={<EditOutlined />}
                onClick={() => handleEdit(item)}
              >
                编辑
              </Button>
              <Popconfirm
                title="删除此导入项？"
                description="删除后不会参与本批次计算。"
                okText="删除"
                cancelText="取消"
                onConfirm={() => handleDelete(item)}
              >
                <Button type="link" danger size="small" icon={<DeleteOutlined />}>
                  删除
                </Button>
              </Popconfirm>
            </Space>
          ) : (
            <Text type="secondary">只读</Text>
          ),
      },
    ],
    [detail?.currency, handleDelete, handleEdit, importMutable],
  );

  if (!Number.isFinite(batchId) || batchId <= 0) {
    return (
      <PageContainer className="payroll-page-shell">
        <Empty description="缺少有效批次 ID" />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      className="payroll-page-shell payroll-entry-page"
      header={{
        title: '数据录入工作台',
        subTitle: detail?.periodLabel
          ? `${detail.periodLabel} · 先把输入事实整理干净，再进入计算核验`
          : '输入事实准备',
        extra: [
          <Button
            key="back"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate(`/payroll/batches?batchId=${batchId}`)}
          >
            返回运营工作台
          </Button>,
          <Button
            key="refresh"
            icon={<ReloadOutlined />}
            onClick={refresh}
            loading={itemsQuery.isFetching}
          >
            刷新
          </Button>,
        ],
      }}
    >
      <div className="payroll-entry-shell">
        <section className="payroll-entry-identity">
          <div>
            <Space size={8} wrap>
              <Title level={3}>{detail?.periodLabel || `批次 #${batchId}`}</Title>
              {flowMeta && <Tag color={flowMeta.color}>{flowMeta.text}</Tag>}
              {calculationMeta && <Tag color={calculationMeta.color}>{calculationMeta.text}</Tag>}
              <Tag>{getBatchRevisionText(detail?.batchRevision)}</Tag>
            </Space>
            <div className="payroll-entry-meta">
              <span>批次 ID：{batchId}</span>
              <span>用工类型：{detail?.payrollType || detail?.type || '—'}</span>
              <span>输入状态：{importMutable ? '可编辑' : '只读'}</span>
            </div>
          </div>
          <Space wrap>
            <Button
              icon={<FileSearchOutlined />}
              onClick={() => navigate(`/payroll/batches/${batchId}/ledger`)}
            >
              查看台账
            </Button>
            <Popconfirm
              title="锁定当前批次？"
              description="锁定前请确认异常导入项已经处理。锁定后仍可按规则继续补录。"
              okText="确认锁定"
              cancelText="取消"
              disabled={
                !importMutable ||
                status !== 'draft' ||
                items.length === 0 ||
                invalidItems.length > 0
              }
              onConfirm={handleLock}
            >
              <Button
                icon={<LockOutlined />}
                disabled={
                  !importMutable ||
                  status !== 'draft' ||
                  items.length === 0 ||
                  invalidItems.length > 0
                }
                loading={lockMutation.isPending}
              >
                锁定输入
              </Button>
            </Popconfirm>
            <Popconfirm
              title="开始计算当前批次？"
              description="系统会使用当前全部有效输入重新生成薪资结果和计算证据。"
              okText="开始计算"
              cancelText="取消"
              disabled={!canManage || !isPayrollBatchComputable(status) || invalidItems.length > 0}
              onConfirm={handleCompute}
            >
              <Button
                type="primary"
                icon={<ThunderboltOutlined />}
                disabled={
                  !canManage || !isPayrollBatchComputable(status) || invalidItems.length > 0
                }
                loading={computeMutation.isPending}
              >
                计算并查看台账
              </Button>
            </Popconfirm>
          </Space>
        </section>

        {!canManage && (
          <Alert
            type="warning"
            showIcon
            title="当前为只读视角"
            description="只有财务或管理员可以导入、补录、修改、删除和锁定输入项。"
          />
        )}
        {!detailQuery.isLoading && !detail && (
          <Alert
            type="error"
            showIcon
            title="批次加载失败"
            description="请刷新页面或返回运营工作台重新选择批次。"
          />
        )}

        <section className="payroll-entry-metrics">
          <div>
            <Text type="secondary">输入项</Text>
            <strong>{items.length}</strong>
            <span>
              手工 {manualCount} · CSV {csvCount}
            </span>
          </div>
          <div>
            <Text type="secondary">员工数</Text>
            <strong>{employeeCount}</strong>
            <span>按当前导入工号去重</span>
          </div>
          <div>
            <Text type="secondary">输入金额</Text>
            <strong>{formatCurrency(totalAmount, detail?.currency || 'CNY')}</strong>
            <span>不等同于最终实发</span>
          </div>
          <div className={invalidItems.length > 0 ? 'is-danger' : 'is-success'}>
            <Text type="secondary">输入异常</Text>
            <strong>{invalidItems.length}</strong>
            <span>{invalidItems.length > 0 ? '处理后才能继续' : '当前没有异常项'}</span>
          </div>
        </section>

        <div className="payroll-entry-layout">
          <aside className="payroll-entry-source-column">
            <section className="payroll-entry-panel">
              <div className="payroll-entry-panel-heading">
                <div>
                  <Title level={5}>输入来源</Title>
                  <Text type="secondary">两种方式都会追加到当前批次</Text>
                </div>
              </div>
              <Segmented
                block
                value={entryMode}
                onChange={(value) => setEntryMode(value as EntryMode)}
                options={[
                  { label: 'CSV 导入', value: 'csv', icon: <UploadOutlined /> },
                  { label: '单条补录', value: 'manual', icon: <UserOutlined /> },
                ]}
              />
              {entryMode === 'csv' ? (
                <div className="payroll-entry-source-form">
                  <Text type="secondary">
                    模板字段：employeeId、itemCode、amount、note。重复导入不会覆盖已有行。
                  </Text>
                  <Upload.Dragger
                    accept=".csv,text/csv"
                    disabled={!importMutable}
                    maxCount={1}
                    multiple={false}
                    fileList={fileList}
                    beforeUpload={(file) => {
                      setFileList([
                        { uid: file.uid, name: file.name, status: 'done', originFileObj: file },
                      ]);
                      return false;
                    }}
                    onRemove={() => {
                      setFileList([]);
                      return true;
                    }}
                  >
                    <p className="ant-upload-drag-icon">
                      <InboxOutlined />
                    </p>
                    <p className="ant-upload-text">选择或拖入 CSV</p>
                    <p className="ant-upload-hint">导入完成后直接在右侧台账检查异常</p>
                  </Upload.Dragger>
                  <Button
                    type="primary"
                    block
                    icon={<UploadOutlined />}
                    disabled={!importMutable || fileList.length === 0}
                    loading={importMutation.isPending}
                    onClick={handleImport}
                  >
                    提交 CSV
                  </Button>
                  {lastImportResult && (
                    <Alert
                      type="success"
                      showIcon
                      title="最近一次导入"
                      description={`成功 ${importSummary?.valid ?? 0} 行，失败 ${importSummary?.invalid ?? 0} 行`}
                    />
                  )}
                </div>
              ) : (
                <Form
                  form={manualForm}
                  layout="vertical"
                  disabled={!importMutable}
                  className="payroll-entry-source-form"
                >
                  <Form.Item
                    label="搜索员工"
                    extra={
                      employeeQuery.isFetching ? '正在搜索…' : '可按姓名、工号、手机号或邮箱搜索'
                    }
                  >
                    <AutoComplete
                      value={employeeKeyword}
                      options={employeeOptions}
                      onSearch={setEmployeeKeyword}
                      onChange={setEmployeeKeyword}
                      onSelect={(_, option) => {
                        const employee = (option as { employee?: Employee }).employee;
                        if (!employee) return;
                        setSelectedEmployee(employee);
                        setEmployeeKeyword(
                          `${employee.name || employee.employeeId}（${employee.employeeId}）`,
                        );
                        manualForm.setFieldsValue({
                          employeeId: employee.id,
                          employeeNo: employee.employeeId || '',
                        });
                      }}
                      notFoundContent={
                        employeeKeyword ? '未找到匹配员工，可直接输入工号' : '输入关键词搜索'
                      }
                    >
                      <Input placeholder="搜索员工" />
                    </AutoComplete>
                  </Form.Item>
                  <Form.Item name="employeeId" hidden>
                    <Input />
                  </Form.Item>
                  <Form.Item
                    name="employeeNo"
                    label="员工工号"
                    rules={[{ required: true, message: '请输入员工工号' }]}
                  >
                    <Input
                      placeholder="如 emp-001"
                      onChange={(event) => {
                        if (
                          selectedEmployee &&
                          event.target.value !== selectedEmployee.employeeId
                        ) {
                          setSelectedEmployee(null);
                          manualForm.setFieldValue('employeeId', undefined);
                        }
                      }}
                    />
                  </Form.Item>
                  <Form.Item
                    name="itemCode"
                    label="薪资项"
                    rules={[{ required: true, message: '请选择薪资项' }]}
                  >
                    <Select
                      showSearch
                      optionFilterProp="label"
                      placeholder="选择薪资项"
                      loading={salaryItemsQuery.isLoading}
                      options={(salaryItemsQuery.data ?? []).map((item) => ({
                        label: `${item.name || item.code}（${item.code}）`,
                        value: item.code,
                      }))}
                    />
                  </Form.Item>
                  <Form.Item
                    name="amount"
                    label="金额"
                    rules={[{ required: true, message: '请输入金额' }]}
                  >
                    <InputNumber
                      min={0.01}
                      precision={2}
                      style={{ width: '100%' }}
                      placeholder="输入金额"
                    />
                  </Form.Item>
                  <Form.Item name="note" label="备注">
                    <Input.TextArea rows={3} placeholder="可选，说明该笔调整原因" />
                  </Form.Item>
                  {selectedEmployee && (
                    <Alert
                      type="success"
                      showIcon
                      title={`已匹配：${selectedEmployee.name || selectedEmployee.employeeId}`}
                      description={`工号 ${selectedEmployee.employeeId || '—'} · ${selectedEmployee.department || '未分配部门'}`}
                    />
                  )}
                  <Button
                    type="primary"
                    block
                    icon={<SaveOutlined />}
                    loading={addManualMutation.isPending}
                    onClick={handleManualSubmit}
                  >
                    追加这条输入
                  </Button>
                </Form>
              )}
            </section>
            <section className="payroll-entry-panel payroll-entry-gate-panel">
              <div className="payroll-entry-panel-heading">
                <div>
                  <Title level={5}>继续条件</Title>
                  <Text type="secondary">系统会在动作前拦截明显问题</Text>
                </div>
              </div>
              <div className="payroll-entry-gate-list">
                <div className={items.length > 0 ? 'is-ready' : 'is-pending'}>
                  <span>{items.length > 0 ? <CheckCircleOutlined /> : <WarningOutlined />}</span>
                  <strong>至少一条输入项</strong>
                </div>
                <div className={invalidItems.length === 0 ? 'is-ready' : 'is-danger'}>
                  <span>
                    {invalidItems.length === 0 ? (
                      <CheckCircleOutlined />
                    ) : (
                      <ExclamationCircleOutlined />
                    )}
                  </span>
                  <strong>
                    {invalidItems.length === 0
                      ? '输入校验通过'
                      : `${invalidItems.length} 条输入异常`}
                  </strong>
                </div>
                <div
                  className={status === 'draft' || status === 'locked' ? 'is-ready' : 'is-pending'}
                >
                  <span>
                    <LockOutlined />
                  </span>
                  <strong>{importMutable ? '当前阶段可编辑' : '批次已进入只读阶段'}</strong>
                </div>
              </div>
            </section>
          </aside>

          <main className="payroll-entry-ledger-column">
            <section className="payroll-entry-panel">
              <div className="payroll-entry-panel-heading payroll-entry-ledger-heading">
                <div>
                  <Title level={5}>输入明细</Title>
                  <Text type="secondary">先处理异常项，再锁定并进入计算核验</Text>
                </div>
                <Space wrap>
                  <Input.Search
                    allowClear
                    value={keyword}
                    onChange={(event) => setKeyword(event.target.value)}
                    placeholder="搜索员工/薪资项"
                    style={{ width: 190 }}
                  />
                  <Button
                    type={showInvalidOnly ? 'primary' : 'default'}
                    icon={<ExclamationCircleOutlined />}
                    onClick={() => setShowInvalidOnly((current) => !current)}
                  >
                    {showInvalidOnly ? '显示全部' : '只看异常'}
                  </Button>
                </Space>
              </div>
              <Table<PayrollImportItemDto>
                rowKey="id"
                size="middle"
                loading={
                  itemsQuery.isLoading || itemsQuery.isFetching || deleteItemMutation.isPending
                }
                columns={columns}
                dataSource={filteredItems}
                pagination={{
                  pageSize: 20,
                  showSizeChanger: true,
                  showTotal: (total) => `共 ${total} 条`,
                }}
                scroll={{ x: 1160 }}
                locale={{ emptyText: showInvalidOnly ? '当前没有异常输入项' : '还没有录入输入项' }}
              />
            </section>
          </main>

          <aside className="payroll-entry-issue-column">
            <section className="payroll-entry-panel">
              <div className="payroll-entry-panel-heading">
                <div>
                  <Title level={5}>输入问题</Title>
                  <Text type="secondary">按影响优先级处理</Text>
                </div>
                <Tag color={invalidItems.length > 0 ? 'error' : 'success'}>
                  {invalidItems.length}
                </Tag>
              </div>
              {invalidItems.length === 0 ? (
                <Alert
                  type="success"
                  showIcon
                  title="输入项检查通过"
                  description="当前没有导入格式或必填字段异常。"
                />
              ) : (
                <div className="payroll-entry-issue-list">
                  {invalidItems.slice(0, 8).map((item) => (
                    <button type="button" key={item.id} onClick={() => handleEdit(item)}>
                      <span className="payroll-entry-issue-icon">
                        <ExclamationCircleOutlined />
                      </span>
                      <span>
                        <strong>
                          {item.employeeName || item.employeeNo || `第 ${item.rowNo ?? '—'} 行`}
                        </strong>
                        <Text type="secondary">{getImportErrorText(item)}</Text>
                      </span>
                      <EditOutlined />
                    </button>
                  ))}
                  {invalidItems.length > 8 && (
                    <Text type="secondary">
                      还有 {invalidItems.length - 8} 条异常，请使用“只看异常”筛选。
                    </Text>
                  )}
                </div>
              )}
            </section>
            <section className="payroll-entry-panel">
              <div className="payroll-entry-panel-heading">
                <div>
                  <Title level={5}>来源分布</Title>
                  <Text type="secondary">当前批次的输入构成</Text>
                </div>
              </div>
              <div className="payroll-entry-source-list">
                <div>
                  <span>
                    <UploadOutlined /> CSV 导入
                  </span>
                  <strong>{csvCount}</strong>
                </div>
                <div>
                  <span>
                    <UserOutlined /> 手动录入
                  </span>
                  <strong>{manualCount}</strong>
                </div>
              </div>
            </section>
          </aside>
        </div>
      </div>

      <Modal
        title={`编辑输入项${editingItem?.rowNo ? ` · 第 ${editingItem.rowNo} 行` : ''}`}
        open={Boolean(editingItem)}
        onOk={handleEditSubmit}
        onCancel={() => {
          setEditingItem(null);
          editForm.resetFields();
        }}
        confirmLoading={updateItemMutation.isPending}
        okText="保存修改"
        cancelText="取消"
      >
        <Form form={editForm} layout="vertical">
          <Space size={12} style={{ width: '100%' }} align="start">
            <Form.Item
              name="rowNo"
              label="行号"
              rules={[{ required: true, message: '请输入行号' }]}
              style={{ flex: 1 }}
            >
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="employeeNo"
              label="员工工号"
              rules={[{ required: true, message: '请输入员工工号' }]}
              style={{ flex: 2 }}
            >
              <Input />
            </Form.Item>
          </Space>
          <Form.Item
            name="itemCode"
            label="薪资项"
            rules={[{ required: true, message: '请选择薪资项' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={(salaryItemsQuery.data ?? []).map((item) => ({
                label: `${item.name || item.code}（${item.code}）`,
                value: item.code,
              }))}
            />
          </Form.Item>
          <Form.Item name="amount" label="金额" rules={[{ required: true, message: '请输入金额' }]}>
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="note" label="备注">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default PayrollBatchEntry;
