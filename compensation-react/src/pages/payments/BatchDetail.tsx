import React, { useState, useRef } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Card,
  Statistic,
  Progress,
  Tag,
  Timeline,
  Button,
  Space,
  Typography,
  Alert,
  Tooltip,
  Spin,
  App as AntdApp,
  Row,
  Col,
} from 'antd';
import {
  PageContainer,
  ProTable,
  ProCard,
  ProDescriptions,
  type ProColumns,
  type ActionType,
} from '@ant-design/pro-components';
import {
  ReloadOutlined,
  ArrowLeftOutlined,
  RedoOutlined,
  ExclamationCircleOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  PlayCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import {
  usePaymentBatchQuery,
  usePaymentRecordsQuery,
  useRetryPaymentRecordMutation,
  type PaymentRecord,
  getBatchStatusInfo,
  getPaymentRecordStatusInfo,
  formatAmount,
  calculateBatchProgress,
  calculateBatchSuccessRate,
} from '@services/queries/paymentBatch';
import { toMessage, withActionPrefix } from '@utils/error';
import dayjs from 'dayjs';

const { Text } = Typography;

const BatchDetail: React.FC = () => {
  const { batchNo } = useParams<{ batchNo: string }>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [selectedStatus, setSelectedStatus] = useState<PaymentRecord['status'] | undefined>();
  const actionRef = useRef<ActionType>();

  const { message, modal } = AntdApp.useApp();

  // 查询批次详情
  const batchQuery = usePaymentBatchQuery(batchNo!);
  const batch = batchQuery.data;

  // 查询支付记录
  const recordsQuery = usePaymentRecordsQuery({
    batchNo: batchNo!,
    status: selectedStatus,
  });

  // 重试失败的支付记录
  const retryMutation = useRetryPaymentRecordMutation();

  // 返回列表页面，保留搜索参数
  const handleBackToList = () => {
    const queryString = searchParams.toString();
    navigate(queryString ? `/payments/batches?${queryString}` : '/payments/batches');
  };

  // 重试失败的支付记录
  const handleRetryRecord = async (record: PaymentRecord) => {
    if (record.status !== 'failed') {
      message.warning('只能重试失败的支付记录');
      return;
    }

    modal.confirm({
      title: '重试支付记录',
      content: (
        <div>
          <p>确定要重试这笔失败的支付吗？</p>
          <div style={{ marginTop: 8 }}>
            <Text type="secondary">支付金额：{formatAmount(record.amount)}</Text><br />
            <Text type="secondary">失败原因：{record.errorMsg || '未知错误'}</Text>
          </div>
        </div>
      ),
      icon: <ExclamationCircleOutlined />,
      onOk: async () => {
        try {
          await retryMutation.mutateAsync(record.id);
          message.success('重试请求已提交，正在处理中');
          actionRef.current?.reload();
          // 刷新批次详情以更新统计数据
          batchQuery.refetch();
        } catch (error: any) {
          message.error(withActionPrefix('重试失败', error));
        }
      },
    });
  };

  // 批量重试失败的记录
  const handleBatchRetry = async () => {
    const failedRecords = recordsQuery.data?.filter(r => r.status === 'failed') || [];

    if (failedRecords.length === 0) {
      message.info('没有失败的记录需要重试');
      return;
    }

    modal.confirm({
      title: '批量重试失败记录',
      content: (
        <div>
          <p>确定要重试 {failedRecords.length} 笔失败的支付记录吗？</p>
          <Alert
            message="批量重试将逐一处理每笔失败记录，请耐心等待"
            type="info"
            showIcon
            style={{ marginTop: 8 }}
          />
        </div>
      ),
      icon: <ExclamationCircleOutlined />,
      onOk: async () => {
        // 并发重试所有失败的记录，保留每笔业务失败原因用于提示。
        const results = await Promise.allSettled(
          failedRecords.map(record => retryMutation.mutateAsync(record.id)),
        );
        const rejectedResults = results.filter(
          (result): result is PromiseRejectedResult => result.status === 'rejected',
        );

        if (rejectedResults.length === 0) {
          message.success('批量重试已完成');
        } else {
          const firstReason = toMessage(rejectedResults[0].reason);
          message.error(`批量重试完成，${rejectedResults.length} 笔失败：${firstReason}`);
        }
        actionRef.current?.reload();
        batchQuery.refetch();
      },
    });
  };

  // 获取时间线数据
  const getTimelineItems = () => {
    if (!batch) return [];

    const items = [];

    // 提交时间
    if (batch.submitTime) {
      items.push({
        color: 'blue',
        dot: <CheckCircleOutlined style={{ fontSize: '16px' }} />,
        children: (
          <div>
            <Text strong>批次已提交</Text>
            <div style={{ marginTop: 4 }}>
              <Text type="secondary">{dayjs(batch.submitTime).format('YYYY-MM-DD HH:mm:ss')}</Text>
            </div>
          </div>
        ),
      });
    }

    // 审批时间
    if (batch.approveTime) {
      items.push({
        color: 'green',
        dot: <CheckCircleOutlined style={{ fontSize: '16px' }} />,
        children: (
          <div>
            <Text strong>批次已审批</Text>
            <div style={{ marginTop: 4 }}>
              <Text type="secondary">{dayjs(batch.approveTime).format('YYYY-MM-DD HH:mm:ss')}</Text>
            </div>
          </div>
        ),
      });
    }

    // 处理开始时间
    if (batch.processStartTime) {
      items.push({
        color: batch.status === 'processing' ? 'blue' : 'green',
        dot: batch.status === 'processing' ?
          <SyncOutlined spin style={{ fontSize: '16px' }} /> :
          <PlayCircleOutlined style={{ fontSize: '16px' }} />,
        children: (
          <div>
            <Text strong>开始处理转账</Text>
            <div style={{ marginTop: 4 }}>
              <Text type="secondary">{dayjs(batch.processStartTime).format('YYYY-MM-DD HH:mm:ss')}</Text>
            </div>
          </div>
        ),
      });
    }

    // 处理结束时间
    if (batch.processEndTime) {
      const isSuccess = batch.status === 'completed';
      items.push({
        color: isSuccess ? 'green' : 'red',
        dot: isSuccess ?
          <CheckCircleOutlined style={{ fontSize: '16px' }} /> :
          <CloseCircleOutlined style={{ fontSize: '16px' }} />,
        children: (
          <div>
            <Text strong>{isSuccess ? '处理完成' : '处理失败'}</Text>
            <div style={{ marginTop: 4 }}>
              <Text type="secondary">{dayjs(batch.processEndTime).format('YYYY-MM-DD HH:mm:ss')}</Text>
            </div>
          </div>
        ),
      });
    }

    // 如果正在处理，添加当前状态
    if (batch.status === 'processing' && !batch.processEndTime) {
      items.push({
        color: 'blue',
        dot: <ClockCircleOutlined style={{ fontSize: '16px' }} />,
        children: (
          <div>
            <Text strong>正在处理中...</Text>
            <div style={{ marginTop: 4 }}>
              <Text type="secondary">已处理 {batch.successCount + batch.failedCount} / {batch.totalCount} 笔</Text>
            </div>
          </div>
        ),
      });
    }

    return items;
  };

  const renderProviderTag = (providerCode?: string) => {
    const normalizedCode = (providerCode || 'alipay').trim().toLowerCase();
    if (normalizedCode === 'alipay') {
      return <Tag color="blue">支付宝</Tag>;
    }
    if (normalizedCode === 'yunzhanghu') {
      return <Tag color="geekblue">云账户</Tag>;
    }
    return <Tag>{normalizedCode || '-'}</Tag>;
  };

  // 支付记录表格列定义
  const columns: ProColumns<PaymentRecord>[] = [
    {
      title: '记录ID',
      dataIndex: 'id',
      width: 90,
      search: false,
    },
    {
      title: '员工',
      dataIndex: 'employeeName',
      width: 150,
      search: false,
      render: (_, record) => record.employeeName || `#${record.employeeId ?? '-'}`,
    },
    {
      title: '收款人',
      dataIndex: 'recipientName',
      width: 120,
      search: false,
      render: (_, record) => record.recipientName || '-',
    },
    {
      title: '收款账号',
      dataIndex: 'recipientAccountMasked',
      width: 170,
      search: false,
      render: (_, record) => record.recipientAccountMasked || '-',
    },
    {
      title: '支付金额',
      dataIndex: 'amount',
      width: 120,
      search: false,
      render: (_, record) => (
        <Statistic
          value={record.amount}
          formatter={(value) => formatAmount(Number(value))}
          valueStyle={{ fontSize: '14px' }}
        />
      ),
    },
    {
      title: '支付状态',
      dataIndex: 'status',
      width: 120,
      valueType: 'select',
      valueEnum: {
        pending: { text: '待处理', status: 'Default' },
        processing: { text: '处理中', status: 'Processing' },
        success: { text: '成功', status: 'Success' },
        failed: { text: '失败', status: 'Error' },
        cancelled: { text: '已取消', status: 'Default' },
      },
      render: (_, record) => {
        const statusInfo = getPaymentRecordStatusInfo(record.status);
        return (
          <Tag color={statusInfo.color}>
            {statusInfo.icon} {statusInfo.text}
          </Tag>
        );
      },
    },
    {
      title: '打款渠道',
      dataIndex: 'providerCode',
      width: 120,
      search: false,
      render: (_, record) => renderProviderTag(record.providerCode),
    },
    {
      title: '商户订单号',
      dataIndex: 'providerOrderNo',
      width: 180,
      copyable: true,
      ellipsis: true,
      search: false,
      render: (_, record) => record.providerOrderNo || record.alipayOrderNo || '-',
    },
    {
      title: '渠道流水号',
      dataIndex: 'providerTradeNo',
      width: 200,
      copyable: true,
      ellipsis: true,
      search: false,
      render: (_, record) => (record.providerTradeNo || record.alipayTradeNo) ? (
        <Text code>{record.providerTradeNo || record.alipayTradeNo}</Text>
      ) : (
        <Text type="secondary">-</Text>
      ),
    },
    {
      title: '错误信息',
      dataIndex: 'errorMsg',
      width: 200,
      ellipsis: true,
      search: false,
      render: (_, record) => {
        if (record.status === 'failed' && record.errorMsg) {
          return (
            <Tooltip title={record.errorMsg}>
              <Text type="danger" ellipsis>
                {record.errorCode ? `[${record.errorCode}] ` : ''}{record.errorMsg}
              </Text>
            </Tooltip>
          );
        }
        return <Text type="secondary">-</Text>;
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 160,
      search: false,
      render: (_, record) => record.createTime ? (
        <Text>{dayjs(record.createTime).format('MM-DD HH:mm:ss')}</Text>
      ) : (
        <Text type="secondary">-</Text>
      ),
    },
    {
      title: '支付时间',
      dataIndex: 'paymentTime',
      width: 160,
      search: false,
      render: (_, record) => record.paymentTime ? (
        <Text>{dayjs(record.paymentTime).format('MM-DD HH:mm:ss')}</Text>
      ) : (
        <Text type="secondary">-</Text>
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      render: (_, record) => [
        record.status === 'failed' && (
          <Tooltip key="retry" title="重试支付">
            <Button
              type="link"
              size="small"
              icon={<RedoOutlined />}
              onClick={() => handleRetryRecord(record)}
              loading={retryMutation.isPending}
            >
              重试
            </Button>
          </Tooltip>
        ),
      ].filter(Boolean),
    },
  ];

  if (batchQuery.isLoading) {
    return (
      <PageContainer>
        <div style={{ textAlign: 'center', padding: '50px' }}>
          <Spin size="large" />
          <div style={{ marginTop: 16 }}>
            <Text type="secondary">加载批次详情中...</Text>
          </div>
        </div>
      </PageContainer>
    );
  }

  if (batchQuery.isError || !batch) {
    return (
      <PageContainer>
        <div style={{ textAlign: 'center', padding: '50px' }}>
          <ExclamationCircleOutlined style={{ fontSize: 48, color: '#ff4d4f', marginBottom: 16 }} />
          <div style={{ fontSize: 16, marginBottom: 8 }}>批次加载失败</div>
          <div style={{ color: '#8c8c8c', marginBottom: 16 }}>
            {toMessage(batchQuery.error) || '网络错误，请检查网络连接'}
          </div>
          <Space>
            <Button
              type="primary"
              icon={<ReloadOutlined />}
              onClick={() => batchQuery.refetch()}
            >
              重新加载
            </Button>
            <Button
              icon={<ArrowLeftOutlined />}
              onClick={handleBackToList}
            >
              返回列表
            </Button>
          </Space>
        </div>
      </PageContainer>
    );
  }

  const statusInfo = getBatchStatusInfo(batch.status);
  const progress = calculateBatchProgress(batch);
  const successRate = calculateBatchSuccessRate(batch);
  const failedCount = recordsQuery.data?.filter(r => r.status === 'failed').length || 0;

  return (
    <PageContainer
      header={{
        title: batch.batchName,
        subTitle: (
          <Space>
            <Text code>{batch.batchNo}</Text>
            <Tag color={statusInfo.color}>
              {statusInfo.icon} {statusInfo.text}
            </Tag>
          </Space>
        ),
        onBack: handleBackToList,
        extra: [
          <Button
            key="refresh"
            icon={<ReloadOutlined />}
            onClick={() => {
              batchQuery.refetch();
              actionRef.current?.reload();
            }}
            loading={batchQuery.isLoading}
          >
            刷新
          </Button>,
        ],
      }}
    >
      {/* 统计卡片 - 单行显示 */}
      <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 4, marginBottom: 16 }}>
        <Card size="small" style={{ flex: '0 0 auto', width: 140 }}>
          <Statistic
            title="支付总额"
            value={batch.totalAmount}
            formatter={(value) => formatAmount(Number(value))}
            valueStyle={{ fontSize: '18px' }}
          />
        </Card>
        <Card size="small" style={{ flex: '0 0 auto', width: 140 }}>
          <Statistic
            title="支付笔数"
            value={batch.totalCount}
            suffix="笔"
            valueStyle={{ fontSize: '18px' }}
          />
        </Card>
        <Card size="small" style={{ flex: '0 0 auto', width: 140 }}>
          <Statistic
            title="成功笔数"
            value={batch.successCount}
            valueStyle={{ color: '#52c41a', fontSize: '18px' }}
          />
        </Card>
        <Card size="small" style={{ flex: '0 0 auto', width: 140 }}>
          <Statistic
            title="失败笔数"
            value={batch.failedCount}
            valueStyle={{ color: '#ff4d4f', fontSize: '18px' }}
          />
        </Card>
      </div>

      {/* 处理进度和处理时间线 - 保持两列布局 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col xs={24} md={12}>
          <ProCard title="处理进度" size="small">
            <div style={{ padding: '16px 0' }}>
              <Progress
                percent={progress}
                status={batch.status === 'failed' ? 'exception' :
                       batch.status === 'completed' ? 'success' : 'active'}
                strokeWidth={8}
              />
              <div style={{ marginTop: 8, fontSize: '14px' }}>
                <Text>已处理：{batch.successCount + batch.failedCount} / {batch.totalCount} 笔</Text>
                {progress > 0 && (
                  <div style={{ marginTop: 4 }}>
                    <Text type="secondary">成功率：{successRate}%</Text>
                  </div>
                )}
              </div>
            </div>
          </ProCard>
        </Col>
        <Col xs={24} md={12}>
          <ProCard title="处理时间线" size="small">
            <Timeline
              items={getTimelineItems()}
              style={{ marginTop: 16 }}
            />
          </ProCard>
        </Col>
      </Row>

      {/* 批次详细信息 */}
      <ProCard title="批次信息" style={{ marginBottom: 16 }}>
        <ProDescriptions
          column={2}
          dataSource={batch}
          columns={[
            {
              title: '批次编号',
              dataIndex: 'batchNo',
              copyable: true,
            },
            {
              title: '批次名称',
              dataIndex: 'batchName',
            },
            {
              title: '支付类型',
              dataIndex: 'paymentType',
              render: () => {
                const typeMap: Record<string, string> = {
                  salary: '工资',
                  bonus: '奖金',
                  reimbursement: '报销',
                };
                return typeMap[batch.paymentType || ''] || batch.paymentType;
              },
            },
            {
              title: '批次状态',
              dataIndex: 'status',
              render: () => (
                <Tag color={statusInfo.color}>
                  {statusInfo.icon} {statusInfo.text}
                </Tag>
              ),
            },
            {
              title: '提交时间',
              dataIndex: 'submitTime',
              render: () => batch.submitTime ?
                dayjs(batch.submitTime).format('YYYY-MM-DD HH:mm:ss') : '-',
            },
            {
              title: '审批时间',
              dataIndex: 'approveTime',
              render: () => batch.approveTime ?
                dayjs(batch.approveTime).format('YYYY-MM-DD HH:mm:ss') : '-',
            },
            {
              title: '开始处理时间',
              dataIndex: 'processStartTime',
              render: () => batch.processStartTime ?
                dayjs(batch.processStartTime).format('YYYY-MM-DD HH:mm:ss') : '-',
            },
            {
              title: '处理完成时间',
              dataIndex: 'processEndTime',
              render: () => batch.processEndTime ?
                dayjs(batch.processEndTime).format('YYYY-MM-DD HH:mm:ss') : '-',
            },
            {
              title: '备注',
              dataIndex: 'remark',
              span: 2,
              render: () => batch.remark || <Text type="secondary">无</Text>,
            },
          ]}
        />
      </ProCard>

      {/* 支付记录列表 */}
      <ProCard
        title="支付记录"
        extra={
          failedCount > 0 && (
            <Button
              type="primary"
              icon={<RedoOutlined />}
              onClick={handleBatchRetry}
              loading={retryMutation.isPending}
            >
              批量重试失败记录 ({failedCount})
            </Button>
          )
        }
      >
        <ProTable<PaymentRecord>
          columns={columns}
          actionRef={actionRef}
          request={async (params) => {
            // 支持状态筛选
            const status = params.status as PaymentRecord['status'] | undefined;
            setSelectedStatus(status);

            try {
              const result = await recordsQuery.refetch();
              const data = result.data || [];

              return {
                data,
                success: true,
                total: data.length,
              };
            } catch (error) {
              return {
                data: [],
                success: false,
                total: 0,
              };
            }
          }}
          rowKey="id"
          search={{
            labelWidth: 'auto',
            collapsed: false,
            collapseRender: false,
          }}
	          pagination={{
	            pageSize: 20,
	            showSizeChanger: true,
	            showQuickJumper: true,
	            showTotal: (total = 0, range) => {
	              const [start, end] = range ?? [0, 0];
	              return `第 ${start}-${end} 条/共 ${total} 条`;
	            },
	          }}
          loading={recordsQuery.isLoading || retryMutation.isPending}
          locale={{
            emptyText: recordsQuery.isError ? (
              <div style={{ textAlign: 'center', padding: '40px 20px' }}>
                <ExclamationCircleOutlined style={{ fontSize: 48, color: '#ff4d4f', marginBottom: 16 }} />
                <div style={{ fontSize: 16, marginBottom: 8 }}>数据加载失败</div>
                <div style={{ color: '#8c8c8c', marginBottom: 16 }}>
                  {toMessage(recordsQuery.error) || '网络错误，请检查网络连接'}
                </div>
                <Button
                  type="primary"
                  icon={<ReloadOutlined />}
                  onClick={() => actionRef.current?.reload()}
                >
                  重新加载
                </Button>
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: '40px 20px' }}>
                <div style={{ fontSize: 16, marginBottom: 8 }}>暂无支付记录</div>
                <div style={{ color: '#8c8c8c' }}>
                  {selectedStatus ? `没有状态为"${selectedStatus}"的支付记录` : '该批次还没有支付记录'}
                </div>
              </div>
            ),
          }}
          options={{
            reload: true,
            density: true,
            setting: true,
          }}
          scroll={{ x: 1200 }}
        />
      </ProCard>
    </PageContainer>
  );
};

export default BatchDetail;
