import React, { useMemo } from 'react';
import { useParams } from 'react-router-dom';
import {
  PageContainer,
  ProTable,
  type ProColumns,
} from '@ant-design/pro-components';
import {
  Alert,
  Card,
  Space,
  Statistic,
  Tag,
  Typography,
  Button,
  Tooltip,
} from 'antd';
import { ReloadOutlined, WarningOutlined } from '@ant-design/icons';
import { usePayrollLedgerQuery } from '@services/queries/payroll';
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

const EMPLOYMENT_TEXT: Record<string, string> = {
  full_time: '全职',
  part_time: '兼职',
  contractor: '外包',
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
      <Space size={4} wrap>
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
      <Space size={4} wrap>
        {warnings.map((warning, idx) => (
          <Tag color="warning" key={`${warning}-${idx}`}>
            <WarningOutlined style={{ marginRight: 4 }} />
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
    render: (_, record) => record.employeeNo || '—',
  },
  {
    title: '姓名',
    dataIndex: 'employeeName',
    width: 120,
    render: (_, record) => record.employeeName || '—',
  },
  {
    title: '部门',
    dataIndex: 'department',
    ellipsis: true,
    render: (_, record) => record.department || (record.departments?.join(' / ') ?? '—'),
  },
  {
    title: '直属经理',
    dataIndex: 'managerName',
    width: 140,
    render: (_, record) => record.managerName || '—',
  },
  {
    title: '雇佣类型',
    dataIndex: 'employmentType',
    width: 100,
    render: (_, record) => EMPLOYMENT_TEXT[record.employmentType ?? ''] ?? record.employmentType ?? '—',
  },
  {
    title: '应发',
    dataIndex: 'grossAmount',
    width: 120,
    align: 'right',
    render: (_, record) => formatCurrency(record.grossAmount, record.currency as any),
  },
  {
    title: '扣除',
    dataIndex: 'deductionsAmount',
    width: 120,
    align: 'right',
    render: (_, record) =>
      formatCurrency(record.deductionsTotal ?? record.deductionsAmount, record.currency as any),
  },
  {
    title: '个税',
    dataIndex: 'taxAmount',
    width: 120,
    align: 'right',
    render: (_, record) => formatCurrency(record.taxAmount, record.currency as any),
  },
  {
    title: '社保公积金',
    dataIndex: 'socialAmount',
    width: 140,
    align: 'right',
    render: (_, record) => formatCurrency(record.socialAmount, record.currency as any),
  },
  {
    title: '实发',
    dataIndex: 'netAmount',
    width: 120,
    align: 'right',
    render: (_, record) => formatCurrency(record.netAmount, record.currency as any),
  },
  {
    title: '校验问题',
    dataIndex: 'issues',
    width: 220,
    render: (_, record) => renderIssueTags(record.issues, record.warnings),
  },
];

const BatchLedger: React.FC = () => {
  const params = useParams<{ batchId: string }>();
  const batchId = params.batchId;

  const ledgerQuery = usePayrollLedgerQuery(batchId ?? '', { enabled: Boolean(batchId) });
  const ledger = ledgerQuery.data;

  const currency = ledger?.currency ?? 'CNY';

  const summaryCards = useMemo(() => [
    {
      title: '员工数',
      value: ledger?.totalEmployees ?? 0,
    },
    {
      title: '应发合计',
      value: formatCurrency(ledger?.grossTotal, currency),
    },
    {
      title: '扣除合计',
      value: formatCurrency(ledger?.deductionsTotal, currency),
    },
    {
      title: '实发合计',
      value: formatCurrency(ledger?.netTotal, currency),
    },
    {
      title: '阻塞员工行',
      value: ledger?.linesWithBlockingIssues ?? 0,
    },
    {
      title: '阻塞问题',
      value: ledger?.blockingIssueCount ?? 0,
    },
    {
      title: '复核提醒',
      value: ledger?.reviewIssueCount ?? 0,
    },
  ], [ledger, currency]);

  const statusTag = ledger?.status ? (
    <Tag color={ledger.status === 'approved' ? 'green' : ledger.status === 'locked' ? 'blue' : 'default'}>
      {ledger.status?.toUpperCase()}
    </Tag>
  ) : null;

  return (
    <PageContainer
      header={{
        title: '薪酬财务台账',
        breadcrumb: {},
        extra: [
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => ledgerQuery.refetch()} loading={ledgerQuery.isFetching}>
            刷新
          </Button>,
        ],
      }}
    >
      <Space direction="vertical" size={16} style={{ width: '100%', padding: 24 }}>
        <Card>
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            {!batchId && (
              <Alert type="info" showIcon message="未指定批次" description="请从批次列表进入此页面，或检查路由参数。" />
            )}
            {ledgerQuery.isError && (
              <Alert
                type="error"
                showIcon
                message="财务台账加载失败"
                description={(ledgerQuery.error as Error)?.message ?? '请稍后重试或联系管理员'}
              />
            )}
            <Space size={12} wrap align="center">
              <Title level={4} style={{ margin: 0 }}>
                批次 {ledger?.batchId ?? batchId ?? '—'}
              </Title>
              {statusTag}
              {ledger?.periodLabel && <Tag color="cyan">周期：{ledger.periodLabel}</Tag>}
            </Space>
            {/* 统计卡片 - 单行显示 */}
            <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 4 }}>
              {summaryCards.map((card) => (
                <Card key={card.title} size="small" style={{ flex: '0 0 auto', width: 130 }}>
                  <Statistic title={card.title} value={card.value} valueStyle={{ fontSize: 20 }} />
                </Card>
              ))}
            </div>
            {ledger?.issues && ledger.issues.length > 0 && (
              <Alert
                type={ledger.hasBlockingIssues ? 'error' : 'warning'}
                showIcon
                message={ledger.hasBlockingIssues ? '批次阻塞问题' : '批次复核提醒'}
                description={renderIssueTags(ledger.issues, ledger.warnings)}
              />
            )}
            {!ledger?.issues?.length && ledger?.warnings && ledger.warnings.length > 0 && (
              <Alert
                type="warning"
                showIcon
                message="批次复核提醒"
                description={
                  <Space direction="vertical" size={4} style={{ width: '100%' }}>
                    {ledger.warnings.map((warning, idx) => (
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
          dataSource={ledger?.lines ?? []}
          loading={ledgerQuery.isLoading || ledgerQuery.isFetching}
          search={false}
          pagination={{ pageSize: 20, showSizeChanger: true }}
          headerTitle="员工行明细"
          toolBarRender={() => {
            const tools = [] as React.ReactNode[];
            if (ledger?.linesWithBlockingIssues) {
              tools.push(
                <Tooltip key="blocking" title="存在阻塞问题的员工行">
                  <Tag color="error">{ledger.linesWithBlockingIssues} 行阻塞</Tag>
                </Tooltip>,
              );
            }
            if (ledger?.linesWithWarnings) {
              tools.push(
                <Tooltip key="warnings" title="存在复核提醒的员工行">
                  <Tag color="warning">
                    <WarningOutlined style={{ marginRight: 4 }} />
                    {ledger.linesWithWarnings} 行提醒
                  </Tag>
                </Tooltip>,
              );
            }
            return tools;
          }}
        />
      </Space>
    </PageContainer>
  );
};

export default BatchLedger;
