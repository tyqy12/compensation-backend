import React, { useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  PageContainer,
  ProTable,
  type ProColumns,
} from '@ant-design/pro-components';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import {
  usePayrollManagerReviewQuery,
  type PayrollManagerReviewFilters,
} from '@services/queries/payroll';
import type { PayrollPreviewLineDto, PayrollValidationIssueDto } from '@types/openapi';

const { Text, Title } = Typography;

const formatCurrency = (value?: number, currency = 'CNY') => {
  if (value === undefined || value === null) return '—';
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(value);
};

const getIssueSeverity = (issue?: PayrollValidationIssueDto) => {
  const severity = String(issue?.severity ?? '').toLowerCase();
  if (severity === 'blocking' || issue?.blocking) {
    return 'blocking';
  }
  if (severity === 'info') {
    return 'info';
  }
  return 'review';
};

const getIssueColor = (issue?: PayrollValidationIssueDto) => {
  const severity = getIssueSeverity(issue);
  if (severity === 'blocking') {
    return 'error';
  }
  if (severity === 'info') {
    return 'processing';
  }
  return 'warning';
};

const renderIssueTags = (issues?: PayrollValidationIssueDto[], warnings?: string[]) => {
  if (issues && issues.length > 0) {
    return (
      <Space wrap size={4}>
        {issues.map((issue, idx) => (
          <Tag color={getIssueColor(issue)} key={`${issue.code ?? issue.message ?? 'issue'}-${idx}`}>
            {issue.message}
          </Tag>
        ))}
      </Space>
    );
  }
  if (warnings && warnings.length > 0) {
    return (
      <Space wrap size={4}>
        {warnings.map((warning, idx) => (
          <Tag color="warning" key={`${warning}-${idx}`}>
            {warning}
          </Tag>
        ))}
      </Space>
    );
  }
  return <Text type="secondary">正常</Text>;
};

const columns: ProColumns<PayrollPreviewLineDto>[] = [
  {
    title: '员工编号',
    dataIndex: 'employeeNo',
    width: 120,
  },
  {
    title: '姓名',
    dataIndex: 'employeeName',
    width: 120,
  },
  {
    title: '部门',
    dataIndex: 'department',
    ellipsis: true,
    render: (_, record) => record.department || record.departments?.join(' / ') || '—',
  },
  {
    title: '直属经理',
    dataIndex: 'managerName',
    width: 140,
  },
  {
    title: '实发',
    dataIndex: 'netAmount',
    align: 'right',
    width: 120,
    render: (_, record) => formatCurrency(record.netAmount, record.currency as any),
  },
  {
    title: '差异提示',
    dataIndex: 'diff',
    ellipsis: true,
    render: (_, record) => {
      if (record.diff?.netDeltaAmount !== undefined || record.diff?.netDeltaPercent !== undefined) {
        return (
          <Space wrap size={4}>
            {record.diff?.netDeltaAmount !== undefined && (
              <Tag color="purple">净额差异：{formatCurrency(record.diff.netDeltaAmount, record.currency as any)}</Tag>
            )}
            {record.diff?.netDeltaPercent !== undefined && (
              <Tag color="geekblue">变动比例：{`${(record.diff.netDeltaPercent * 100).toFixed(2)}%`}</Tag>
            )}
          </Space>
        );
      }
      if (record.differences && record.differences.length > 0) {
        return (
          <Space wrap size={4}>
            {record.differences.map((diff, idx) => (
              <Tag color="purple" key={`${diff}-${idx}`}>
                {diff}
              </Tag>
            ))}
          </Space>
        );
      }
      return <Text type="secondary">无差异</Text>;
    },
  },
  {
    title: '校验问题',
    dataIndex: 'issues',
    ellipsis: true,
    render: (_, record) => renderIssueTags(record.issues, record.warnings),
  },
];

type SearchFormValues = {
  department?: string;
  managerId?: number;
  keyword?: string;
};

