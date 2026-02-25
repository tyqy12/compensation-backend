import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Space,
  Statistic,
  Switch,
  Tag,
  Typography,
  List,
} from 'antd';
import { ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  useClientCredentialsTokenMutation,
  usePartTimePayrollBatchesQuery,
  usePartTimePayrollLinesQuery,
  usePartTimePayslipsQuery,
  type PartTimeBatchQueryParams,
  type PartTimeLinesQueryParams,
  type PartTimePayslipQueryParams,
} from '@services/queries/payroll';
import type {
  ClientCredentialsToken,
  OpenApiPayrollBatchDto,
  OpenApiPayrollLineDto,
} from '@types/openapi';

const { Text } = Typography;

const TOKEN_STORAGE_KEY = 'pt_readonly_token';

interface StoredToken extends ClientCredentialsToken {
  expiresAt: number;
}

const getSessionStorage = () => {
  if (typeof window === 'undefined' || typeof window.sessionStorage === 'undefined') {
    return undefined;
  }
  return window.sessionStorage;
};

const readStoredToken = (): StoredToken | undefined => {
  const storage = getSessionStorage();
  if (!storage) return undefined;
  try {
    const raw = storage.getItem(TOKEN_STORAGE_KEY);
    if (!raw) return undefined;
    const parsed = JSON.parse(raw) as StoredToken;
    if (!parsed.accessToken || !parsed.expiresAt) return undefined;
    if (parsed.expiresAt <= Date.now()) return undefined;
    return parsed;
  } catch (error) {
    console.warn('Failed to parse stored PT token', error);
    return undefined;
  }
};

const writeStoredToken = (token?: StoredToken) => {
  const storage = getSessionStorage();
  if (!storage) return;
  try {
    if (!token) {
      storage.removeItem(TOKEN_STORAGE_KEY);
      return;
    }
    storage.setItem(TOKEN_STORAGE_KEY, JSON.stringify(token));
  } catch (error) {
    console.warn('Failed to persist PT token', error);
  }
};

const formatCurrency = (value?: number, currency = 'CNY') => {
  if (value === undefined || value === null) return '—';
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(value);
};

const batchColumns: ProColumns<OpenApiPayrollBatchDto>[] = [
  {
    title: '批次号',
    dataIndex: 'batchNo',
    width: 180,
  },
  {
    title: '周期',
    dataIndex: 'periodLabel',
    width: 120,
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 120,
    valueType: 'select',
    valueEnum: {
      approved: { text: '已审批', status: 'Success' },
      paid: { text: '已发薪', status: 'Processing' },
      archived: { text: '已归档', status: 'Default' },
    },
    render: (_, record) => (
      <Tag color={record.status === 'paid' ? 'green' : record.status === 'approved' ? 'blue' : 'default'}>
        {record.status?.toUpperCase()}
      </Tag>
    ),
  },
  {
    title: '工资行数',
    dataIndex: 'lineCount',
    width: 120,
  },
  {
    title: '支付完成时间',
    dataIndex: 'paidAt',
    width: 180,
    valueType: 'dateTime',
    render: (_, record) => record.paidAt ? dayjs(record.paidAt).format('YYYY-MM-DD HH:mm:ss') : '—',
  },
  {
    title: '更新时间',
    dataIndex: 'updatedAt',
    hideInSearch: true,
    render: (_, record) => record.updatedAt ? dayjs(record.updatedAt).format('YYYY-MM-DD HH:mm:ss') : '—',
  },
  {
    title: '筛选周期',
    dataIndex: 'period',
    valueType: 'dateMonth',
    hideInTable: true,
  },
];

