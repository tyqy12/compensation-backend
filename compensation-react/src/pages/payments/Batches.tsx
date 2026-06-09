import React, { useState, useRef, useCallback, useMemo } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Button,
  Space,
  Tag,
  Typography,
  Progress,
  Statistic,
  Card,
  App as AntdApp,
  Drawer,
  Table,
  Alert,
  Dropdown,
  Select,
  Spin,
  Tooltip,
  Row,
  Col,
} from 'antd';
import {
  PageContainer,
  ProTable,
  type ProColumns,
  type ActionType,
  type ProFormInstance,
} from '@ant-design/pro-components';
import {
  PlayCircleOutlined,
  ReloadOutlined,
  ExclamationCircleOutlined,
  DollarOutlined,
  TeamOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  ExportOutlined,
  MoreOutlined,
  RedoOutlined,
  FileSearchOutlined,
} from '@ant-design/icons';
import {
  checkBatchTransfer,
  fetchPaymentBatches,
  type TransferValidationIssue,
  usePaymentBatchesQuery,
  usePaymentBatchQuery,
  usePaymentRecordsQuery,
  useStartBatchTransferMutation,
  useRetryFailedRecordsMutation,
  type PaymentBatch,
  type PaymentBatchListResponse,
  type PaymentBatchQueryParams,
  type PaymentRecord,
  getBatchStatusInfo,
  getPaymentRecordStatusInfo,
  getPaymentTypeInfo,
  formatAmount,
  calculateBatchProgress,
  calculateBatchSuccessRate,
  isStartablePaymentBatch,
} from '@services/queries/paymentBatch';
import dayjs from 'dayjs';
import { useHasAction } from '@services/queries/rbac';
import { withActionPrefix } from '@utils/error';

const { Text } = Typography;

// ==================== 枚举定义 ====================
const statusEnum: Record<string, { text: string; status: string; color: string }> = {
  draft: { text: '草稿', status: 'Default', color: 'default' },
  submitted: { text: '已提交', status: 'Processing', color: 'processing' },
  approved: { text: '已审批', status: 'Success', color: 'success' },
  processing: { text: '处理中', status: 'Warning', color: 'warning' },
  completed: { text: '已完成', status: 'Success', color: 'success' },
  failed: { text: '失败', status: 'Error', color: 'error' },
};

const paymentTypeEnum: Record<string, { text: string; color: string }> = {
  salary: { text: '工资', color: 'blue' },
  bonus: { text: '奖金', color: 'gold' },
  reimbursement: { text: '报销', color: 'purple' },
};

// ==================== 工具函数 ====================
const formatDateTime = (value?: string): string =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—';

const formatSimpleDate = (value?: string): string =>
  value ? dayjs(value).format('MM-DD HH:mm') : '—';