const ManagerReview: React.FC = () => {
  const { batchId } = useParams<{ batchId: string }>();
  const [filters, setFilters] = useState<PayrollManagerReviewFilters>({});

  const reviewQuery = usePayrollManagerReviewQuery(batchId ?? '', filters, { enabled: Boolean(batchId) });
  const review = reviewQuery.data;

  const currency = review?.currency ?? 'CNY';

  const summaryCards = useMemo(() => [
    {
      title: '筛选后员工数',
      value: review?.lines?.length ?? 0,
    },
    {
      title: '实发合计',
      value: formatCurrency(review?.netTotal, currency),
    },
    {
      title: '阻塞员工行',
      value: review?.linesWithBlockingIssues ?? 0,
    },
    {
      title: '阻塞问题',
      value: review?.blockingIssueCount ?? 0,
    },
    {
      title: '复核提醒',
      value: review?.reviewIssueCount ?? 0,
    },
  ], [review, currency]);

  const handleSearch = (values: SearchFormValues) => {
    setFilters({
      department: values.department?.trim() || undefined,
      keyword: values.keyword?.trim() || undefined,
      managerId: values.managerId || undefined,
    });
  };

  const handleReset = () => {
    setFilters({});
  };

  return (
    <PageContainer
      header={{
        title: '经理核对视图',
        breadcrumb: {},
        extra: [
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => reviewQuery.refetch()} loading={reviewQuery.isFetching}>
            刷新
          </Button>,
        ],
      }}
    >
      <Space direction="vertical" size={16} style={{ width: '100%', padding: 24 }}>
        <Card>
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            {!batchId && (
              <Alert type="info" showIcon message="未指定批次" description="请通过批次入口进入此页面，以便加载差异数据。" />
            )}
            {reviewQuery.isError && (
              <Alert
                type="error"
                showIcon
                message="经理核对数据加载失败"
                description={(reviewQuery.error as Error)?.message ?? '请稍后重试或联系管理员'}
              />
            )}
            <Space size={12} wrap align="center">
              <Title level={4} style={{ margin: 0 }}>
                批次 {review?.batchId ?? batchId ?? '—'}
              </Title>
              {review?.status && (
                <Tag color={review.status === 'approved' ? 'green' : 'blue'}>{review.status.toUpperCase()}</Tag>
              )}
              {review?.department && <Tag color="geekblue">部门：{review.department}</Tag>}
              {review?.managerId && <Tag color="volcano">经理 ID：{review.managerId}</Tag>}
            </Space>

            <Form<SearchFormValues>
              layout="inline"
              onFinish={handleSearch}
              onReset={handleReset}
            >
              <Form.Item name="department" label="部门">
                <Input allowClear placeholder="按部门筛选" style={{ width: 220 }} />
              </Form.Item>
              <Form.Item name="managerId" label="经理ID">
                <InputNumber min={0} placeholder="输入经理ID" style={{ width: 180 }} />
              </Form.Item>
              <Form.Item name="keyword" label="关键字">
                <Input allowClear placeholder="姓名或员工号" style={{ width: 220 }} />
              </Form.Item>
              <Form.Item>
                <Space>
                  <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={reviewQuery.isLoading}>
                    查询
                  </Button>
                  <Button htmlType="reset">重置</Button>
                </Space>
              </Form.Item>
            </Form>

            {/* 统计卡片 - 单行显示 */}
            <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 4 }}>
              {summaryCards.map((card) => (
                <Card key={card.title} size="small" style={{ flex: '0 0 auto', width: 140 }}>
                  <Statistic title={card.title} value={card.value} valueStyle={{ fontSize: 20 }} />
                </Card>
              ))}
            </div>

            {review?.issues && review.issues.length > 0 && (
              <Alert
                type={review.hasBlockingIssues ? 'error' : 'warning'}
                showIcon
                message={review.hasBlockingIssues ? '批次阻塞问题' : '批次复核提醒'}
                description={renderIssueTags(review.issues, review.warnings)}
              />
            )}
            {!review?.issues?.length && review?.warnings && review.warnings.length > 0 && (
              <Alert
                type="warning"
                showIcon
                message="批次复核提醒"
                description={
                  <Space direction="vertical" size={4} style={{ width: '100%' }}>
                    {review.warnings.map((warning, idx) => (
                      <Text key={`${warning}-${idx}`}>{warning}</Text>
                    ))}
                  </Space>
                }
              />
            )}
          </Space>
        </Card>

        <ProTable<PayrollPreviewLineDto>
          rowKey={(record) =>
            String(
              record.lineId ??
              record.employeeId ??
              record.employeeNo ??
              `${record.employeeName ?? 'line'}-${record.managerId ?? 'unknown'}`,
            )}
          columns={columns}
          dataSource={review?.lines ?? []}
          loading={reviewQuery.isLoading || reviewQuery.isFetching}
          search={false}
          pagination={{ pageSize: 20, showSizeChanger: true }}
          headerTitle="员工核对明细"
        />
      </Space>
    </PageContainer>
  );
};

export default ManagerReview;
