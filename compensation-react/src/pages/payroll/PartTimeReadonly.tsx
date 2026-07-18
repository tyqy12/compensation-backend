import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components';
import {
  Alert,
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Input,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
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
import './PartTimeReadonly.less';

const { Text, Title } = Typography;
const TOKEN_STORAGE_KEY = 'pt_readonly_token';

interface StoredToken extends ClientCredentialsToken {
  expiresAt: number;
}

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

const ptStatusOptions = [
  { label: '已发薪', value: 'paid' },
  { label: '已审批', value: 'approved' },
  { label: '已归档', value: 'archived' },
];

const formatPtStatus = (status?: string) =>
  ptStatusOptions.find((option) => option.value === status)?.label ?? status ?? '未知';

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
    if (!parsed.accessToken || !parsed.expiresAt || parsed.expiresAt <= Date.now()) {
      storage.removeItem(TOKEN_STORAGE_KEY);
      return undefined;
    }
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
    ellipsis: true,
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
    render: (_, record) => (
      <Tag
        color={
          record.status === 'paid' ? 'green' : record.status === 'approved' ? 'blue' : 'default'
        }
      >
        {formatPtStatus(record.status)}
      </Tag>
    ),
  },
  {
    title: '工资行数',
    dataIndex: 'lineCount',
    width: 110,
  },
  {
    title: '支付完成时间',
    dataIndex: 'paidAt',
    width: 180,
    render: (_, record) =>
      record.paidAt ? dayjs(record.paidAt).format('YYYY-MM-DD HH:mm:ss') : '—',
  },
  {
    title: '更新时间',
    dataIndex: 'updatedAt',
    width: 180,
    render: (_, record) =>
      record.updatedAt ? dayjs(record.updatedAt).format('YYYY-MM-DD HH:mm:ss') : '—',
  },
];

const lineColumns: ProColumns<OpenApiPayrollLineDto>[] = [
  {
    title: '员工引用',
    dataIndex: 'employeeRef',
    width: 180,
    ellipsis: true,
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
    width: 220,
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
    render: (_, record) =>
      record.updatedAt ? dayjs(record.updatedAt).format('YYYY-MM-DD HH:mm:ss') : '—',
  },
];

