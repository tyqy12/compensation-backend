import React, { useMemo, useState } from 'react';
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components';
import {
  App as AntdApp,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  Checkbox,
} from 'antd';
import {
  ReloadOutlined,
  PlusOutlined,
  EditOutlined,
  KeyOutlined,
  DeleteOutlined,
  SafetyOutlined,
} from '@ant-design/icons';
import {
  useAppDataGrantsQuery,
  useAppRegistriesQuery,
  useCreateAppDataGrantMutation,
  useCreateAppRegistryMutation,
  useRevokeAppDataGrantMutation,
  useUpdateAppRegistryMutation,
  useRotateAppRegistrySecretMutation,
  type AppRegistryQueryParams,
  type AppRegistryDto,
} from '@services/queries/appRegistry';
import type {
  AppDataGrantDto,
  AppDataGrantScopeType,
  AppRegistryRequest,
} from '@types/openapi';
import { getPagedRecords } from '@types/api';
import dayjs from 'dayjs';

const { Paragraph, Text } = Typography;

const AVAILABLE_SCOPES = [
  { value: 'payroll:read', label: 'payroll:read (批次/工资行)' },
  { value: 'payslip:read', label: 'payslip:read (工资条)' },
];

const DATA_GRANT_SCOPE_OPTIONS: Array<{ value: AppDataGrantScopeType; label: string }> = [
  { value: 'tenant', label: '租户（全部薪酬数据）' },
  { value: 'department', label: '部门（本地部门 ID）' },
  { value: 'employee', label: '员工（员工 ID）' },
  { value: 'payroll_batch', label: '工资批次（批次 ID）' },
];

const DATA_GRANT_SCOPE_LABELS = Object.fromEntries(
  DATA_GRANT_SCOPE_OPTIONS.map((item) => [item.value, item.label]),
) as Record<AppDataGrantScopeType, string>;

type FormValues = {
  appName: string;
  scopes: string[];
  status: boolean;
  ipWhitelist?: string;
  webhookUrl?: string;
  description?: string;
};

type GrantFormValues = {
  scopeType: AppDataGrantScopeType;
  scopeValue: string;
};

const toRequestPayload = (values: FormValues): AppRegistryRequest => {
  const whitelist = values.ipWhitelist
    ? values.ipWhitelist
        .split(/\s+/)
        .map((item) => item.trim())
        .filter(Boolean)
    : [];
  return {
    appName: values.appName.trim(),
    scopes: values.scopes,
    status: values.status ? 'enabled' : 'disabled',
    ipWhitelist: whitelist.length > 0 ? whitelist : undefined,
    webhookUrl: values.webhookUrl?.trim() || undefined,
    description: values.description?.trim() || undefined,
  };
};

const formatWhitelist = (ips?: string[] | null) => (ips && ips.length > 0 ? ips.join('\n') : '');

const columns: ProColumns<AppRegistryDto>[] = [
  {
    title: '应用名称',
    dataIndex: 'appName',
    width: 180,
    ellipsis: true,
    fixed: 'left',
  },
  {
    title: 'Client ID',
    dataIndex: 'clientId',
    copyable: true,
    width: 280,
    ellipsis: true,
  },
  {
    title: 'Scopes',
    dataIndex: 'scopes',
    width: 280,
    render: (_, record) => (
      <Space size={4} wrap>
        {(record.scopes || []).map((scope) => (
          <Tag color="blue" key={scope}>
            {scope}
          </Tag>
        ))}
      </Space>
    ),
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 100,
    align: 'center',
    valueType: 'select',
    valueEnum: {
      enabled: { text: '启用', status: 'Success' },
      disabled: { text: '禁用', status: 'Default' },
    },
    render: (_, record) => (
      <Tag color={record.status === 'enabled' ? 'green' : 'default'}>
        {record.status === 'enabled' ? '启用' : '禁用'}
      </Tag>
    ),
  },
  {
    title: 'IP 白名单',
    dataIndex: 'ipWhitelist',
    width: 180,
    ellipsis: true,
    render: (_, record) => (record.ipWhitelist && record.ipWhitelist.length > 0 ? record.ipWhitelist.join(', ') : '—'),
  },
  {
    title: '更新时间',
    dataIndex: 'updateTime',
    width: 180,
    valueType: 'dateTime',
    render: (_, record) => (record.updateTime ? dayjs(record.updateTime).format('YYYY-MM-DD HH:mm:ss') : '—'),
  },
];