const lineColumns: ProColumns<OpenApiPayrollLineDto>[] = [
  {
    title: '员工引用',
    dataIndex: 'employeeRef',
    width: 180,
  },
  {
    title: '姓名',
    dataIndex: 'employeeNameMasked',
    width: 120,
  },
  {
    title: '手机号',
    dataIndex: 'phoneMasked',
    width: 140,
  },
  {
    title: '部门',
    dataIndex: 'departments',
    ellipsis: true,
    render: (_, record) => record.departments?.join(' / ') ?? '—',
  },
  {
    title: '应发',
    dataIndex: 'grossAmount',
    align: 'right',
    width: 120,
    render: (_, record) => formatCurrency(record.grossAmount, record.currency),
  },
  {
    title: '实发',
    dataIndex: 'netAmount',
    align: 'right',
    width: 120,
    render: (_, record) => formatCurrency(record.netAmount, record.currency),
  },
  {
    title: '更新时间',
    dataIndex: 'updatedAt',
    width: 180,
    render: (_, record) => record.updatedAt ? dayjs(record.updatedAt).format('YYYY-MM-DD HH:mm:ss') : '—',
  },
];

type CredentialFormValues = {
  clientId: string;
  clientSecret: string;
  scopes?: string;
};

type CredentialState = {
  clientId: string;
  clientSecret: string;
  scopes?: string[];
};