// ==================== 主组件 ====================
const PaymentBatches: React.FC = () => {
  const { message, modal } = AntdApp.useApp();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  // URL 参数解析
  const parsePositiveInt = (raw: string | null | undefined, fallback: number): number => {
    const parsed = Number.parseInt(String(raw ?? ''), 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
  };

  const [queryParams, setQueryParams] = useState<PaymentBatchQueryParams>(() => ({
    current: parsePositiveInt(searchParams.get('current') ?? searchParams.get('page'), 1),
    pageSize: parsePositiveInt(searchParams.get('pageSize') ?? searchParams.get('size'), 10),
    keyword: searchParams.get('keyword') || undefined,
    status: (searchParams.get('status') as any) || undefined,
    paymentType: (searchParams.get('paymentType') as any) || undefined,
    startDate: searchParams.get('startDate') || undefined,
    endDate: searchParams.get('endDate') || undefined,
    sortBy: searchParams.get('sortBy') || 'submitTime',
    order: (searchParams.get('order') as 'asc' | 'desc') || 'desc',
  }));
  const [tableResult, setTableResult] = useState<PaymentBatchListResponse | null>(null);

  // 选中状态
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  // Drawer 状态
  const [drawerBatchNo, setDrawerBatchNo] = useState<string | null>(null);
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [recordFilters, setRecordFilters] = useState({ status: '' as string });

  // 核心引用
  const actionRef = useRef<ActionType>();
  const formRef = useRef<ProFormInstance>();

  // 权限
  const canStart = useHasAction('api.payment.batch.start');
  const canRetry = useHasAction('api.payment.record.retry');

  // 查询
  const batchesQuery = usePaymentBatchesQuery(queryParams);
  const batchDetailQuery = usePaymentBatchQuery(drawerBatchNo ?? '', {
    enabled: !!drawerBatchNo,
  });
  const recordsQuery = usePaymentRecordsQuery(
    { batchNo: drawerBatchNo ?? '', status: recordFilters.status || undefined },
    { enabled: !!drawerBatchNo },
  );

  // Mutations
  const startTransferMutation = useStartBatchTransferMutation();
  const retryMutation = useRetryFailedRecordsMutation();

  // ==================== URL 同步 ====================
  const updateUrlParams = useCallback((params: PaymentBatchQueryParams) => {
    const newSearchParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '' && value !== null) {
        newSearchParams.set(key, String(value));
      }
    });
    setSearchParams(newSearchParams);
  }, [setSearchParams]);

  // ==================== 统计数据 ====================
  const summary = useMemo(() => {
    const list = tableResult?.list ?? batchesQuery.data?.list ?? [];
    const total = tableResult?.total ?? batchesQuery.data?.total ?? list.length;
    const processing = list.filter((b: PaymentBatch) => b.status === 'processing').length;
    const completed = list.filter((b: PaymentBatch) => b.status === 'completed').length;
    const failed = list.filter((b: PaymentBatch) => b.status === 'failed').length;
    const readyToStart = list.filter(isStartablePaymentBatch).length;
    const totalAmount = list.reduce((acc: number, b: PaymentBatch) => acc + (b.totalAmount || 0), 0);
    const totalCount = list.reduce((acc: number, b: PaymentBatch) => acc + (b.totalCount || 0), 0);
    return { total, processing, completed, failed, readyToStart, totalAmount, totalCount };
  }, [batchesQuery.data, tableResult]);

  const summaryCards = useMemo(
    () => [
      {
        key: 'total',
        title: '总批次数',
        value: summary.total,
        prefix: <TeamOutlined />,
      },
      {
        key: 'approved',
        title: '待启动批次',
        value: summary.readyToStart,
        prefix: <PlayCircleOutlined />,
        valueStyle: { color: '#1677ff' },
      },
      {
        key: 'processing',
        title: '处理中批次',
        value: summary.processing,
        prefix: <SyncOutlined />,
        valueStyle: { color: '#fa8c16' },
      },
      {
        key: 'completed',
        title: '已完成批次',
        value: summary.completed,
        prefix: <CheckCircleOutlined />,
        valueStyle: { color: '#52c41a' },
      },
      {
        key: 'failed',
        title: '失败批次',
        value: summary.failed,
        prefix: <CloseCircleOutlined />,
        valueStyle: { color: '#ff4d4f' },
      },
      {
        key: 'amount',
        title: '总支付金额',
        value: formatAmount(summary.totalAmount),
        prefix: <DollarOutlined />,
        valueStyle: { fontSize: 16 },
      },
      {
        key: 'count',
        title: '总支付笔数',
        value: summary.totalCount,
        prefix: <TeamOutlined />,
      },
    ],
    [summary],
  );

  // ==================== 操作处理 ====================
  const showBlockedValidationModal = useCallback((batchNo: string, issues: TransferValidationIssue[]) => {
    modal.error({
      title: `批次 ${batchNo} 校验未通过`,
      width: 680,
      content: (
        <div>
          <Alert
            type="error"
            showIcon
            message={`检测到 ${issues.length} 条高风险记录，已拦截发放`}
            description="请先修复员工结算账户后再启动转账"
            style={{ marginBottom: 12 }}
          />
          <div style={{ maxHeight: 320, overflowY: 'auto' }}>
            {issues.slice(0, 10).map((issue, index) => (
              <div key={`${issue.recordId || index}-${index}`} style={{ marginBottom: 8 }}>
                <Text>
                  {index + 1}. {issue.employeeName || '-'} / {issue.recipientAccountMasked || '-'} / {issue.errorMsg || '校验失败'}
                </Text>
              </div>
            ))}
            {issues.length > 10 && (
              <Text type="secondary">仅展示前 10 条，请进入记录列表查看完整失败原因。</Text>
            )}
          </div>
        </div>
      ),
    });
  }, [modal]);

  const handleStartTransfer = useCallback(async (batch: PaymentBatch) => {
    if (!isStartablePaymentBatch(batch)) {
      message.warning('只能启动已提交或已审批的批次');
      return;
    }

    modal.confirm({
      title: '启动批次转账',
      content: (
        <div>
          <p>确定要启动批次 <Text code>{batch.batchNo}</Text> 的转账操作吗？</p>
          <div style={{ marginTop: 8 }}>
            <Text type="secondary">批次名称：{batch.batchName}</Text><br />
            <Text type="secondary">支付总额：{formatAmount(batch.totalAmount)}</Text><br />
            <Text type="secondary">支付笔数：{batch.totalCount} 笔</Text>
          </div>
        </div>
      ),
      icon: <ExclamationCircleOutlined />,
      onOk: async () => {
        try {
          const validation = await checkBatchTransfer(batch.batchNo, true);
          if (!validation.pass) {
            showBlockedValidationModal(batch.batchNo, validation.blockedRecords || []);
            actionRef.current?.reload();
            return;
          }
          await startTransferMutation.mutateAsync(batch.batchNo);
          message.success('批次转账已启动，正在后台处理');
          actionRef.current?.reload();
        } catch (error: any) {
          message.error(withActionPrefix('启动失败', error));
        }
      },
    });
  }, [startTransferMutation, message, modal, showBlockedValidationModal]);

  const handleBatchStart = useCallback(async () => {
    const sourceBatches = tableResult?.list ?? batchesQuery.data?.list ?? [];
    const selectedBatches = sourceBatches.filter(
      (b: PaymentBatch) => selectedRowKeys.includes(b.batchNo) && isStartablePaymentBatch(b),
    );

    if (selectedBatches.length === 0) {
      message.warning('请选择已提交或已审批的批次');
      return;
    }

    modal.confirm({
      title: '批量启动转账',
      content: (
        <div>
          <p>确定要启动选中的 <Text strong>{selectedBatches.length}</Text> 个批次吗？</p>
          <div style={{ marginTop: 8 }}>
            <Text type="secondary">涉及总额：{formatAmount(selectedBatches.reduce((acc: number, b: PaymentBatch) => acc + (b.totalAmount || 0), 0))}</Text><br />
            <Text type="secondary">涉及笔数：{selectedBatches.reduce((acc: number, b: PaymentBatch) => acc + (b.totalCount || 0), 0)} 笔</Text>
          </div>
        </div>
      ),
      icon: <ExclamationCircleOutlined />,
      onOk: async () => {
        try {
          const blockedBatches: Array<{ batchNo: string; issues: TransferValidationIssue[] }> = [];
          let startedCount = 0;

          for (const batch of selectedBatches) {
            const validation = await checkBatchTransfer(batch.batchNo, true);
            if (!validation.pass) {
              blockedBatches.push({
                batchNo: batch.batchNo,
                issues: validation.blockedRecords || [],
              });
              continue;
            }
            await startTransferMutation.mutateAsync(batch.batchNo);
            startedCount++;
          }

          if (blockedBatches.length > 0) {
            const firstBlocked = blockedBatches[0];
            showBlockedValidationModal(firstBlocked.batchNo, firstBlocked.issues);
            if (startedCount > 0) {
              message.warning(`已启动 ${startedCount} 个批次，拦截 ${blockedBatches.length} 个高风险批次`);
            } else {
              message.error(`全部批次校验未通过，共拦截 ${blockedBatches.length} 个批次`);
            }
          } else {
            message.success(`已启动 ${startedCount} 个批次的转账`);
          }

          setSelectedRowKeys([]);
          actionRef.current?.reload();
        } catch (error: any) {
          message.error(withActionPrefix('批量启动失败', error));
        }
      },
    });
  }, [selectedRowKeys, tableResult, batchesQuery.data, startTransferMutation, message, modal, showBlockedValidationModal]);

  const handleViewDetail = useCallback((batch: PaymentBatch) => {
    setDrawerBatchNo(batch.batchNo);
    setDrawerVisible(true);
  }, []);

  const buildDetailPath = useCallback((batchNo: string) => {
    const queryString = searchParams.toString();
    return queryString ? `/payments/batches/${batchNo}?${queryString}` : `/payments/batches/${batchNo}`;
  }, [searchParams]);

  const handleOpenDetailPage = useCallback((batch: PaymentBatch) => {
    navigate(buildDetailPath(batch.batchNo));
  }, [navigate, buildDetailPath]);

  const handleRetryFailed = useCallback(async (batch: PaymentBatch) => {
    modal.confirm({
      title: '重试失败记录',
      content: `确定要重试批次 ${batch.batchNo} 中的失败记录吗？`,
      icon: <RedoOutlined />,
      onOk: async () => {
        try {
          await retryMutation.mutateAsync({ batchNo: batch.batchNo });
          message.success('重试任务已提交');
          batchDetailQuery.refetch();
          recordsQuery.refetch();
        } catch (error: any) {
          message.error(withActionPrefix('重试失败', error));
        }
      },
    });
  }, [retryMutation, message, modal, batchDetailQuery, recordsQuery]);

  const handleExport = useCallback(() => {
    message.info('导出功能开发中');
  }, [message]);

  // ==================== 表格列定义 ====================
  const columns: ProColumns<PaymentBatch>[] = [
    {
      title: '关键字',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '搜索批次号或批次名称' },
    },
    {
      title: '批次号',
      dataIndex: 'batchNo',
      width: 150,
      copyable: true,
      render: (_, record) => (
        <Link to={buildDetailPath(record.batchNo)}>
          <Text code>{record.batchNo}</Text>
        </Link>
      ),
    },
    {
      title: '批次名称',
      dataIndex: 'batchName',
      ellipsis: true,
      render: (_, record) => (
        <div>
          <Text strong>{record.batchName}</Text>
          {record.remark && (
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {record.remark}
              </Text>
            </div>
          )}
        </div>
      ),
    },
    {
      title: '支付类型',
      dataIndex: 'paymentType',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(paymentTypeEnum).map(([k, v]) => [k, { text: v.text }]),
      ),
      render: (_, record) => {
        const meta = paymentTypeEnum[record.paymentType ?? ''];
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : record.paymentType;
      },
    },
    {
      title: '批次状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(statusEnum).map(([k, v]) => [k, { text: v.text }]),
      ),
      render: (_, record) => {
        const meta = statusEnum[record.status ?? ''];
        return meta ? (
          <Tag color={meta.color}>{meta.text}</Tag>
        ) : (
          record.status
        );
      },
    },
    {
      title: '提交时间',
      dataIndex: 'submitRange',
      valueType: 'dateRange',
      hideInTable: true,
      search: {
        transform: (value) => ({
          startDate: value?.[0]?.format('YYYY-MM-DD'),
          endDate: value?.[1]?.format('YYYY-MM-DD'),
        }),
      },
    },
    {
      title: '金额统计',
      dataIndex: 'totalAmount',
      width: 150,
      search: false,
      render: (_, record) => (
        <div>
          <Statistic
            value={record.totalAmount}
            formatter={(value) => formatAmount(Number(value))}
            valueStyle={{ fontSize: 14 }}
          />
          <Text type="secondary" style={{ fontSize: 12 }}>
            共 {record.totalCount} 笔
          </Text>
        </div>
      ),
    },
    {
      title: '处理进度',
      dataIndex: 'progress',
      width: 130,
      search: false,
      render: (_, record) => {
        const progress = calculateBatchProgress(record);
        const successRate = calculateBatchSuccessRate(record);

        return (
          <div>
            <Progress
              percent={progress}
              size="small"
              status={record.status === 'failed' ? 'exception' : 'active'}
            />
            <div style={{ fontSize: 12, marginTop: 2 }}>
              <Text type="secondary">
                成功: {record.successCount} / 失败: {record.failedCount}
              </Text>
              {progress > 0 && (
                <div>
                  <Text type="secondary">成功率: {successRate}%</Text>
                </div>
              )}
            </div>
          </div>
        );
      },
    },
    {
      title: '时间信息',
      dataIndex: 'timeInfo',
      width: 160,
      search: false,
      render: (_, record) => (
        <div style={{ fontSize: 12 }}>
          {record.submitTime && (
            <div>
              <Text type="secondary">提交: {formatSimpleDate(record.submitTime)}</Text>
            </div>
          )}
          {record.processEndTime && (
            <div>
              <Text type="secondary">完成: {formatSimpleDate(record.processEndTime)}</Text>
            </div>
          )}
        </div>
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 180,
      render: (_, record) => {
        const items = [
          {
            key: 'detail-page',
            label: '打开详情页',
            icon: <FileSearchOutlined />,
            onClick: () => handleOpenDetailPage(record),
          },
        ];

        if (isStartablePaymentBatch(record) && canStart) {
          items.push({
            key: 'start',
            label: '启动转账',
            icon: <PlayCircleOutlined />,
            onClick: () => handleStartTransfer(record),
          });
        }

        if (record.failedCount && record.failedCount > 0 && canRetry) {
          items.push({
            key: 'retry',
            label: '重试失败',
            icon: <RedoOutlined />,
            onClick: () => handleRetryFailed(record),
          });
        }

        return [
          <Button key="preview" type="link" size="small" onClick={() => handleViewDetail(record)}>
            预览
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

  const isLoading = batchesQuery.isLoading || startTransferMutation.isPending;

  const resolveProviderOrderNo = useCallback((record: PaymentRecord): string => {
    return record.providerOrderNo || record.alipayOrderNo || '';
  }, []);

  const resolveProviderTradeNo = useCallback((record: PaymentRecord): string => {
    return record.providerTradeNo || record.alipayTradeNo || '';
  }, []);

  const resolveFailureText = useCallback((record: PaymentRecord): string => {
    if (!record.errorMsg) {
      return '';
    }
    return `${record.errorCode ? `[${record.errorCode}] ` : ''}${record.errorMsg}`;
  }, []);

  const renderProviderTag = useCallback((providerCode?: string) => {
    const normalizedCode = (providerCode || 'alipay').trim().toLowerCase();
    if (normalizedCode === 'alipay') {
      return <Tag color="blue">支付宝</Tag>;
    }
    if (normalizedCode === 'yunzhanghu') {
      return <Tag color="geekblue">云账户</Tag>;
    }
    return <Tag>{normalizedCode || '-'}</Tag>;
  }, []);

  // ==================== 记录表格列 ====================
  const recordColumns: ProColumns<PaymentRecord>[] = [
    {
      title: '记录ID',
      dataIndex: 'id',
      width: 90,
    },
    {
      title: '员工',
      dataIndex: 'employeeName',
      width: 150,
      render: (_, record) => record.employeeName || `#${record.employeeId ?? '-'}`,
    },
    {
      title: '收款人',
      dataIndex: 'recipientName',
      width: 120,
      render: (_, record) => record.recipientName || '-',
    },
    {
      title: '收款账号',
      dataIndex: 'recipientAccountMasked',
      width: 170,
      render: (_, record) => record.recipientAccountMasked || '-',
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 120,
      render: (_, record) => formatAmount(record.amount ?? 0, record.currency || 'CNY'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = getPaymentRecordStatusInfo(record.status);
        return <Tag color={statusInfo.color}>{statusInfo.text}</Tag>;
      },
    },
    {
      title: '打款渠道',
      dataIndex: 'providerCode',
      width: 120,
      render: (_, record) => renderProviderTag(record.providerCode),
    },
    {
      title: '商户订单号',
      dataIndex: 'providerOrderNo',
      width: 180,
      copyable: true,
      ellipsis: true,
      render: (_, record) => {
        const providerOrderNo = resolveProviderOrderNo(record);
        return providerOrderNo || '-';
      },
    },
    {
      title: '渠道流水号',
      dataIndex: 'providerTradeNo',
      width: 200,
      copyable: true,
      ellipsis: true,
      render: (_, record) => {
        const providerTradeNo = resolveProviderTradeNo(record);
        return providerTradeNo ? <Text code>{providerTradeNo}</Text> : <Text type="secondary">-</Text>;
      },
    },
    {
      title: '失败原因',
      dataIndex: 'errorMsg',
      width: 240,
      ellipsis: true,
      render: (_, record) => {
        const failureText = resolveFailureText(record);
        if (!failureText) {
          return '-';
        }
        return (
          <Tooltip title={failureText}>
            <Text type="danger" ellipsis>
              {failureText}
            </Text>
          </Tooltip>
        );
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 160,
      render: (_, record) => formatDateTime(record.createTime),
    },
  ];

  // ==================== 渲染 ====================
  return (
    <PageContainer
      header={{
        title: '支付批次管理',
        subTitle: '管理批量支付操作和转账状态',
      }}
      content={(
        <Row gutter={[16, 16]}>
          {summaryCards.map((item) => (
            <Col key={item.key} xs={24} sm={12} md={8} lg={6} xl={6} xxl={4}>
              <Card size="small">
                <Statistic
                  title={item.title}
                  value={item.value}
                  prefix={item.prefix}
                  valueStyle={item.valueStyle}
                />
              </Card>
            </Col>
          ))}
        </Row>
      )}
    >
      <ProTable<PaymentBatch>
        cardBordered
        headerTitle="批次列表"
        columns={columns}
        actionRef={actionRef}
        formRef={formRef}
        request={async (params) => {
          const newParams: PaymentBatchQueryParams = {
            current: params.current || 1,
            pageSize: params.pageSize || 10,
            keyword: params.keyword,
            status: params.status,
            paymentType: params.paymentType,
            startDate: params.startDate,
            endDate: params.endDate,
            sortBy: Object.keys(params.sort || {})[0] || 'submitTime',
            order:
              Object.keys(params.sort || {}).length > 0
                ? Object.values(params.sort || {})[0] === 'ascend'
                  ? 'asc'
                  : 'desc'
                : 'desc',
          };

          setQueryParams(newParams);
          updateUrlParams(newParams);

          try {
            const result = await fetchPaymentBatches(newParams);
            setTableResult(result);
            return {
              data: result.list,
              success: true,
              total: result.total || 0,
            };
          } catch {
            return { data: [], success: false, total: 0 };
          }
        }}
        rowKey="batchNo"
        search={{
          labelWidth: 'auto',
          searchText: '搜索',
          resetText: '重置',
          collapseRender: false,
          optionRender: (_, __, dom) => dom,
        }}
        pagination={{
          pageSize: queryParams.pageSize,
          current: queryParams.current,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条/共 ${total} 条`,
        }}
        rowSelection={
          canStart
            ? {
                selectedRowKeys,
                onChange: setSelectedRowKeys,
                getCheckboxProps: (record: PaymentBatch) => ({
                  disabled: !isStartablePaymentBatch(record) || isLoading,
                }),
              }
            : undefined
        }
        tableAlertRender={
          selectedRowKeys.length > 0 && canStart
            ? ({ selectedRowKeys: keys }) => (
                <Space>
                  <span>已选择 {keys.length} 项</span>
                  <Button
                    type="primary"
                    size="small"
                    icon={<PlayCircleOutlined />}
                    onClick={handleBatchStart}
                    loading={startTransferMutation.isPending}
                  >
                    批量启动
                  </Button>
                  <Button size="small" onClick={() => setSelectedRowKeys([])}>
                    取消
                  </Button>
                </Space>
              )
            : undefined
        }
        loading={isLoading}
        locale={{
          emptyText: '暂无支付批次',
        }}
        toolBarRender={() => [
          canStart && (
            <Button
              key="batch-start"
              type="primary"
              icon={<PlayCircleOutlined />}
              onClick={handleBatchStart}
              disabled={selectedRowKeys.length === 0}
              loading={startTransferMutation.isPending}
            >
              批量启动
            </Button>
          ),
          <Button key="export" icon={<ExportOutlined />} onClick={handleExport}>
            导出
          </Button>,
        ].filter(Boolean) as React.ReactNode[]}
        options={{ reload: true, density: true, setting: true }}
        scroll={{ x: 1400 }}
      />

      {/* 批次详情 Drawer */}
      <Drawer
        title={
          <Space>
            <FileSearchOutlined />
            批次详情 - {drawerBatchNo}
          </Space>
        }
        width={800}
        open={drawerVisible}
        onClose={() => {
          setDrawerVisible(false);
          setDrawerBatchNo(null);
        }}
        destroyOnHidden
      >
        <Spin spinning={batchDetailQuery.isLoading}>
          {batchDetailQuery.data && (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              {/* 基本信息 */}
              <Card size="small" title="基本信息">
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 16 }}>
                  <div>
                    <Text type="secondary">批次号：</Text>
                    <Text code>{batchDetailQuery.data.batchNo}</Text>
                  </div>
                  <div>
                    <Text type="secondary">批次名称：</Text>
                    <Text strong>{batchDetailQuery.data.batchName}</Text>
                  </div>
                  <div>
                    <Text type="secondary">支付类型：</Text>
                    <Tag color={paymentTypeEnum[batchDetailQuery.data.paymentType ?? '']?.color}>
                      {paymentTypeEnum[batchDetailQuery.data.paymentType ?? '']?.text ||
                        batchDetailQuery.data.paymentType}
                    </Tag>
                  </div>
                  <div>
                    <Text type="secondary">状态：</Text>
                    <Tag color={statusEnum[batchDetailQuery.data.status ?? '']?.color}>
                      {statusEnum[batchDetailQuery.data.status ?? '']?.text || batchDetailQuery.data.status}
                    </Tag>
                  </div>
                  <div>
                    <Text type="secondary">支付总额：</Text>
                    <Text strong style={{ color: '#52c41a' }}>
                      {formatAmount(batchDetailQuery.data.totalAmount)}
                    </Text>
                  </div>
                  <div>
                    <Text type="secondary">支付笔数：</Text>
                    <Text strong>{batchDetailQuery.data.totalCount} 笔</Text>
                  </div>
                </div>
              </Card>

              {/* 处理进度 */}
              <Card size="small" title="处理进度">
                <div style={{ marginBottom: 16 }}>
                  <Progress
                    percent={calculateBatchProgress(batchDetailQuery.data)}
                    status={batchDetailQuery.data.status === 'failed' ? 'exception' : 'active'}
                  />
                </div>
                <Space size={32}>
                  <Statistic title="成功" value={batchDetailQuery.data.successCount} valueStyle={{ color: '#52c41a' }} />
                  <Statistic title="失败" value={batchDetailQuery.data.failedCount} valueStyle={{ color: '#ff4d4f' }} />
                  <Statistic title="待处理" value={batchDetailQuery.data.pendingCount} valueStyle={{ color: '#1890ff' }} />
                  <Statistic title="成功率" value={`${calculateBatchSuccessRate(batchDetailQuery.data)}%`} />
                </Space>
                {batchDetailQuery.data.failedCount && batchDetailQuery.data.failedCount > 0 && canRetry && (
                  <Alert
                    type="warning"
                    showIcon
                    message="有失败记录"
                    description={
                      <Button
                        type="primary"
                        size="small"
                        icon={<RedoOutlined />}
                        onClick={() => handleRetryFailed(batchDetailQuery.data)}
                        loading={retryMutation.isPending}
                      >
                        重试失败记录
                      </Button>
                    }
                    style={{ marginTop: 16 }}
                  />
                )}
              </Card>

              {/* 时间线 */}
              <Card size="small" title="时间线">
                <Space direction="vertical" size={8} style={{ width: '100%' }}>
                  {batchDetailQuery.data.submitTime && (
                    <div>
                      <Tag color="blue">提交</Tag>
                      <Text>{formatDateTime(batchDetailQuery.data.submitTime)}</Text>
                    </div>
                  )}
                  {batchDetailQuery.data.approveTime && (
                    <div>
                      <Tag color="green">审批</Tag>
                      <Text>{formatDateTime(batchDetailQuery.data.approveTime)}</Text>
                    </div>
                  )}
                  {batchDetailQuery.data.processStartTime && (
                    <div>
                      <Tag color="orange">开始处理</Tag>
                      <Text>{formatDateTime(batchDetailQuery.data.processStartTime)}</Text>
                    </div>
                  )}
                  {batchDetailQuery.data.processEndTime && (
                    <div>
                      <Tag color={batchDetailQuery.data.status === 'failed' ? 'red' : 'green'}>
                        {batchDetailQuery.data.status === 'failed' ? '处理失败' : '处理完成'}
                      </Tag>
                      <Text>{formatDateTime(batchDetailQuery.data.processEndTime)}</Text>
                    </div>
                  )}
                </Space>
              </Card>

              {/* 支付记录列表 */}
              <Card
                size="small"
                title="支付记录"
                extra={
                  <Space>
                    <Select
                      size="small"
                      style={{ width: 100 }}
                      value={recordFilters.status}
                      onChange={(v) => setRecordFilters({ status: v })}
                      options={[
                        { label: '全部', value: '' },
                        { label: '成功', value: 'success' },
                        { label: '失败', value: 'failed' },
                        { label: '待处理', value: 'pending' },
                      ]}
                    />
                    <Button size="small" icon={<ReloadOutlined />} onClick={() => recordsQuery.refetch()}>
                      刷新
                    </Button>
                  </Space>
                }
              >
                <Table
                  columns={recordColumns as any}
                  dataSource={recordsQuery.data || []}
                  rowKey="id"
                  pagination={{ pageSize: 5, size: 'small' }}
                  loading={recordsQuery.isLoading}
                  size="small"
                />
              </Card>
            </Space>
          )}
        </Spin>
      </Drawer>
    </PageContainer>
  );
};

export default PaymentBatches;