const AppRegistryPage: React.FC = () => {
  const [queryParams, setQueryParams] = useState<AppRegistryQueryParams>({ current: 1, size: 10 });
  const [modalVisible, setModalVisible] = useState(false);
  const [editing, setEditing] = useState<AppRegistryDto | null>(null);
  const [grantApp, setGrantApp] = useState<AppRegistryDto | null>(null);
  const [form] = Form.useForm<FormValues>();
  const [grantForm] = Form.useForm<GrantFormValues>();

  const { message, modal } = AntdApp.useApp();

  const listQuery = useAppRegistriesQuery(queryParams);
  const createMutation = useCreateAppRegistryMutation();
  const updateMutation = useUpdateAppRegistryMutation();
  const rotateMutation = useRotateAppRegistrySecretMutation();
  const grantQuery = useAppDataGrantsQuery(grantApp?.id ?? 0, { enabled: !!grantApp });
  const createGrantMutation = useCreateAppDataGrantMutation();
  const revokeGrantMutation = useRevokeAppDataGrantMutation();

  const dataSource = getPagedRecords(listQuery.data);

  const stats = useMemo(() => {
    const enabled = dataSource.filter((item) => item.status === 'enabled').length;
    const disabled = dataSource.filter((item) => item.status === 'disabled').length;
    return { enabled, disabled };
  }, [dataSource]);

  const openCreateModal = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ scopes: ['payroll:read'], status: true });
    setModalVisible(true);
  };

  const openEditModal = (record: AppRegistryDto) => {
    setEditing(record);
    form.setFieldsValue({
      appName: record.appName,
      scopes: record.scopes || [],
      status: record.status === 'enabled',
      ipWhitelist: formatWhitelist(record.ipWhitelist),
      webhookUrl: record.webhookUrl || undefined,
      description: record.description || undefined,
    });
    setModalVisible(true);
  };

  const closeModal = () => {
    setModalVisible(false);
    setEditing(null);
    form.resetFields();
  };

  const openGrantDrawer = (record: AppRegistryDto) => {
    setGrantApp(record);
    grantForm.resetFields();
    grantForm.setFieldsValue({ scopeType: 'employee' });
  };

  const closeGrantDrawer = () => {
    setGrantApp(null);
    grantForm.resetFields();
  };

  const handleGrantSubmit = async (values: GrantFormValues) => {
    if (!grantApp) return;
    try {
      await createGrantMutation.mutateAsync({
        id: grantApp.id,
        payload: { scopeType: values.scopeType, scopeValue: values.scopeValue.trim() },
      });
      message.success('数据范围已授权');
      grantForm.resetFields(['scopeValue']);
      grantForm.setFieldsValue({ scopeType: values.scopeType });
      await grantQuery.refetch();
    } catch (error: any) {
      message.error(error?.response?.data?.message || error?.message || '授权失败');
    }
  };

  const handleRevokeGrant = async (grant: AppDataGrantDto) => {
    if (!grantApp) return;
    try {
      await revokeGrantMutation.mutateAsync({ id: grantApp.id, grantId: grant.id });
      message.success('数据范围已撤销');
      await grantQuery.refetch();
    } catch (error: any) {
      message.error(error?.response?.data?.message || error?.message || '撤销失败');
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const payload = toRequestPayload(values);

      if (editing) {
        await updateMutation.mutateAsync({ id: editing.id, payload });
        message.success('应用已更新');
      } else {
        const created = await createMutation.mutateAsync(payload);
        message.success('应用创建成功');
        if (created.clientSecret) {
          modal.success({
            title: '请立即保存 Client Secret',
            icon: <SafetyOutlined style={{ color: '#52c41a' }} />,
            content: (
              <Space size={8} style={{ marginTop: 12 }}>
                <Paragraph copyable={{ text: created.clientId }}>
                  <Text strong>Client ID：</Text>
                  {created.clientId}
                </Paragraph>
                <Paragraph copyable={{ text: created.clientSecret }}>
                  <Text strong>Client Secret：</Text>
                  {created.clientSecret}
                </Paragraph>
                <Text type="secondary">关闭弹窗后将无法再次查看密钥，请妥善存储。</Text>
              </Space>
            ),
          });
        }
      }
      closeModal();
      await listQuery.refetch();
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error?.response?.data?.message || error?.message || '提交失败');
    }
  };

  const handleRotateSecret = async (record: AppRegistryDto) => {
    try {
      const result = await rotateMutation.mutateAsync(record.id);
      modal.success({
        title: '密钥已重置',
        content: (
          <Space size={8} style={{ marginTop: 12 }}>
            <Paragraph copyable={{ text: result.clientId }}>
              <Text strong>Client ID：</Text>
              {result.clientId}
            </Paragraph>
            <Paragraph copyable={{ text: result.clientSecret }}>
              <Text strong>新的 Client Secret：</Text>
              {result.clientSecret}
            </Paragraph>
            <Text type="secondary">旧密钥已失效，请同步给调用方。</Text>
          </Space>
        ),
      });
      await listQuery.refetch();
    } catch (error: any) {
      message.error(error?.response?.data?.message || error?.message || '密钥轮换失败');
    }
  };

  const handleTableChange = (page: { current?: number; pageSize?: number }) => {
    setQueryParams((prev) => ({
      ...prev,
      current: page.current ?? prev.current ?? 1,
      size: page.pageSize ?? prev.size ?? 10,
    }));
  };

  const handleSearchSubmit = (values: Record<string, any>) => {
    setQueryParams((prev) => ({
      ...prev,
      current: 1,
      keyword: values.appName ? values.appName.trim() : undefined,
      status: values.status || undefined,
    }));
  };

  const handleSearchReset = () => {
    setQueryParams({ current: 1, size: queryParams.size ?? 10 });
  };

  return (
    <PageContainer
      header={{
        title: '外部应用注册',
        breadcrumb: {},
        extra: [
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
            新建应用
          </Button>,
        ],
      }}
    >
      {/* 统计卡片区域 */}
      <div style={{ marginBottom: 16 }}>
        <Space size={12}>
          <Card size="small" style={{ minWidth: 140 }}>
            <div style={{ color: 'rgba(0, 0, 0, 0.45)', fontSize: 14, marginBottom: 8 }}>启用应用</div>
            <div style={{ fontSize: 24, fontWeight: 500, color: '#52c41a' }}>{stats.enabled}</div>
          </Card>
          <Card size="small" style={{ minWidth: 140 }}>
            <div style={{ color: 'rgba(0, 0, 0, 0.45)', fontSize: 14, marginBottom: 8 }}>禁用应用</div>
            <div style={{ fontSize: 24, fontWeight: 500, color: '#8c8c8c' }}>{stats.disabled}</div>
          </Card>
        </Space>
      </div>

      {/* 表格区域 */}
      <ProTable<AppRegistryDto>
        rowKey={(record) => String(record.id)}
        columns={[
          ...columns,
          {
            title: '操作',
            valueType: 'option',
            width: 200,
            fixed: 'right',
            render: (_, record) => [
              <Button key="edit" type="link" size="small" icon={<EditOutlined />} onClick={() => openEditModal(record)}>
                编辑
              </Button>,
              <Button
                key="rotate"
                type="link"
                size="small"
                icon={<KeyOutlined />}
                onClick={() => handleRotateSecret(record)}
                loading={rotateMutation.isPending}
              >
                轮换密钥
              </Button>,
              <Button
                key="grants"
                type="link"
                size="small"
                icon={<SafetyOutlined />}
                onClick={() => openGrantDrawer(record)}
              >
                数据范围
              </Button>,
            ],
          },
        ]}
        dataSource={dataSource}
        loading={listQuery.isLoading || listQuery.isFetching}
        search={{
          labelWidth: 'auto',
          optionRender: (searchConfig, formProps, dom) => [
            ...dom,
            <Button key="clear" onClick={handleSearchReset}>
              重置
            </Button>,
          ],
        }}
        onSubmit={handleSearchSubmit}
        pagination={{
          current: queryParams.current,
          pageSize: queryParams.size,
          total: listQuery.data?.total ?? 0,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条`,
          onChange: (current, pageSize) => handleTableChange({ current, pageSize }),
        }}
        toolBarRender={() => [
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => listQuery.refetch()} loading={listQuery.isFetching}>
            刷新
          </Button>,
        ]}
        options={{
          reload: false,
          density: true,
          setting: true,
        }}
        scroll={{ x: 1200 }}
      />

      <Modal
        title={editing ? '编辑应用' : '新建应用'}
        open={modalVisible}
        onCancel={closeModal}
        onOk={handleSubmit}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        width={520}
      >
        <Form<FormValues> form={form} layout="vertical" initialValues={{ scopes: ['payroll:read'], status: true }}>
          <Form.Item
            name="appName"
            label="应用名称"
            rules={[{ required: true, message: '请输入应用名称' }]}
          >
            <Input placeholder="例如：渠道合作方" />
          </Form.Item>

          <Form.Item
            name="scopes"
            label="授权范围"
            rules={[{ required: true, message: '至少选择一个 scope' }]}
          >
            <Checkbox.Group options={AVAILABLE_SCOPES} />
          </Form.Item>

          <Form.Item name="status" label="状态" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>

          <Form.Item
            name="ipWhitelist"
            label="IP 白名单"
            tooltip="多条 IP 使用换行或空格分隔"
          >
            <Input.TextArea rows={3} placeholder="例如：1.1.1.1\n2.2.2.2" />
          </Form.Item>

          <Form.Item name="webhookUrl" label="Webhook 地址">
            <Input placeholder="https://example.com/webhook" />
          </Form.Item>

          <Form.Item name="description" label="备注">
            <Input.TextArea rows={2} placeholder="可选的用途说明" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={grantApp ? `数据范围：${grantApp.appName}` : '数据范围'}
        open={!!grantApp}
        onClose={closeGrantDrawer}
        width={620}
      >
        <Form<GrantFormValues>
          form={grantForm}
          layout="vertical"
          onFinish={handleGrantSubmit}
          initialValues={{ scopeType: 'employee' }}
        >
          <Form.Item
            name="scopeType"
            label="范围类型"
            rules={[{ required: true, message: '请选择范围类型' }]}
          >
            <Select options={DATA_GRANT_SCOPE_OPTIONS} />
          </Form.Item>
          <Form.Item
            name="scopeValue"
            label="范围值"
            extra="租户使用配置的租户标识；部门、员工和批次使用数字 ID。禁止使用通配符。"
            rules={[{ required: true, whitespace: true, message: '请输入范围值' }]}
          >
            <Input placeholder="例如：1001" />
          </Form.Item>
          <Button
            type="primary"
            htmlType="submit"
            icon={<PlusOutlined />}
            loading={createGrantMutation.isPending}
          >
            添加授权
          </Button>
        </Form>

        <Table<AppDataGrantDto>
          style={{ marginTop: 24 }}
          rowKey={(record) => String(record.id)}
          size="small"
          loading={grantQuery.isLoading || grantQuery.isFetching}
          dataSource={grantQuery.data || []}
          pagination={false}
          locale={{ emptyText: '尚未配置数据范围，含薪酬 scope 的应用将无法获取数据' }}
          columns={[
            {
              title: '类型',
              dataIndex: 'scopeType',
              render: (value: AppDataGrantScopeType) => DATA_GRANT_SCOPE_LABELS[value] || value,
            },
            { title: '范围值', dataIndex: 'scopeValue', ellipsis: true },
            {
              title: '操作',
              width: 90,
              render: (_, record) => (
                <Popconfirm
                  title="撤销这条数据授权？"
                  okText="撤销"
                  cancelText="取消"
                  onConfirm={() => handleRevokeGrant(record)}
                >
                  <Button
                    type="link"
                    danger
                    size="small"
                    icon={<DeleteOutlined />}
                    loading={revokeGrantMutation.isPending}
                  >
                    撤销
                  </Button>
                </Popconfirm>
              ),
            },
          ]}
        />
      </Drawer>
    </PageContainer>
  );
};

export default AppRegistryPage;