const PartTimeReadonly: React.FC = () => {
  const [credentials, setCredentials] = useState<CredentialState>();
  const [autoRenew, setAutoRenew] = useState<boolean>(true);
  const [token, setToken] = useState<StoredToken | undefined>(() => readStoredToken());
  const [batchParams, setBatchParams] = useState<PartTimeBatchQueryParams>({ current: 1, size: 10, status: 'paid' });
  const [selectedBatch, setSelectedBatch] = useState<OpenApiPayrollBatchDto | undefined>();
  const [lineParams, setLineParams] = useState<PartTimeLinesQueryParams>({ current: 1, size: 20 });
  const [payslipParams, setPayslipParams] = useState<PartTimePayslipQueryParams | undefined>();

  const { message } = AntdApp.useApp();

  const accessToken = token?.accessToken;
  const batchesQuery = usePartTimePayrollBatchesQuery(batchParams, {
    accessToken,
    enabled: Boolean(accessToken),
  });
  const batchIdentifier = selectedBatch?.batchId ?? selectedBatch?.id ?? selectedBatch?.batchNo ?? '';

  const linesQuery = usePartTimePayrollLinesQuery(batchIdentifier, lineParams, {
    enabled: Boolean(batchIdentifier) && Boolean(accessToken),
    accessToken,
  });
  const payslipQuery = usePartTimePayslipsQuery(payslipParams ?? { employeeRef: '', period: '' }, {
    enabled:
      Boolean(payslipParams?.employeeRef && payslipParams?.period) && Boolean(accessToken),
    accessToken,
  });

  const tokenMutation = useClientCredentialsTokenMutation();

  const expiresLabel = useMemo(() => {
    if (!token?.expiresAt) return null;
    const remainSeconds = Math.max(0, Math.floor((token.expiresAt - Date.now()) / 1000));
    const minutes = Math.floor(remainSeconds / 60);
    return `${minutes} 分钟 ${remainSeconds % 60} 秒`;
  }, [token]);

  const fetchToken = useCallback(
    async (state: CredentialState) => {
      const result = await tokenMutation.mutateAsync({
        clientId: state.clientId,
        clientSecret: state.clientSecret,
        scope: state.scopes,
      });
      const expiresIn = result.expiresIn ?? 1800;
      const expiresAt = Date.now() + Math.max(0, expiresIn - 30) * 1000;
      const nextToken: StoredToken = { ...result, expiresAt };
      setToken(nextToken);
      writeStoredToken(nextToken);
    },
    [tokenMutation],
  );

  const requestToken = async (formValues: CredentialFormValues) => {
    const scopeList = formValues.scopes
      ? formValues.scopes
          .split(/\s+/)
          .map((item) => item.trim())
          .filter(Boolean)
      : undefined;

    const state: CredentialState = {
      clientId: formValues.clientId,
      clientSecret: formValues.clientSecret,
      scopes: scopeList,
    };

    try {
      await fetchToken(state);
      setCredentials(state);
      message.success('已获取 AccessToken');
    } catch (error: any) {
      const msg = error?.response?.data?.message || error?.message || '获取 Token 失败';
      message.error(msg);
    }
  };

  useEffect(() => {
    if (!token || !credentials || !autoRenew) return;
    if (typeof window === 'undefined') return;
    const now = Date.now();
    const delay = Math.max(0, token.expiresAt - now - 120 * 1000);
    if (delay === 0) {
      fetchToken(credentials).catch((error) => {
        console.warn('Failed to renew PT token immediately', error);
      });
      return;
    }
    const timer = window.setTimeout(() => {
      fetchToken(credentials).catch((error) => {
        console.warn('Failed to renew PT token', error);
      });
    }, delay);
    return () => window.clearTimeout(timer);
  }, [token, credentials, autoRenew, fetchToken]);

  const handleBatchTableChange = (page: { current?: number; pageSize?: number }) => {
    setBatchParams((prev) => ({
      ...prev,
      current: page.current ?? prev.current ?? 1,
      size: page.pageSize ?? prev.size ?? 10,
    }));
  };

  const handleBatchSubmit = (values: Record<string, any>) => {
    setBatchParams((prev) => ({
      ...prev,
      current: 1,
      period: values.period ? dayjs(values.period).format('YYYY-MM') : undefined,
      status: values.status || prev.status,
    }));
    setSelectedBatch(undefined);
  };

  const handleBatchReset = () => {
    setBatchParams({ current: 1, size: 10, status: 'paid' });
    setSelectedBatch(undefined);
  };

  const handleLineSearch = (employeeRef?: string) => {
    setLineParams((prev) => ({
      ...prev,
      current: 1,
      employeeRef: employeeRef?.trim() ? employeeRef.trim() : undefined,
    }));
  };

  const handlePayslipSearch = (values: { employeeRef: string; period: dayjs.Dayjs }) => {
    if (!values.employeeRef || !values.period) {
      setPayslipParams(undefined);
      return;
    }
    setPayslipParams({
      employeeRef: values.employeeRef.trim(),
      period: values.period.format('YYYY-MM'),
    });
  };

  return (
    <PageContainer
      header={{
        title: 'PT 只读接口调试台',
        breadcrumb: {},
      }}
    >
      <Space size={16} style={{ width: '100%', padding: 24 }}>
        <Card title="Client Credentials 令牌管理" extra={token ? <Tag color="blue">有效期剩余：{expiresLabel}</Tag> : null}>
          <Space size={16} style={{ width: '100%' }}>
            <Form<CredentialFormValues>
              layout="inline"
              onFinish={requestToken}
              initialValues={{ scopes: 'payroll:read payslip:read' }}
            >
              <Form.Item
                name="clientId"
                label="Client ID"
                rules={[{ required: true, message: '请输入 Client ID' }]}
              >
                <Input autoComplete="off" placeholder="appId" style={{ width: 220 }} />
              </Form.Item>
              <Form.Item
                name="clientSecret"
                label="Client Secret"
                rules={[{ required: true, message: '请输入 Client Secret' }]}
              >
                <Input.Password autoComplete="new-password" placeholder="密钥" style={{ width: 260 }} />
              </Form.Item>
              <Form.Item name="scopes" label="Scopes">
                <Input placeholder="payroll:read payslip:read" style={{ width: 240 }} />
              </Form.Item>
              <Form.Item label="自动续签">
                <Switch checked={autoRenew} onChange={setAutoRenew} />
              </Form.Item>
              <Form.Item>
                <Space>
                  <Button type="primary" htmlType="submit" loading={tokenMutation.isPending} icon={<SafetyCertificateOutlined />}>
                    获取 Token
                  </Button>
                  {token && (
                    <Button
                      onClick={() => {
                        setToken(undefined);
                        writeStoredToken(undefined);
                        setBatchParams({ current: 1, size: 10, status: 'paid' });
                        setSelectedBatch(undefined);
                        setPayslipParams(undefined);
                        setLineParams({ current: 1, size: 20 });
                        batchesQuery.remove();
                        linesQuery.remove();
                        payslipQuery.remove();
                      }}
                    >
                      清除缓存
                    </Button>
                  )}
                </Space>
              </Form.Item>
            </Form>

            {token && (
              <Alert
                type="success"
                showIcon
                message="AccessToken 已缓存"
                description={
                  <Space size={4} style={{ width: '100%' }}>
                    <Text type="secondary">Authorization: Bearer {token.accessToken.slice(0, 12)}...</Text>
                    <Text type="secondary">Scope: {token.scope}</Text>
                    <Text type="secondary">TokenType: {token.tokenType}</Text>
                  </Space>
                }
              />
            )}
            {!token && (
              <Alert
                type="info"
                showIcon
                message="请先获取访问令牌"
                description="仅当成功获取 AccessToken 后，才能查询兼职批次和工资条数据"
              />
            )}
          </Space>
        </Card>

        <Card title="兼职批次列表">
          <ProTable<OpenApiPayrollBatchDto>
            rowKey={(record) =>
              String(record.batchId ?? record.id ?? record.batchNo ?? `${record.periodLabel}-${record.lineCount}`)}
            columns={batchColumns}
            dataSource={batchesQuery.data?.list ?? []}
            loading={batchesQuery.isLoading || batchesQuery.isFetching}
            search={accessToken ? {
              labelWidth: 'auto',
              optionRender: (searchConfig, formProps, dom) => [
                ...dom,
                <Button key="reset" onClick={handleBatchReset}>
                  重置
                </Button>,
              ],
              defaultCollapsed: false,
            } : false}
            onSubmit={handleBatchSubmit}
            pagination={{
              current: batchParams.current,
              pageSize: batchParams.size,
              total: batchesQuery.data?.total ?? 0,
              showSizeChanger: true,
              showQuickJumper: true,
              onChange: (current, pageSize) => handleBatchTableChange({ current, pageSize }),
            }}
            onRow={(record) => ({
              onClick: () => {
                if (!accessToken) return;
                setSelectedBatch(record);
                setLineParams((prev) => ({ ...prev, current: 1 }));
              },
            })}
            rowClassName={(record) =>
              selectedBatch && (
                (record.batchId && selectedBatch.batchId === record.batchId) ||
                (record.id && selectedBatch.id === record.id) ||
                (record.batchNo && selectedBatch.batchNo === record.batchNo)
              )
                ? 'ant-table-row-selected'
                : ''}
            toolBarRender={() => [
              <Button
                key="refresh"
                icon={<ReloadOutlined />}
                onClick={() => batchesQuery.refetch()}
                loading={batchesQuery.isFetching}
                disabled={!accessToken}
              >
                刷新
              </Button>,
            ]}
            size="small"
            locale={{
              emptyText: accessToken ? undefined : '请先获取 AccessToken',
            }}
          />
        </Card>

        {selectedBatch && accessToken && (
          <Card
            title={`工资行明细 - ${selectedBatch.batchNo || selectedBatch.batchId}`}
            extra={<Text type="secondary">共 {selectedBatch.lineCount ?? linesQuery.data?.total ?? 0} 条</Text>}
          >
            <Space size={12} style={{ width: '100%' }}>
              {/* 统计卡片 - 单行显示 */}
              <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 4 }}>
                <Card size="small" style={{ flex: '0 0 auto', width: 120 }}>
                  <Statistic title="周期" value={selectedBatch.periodLabel ?? '—'} />
                </Card>
                <Card size="small" style={{ flex: '0 0 auto', width: 120 }}>
                  <Statistic title="状态" value={selectedBatch.status?.toUpperCase() ?? '—'} />
                </Card>
                <Card size="small" style={{ flex: '0 0 auto', width: 100 }}>
                  <Statistic title="行数" value={selectedBatch.lineCount ?? linesQuery.data?.total ?? 0} />
                </Card>
                <Card size="small" style={{ flex: '0 0 auto', width: 180 }}>
                  <Statistic
                    title="支付时间"
                    value={selectedBatch.paidAt ? dayjs(selectedBatch.paidAt).format('YYYY-MM-DD HH:mm:ss') : '—'}
                  />
                </Card>
              </div>

              <Input.Search
                placeholder="按员工引用过滤 (emp:、wx:等)"
                allowClear
                enterButton="筛选"
                onSearch={handleLineSearch}
                disabled={!accessToken}
                style={{ maxWidth: 320 }}
              />

              <ProTable<OpenApiPayrollLineDto>
                rowKey={(record) =>
                  String(record.id ?? record.lineNo ?? record.employeeRef ?? `${record.employeeRef}-${record.updatedAt}`)}
                columns={lineColumns}
                dataSource={linesQuery.data?.list ?? []}
                loading={linesQuery.isLoading || linesQuery.isFetching}
                search={false}
                pagination={{
                  current: lineParams.current,
                  pageSize: lineParams.size,
                  total: linesQuery.data?.total ?? 0,
                  showSizeChanger: true,
                  onChange: (current, pageSize) =>
                    setLineParams((prev) => ({ ...prev, current, size: pageSize })),
                }}
                size="small"
                locale={{
                  emptyText: accessToken ? undefined : '请先获取 AccessToken',
                }}
              />
            </Space>
          </Card>
        )}

        <Card title="工资条查询">
          <Space size={16} style={{ width: '100%' }}>
            <Form<{ employeeRef: string; period: dayjs.Dayjs }>
              layout="inline"
              onFinish={handlePayslipSearch}
            >
              <Form.Item
                name="employeeRef"
                label="员工引用"
                rules={[{ required: true, message: '请输入员工引用 (emp:E0001)' }]}
              >
                <Input placeholder="emp:E0001" style={{ width: 220 }} disabled={!accessToken} />
              </Form.Item>
              <Form.Item
                name="period"
                label="工资周期"
                rules={[{ required: true, message: '请选择工资周期' }]}
              >
                <DatePicker picker="month" format="YYYY-MM" style={{ width: 180 }} disabled={!accessToken} />
              </Form.Item>
              <Form.Item>
                <Button type="primary" htmlType="submit" loading={payslipQuery.isLoading} disabled={!accessToken}>
                  查询
                </Button>
              </Form.Item>
            </Form>

            {!accessToken && (
              <Alert
                type="info"
                showIcon
                message="请先获取 AccessToken"
                description="输入客户端凭证并点击“获取 Token”后，才能查询工资条"
              />
            )}

            {accessToken && payslipQuery.isFetching && <Text type="secondary">正在查询工资条...</Text>}

            {accessToken && payslipQuery.isError && (
              <Alert
                type="error"
                showIcon
                message="查询失败"
                description={(payslipQuery.error as Error)?.message ?? '请检查凭证和网络后重试'}
              />
            )}

            {accessToken && payslipQuery.data && payslipQuery.data.length === 0 && payslipParams && (
              <Alert
                type="info"
                showIcon
                message="未找到工资条"
                description="请核对员工引用、周期或 scope 权限"
              />
            )}

            {accessToken && payslipQuery.data && payslipQuery.data.length > 0 && (
              <List
                itemLayout="vertical"
                dataSource={payslipQuery.data}
                renderItem={(item) => (
                  <List.Item key={item.id ?? item.period}>
                    <List.Item.Meta
                      title={`${item.employeeRef} - ${item.period}`}
                      description={
                        <Space size={24} wrap>
                          <Text>实发：{formatCurrency(item.netAmount, item.currency)}</Text>
                          <Text>部门：{item.departments?.join(' / ') ?? '—'}</Text>
                          <Text>更新时间：{item.updatedAt ? dayjs(item.updatedAt).format('YYYY-MM-DD HH:mm') : '—'}</Text>
                        </Space>
                      }
                    />
                    <ProTable
                      rowKey={(record) => record.itemCode ?? `${record.itemName}-${record.order}`}
                      columns={[
                        { title: '项目', dataIndex: 'itemName' },
                        {
                          title: '金额',
                          dataIndex: 'amount',
                          align: 'right',
                          render: (_, record) => formatCurrency(record.amount, item.currency),
                        },
                        {
                          title: '展示',
                          dataIndex: 'showOnPayslip',
                          render: (_, record) => (record.showOnPayslip ? '是' : '否'),
                        },
                      ]}
                      dataSource={item.items ?? []}
                      pagination={false}
                      search={false}
                      options={false}
                      size="small"
                    />
                  </List.Item>
                )}
              />
            )}
          </Space>
        </Card>
      </Space>
    </PageContainer>
  );
};

export default PartTimeReadonly;