const PartTimeReadonly: React.FC = () => {
  const { message } = AntdApp.useApp();
  const [credentials, setCredentials] = useState<CredentialState>();
  const [autoRenew, setAutoRenew] = useState(true);
  const [token, setToken] = useState<StoredToken | undefined>(() => readStoredToken());
  const [now, setNow] = useState(() => Date.now());
  const [batchParams, setBatchParams] = useState<PartTimeBatchQueryParams>({
    current: 1,
    size: 10,
    status: 'paid',
  });
  const [selectedBatch, setSelectedBatch] = useState<OpenApiPayrollBatchDto>();
  const [lineParams, setLineParams] = useState<PartTimeLinesQueryParams>({ current: 1, size: 20 });
  const [payslipParams, setPayslipParams] = useState<PartTimePayslipQueryParams>();
  const [batchFilterForm] = Form.useForm();

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
    enabled: Boolean(payslipParams?.employeeRef && payslipParams?.period) && Boolean(accessToken),
    accessToken,
  });
  const tokenMutation = useClientCredentialsTokenMutation();

  const expiresLabel = useMemo(() => {
    if (!token?.expiresAt) return null;
    const remainSeconds = Math.max(0, Math.floor((token.expiresAt - now) / 1000));
    return `${Math.floor(remainSeconds / 60)} 分钟 ${remainSeconds % 60} 秒`;
  }, [token, now]);

  useEffect(() => {
    if (!token) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
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
      setNow(Date.now());
      writeStoredToken(nextToken);
    },
    [tokenMutation],
  );

  const requestToken = async (formValues: CredentialFormValues) => {
    const scopeList = formValues.scopes
      ?.split(/\s+/)
      .map((item) => item.trim())
      .filter(Boolean);
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
      message.error(error?.response?.data?.message || error?.message || '获取 Token 失败');
    }
  };

  useEffect(() => {
    if (!token || !credentials || !autoRenew) return;
    const delay = Math.max(0, token.expiresAt - Date.now() - 120 * 1000);
    const renew = () => {
      fetchToken(credentials).catch((error) => {
        console.warn('Failed to renew PT token', error);
      });
    };
    if (delay === 0) {
      renew();
      return;
    }
    const timer = window.setTimeout(renew, delay);
    return () => window.clearTimeout(timer);
  }, [token, credentials, autoRenew, fetchToken]);

  const clearToken = () => {
    setToken(undefined);
    setCredentials(undefined);
    writeStoredToken(undefined);
    setBatchParams({ current: 1, size: 10, status: 'paid' });
    setSelectedBatch(undefined);
    setPayslipParams(undefined);
    setLineParams({ current: 1, size: 20 });
    batchesQuery.remove();
    linesQuery.remove();
    payslipQuery.remove();
  };

  const handleBatchTableChange = (current?: number, pageSize?: number) => {
    setBatchParams((prev) => ({
      ...prev,
      current: current ?? prev.current ?? 1,
      size: pageSize ?? prev.size ?? 10,
    }));
    setSelectedBatch(undefined);
  };

  const handleBatchSubmit = (values: { period?: dayjs.Dayjs; status?: string }) => {
    setBatchParams((prev) => ({
      ...prev,
      current: 1,
      period: values.period?.format('YYYY-MM'),
      status: values.status || undefined,
    }));
    setSelectedBatch(undefined);
  };

  const handleBatchReset = () => {
    batchFilterForm.resetFields();
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
    if (!values.employeeRef?.trim() || !values.period) {
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
      className="part-time-readonly-page"
      header={{
        title: 'PT 只读工作台',
        subTitle: '通过客户端凭证查看已发放的兼职工资数据',
        breadcrumb: {},
      }}
    >
      <div className="part-time-workspace">
        <section className="part-time-section part-time-access-section">
          <div className="part-time-section-heading">
            <div>
              <Text className="part-time-section-kicker">ACCESS CONTROL</Text>
              <Title level={4} className="part-time-section-title">
                客户端凭证
              </Title>
            </div>
            {token && <Tag color="blue">有效期剩余：{expiresLabel}</Tag>}
          </div>

          <Form<CredentialFormValues>
            layout="vertical"
            className="part-time-credential-form"
            onFinish={requestToken}
            initialValues={{ scopes: 'payroll:read payslip:read' }}
          >
            <div className="part-time-credential-grid">
              <Form.Item
                name="clientId"
                label="Client ID"
                rules={[{ required: true, message: '请输入 Client ID' }]}
              >
                <Input autoComplete="off" placeholder="appId" />
              </Form.Item>
              <Form.Item
                name="clientSecret"
                label="Client Secret"
                rules={[{ required: true, message: '请输入 Client Secret' }]}
              >
                <Input.Password autoComplete="new-password" placeholder="密钥" />
              </Form.Item>
              <Form.Item name="scopes" label="Scopes">
                <Input placeholder="payroll:read payslip:read" />
              </Form.Item>
              <Form.Item label="自动续签" className="part-time-renew-field">
                <Switch checked={autoRenew} onChange={setAutoRenew} />
              </Form.Item>
            </div>
            <div className="part-time-credential-actions">
              <Button
                type="primary"
                htmlType="submit"
                loading={tokenMutation.isPending}
                icon={<SafetyCertificateOutlined />}
              >
                获取 Token
              </Button>
              {token && <Button onClick={clearToken}>清除缓存</Button>}
            </div>
          </Form>

          {token ? (
            <Alert
              type="success"
              showIcon
              title="AccessToken 已缓存"
              description={
                <div className="part-time-token-meta">
                  <Text type="secondary">Bearer {token.accessToken.slice(0, 12)}...</Text>
                  <Text type="secondary">Scope: {token.scope || '—'}</Text>
                  <Text type="secondary">TokenType: {token.tokenType || 'Bearer'}</Text>
                </div>
              }
            />
          ) : (
            <Alert
              type="info"
              showIcon
              title="请先获取访问令牌"
              description="获取成功后，批次、工资行和工资条查询才会开放。"
            />
          )}
        </section>

        <section className="part-time-section">
          <div className="part-time-section-heading">
            <div>
              <Text className="part-time-section-kicker">PAYROLL BATCHES</Text>
              <Title level={4} className="part-time-section-title">
                兼职批次
              </Title>
              <Text type="secondary">仅展示已审批、已发薪和已归档批次</Text>
            </div>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => batchesQuery.refetch()}
              loading={batchesQuery.isFetching}
              disabled={!accessToken}
            >
              刷新
            </Button>
          </div>

          <Form
            form={batchFilterForm}
            layout="vertical"
            className="part-time-filter-form"
            initialValues={{ status: 'paid' }}
            onFinish={handleBatchSubmit}
          >
            <Form.Item name="period" label="工资周期">
              <DatePicker picker="month" format="YYYY-MM" placeholder="全部周期" />
            </Form.Item>
            <Form.Item name="status" label="批次状态">
              <Select allowClear options={ptStatusOptions} placeholder="全部状态" />
            </Form.Item>
            <div className="part-time-filter-actions">
              <Button type="primary" htmlType="submit" disabled={!accessToken}>
                查询
              </Button>
              <Button onClick={handleBatchReset}>重置</Button>
            </div>
          </Form>

          <div className="part-time-table-shell">
            <ProTable<OpenApiPayrollBatchDto>
              rowKey={(record) =>
                String(
                  record.batchId ??
                    record.id ??
                    record.batchNo ??
                    `${record.periodLabel}-${record.lineCount}`,
                )
              }
              columns={batchColumns}
              dataSource={batchesQuery.data?.list ?? []}
              loading={batchesQuery.isLoading || batchesQuery.isFetching}
              search={false}
              options={false}
              cardBordered={false}
              pagination={{
                current: batchParams.current,
                pageSize: batchParams.size,
                total: batchesQuery.data?.total ?? 0,
                showSizeChanger: true,
                showQuickJumper: true,
                onChange: (current, pageSize) => handleBatchTableChange(current, pageSize),
              }}
              onRow={(record) => ({
                onClick: () => {
                  if (!accessToken) return;
                  setSelectedBatch(record);
                  setLineParams((prev) => ({ ...prev, current: 1 }));
                },
              })}
              rowClassName={(record) =>
                selectedBatch &&
                ((record.batchId && selectedBatch.batchId === record.batchId) ||
                  (record.id && selectedBatch.id === record.id) ||
                  (record.batchNo && selectedBatch.batchNo === record.batchNo))
                  ? 'ant-table-row-selected'
                  : ''
              }
              size="small"
              scroll={{ x: 920 }}
              locale={{
                emptyText: accessToken ? '暂无符合条件的批次' : '请先获取 AccessToken',
              }}
            />
          </div>
        </section>

        {selectedBatch && accessToken && (
          <section className="part-time-section part-time-lines-section">
            <div className="part-time-section-heading">
              <div>
                <Text className="part-time-section-kicker">PAYROLL LINES</Text>
                <Title level={4} className="part-time-section-title">
                  工资行明细
                </Title>
              </div>
              <Text type="secondary">
                {selectedBatch.batchNo || selectedBatch.batchId} · 共{' '}
                {selectedBatch.lineCount ?? linesQuery.data?.total ?? 0} 条
              </Text>
            </div>

            <div className="part-time-detail-summary">
              <div>
                <Text type="secondary">工资周期</Text>
                <Text strong>{selectedBatch.periodLabel ?? '—'}</Text>
              </div>
              <div>
                <Text type="secondary">批次状态</Text>
                <Tag color={selectedBatch.status === 'paid' ? 'green' : 'blue'}>
                  {formatPtStatus(selectedBatch.status)}
                </Tag>
              </div>
              <div>
                <Text type="secondary">工资行数</Text>
                <Text strong>{selectedBatch.lineCount ?? linesQuery.data?.total ?? 0}</Text>
              </div>
              <div>
                <Text type="secondary">支付完成</Text>
                <Text strong>
                  {selectedBatch.paidAt
                    ? dayjs(selectedBatch.paidAt).format('YYYY-MM-DD HH:mm')
                    : '—'}
                </Text>
              </div>
            </div>

            <div className="part-time-line-toolbar">
              <Input.Search
                placeholder="按员工引用过滤，如 emp:E0001"
                allowClear
                enterButton="筛选"
                onSearch={handleLineSearch}
                disabled={!accessToken}
              />
            </div>

            <div className="part-time-table-shell">
              <ProTable<OpenApiPayrollLineDto>
                rowKey={(record) =>
                  String(
                    record.id ??
                      record.lineNo ??
                      record.employeeRef ??
                      `${record.employeeRef}-${record.updatedAt}`,
                  )
                }
                columns={lineColumns}
                dataSource={linesQuery.data?.list ?? []}
                loading={linesQuery.isLoading || linesQuery.isFetching}
                search={false}
                options={false}
                cardBordered={false}
                pagination={{
                  current: lineParams.current,
                  pageSize: lineParams.size,
                  total: linesQuery.data?.total ?? 0,
                  showSizeChanger: true,
                  onChange: (current, pageSize) =>
                    setLineParams((prev) => ({ ...prev, current, size: pageSize })),
                }}
                size="small"
                scroll={{ x: 980 }}
                locale={{ emptyText: '暂无工资行数据' }}
              />
            </div>
          </section>
        )}

        <section className="part-time-section part-time-payslip-section">
          <div className="part-time-section-heading">
            <div>
              <Text className="part-time-section-kicker">PAYSLIPS</Text>
              <Title level={4} className="part-time-section-title">
                工资条查询
              </Title>
            </div>
          </div>

          <Form<{ employeeRef: string; period: dayjs.Dayjs }>
            layout="vertical"
            className="part-time-payslip-form"
            onFinish={handlePayslipSearch}
          >
            <Form.Item
              name="employeeRef"
              label="员工引用"
              rules={[{ required: true, message: '请输入员工引用 (emp:E0001)' }]}
            >
              <Input placeholder="emp:E0001" disabled={!accessToken} />
            </Form.Item>
            <Form.Item
              name="period"
              label="工资周期"
              rules={[{ required: true, message: '请选择工资周期' }]}
            >
              <DatePicker picker="month" format="YYYY-MM" disabled={!accessToken} />
            </Form.Item>
            <div className="part-time-filter-actions">
              <Button
                type="primary"
                htmlType="submit"
                loading={payslipQuery.isLoading}
                disabled={!accessToken}
              >
                查询
              </Button>
            </div>
          </Form>

          {!accessToken && (
            <Alert
              type="info"
              showIcon
              title="请先获取 AccessToken"
              description="输入客户端凭证并获取令牌后，才能查询工资条。"
            />
          )}

          {accessToken && payslipQuery.isFetching && (
            <Text type="secondary">正在查询工资条...</Text>
          )}

          {accessToken && payslipQuery.isError && (
            <Alert
              type="error"
              showIcon
              title="查询失败"
              description={(payslipQuery.error as Error)?.message ?? '请检查凭证和网络后重试'}
            />
          )}

          {accessToken &&
            payslipQuery.data &&
            payslipQuery.data.length === 0 &&
            payslipParams && (
              <Alert
                type="info"
                showIcon
                title="未找到工资条"
                description="请核对员工引用、周期或 scope 权限。"
              />
            )}

          {accessToken && payslipQuery.data && payslipQuery.data.length > 0 && (
            <div className="part-time-payslip-list">
              {payslipQuery.data.map((item) => (
                <article className="part-time-payslip-item" key={item.id ?? item.period}>
                  <div className="part-time-payslip-heading">
                    <div>
                      <Text strong>{`${item.employeeRef} · ${item.period}`}</Text>
                      <Text type="secondary">
                        {item.departments?.join(' / ') ?? '未分配部门'}
                      </Text>
                    </div>
                    <Space size={16} wrap>
                      <Text>实发：{formatCurrency(item.netAmount, item.currency)}</Text>
                      <Text type="secondary">
                        更新于{' '}
                        {item.updatedAt ? dayjs(item.updatedAt).format('YYYY-MM-DD HH:mm') : '—'}
                      </Text>
                    </Space>
                  </div>
                  <div className="part-time-table-shell">
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
                      cardBordered={false}
                      size="small"
                    />
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </PageContainer>
  );
};

export default PartTimeReadonly;
