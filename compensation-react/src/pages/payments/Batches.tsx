import React, { useState, useRef, useCallback, useMemo } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import {
  Button,
  Space,
  Tag,
  Typography,
  Progress,
  Statistic,
  Card,
  Tooltip,
  App as AntdApp,
  Modal,
  Drawer,
  Table,
  Alert,
  Dropdown,
  message,
  DatePicker,
  Input,
  Select,
  Spin,
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
  EyeOutlined,
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
  usePaymentBatchesQuery,
  usePaymentBatchQuery,
  usePaymentRecordsQuery,
  useStartBatchTransferMutation,
  useRetryFailedRecordsMutation,
  type PaymentBatch,
  type PaymentBatchQueryParams,
  type PaymentRecord,
  getBatchStatusInfo,
  getPaymentTypeInfo,
  formatAmount,
  calculateBatchProgress,
  calculateBatchSuccessRate,
} from '@services/queries/paymentBatch';
import dayjs from 'dayjs';
import { useHasAction } from '@services/queries/rbac';

const { Text, Title } = Typography;
const { RangePicker } = DatePicker;

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
    const list = batchesQuery.data?.list || [];
    const total = batchesQuery.data?.total || 0;
    const processing = list.filter((b: PaymentBatch) => b.status === 'processing').length;
    const completed = list.filter((b: PaymentBatch) => b.status === 'completed').length;
    const failed = list.filter((b: PaymentBatch) => b.status === 'failed').length;
    const approved = list.filter((b: PaymentBatch) => b.status === 'approved').length;
    const totalAmount = list.reduce((acc: number, b: PaymentBatch) => acc + (b.totalAmount || 0), 0);
    const totalCount = list.reduce((acc: number, b: PaymentBatch) => acc + (b.totalCount || 0), 0);
    return { total, processing, completed, failed, approved, totalAmount, totalCount };
  }, [batchesQuery.data]);

  // ==================== 操作处理 ====================
  const handleStartTransfer = useCallback(async (batch: PaymentBatch) => {
    if (batch.status !== 'approved') {
      message.warning('只能启动已审批的批次');
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
          await startTransferMutation.mutateAsync(batch.batchNo);
          message.success('批次转账已启动，正在后台处理');
          actionRef.current?.reload();
        } catch (error: any) {
          message.error(`启动失败：${error.message || '网络错误'}`);
        }
      },
    });
  }, [startTransferMutation, message, modal]);

  const handleBatchStart = useCallback(async () => {
    const selectedBatches = batchesQuery.data?.list?.filter(
      (b: PaymentBatch) => selectedRowKeys.includes(b.batchNo) && b.status === 'approved',
    ) || [];

    if (selectedBatches.length === 0) {
      message.warning('请选择已审批的批次');
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
          for (const batch of selectedBatches) {
            await startTransferMutation.mutateAsync(batch.batchNo);
          }
          message.success(`已启动 ${selectedBatches.length} 个批次的转账`);
          setSelectedRowKeys([]);
          actionRef.current?.reload();
        } catch (error: any) {
          message.error(`批量启动失败：${error.message || '网络错误'}`);
        }
      },
    });
  }, [selectedRowKeys, batchesQuery.data, startTransferMutation, message, modal]);

  const handleViewDetail = useCallback((batch: PaymentBatch) => {
    setDrawerBatchNo(batch.batchNo);
    setDrawerVisible(true);
  }, []);

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
          message.error(`重试失败：${error.message || '网络错误'}`);
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
        <Link to={`/payments/batches/${record.batchNo}`}>
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
            key: 'detail',
            label: '查看详情',
            icon: <EyeOutlined />,
            onClick: () => handleViewDetail(record),
          },
        ];

        if (record.status === 'approved' && canStart) {
          items.unshift({
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
          <Button key="detail" type="link" size="small" onClick={() => handleViewDetail(record)}>
            详情
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

  // ==================== 记录表格列 ====================
  const recordColumns: ProColumns<PaymentRecord>[] = [
    {
      title: '记录号',
      dataIndex: 'recordNo',
      width: 180,
      copyable: true,
    },
    {
      title: '员工ID',
      dataIndex: 'employeeId',
      width: 100,
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 120,
      render: (_, record) => formatAmount(record.amount),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const color = record.status === 'SUCCESS' ? 'success' : record.status === 'FAILED' ? 'error' : 'processing';
        const text = record.status === 'SUCCESS' ? '成功' : record.status === 'FAILED' ? '失败' : '处理中';
        return <Tag color={color}>{text}</Tag>;
      },
    },
    {
      title: '失败原因',
      dataIndex: 'message',
      width: 200,
      ellipsis: true,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 160,
      render: (_, record) => formatDateTime(record.createdAt),
    },
  ];

  // ==================== 渲染 ====================
  return (
    <PageContainer
      header={{
        title: '支付批次管理',
        subTitle: '管理批量支付操作和转账状态',
      }}
      extra={[
        <Button
          key="refresh"
          icon={<ReloadOutlined />}
          onClick={() => actionRef.current?.reload()}
          loading={batchesQuery.isLoading}
        >
          刷新
        </Button>,
        <Button key="export" icon={<ExportOutlined />} onClick={handleExport}>
          导出
        </Button>,
      ]}
    >
      {/* 统计卡片 */}
      <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 4, marginBottom: 16 }}>
        <Card size="small" style={{ flex: '0 0 auto', width: 140 }}>
          <Statistic title="总批次数" value={summary.total} prefix={<TeamOutlined />} />
        </Card>
        <Card size="small" style={{ flex: '0 0 auto', width: 140 }}>
          <Statistic
            title="待启动"
            value={summary.approved}
            prefix={<PlayCircleOutlined />}
            valueStyle={{ color: '#1890ff' }}
          />
        </Card>
        <Card size="small" style={{ flex: '0 0 auto', width: 140 }}>
          <Statistic
            title="处理中"
            value={summary.processing}
            prefix={<SyncOutlined />}
            valueStyle={{ color: '#fa8c16' }}
          />
        </Card>
        <Card size="small" style={{ flex: '0 0 auto', width: 140 }}>
          <Statistic
            title="已完成"
            value={summary.completed}
            prefix={<CheckCircleOutlined />}
            valueStyle={{ color: '#52c41a' }}
          />
        </Card>
        <Card size="small" style={{ flex: '0 0 auto', width: 140 }}>
          <Statistic
            title="失败批次"
            value={summary.failed}
            prefix={<CloseCircleOutlined />}
            valueStyle={{ color: '#ff4d4f' }}
          />
        </Card>
        <Card size="small" style={{ flex: '0 0 auto', width: 180 }}>
          <Statistic
            title="总支付金额"
            value={formatAmount(summary.totalAmount)}
            prefix={<DollarOutlined />}
            valueStyle={{ fontSize: 16 }}
          />
        </Card>
      </div>

      <ProTable<PaymentBatch>
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
            const result = await batchesQuery.refetch();
            return {
              data: result.data?.list || [],
              success: true,
              total: result.data?.total || 0,
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
          collapsed: false,
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
                  disabled: record.status !== 'approved' || isLoading,
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
                        { label: '成功', value: 'SUCCESS' },
                        { label: '失败', value: 'FAILED' },
                        { label: '待处理', value: 'PENDING' },
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
                  rowKey="recordNo"
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
