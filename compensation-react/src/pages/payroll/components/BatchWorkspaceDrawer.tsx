import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  App as AntdApp,
  AutoComplete,
  Button,
  Card,
  Descriptions,
  Divider,
  Drawer,
  Empty,
  Form,
  Modal,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Spin,
  Statistic,
  Steps,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import {
  AuditOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  FileSearchOutlined,
  InboxOutlined,
  LockOutlined,
  RedoOutlined,
  ReloadOutlined,
  SendOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import {
  useAddPayrollManualImportItemMutation,
  useComputePayrollBatchMutation,
  useDeletePayrollImportItemMutation,
  useUpdatePayrollImportItemMutation,
  useImportPayrollBatchCsvMutation,
  useLockPayrollBatchMutation,
  usePayrollBatchDetailQuery,
  usePayrollDryRunQuery,
  usePayrollImportItemsQuery,
  usePayrollImportSalaryItemsQuery,
  useRetryPayrollPaymentMutation,
  useSubmitPayrollBatchApprovalMutation,
  type PayrollBatchDetailDto,
  type PayrollBatchImportResult,
  type PayrollImportItemDto,
} from '@services/queries/payroll';
import type { PayrollBatchSummaryDto, PayrollValidationIssueDto } from '@types/openapi';
import { useEmployeesQuery, type Employee } from '@services/queries/employee';
import {
  getBatchRevisionText,
  getCalculationStatusMeta,
  getConfirmationModeText,
  getDistributionStatusMeta,
  getFlowStatusMeta,
  getPayrollBlockers,
  getPayrollFlowCurrentStep,
  getPayrollFlowSteps,
  getPayrollNextAction,
} from './payrollFlow';

const { Text, Title } = Typography;

const MUTABLE_IMPORT_STATUSES = ['draft', 'locked'];
const COMPUTABLE_STATUSES = [
  'locked',
  'confirming',
  'dispute_processing',
  'confirmed',
  'approved',
  'rejected',
];
const PREVIEWABLE_STATUSES = [
  'draft',
  'locked',
  'confirming',
  'dispute_processing',
  'confirmed',
  'submitted',
  'approved',
  'rejected',
];

type WorkspaceTabKey = 'overview' | 'entry' | 'items';

export interface BatchWorkspaceDrawerProps {
  open: boolean;
  batch: PayrollBatchSummaryDto | null;
  defaultTab?: WorkspaceTabKey;
  canManageBatch: boolean;
  onClose: () => void;
  onBatchChanged?: () => void;
}

const importStatusTextMap: Record<string, string> = {
  valid: '有效',
  invalid: '异常',
};

const formatCurrency = (value?: number | null, currency = 'CNY') => {
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

const normalizeStatus = (status?: string) => String(status ?? '').toLowerCase();

const normalizeWorkspaceTab = (tab?: WorkspaceTabKey): WorkspaceTabKey => {
  if (tab === 'items') {
    return 'entry';
  }
  return tab ?? 'overview';
};

const isManualImportSource = (sourceName?: string, manual?: boolean) =>
  manual || normalizeStatus(sourceName) === 'manual_entry';

const formatImportSource = (sourceName?: string, manual?: boolean) => {
  if (isManualImportSource(sourceName, manual)) {
    return '手动录入';
  }
  if (!sourceName) {
    return '—';
  }
  return sourceName.toLowerCase().endsWith('.csv') ? `CSV 导入（${sourceName}）` : sourceName;
};

const getImportSourceColor = (sourceName?: string, manual?: boolean) =>
  isManualImportSource(sourceName, manual) ? 'blue' : 'default';

const hasImportError = (status?: string, errorMsg?: string) =>
  Boolean(errorMsg) || normalizeStatus(status) === 'invalid';

const getImportErrorMessage = (status?: string, errorMsg?: string) => {
  if (errorMsg) {
    return errorMsg;
  }
  if (normalizeStatus(status) === 'invalid') {
    return '该记录状态异常，请检查导入数据。';
  }
  return undefined;
};

const formatImportStatus = (status?: string, errorMsg?: string) => {
  if (hasImportError(status, errorMsg)) {
    return '异常';
  }
  if (!status) {
    return '—';
  }
  return importStatusTextMap[normalizeStatus(status)] || status;
};

const getImportStatusColor = (status?: string, errorMsg?: string) => {
  if (hasImportError(status, errorMsg)) {
    return 'error';
  }
  if (normalizeStatus(status) === 'valid') {
    return 'success';
  }
  return 'default';
};

const renderImportStatusTag = (status?: string, errorMsg?: string) => {
  const tag = (
    <Tag color={getImportStatusColor(status, errorMsg)}>{formatImportStatus(status, errorMsg)}</Tag>
  );
  const detail = getImportErrorMessage(status, errorMsg);
  if (!detail) {
    return tag;
  }
  return (
    <Tooltip title={detail}>
      <span>{tag}</span>
    </Tooltip>
  );
};

const getBatchId = (
  record?: PayrollBatchSummaryDto | PayrollBatchDetailDto | null,
): number | undefined => {
  const raw = record?.batchId ?? (record as any)?.id;
  if (typeof raw === 'number' && Number.isFinite(raw)) {
    return raw;
  }
  if (typeof raw === 'string' && raw.trim()) {
    const next = Number(raw);
    return Number.isFinite(next) ? next : undefined;
  }
  return undefined;
};

const parseImportSummary = (result?: PayrollBatchImportResult) => {
  if (!result?.importSummary) {
    return undefined;
  }
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

const normalizeIssueSeverity = (issue?: PayrollValidationIssueDto) => {
  if (!issue) {
    return 'review';
  }
  const severity = String(issue.severity ?? '').toLowerCase();
  if (severity === 'blocking' || issue.blocking) {
    return 'blocking';
  }
  if (severity === 'info') {
    return 'info';
  }
  return 'review';
};

const collectIssueMessages = (
  issues: PayrollValidationIssueDto[] | undefined,
  severity?: 'blocking' | 'review' | 'info',
) => {
  const messages = new Set<string>();
  (issues ?? []).forEach((issue) => {
    const normalizedSeverity = normalizeIssueSeverity(issue);
    if (severity && normalizedSeverity !== severity) {
      return;
    }
    if (issue?.message) {
      messages.add(issue.message);
    }
  });
  return Array.from(messages);
};

const collectWarnings = (
  detail: PayrollBatchDetailDto,
  previewWarnings?: string[],
  previewIssues?: PayrollValidationIssueDto[],
) => {
  const warnings = new Set<string>();
  (detail.warnings ?? []).forEach((warning) => {
    if (warning) {
      warnings.add(warning);
    }
  });
  (previewWarnings ?? []).forEach((warning) => {
    if (warning) {
      warnings.add(warning);
    }
  });
  collectIssueMessages(previewIssues, 'review').forEach((warning) => warnings.add(warning));
  collectIssueMessages(previewIssues, 'info').forEach((warning) => warnings.add(warning));
  return Array.from(warnings);
};

export function BatchWorkspaceDrawer({
  open,
  batch,
  defaultTab = 'overview',
  canManageBatch,
  onClose,
  onBatchChanged,
}: BatchWorkspaceDrawerProps) {
  const { message, modal } = AntdApp.useApp();
  const navigate = useNavigate();
  const [manualForm] = Form.useForm();
  const [editItemForm] = Form.useForm();
  const [activeTab, setActiveTab] = useState<WorkspaceTabKey>(normalizeWorkspaceTab(defaultTab));
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [lastImportResult, setLastImportResult] = useState<PayrollBatchImportResult | null>(null);
  const [employeeSearchKeyword, setEmployeeSearchKeyword] = useState('');
  const [debouncedEmployeeSearchKeyword, setDebouncedEmployeeSearchKeyword] = useState('');
  const [selectedEmployee, setSelectedEmployee] = useState<Employee | null>(null);
  const [editingItem, setEditingItem] = useState<PayrollImportItemDto | null>(null);
  const manualFormMountedRef = useRef(false);

  const batchId = getBatchId(batch);

  const detailQuery = usePayrollBatchDetailQuery(batchId ?? 0, { enabled: open && !!batchId });
  const itemsQuery = usePayrollImportItemsQuery(batchId ?? 0, { enabled: open && !!batchId });
  const salaryItemsQuery = usePayrollImportSalaryItemsQuery({
    enabled: open && !!batchId && canManageBatch,
  });

  const mergedDetail = useMemo<PayrollBatchDetailDto>(
    () => ({
      ...(batch ?? {}),
      ...(detailQuery.data ?? {}),
    }),
    [batch, detailQuery.data],
  );

  const status = normalizeStatus(mergedDetail.status);
  const importItems = itemsQuery.data ?? [];
  const hasImportData = importItems.length > 0;
  const canPreview = open && !!batchId && hasImportData && PREVIEWABLE_STATUSES.includes(status);
  const previewQuery = usePayrollDryRunQuery(batchId ?? 0, undefined, { enabled: canPreview });

  const importMutable = canManageBatch && MUTABLE_IMPORT_STATUSES.includes(status);
  const employeeQueryParams = useMemo(
    () => ({
      current: 1,
      pageSize: 10,
      keyword: debouncedEmployeeSearchKeyword.trim() || undefined,
    }),
    [debouncedEmployeeSearchKeyword],
  );
  const employeeQuery = useEmployeesQuery(employeeQueryParams, {
    enabled: open && importMutable && !!employeeQueryParams.keyword,
  });
  const employeeOptions = useMemo(
    () =>
      (employeeQuery.data?.records ?? []).map((employee) => ({
        value: employee.employeeId,
        label: `${employee.name || employee.employeeId}（${employee.employeeId}）${employee.department ? ` / ${employee.department}` : ''}`,
        employee,
      })),
    [employeeQuery.data?.records],
  );
  const employeeMatchHint = useMemo(() => {
    if (!debouncedEmployeeSearchKeyword) {
      return '支持按姓名、工号、手机号或邮箱搜索匹配；如果你已知道工号，也可以直接在下方输入。';
    }
    if (employeeQuery.isFetching) {
      return '正在搜索匹配员工…';
    }
    if (employeeOptions.length > 0) {
      return `已找到 ${employeeOptions.length} 个匹配员工，请选择正确的员工。`;
    }
    return '未找到匹配员工，可继续直接输入工号。';
  }, [debouncedEmployeeSearchKeyword, employeeOptions.length, employeeQuery.isFetching]);
  const canLock = canManageBatch && status === 'draft' && hasImportData;
  const canCompute = canManageBatch && COMPUTABLE_STATUSES.includes(status);
  const previewIssues = previewQuery.data?.issues ?? [];
  const structuredBlockers = useMemo(
    () => collectIssueMessages(previewIssues, 'blocking'),
    [previewIssues],
  );
  const warnings = useMemo(
    () => collectWarnings(mergedDetail, previewQuery.data?.warnings, previewIssues),
    [mergedDetail, previewIssues, previewQuery.data?.warnings],
  );
  const reviewReminders = useMemo(() => {
    const reminders = new Set<string>(collectIssueMessages(previewIssues, 'review'));
    collectIssueMessages(previewIssues, 'info').forEach((message) => reminders.add(message));
    warnings.forEach((warning) => {
      if (!structuredBlockers.includes(warning)) {
        reminders.add(warning);
      }
    });
    return Array.from(reminders);
  }, [previewIssues, structuredBlockers, warnings]);
  const blockers = useMemo(() => {
    const merged = new Set<string>(getPayrollBlockers(mergedDetail, hasImportData));
    structuredBlockers.forEach((message) => merged.add(message));
    if ((previewQuery.data?.hasBlockingIssues ?? false) && structuredBlockers.length === 0) {
      merged.add('存在员工级阻塞问题，请前往台账或经理核对页面处理后再提交审批。');
    }
    return Array.from(merged);
  }, [hasImportData, mergedDetail, previewQuery.data?.hasBlockingIssues, structuredBlockers]);
  const canSubmit = canManageBatch && status === 'confirmed' && !(previewQuery.data?.hasBlockingIssues ?? false);
  const canRetry =
    canManageBatch &&
    ['pay_failed', 'failed', 'partial_success'].includes(
      String(mergedDetail.paymentStatus ?? status).toLowerCase(),
    );

  const manualCount = useMemo(
    () => importItems.filter((item) => isManualImportSource(item.sourceName, item.manual)).length,
    [importItems],
  );
  const csvCount = importItems.length - manualCount;
  const sourceSummary = useMemo(() => {
    const summary = new Map<string, number>();
    importItems.forEach((item) => {
      const key = formatImportSource(item.sourceName, item.manual);
      summary.set(key, (summary.get(key) ?? 0) + 1);
    });
    return Array.from(summary.entries());
  }, [importItems]);
  const invalidItemCount = useMemo(
    () => importItems.filter((item) => hasImportError(item.status, item.errorMsg)).length,
    [importItems],
  );

  const nextAction = useMemo(
    () => getPayrollNextAction(mergedDetail, hasImportData, canManageBatch),
    [canManageBatch, hasImportData, mergedDetail],
  );

  const loading = detailQuery.isLoading || itemsQuery.isLoading;
  const errorMessage =
    (detailQuery.error as any)?.response?.data?.message ||
    (itemsQuery.error as any)?.response?.data?.message ||
    (detailQuery.error as any)?.message ||
    (itemsQuery.error as any)?.message;

  const switchActiveTab = useCallback((nextTab: WorkspaceTabKey) => {
    const normalizedNextTab = normalizeWorkspaceTab(nextTab);
    setActiveTab((currentTab) => {
      if (currentTab === normalizedNextTab) {
        return currentTab;
      }
      const activeElement = document.activeElement;
      if (activeElement instanceof HTMLElement) {
        activeElement.blur();
      }
      return normalizedNextTab;
    });
  }, []);

  useEffect(() => {
    if (!open) {
      return;
    }
    switchActiveTab(defaultTab);
  }, [defaultTab, open, batchId, switchActiveTab]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedEmployeeSearchKeyword(employeeSearchKeyword.trim());
    }, 300);
    return () => window.clearTimeout(timer);
  }, [employeeSearchKeyword]);

  useEffect(() => {
    if (open) {
      return;
    }
    const activeElement = document.activeElement;
    if (activeElement instanceof HTMLElement) {
      activeElement.blur();
    }
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    setFileList([]);
    setLastImportResult(null);
    setEmployeeSearchKeyword('');
    setDebouncedEmployeeSearchKeyword('');
    setSelectedEmployee(null);
    setEditingItem(null);
    if (manualFormMountedRef.current) {
      manualForm.resetFields();
    }
    editItemForm.resetFields();
  }, [batchId, open, editItemForm, manualForm]);

  const notifyBatchChanged = useCallback(() => {
    onBatchChanged?.();
  }, [onBatchChanged]);

  const getErrorMessage = useCallback((error: any, fallback: string) => {
    return error?.response?.data?.message || error?.message || fallback;
  }, []);

  const importMutation = useImportPayrollBatchCsvMutation();
  const lockMutation = useLockPayrollBatchMutation();
  const computeMutation = useComputePayrollBatchMutation();
  const submitMutation = useSubmitPayrollBatchApprovalMutation();
  const retryPaymentMutation = useRetryPayrollPaymentMutation();
  const addManualMutation = useAddPayrollManualImportItemMutation();
  const updateItemMutation = useUpdatePayrollImportItemMutation();
  const deleteItemMutation = useDeletePayrollImportItemMutation();

  const handleBatchAction = useCallback(
    (options: {
      title: string;
      content: React.ReactNode;
      successMessage: string;
      okText: string;
      run: () => Promise<unknown>;
    }) => {
      modal.confirm({
        title: options.title,
        content: options.content,
        okText: options.okText,
        cancelText: '取消',
        onOk: async () => {
          try {
            await options.run();
            message.success(options.successMessage);
            notifyBatchChanged();
          } catch (error: any) {
            message.error(getErrorMessage(error, `${options.title}失败`));
            throw error;
          }
        },
      });
    },
    [getErrorMessage, message, modal, notifyBatchChanged],
  );

  const handleImportCsv = useCallback(async () => {
    const file = fileList[0]?.originFileObj as File | undefined;
    if (!batchId) {
      message.warning('缺少批次 ID，无法导入');
      return;
    }
    if (!file) {
      message.warning('请先选择 CSV 文件');
      return;
    }
    try {
      const result = await importMutation.mutateAsync({ batchId, file });
      setLastImportResult(result);
      const summary = parseImportSummary(result);
      message.success(
        summary
          ? `CSV 已追加导入：成功 ${summary.valid ?? 0} 行，失败 ${summary.invalid ?? 0} 行`
          : 'CSV 导入成功',
      );
      setFileList([]);
      switchActiveTab('items');
      notifyBatchChanged();
    } catch (error: any) {
      message.error(getErrorMessage(error, 'CSV 导入失败'));
    }
  }, [
    batchId,
    fileList,
    getErrorMessage,
    importMutation,
    message,
    notifyBatchChanged,
    switchActiveTab,
  ]);

  const handleManualSubmit = useCallback(async () => {
    if (!batchId) {
      message.warning('缺少批次 ID，无法手动录入');
      return;
    }
    try {
      const values = await manualForm.validateFields();
      await addManualMutation.mutateAsync({
        batchId,
        payload: {
          employeeId: values.employeeId,
          employeeNo: values.employeeNo,
          itemCode: values.itemCode,
          amount: values.amount,
          note: values.note,
        },
      });
      message.success('手动录入成功，已追加到当前批次');
      setSelectedEmployee(null);
      setEmployeeSearchKeyword('');
      setDebouncedEmployeeSearchKeyword('');
      manualForm.resetFields(['employeeId', 'employeeNo', 'amount', 'note']);
      switchActiveTab('items');
      notifyBatchChanged();
    } catch (error: any) {
      if (error?.errorFields) {
        return;
      }
      message.error(getErrorMessage(error, '手动录入失败'));
    }
  }, [
    addManualMutation,
    batchId,
    getErrorMessage,
    manualForm,
    message,
    notifyBatchChanged,
    switchActiveTab,
  ]);

  const handleEditItem = useCallback(
    (item: PayrollImportItemDto) => {
      setEditingItem(item);
      editItemForm.setFieldsValue({
        rowNo: item.rowNo,
        employeeNo: item.employeeNo,
        itemCode: item.itemCode,
        amount: item.amount,
        note: item.note,
      });
    },
    [editItemForm],
  );

  const handleEditItemSubmit = useCallback(async () => {
    if (!batchId || !editingItem?.id) {
      message.warning('缺少导入项 ID，无法更新');
      return;
    }
    try {
      const values = await editItemForm.validateFields();
      await updateItemMutation.mutateAsync({
        batchId,
        itemId: editingItem.id,
        payload: {
          employeeNo: values.employeeNo,
          itemCode: values.itemCode,
          amount: values.amount,
          rowNo: values.rowNo,
          note: values.note,
        },
      });
      message.success('导入项已更新');
      setEditingItem(null);
      editItemForm.resetFields();
      notifyBatchChanged();
    } catch (error: any) {
      if (error?.errorFields) {
        return;
      }
      message.error(getErrorMessage(error, '更新导入项失败'));
    }
  }, [
    batchId,
    editItemForm,
    editingItem,
    getErrorMessage,
    message,
    notifyBatchChanged,
    updateItemMutation,
  ]);

  const handleDeleteItem = useCallback(
    async (item: PayrollImportItemDto) => {
      if (!batchId || !item.id) {
        message.warning('缺少导入项 ID，无法删除');
        return;
      }
      try {
        await deleteItemMutation.mutateAsync({ batchId, itemId: item.id });
        message.success('导入项已删除');
        notifyBatchChanged();
      } catch (error: any) {
        message.error(getErrorMessage(error, '删除导入项失败'));
      }
    },
    [batchId, deleteItemMutation, getErrorMessage, message, notifyBatchChanged],
  );

  const itemColumns = useMemo(
    () => [
      {
        title: '行号',
        dataIndex: 'rowNo',
        width: 72,
        render: (value: number | undefined) => value ?? '—',
      },
      {
        title: '员工',
        key: 'employee',
        width: 180,
        render: (_: unknown, record: PayrollImportItemDto) => (
          <Space direction="vertical" size={0}>
            <Text strong>{record.employeeName || record.employeeNo || '—'}</Text>
            <Text type="secondary">工号：{record.employeeNo || '—'}</Text>
          </Space>
        ),
      },
      {
        title: '薪资项',
        key: 'salaryItem',
        width: 180,
        render: (_: unknown, record: PayrollImportItemDto) => (
          <Space direction="vertical" size={0}>
            <Text strong>{record.itemName || record.itemCode || '—'}</Text>
            <Text type="secondary">编码：{record.itemCode || '—'}</Text>
          </Space>
        ),
      },
      {
        title: '金额',
        dataIndex: 'amount',
        width: 140,
        align: 'right' as const,
        render: (value: number | undefined) => formatCurrency(value),
      },
      {
        title: '来源',
        dataIndex: 'sourceName',
        width: 150,
        render: (value: string | undefined, record: PayrollImportItemDto) => (
          <Tag color={getImportSourceColor(value, record.manual)}>
            {formatImportSource(value, record.manual)}
          </Tag>
        ),
      },
      {
        title: '备注',
        dataIndex: 'note',
        ellipsis: true,
        render: (value: string | undefined) => value || '—',
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (value: string | undefined, record: PayrollImportItemDto) =>
          renderImportStatusTag(value, record.errorMsg),
      },
      {
        title: '异常原因',
        dataIndex: 'errorMsg',
        width: 260,
        ellipsis: { showTitle: false },
        render: (value: string | undefined, record: PayrollImportItemDto) => {
          const detail = getImportErrorMessage(record.status, value);
          if (!detail) {
            return <Text type="secondary">—</Text>;
          }
          return (
            <Tooltip title={detail}>
              <Text type="danger">{detail}</Text>
            </Tooltip>
          );
        },
      },
      {
        title: '录入时间',
        dataIndex: 'createTime',
        width: 160,
        render: (value: string | undefined) => formatDateTime(value),
      },
      {
        title: '操作',
        key: 'actions',
        width: 150,
        render: (_: unknown, record: PayrollImportItemDto) =>
          importMutable ? (
            <Space size={4}>
              <Button
                type="link"
                size="small"
                icon={<EditOutlined />}
                onClick={() => handleEditItem(record)}
              >
                编辑
              </Button>
              <Popconfirm
                title="删除导入项"
                description="删除后该记录不会再参与本批次计算。"
                okText="删除"
                cancelText="取消"
                onConfirm={() => handleDeleteItem(record)}
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
    [handleDeleteItem, handleEditItem, importMutable],
  );

  const lastImportSummary = parseImportSummary(lastImportResult ?? undefined);

  const refreshAll = useCallback(() => {
    void detailQuery.refetch();
    void itemsQuery.refetch();
    if (canPreview) {
      void previewQuery.refetch();
    }
  }, [canPreview, detailQuery, itemsQuery, previewQuery]);

  return (
    <Drawer
      title={
        <Space direction="vertical" size={0}>
          <Title level={5} style={{ margin: 0 }}>
            批次工作台
          </Title>
          <Space size={8} wrap>
            <Text strong>{mergedDetail.periodLabel || '未命名批次'}</Text>
            {batchId && <Text type="secondary">#{batchId}</Text>}
            <Tag>{getBatchRevisionText(mergedDetail.batchRevision)}</Tag>
            {getFlowStatusMeta(status) && (
              <Tag color={getFlowStatusMeta(status)?.color}>{getFlowStatusMeta(status)?.text}</Tag>
            )}
            {getCalculationStatusMeta(mergedDetail.calculationStatus ?? mergedDetail.computeStatus) && (
              <Tag color={getCalculationStatusMeta(mergedDetail.calculationStatus ?? mergedDetail.computeStatus)?.color}>
                {getCalculationStatusMeta(mergedDetail.calculationStatus ?? mergedDetail.computeStatus)?.text}
              </Tag>
            )}
            {getDistributionStatusMeta(mergedDetail.paymentStatus) && (
              <Tag color={getDistributionStatusMeta(mergedDetail.paymentStatus)?.color}>
                {getDistributionStatusMeta(mergedDetail.paymentStatus)?.text}
              </Tag>
            )}
          </Space>
        </Space>
      }
      open={open}
      onClose={onClose}
      width={1080}
      destroyOnClose={false}
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={refreshAll}>
            刷新
          </Button>
          {mergedDetail.approvalWorkflowId && (
            <Button
              icon={<CheckCircleOutlined />}
              onClick={() => navigate(`/approval/workflows?keyword=${mergedDetail.approvalWorkflowId}`)}
            >
              审批流
            </Button>
          )}
          {mergedDetail.paymentBatchNo && (
            <Button
              icon={<ThunderboltOutlined />}
              onClick={() => navigate(`/payments/batches/${mergedDetail.paymentBatchNo}`)}
            >
              支付批次
            </Button>
          )}
          {batchId && (
            <Button
              icon={<SendOutlined />}
              onClick={() => navigate(`/payroll/distributions?batchId=${batchId}`)}
            >
              发放单
            </Button>
          )}
          {batchId && (
            <Button
              icon={<AuditOutlined />}
              onClick={() => navigate(`/payroll/reconciliations?batchId=${batchId}`)}
            >
              对账任务
            </Button>
          )}
          {batchId && (
            <Button
              icon={<FileSearchOutlined />}
              onClick={() => navigate(`/payroll/batches/${batchId}/ledger`)}
            >
              台账
            </Button>
          )}
        </Space>
      }
    >
      <Spin spinning={loading}>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          {errorMessage && (
            <Alert type="error" showIcon message="工作台数据加载失败" description={errorMessage} />
          )}

          <Alert
            type={canManageBatch ? 'info' : 'warning'}
            showIcon
            message="流程与录入规则"
            description={
              <Space direction="vertical" size={4}>
                <Text>CSV 导入是追加，不会覆盖已有导入项；手动录入也会追加到同一批次。</Text>
                <Text>同一员工同一薪资项若出现多条导入记录，系统会在计算时累计金额。</Text>
                <Text>当前设计允许在“草稿 / 已锁定”状态继续补录和删除导入项，提升修正效率。</Text>
              </Space>
            }
          />

          {Number(mergedDetail.batchRevision ?? 1) > 1 && (
            <Alert
              type="warning"
              showIcon
              message={`当前展示的是 ${getBatchRevisionText(mergedDetail.batchRevision)} 版本`}
              description="如果该批次曾重算，旧的确认、审批与发放链路会失效；工作台仅展示当前有效版本。"
            />
          )}

          <Steps
            size="small"
            current={getPayrollFlowCurrentStep(mergedDetail)}
            items={getPayrollFlowSteps(mergedDetail)}
          />

          <Tabs
            activeKey={activeTab}
            onChange={(key) => switchActiveTab(key as WorkspaceTabKey)}
            items={[
              {
                key: 'overview',
                label: '流程看板',
                children: (
                  <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <Card size="small">
                      <Descriptions
                        column={2}
                        size="small"
                        bordered
                        items={[
                          {
                            key: 'flowStatus',
                            label: '流转状态',
                            children: getFlowStatusMeta(status) ? (
                              <Tag color={getFlowStatusMeta(status)?.color}>{getFlowStatusMeta(status)?.text}</Tag>
                            ) : '—',
                          },
                          {
                            key: 'calculationStatus',
                            label: '核算状态',
                            children: getCalculationStatusMeta(
                              mergedDetail.calculationStatus ?? mergedDetail.computeStatus,
                            ) ? (
                              <Tag
                                color={getCalculationStatusMeta(
                                  mergedDetail.calculationStatus ?? mergedDetail.computeStatus,
                                )?.color}
                              >
                                {getCalculationStatusMeta(
                                  mergedDetail.calculationStatus ?? mergedDetail.computeStatus,
                                )?.text}
                              </Tag>
                            ) : '—',
                          },
                          {
                            key: 'batchRevision',
                            label: '批次版本',
                            children: getBatchRevisionText(mergedDetail.batchRevision),
                          },
                          {
                            key: 'nextAction',
                            label: '下一步建议',
                            children: nextAction,
                          },
                          {
                            key: 'periodLabel',
                            label: '批次期间',
                            children: mergedDetail.periodLabel || '—',
                          },
                          {
                            key: 'payrollType',
                            label: '用工类型',
                            children: mergedDetail.payrollType || mergedDetail.type || '—',
                          },
                          {
                            key: 'confirmationMode',
                            label: '确认策略',
                            children: getConfirmationModeText(
                              mergedDetail.confirmationMode,
                              mergedDetail.confirmationRequired,
                            ),
                          },
                          {
                            key: 'confirmationCompletedTime',
                            label: '确认完成时间',
                            children: formatDateTime(mergedDetail.confirmationCompletedTime),
                          },
                          {
                            key: 'approvalWorkflowId',
                            label: '审批流 ID',
                            children: mergedDetail.approvalWorkflowId ? `#${mergedDetail.approvalWorkflowId}` : '—',
                          },
                          {
                            key: 'paymentBatchNo',
                            label: '支付批次',
                            children: mergedDetail.paymentBatchNo || '—',
                          },
                          {
                            key: 'provider',
                            label: '发放渠道',
                            children: mergedDetail.settlementProviderCode || '—',
                          },
                          {
                            key: 'remark',
                            label: '批次备注',
                            children: mergedDetail.remark || '—',
                          },
                          {
                            key: 'updatedAt',
                            label: '最近更新时间',
                            children: formatDateTime(mergedDetail.updatedAt),
                          },
                        ]}
                      />
                    </Card>

                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))',
                        gap: 12,
                      }}
                    >
                      <Card size="small">
                        <Statistic title="导入项数" value={importItems.length} />
                      </Card>
                      <Card size="small">
                        <Statistic title="手工录入" value={manualCount} />
                      </Card>
                      <Card size="small">
                        <Statistic title="CSV 导入" value={csvCount} />
                      </Card>
                      <Card size="small">
                        <Statistic
                          title="员工数"
                          value={
                            previewQuery.data?.totalEmployees ?? mergedDetail.totalEmployees ?? 0
                          }
                        />
                      </Card>
                      <Card size="small">
                        <Statistic
                          title="实发金额"
                          value={previewQuery.data?.netTotal ?? mergedDetail.netTotal ?? 0}
                          precision={2}
                        />
                      </Card>
                      <Card size="small">
                        <Statistic
                          title="阻塞问题"
                          value={previewQuery.data?.blockingIssueCount ?? blockers.length}
                        />
                      </Card>
                      <Card size="small">
                        <Statistic
                          title="复核提醒"
                          value={previewQuery.data?.reviewIssueCount ?? reviewReminders.length}
                        />
                      </Card>
                    </div>                    {blockers.length > 0 && (
                      <Alert
                        type="error"
                        showIcon
                        message="当前阻塞原因"
                        description={
                          <Space direction="vertical" size={4}>
                            {blockers.map((blocker) => (
                              <Text key={blocker}>{blocker}</Text>
                            ))}
                          </Space>
                        }
                      />
                    )}

                    {reviewReminders.length > 0 && (
                      <Alert
                        type="warning"
                        showIcon
                        message="复核提醒"
                        description={
                          <Space size={8} wrap>
                            {reviewReminders.map((warning) => (
                              <Tag color="orange" key={warning}>
                                {warning}
                              </Tag>
                            ))}
                          </Space>
                        }
                      />
                    )}

                    <Card size="small" title="关键动作">
                      <Space wrap>
                        <Button icon={<UploadOutlined />} onClick={() => switchActiveTab('entry')}>
                          去录入数据
                        </Button>
                        <Button
                          icon={<LockOutlined />}
                          disabled={!canLock}
                          onClick={() =>
                            handleBatchAction({
                              title: '锁定批次',
                              okText: '确认锁定',
                              successMessage: '批次已锁定',
                              content: '锁定后仍可继续补录导入项，但建议以工作台为准统一管理。',
                              run: () => lockMutation.mutateAsync(batchId as number),
                            })
                          }
                        >
                          锁定批次
                        </Button>
                        <Button
                          icon={<ThunderboltOutlined />}
                          disabled={!canCompute}
                          onClick={() =>
                            handleBatchAction({
                              title: '计算薪酬',
                              okText: '开始计算',
                              successMessage: '薪酬计算已完成',
                              content: '系统会把当前全部导入项重新汇总并生成最新薪酬结果。',
                              run: () => computeMutation.mutateAsync(batchId as number),
                            })
                          }
                        >
                          {status === 'approved' || status === 'rejected' ? '重新计算' : '计算薪酬'}
                        </Button>
                        <Button
                          icon={<CheckCircleOutlined />}
                          disabled={!batchId}
                          onClick={() => navigate(`/payroll/confirmations?batchId=${batchId}`)}
                        >
                          确认工作台
                        </Button>
                        <Button
                          icon={<SendOutlined />}
                          disabled={!batchId}
                          onClick={() => navigate(`/payroll/distributions?batchId=${batchId}`)}
                        >
                          发放单
                        </Button>
                        <Button
                          icon={<AuditOutlined />}
                          disabled={!batchId}
                          onClick={() => navigate(`/payroll/reconciliations?batchId=${batchId}`)}
                        >
                          对账任务
                        </Button>
                        <Button
                          icon={<SendOutlined />}
                          disabled={!canSubmit}
                          onClick={() =>
                            handleBatchAction({
                              title: '提交审批',
                              okText: '确认提交',
                              successMessage: '批次已提交审批',
                              content: blockers.length > 0
                                ? `当前仍存在阻塞问题：${blockers.join('；')}`
                                : '请确认员工确认已完成，且台账金额与导入项一致。',
                              run: () => submitMutation.mutateAsync(batchId as number),
                            })
                          }
                        >
                          提交审批
                        </Button>
                        <Button
                          icon={<RedoOutlined />}
                          disabled={!canRetry}
                          onClick={() =>
                            handleBatchAction({
                              title: '重试发放',
                              okText: '立即重试',
                              successMessage: '已提交失败子集重试请求',
                              content: '系统会针对失败明细重新发放，请先确认收款账户或渠道问题已修复。',
                              run: () =>
                                retryPaymentMutation.mutateAsync({
                                  batchId: batchId as number,
                                  triggerTransfer: true,
                                }),
                            })
                          }
                        >
                          重试发放
                        </Button>
                        <Button
                          icon={<TeamOutlined />}
                          disabled={!batchId}
                          onClick={() => navigate(`/payroll/batches/${batchId}/manager-review`)}
                        >
                          经理核对
                        </Button>
                      </Space>
                    </Card>

                    <Card size="small" title="试算预览">
                      {previewQuery.data ? (
                        <Descriptions column={3} size="small" bordered>
                          <Descriptions.Item label="应发总额">
                            {formatCurrency(
                              previewQuery.data.grossTotal,
                              mergedDetail.currency || 'CNY',
                            )}
                          </Descriptions.Item>
                          <Descriptions.Item label="扣减总额">
                            {formatCurrency(
                              previewQuery.data.deductionsTotal,
                              mergedDetail.currency || 'CNY',
                            )}
                          </Descriptions.Item>
                          <Descriptions.Item label="实发总额">
                            {formatCurrency(
                              previewQuery.data.netTotal,
                              mergedDetail.currency || 'CNY',
                            )}
                          </Descriptions.Item>
                          <Descriptions.Item label="计税总额">
                            {formatCurrency(
                              previewQuery.data.taxTotal,
                              mergedDetail.currency || 'CNY',
                            )}
                          </Descriptions.Item>
                          <Descriptions.Item label="社保总额">
                            {formatCurrency(
                              previewQuery.data.socialTotal,
                              mergedDetail.currency || 'CNY',
                            )}
                          </Descriptions.Item>
                          <Descriptions.Item label="阻塞问题">
                            {previewQuery.data.blockingIssueCount ?? 0}
                          </Descriptions.Item>
                          <Descriptions.Item label="复核提醒">
                            {previewQuery.data.reviewIssueCount ?? 0}
                          </Descriptions.Item>
                        </Descriptions>
                      ) : (
                        <Empty
                          image={Empty.PRESENTED_IMAGE_SIMPLE}
                          description={
                            hasImportData
                              ? '当前状态暂无试算预览，可刷新或先执行计算。'
                              : '录入导入项后，工作台会展示试算汇总。'
                          }
                        />
                      )}
                    </Card>
                  </Space>
                ),
              },
              {
                key: 'entry',
                label: '数据录入台账',
                children: (
                  <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <Alert
                      type={importMutable ? 'info' : 'warning'}
                      showIcon
                      message={importMutable ? '录入入口已开放' : '当前状态不可修改导入数据'}
                      description={
                        importMutable
                          ? '你可以在这里上传 CSV，或直接手工录入单条薪资项。两种方式都会追加到当前批次，并共同参与计算。'
                          : '当前批次已进入只读阶段。如需继续处理，请根据状态前往确认、审批或支付重试环节。'
                      }
                    />                    {lastImportResult && (
                      <Alert
                        type="success"
                        showIcon
                        message="最近一次 CSV 导入结果"
                        description={
                          <Space direction="vertical" size={4}>
                            <Text>总行数：{lastImportSummary?.total ?? 0}</Text>
                            <Text>
                              成功：{lastImportSummary?.valid ?? 0}，失败：
                              {lastImportSummary?.invalid ?? 0}
                            </Text>
                            {lastImportResult.totalEmployees !== undefined && (
                              <Text>预估员工数：{lastImportResult.totalEmployees}</Text>
                            )}
                            {lastImportResult.warningsCount !== undefined && (
                              <Text>预警数：{lastImportResult.warningsCount}</Text>
                            )}
                          </Space>
                        }
                      />
                    )}

                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
                        gap: 12,
                      }}
                    >
                      <Card size="small">
                        <Statistic title="导入项总数" value={importItems.length} />
                      </Card>
                      <Card size="small">
                        <Statistic title="手工录入" value={manualCount} />
                      </Card>
                      <Card size="small">
                        <Statistic title="CSV 导入" value={csvCount} />
                      </Card>
                      <Card size="small">
                        <Statistic title="异常项" value={invalidItemCount} />
                      </Card>
                    </div>

                    <Card size="small" title="CSV 导入">
                      <Space direction="vertical" size={12} style={{ width: '100%' }}>
                        <Text type="secondary">
                          模板首行必须包含：employeeId,itemCode,amount,note。
                        </Text>
                        <Upload.Dragger
                          accept=".csv,text/csv"
                          disabled={!importMutable}
                          maxCount={1}
                          multiple={false}
                          beforeUpload={(file) => {
                            setFileList([
                              {
                                uid: file.uid,
                                name: file.name,
                                status: 'done',
                                originFileObj: file,
                              },
                            ]);
                            return false;
                          }}
                          onRemove={() => {
                            setFileList([]);
                            return true;
                          }}
                          fileList={fileList}
                        >
                          <p className="ant-upload-drag-icon">
                            <InboxOutlined />
                          </p>
                          <p className="ant-upload-text">点击或拖拽 CSV 文件到此处</p>
                          <p className="ant-upload-hint">
                            CSV 为追加导入，建议在下方台账确认导入结果后再继续计算。
                          </p>
                        </Upload.Dragger>
                        <Button
                          type="primary"
                          icon={<UploadOutlined />}
                          disabled={!importMutable || fileList.length === 0}
                          loading={importMutation.isPending}
                          onClick={handleImportCsv}
                        >
                          提交 CSV
                        </Button>
                      </Space>
                    </Card>

                    <Card size="small" title="手动录入">
                      <div
                        ref={(node) => {
                          manualFormMountedRef.current = Boolean(node);
                        }}
                      >
                        <Form form={manualForm} layout="vertical" disabled={!importMutable}>
                          <Form.Item name="employeeId" hidden>
                            <Input />
                          </Form.Item>
                          <div
                            style={{
                              display: 'grid',
                              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                              gap: 12,
                            }}
                          >
                            <Form.Item label="搜索员工" extra={employeeMatchHint}>
                              <AutoComplete
                                allowClear
                                value={employeeSearchKeyword}
                                options={employeeOptions}
                                onSearch={(value) => {
                                  setEmployeeSearchKeyword(value);
                                }}
                                onChange={(value) => {
                                  setEmployeeSearchKeyword(value);
                                }}
                                onSelect={(_, option) => {
                                  const employee = (option as { employee?: Employee }).employee;
                                  if (!employee) {
                                    return;
                                  }
                                  setSelectedEmployee(employee);
                                  setEmployeeSearchKeyword(
                                    `${employee.name || employee.employeeId}（${employee.employeeId}）`,
                                  );
                                  manualForm.setFieldsValue({
                                    employeeId: employee.id,
                                    employeeNo: employee.employeeId,
                                  });
                                }}
                                notFoundContent={
                                  employeeQuery.isFetching ? (
                                    <Spin size="small" />
                                  ) : debouncedEmployeeSearchKeyword ? (
                                    '未找到匹配员工，可继续直接输入工号'
                                  ) : (
                                    '输入姓名或工号搜索员工'
                                  )
                                }
                              >
                                <Input placeholder="输入姓名、工号、手机号或邮箱搜索员工" />
                              </AutoComplete>
                            </Form.Item>
                            <Form.Item
                              name="employeeNo"
                              label="员工工号"
                              rules={[
                                { required: true, message: '请输入员工工号，或先搜索选择员工' },
                              ]}
                            >
                              <Input
                                placeholder="可直接输入员工工号，如：emp-4"
                                onChange={(event) => {
                                  const value = event.target.value;
                                  if (selectedEmployee && value !== selectedEmployee.employeeId) {
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
                                placeholder="请选择薪资项"
                                optionFilterProp="label"
                                options={(salaryItemsQuery.data ?? []).map((item) => ({
                                  label: `${item.name || item.code} (${item.code})`,
                                  value: item.code,
                                }))}
                                loading={salaryItemsQuery.isLoading}
                              />
                            </Form.Item>
                            <Form.Item
                              name="amount"
                              label="金额"
                              rules={[{ required: true, message: '请输入金额' }]}
                            >
                              <InputNumber
                                style={{ width: '100%' }}
                                min={0.01}
                                precision={2}
                                placeholder="请输入金额"
                              />
                            </Form.Item>
                          </div>
                          {selectedEmployee && (
                            <Alert
                              type="success"
                              showIcon
                              message="已选员工"
                              description={
                                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                                  <Text>姓名：{selectedEmployee.name || '—'}</Text>
                                  <Text>工号：{selectedEmployee.employeeId || '—'}</Text>
                                  <Text>部门：{selectedEmployee.department || '未分配部门'}</Text>
                                </Space>
                              }
                              action={
                                <Button
                                  size="small"
                                  onClick={() => {
                                    setSelectedEmployee(null);
                                    setEmployeeSearchKeyword('');
                                    setDebouncedEmployeeSearchKeyword('');
                                    manualForm.setFieldsValue({
                                      employeeId: undefined,
                                      employeeNo: undefined,
                                    });
                                  }}
                                >
                                  清空匹配
                                </Button>
                              }
                            />
                          )}
                          <Form.Item name="note" label="备注">
                            <Input.TextArea rows={3} placeholder="可选，说明该笔调整原因" />
                          </Form.Item>
                          <Button
                            type="primary"
                            loading={addManualMutation.isPending}
                            onClick={handleManualSubmit}
                          >
                            追加录入
                          </Button>
                        </Form>
                      </div>
                    </Card>

                    <Divider style={{ margin: '8px 0 0' }} />
                    <Space direction="vertical" size={16} style={{ width: '100%' }}>
                      <Alert
                        type="info"
                        showIcon
                        message="导入项台账"
                        description="台账会完整展示 CSV / 手工录入记录、异常原因和可编辑字段，方便录入后立即核对、修正和复算。"
                      />

                      {invalidItemCount > 0 ? (
                        <Alert
                          type="warning"
                          showIcon
                          message={`发现 ${invalidItemCount} 条异常导入项`}
                          description="表格已直接展示“异常原因”列，便于批量排查；悬停在“异常”状态标签上可查看完整原因。"
                        />
                      ) : null}

                      <Card size="small" title="来源分布">
                        <Space size={8} wrap>
                          {sourceSummary.length > 0 ? (
                            sourceSummary.map(([source, count]) => (
                              <Tag key={source} color={source === '手动录入' ? 'blue' : 'default'}>
                                {source}：{count}
                              </Tag>
                            ))
                          ) : (
                            <Text type="secondary">暂无导入项</Text>
                          )}
                        </Space>
                      </Card>

                      <Card
                        size="small"
                        title="导入项明细"
                        extra={<Text type="secondary">录入后可直接在此修正行号、员工、薪资项与金额</Text>}
                      >
                        <Table<PayrollImportItemDto>
                          rowKey="id"
                          loading={itemsQuery.isFetching || deleteItemMutation.isPending}
                          columns={itemColumns}
                          dataSource={importItems}
                          pagination={false}
                          scroll={{ x: 1360 }}
                          locale={{ emptyText: '当前批次还没有导入项' }}
                        />
                      </Card>
                    </Space>
                  </Space>
                ),
              },
            ]}
          />

          <Divider style={{ margin: 0 }} />
          <Text type="secondary">
            工作台已拆分为“流程看板 + 数据录入台账”两块视图，便于按阶段推进并在录入后立即核对明细。
          </Text>
        </Space>
      </Spin>
      <Modal
        title={`编辑导入项${editingItem?.rowNo ? ` #${editingItem.rowNo}` : ''}`}
        open={!!editingItem}
        forceRender
        onOk={handleEditItemSubmit}
        onCancel={() => {
          setEditingItem(null);
          editItemForm.resetFields();
        }}
        confirmLoading={updateItemMutation.isPending}
        okText="保存修改"
        cancelText="取消"
        destroyOnClose={false}
      >
        <Alert
          type="info"
          showIcon
          message="可修改当前导入项"
          description="支持修正行号、员工工号、薪资项、金额和备注；来源、状态与录入时间保留为系统记录。"
          style={{ marginBottom: 16 }}
        />
        <Form form={editItemForm} layout="vertical">
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
              gap: 12,
            }}
          >
            <Form.Item
              name="rowNo"
              label="行号"
              rules={[{ required: true, message: '请输入行号' }]}
            >
              <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="如：1" />
            </Form.Item>
            <Form.Item
              name="employeeNo"
              label="员工工号"
              rules={[{ required: true, message: '请输入员工工号' }]}
            >
              <Input placeholder="如：emp-4" />
            </Form.Item>
            <Form.Item
              name="itemCode"
              label="薪资项"
              rules={[{ required: true, message: '请选择薪资项' }]}
            >
              <Select
                showSearch
                placeholder="请选择薪资项"
                optionFilterProp="label"
                options={(salaryItemsQuery.data ?? []).map((item) => ({
                  label: `${item.name || item.code} (${item.code})`,
                  value: item.code,
                }))}
                loading={salaryItemsQuery.isLoading}
              />
            </Form.Item>
            <Form.Item
              name="amount"
              label="金额"
              rules={[{ required: true, message: '请输入金额' }]}
            >
              <InputNumber
                style={{ width: '100%' }}
                min={0.01}
                precision={2}
                placeholder="请输入金额"
              />
            </Form.Item>
          </div>
          <Form.Item name="note" label="备注">
            <Input.TextArea rows={3} placeholder="可选，说明本次修正原因" />
          </Form.Item>
          <Descriptions column={2} size="small" bordered>
            <Descriptions.Item label="来源">
              {formatImportSource(editingItem?.sourceName, editingItem?.manual)}
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              {renderImportStatusTag(editingItem?.status, editingItem?.errorMsg)}
            </Descriptions.Item>
            <Descriptions.Item label="录入时间">
              {formatDateTime(editingItem?.createTime)}
            </Descriptions.Item>
            <Descriptions.Item label="最近更新时间">
              {formatDateTime(editingItem?.updateTime)}
            </Descriptions.Item>
            {editingItem?.errorMsg ? (
              <Descriptions.Item label="异常原因" span={2}>
                {editingItem.errorMsg}
              </Descriptions.Item>
            ) : null}
          </Descriptions>
        </Form>
      </Modal>
    </Drawer>
  );
}

export default BatchWorkspaceDrawer;
