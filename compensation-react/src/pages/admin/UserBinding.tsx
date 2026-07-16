import React, { useState, useRef, useEffect } from 'react';
import { Button, Space, Tag, App as AntdApp, Typography, Modal, Form, Select, Input } from 'antd';
import { PageContainer, ProTable, type ProColumns, type ActionType, type ProFormInstance } from '@ant-design/pro-components';
import {
  UserOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  LinkOutlined,
  DisconnectOutlined,
  ExclamationCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import {
  useUserBindingsQuery,
  useBindUserMutation,
  useUnbindUserMutation,
  useBindEmployeeMutation,
  type UserBindingParams,
} from '@services/queries/userBinding';
import type { UserBindingItem, Platform } from '../../types/api';
import { getPagedRecords } from '../../types/api';

const { Text } = Typography;

const UserBindingPage: React.FC = () => {
  const [searchParams, setSearchParams] = useState<UserBindingParams>({
    current: 1,
    pageSize: 10,
  });
  const [bindModalVisible, setBindModalVisible] = useState(false);
  const [currentUser, setCurrentUser] = useState<UserBindingItem | null>(null);
  const [bindForm] = Form.useForm<{ provider?: Platform; subjectId: string }>();
  const [employeeModalVisible, setEmployeeModalVisible] = useState(false);
  const [employeeForm] = Form.useForm<{ employeeId: string }>();

  const actionRef = useRef<ActionType>();
  const formRef = useRef<ProFormInstance>();

  const { message, modal } = AntdApp.useApp();

  // 查询用户绑定列表
  const userBindingsQuery = useUserBindingsQuery(searchParams);

  // 绑定/解绑操作
  const bindUserMutation = useBindUserMutation();
  const unbindUserMutation = useUnbindUserMutation();
  const bindEmployeeMutation = useBindEmployeeMutation();

  const getRecordProvider = (user: UserBindingItem) => user.provider as Platform | null | undefined;
  const getRecordSubjectId = (user: UserBindingItem) => user.subjectId;

  const handleBind = async (id: number, provider: Platform, subjectId: string) => {
    try {
      await bindUserMutation.mutateAsync({
        id,
        provider,
        subjectId: subjectId.trim(),
      });
      message.success('用户绑定成功');
      actionRef.current?.reload();
    } catch (error: any) {
      const status = error?.response?.status;
      const msg = error?.response?.data?.message || error?.message || '网络错误';
      if (status === 409) {
        message.warning(msg);
      } else {
        message.error(`绑定失败：${msg}`);
      }
    }
  };

  const handleUnbind = async (id: number) => {
    try {
      await unbindUserMutation.mutateAsync(id);
      message.success('用户解绑成功');
      actionRef.current?.reload();
    } catch (error: any) {
      const msg = error?.response?.data?.message || error?.message || '网络错误';
      message.error(`解绑失败：${msg}`);
      throw error;
    }
  };

  const confirmUnbind = (user: UserBindingItem) => {
    if (!getRecordProvider(user)) {
      message.warning('该用户尚未绑定平台');
      return;
    }
    modal.confirm({
      title: '确认解绑',
      content: `确定要解绑用户 ${user.username} 吗？`,
      icon: <ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />,
      okButtonProps: { danger: true },
      onOk: () => handleUnbind(user.id),
    });
  };

  const openBindModal = (user: UserBindingItem) => {
    setCurrentUser(user);
    setBindModalVisible(true);
  };

  const submitBindModal = async () => {
    if (!currentUser) return;
    try {
      const values = await bindForm.validateFields();
      await handleBind(currentUser.id, values.provider as Platform, values.subjectId);
      setBindModalVisible(false);
      setCurrentUser(null);
      bindForm.resetFields();
      actionRef.current?.reload();
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error?.message || '绑定失败');
    }
  };

  const openEmployeeModal = (user: UserBindingItem) => {
    setCurrentUser(user);
    setEmployeeModalVisible(true);
  };

  useEffect(() => {
    if (!bindModalVisible || !currentUser) {
      return;
    }
    bindForm.setFieldsValue({
      provider: getRecordProvider(currentUser) ?? undefined,
      subjectId: getRecordSubjectId(currentUser) ?? '',
    });
  }, [bindModalVisible, currentUser, bindForm]);

  useEffect(() => {
    if (!employeeModalVisible || !currentUser) {
      return;
    }
    employeeForm.setFieldsValue({ employeeId: currentUser.employeeId ? String(currentUser.employeeId) : '' });
  }, [employeeModalVisible, currentUser, employeeForm]);

  const submitEmployeeModal = async () => {
    if (!currentUser) return;
    try {
      const values = await employeeForm.validateFields();
      const numericEmployeeId = Number(values.employeeId.trim());
      if (Number.isNaN(numericEmployeeId)) {
        message.warning('员工ID需为数字');
        return;
      }
      await bindEmployeeMutation.mutateAsync({
        id: currentUser.id,
        employeeId: numericEmployeeId,
      });
      message.success('已关联员工');
      setEmployeeModalVisible(false);
      setCurrentUser(null);
      employeeForm.resetFields();
      actionRef.current?.reload();
    } catch (error: any) {
      if (error?.errorFields) return;
      const status = error?.response?.status;
      const msg = error?.response?.data?.message || error?.message || '指定失败';
      if (status === 409) {
        message.warning(msg);
      } else {
        message.error(msg);
      }
    }
  };

  const getPlatformName = (platform?: Platform | null | string) => {
    const platformMap: Record<string, string> = {
      wechat: '企业微信',
      wecom: '企业微信',
      qywx: '企业微信',
      wx: '企业微信',
      dingtalk: '钉钉',
      dingding: '钉钉',
      dd: '钉钉',
      feishu: '飞书',
      lark: '飞书',
    };
    return platform ? platformMap[String(platform)] || String(platform) : '未绑定';
  };

  const columns: ProColumns<UserBindingItem>[] = [
    {
      title: '用户ID',
      dataIndex: 'id',
      width: 120,
      copyable: true,
      ellipsis: true,
    },
    {
      title: '用户名',
      dataIndex: 'username',
      copyable: true,
      formItemProps: {
        rules: [{ required: true, message: '请输入用户名' }],
      },
    },
    {
      title: '平台',
      dataIndex: 'provider',
      valueType: 'select',
      valueEnum: {
        wechat: { text: '企业微信' },
        dingtalk: { text: '钉钉' },
        feishu: { text: '飞书' },
      },
      render: (_, record) => {
        const provider = getRecordProvider(record);
        return provider ? (
          <Tag color={provider === 'wechat' ? 'green' : provider === 'dingtalk' ? 'blue' : 'orange'}>
            {getPlatformName(provider)}
          </Tag>
        ) : (
          <Tag color="default">未绑定</Tag>
        );
      },
    },
    {
      title: '平台账号',
      dataIndex: 'subjectId',
      search: false,
      render: (_, record) => {
        const subjectId = getRecordSubjectId(record);
        return subjectId ? <Text code>{subjectId}</Text> : <Text type="secondary">-</Text>;
      },
    },
    {
      title: '绑定状态',
      dataIndex: 'bound',
      valueType: 'select',
      valueEnum: {
        true: { text: '已绑定', status: 'Success' },
        false: { text: '未绑定', status: 'Default' },
      },
      render: (_, record) => (
        <Space>
          {record.bound ? (
            <Tag icon={<CheckCircleOutlined />} color="success">
              已绑定
            </Tag>
          ) : (
            <Tag icon={<CloseCircleOutlined />} color="default">
              未绑定
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: '员工',
      dataIndex: 'employeeName',
      render: (_, record) => (
        record.employeeId ? (
          <Space size={4}>
            <Text>{record.employeeName || '-'}</Text>
            <Text type="secondary">#{record.employeeId}</Text>
          </Space>
        ) : (
          <Text type="secondary">未关联</Text>
        )
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      render: (_, record) => [
        record.bound ? (
          <Button
            key="unbind"
            size="small"
            danger
            icon={<DisconnectOutlined />}
            loading={unbindUserMutation.isPending}
            onClick={() => confirmUnbind(record)}
            disabled={!getRecordProvider(record)}
          >
            解绑
          </Button>
        ) : (
          <Button
            key="bind"
            size="small"
            type="primary"
            icon={<LinkOutlined />}
            onClick={() => openBindModal(record)}
            loading={bindUserMutation.isPending}
          >
            绑定
          </Button>
        ),
        <Button
          key="bindEmployee"
          size="small"
          onClick={() => openEmployeeModal(record)}
          disabled={bindEmployeeMutation.isPending}
        >
          指定员工
        </Button>,
      ],
    },
  ];

  const isLoading = userBindingsQuery.isLoading || bindUserMutation.isPending || unbindUserMutation.isPending || bindEmployeeMutation.isPending;

  return (
    <PageContainer
      header={{
        title: '用户-平台绑定',
        subTitle: '管理用户与第三方平台的绑定关系',
      }}
      extra={[
        <Button
          key="refresh"
          icon={<ReloadOutlined />}
          onClick={() => {
            actionRef.current?.reload();
          }}
          loading={userBindingsQuery.isLoading}
        >
          刷新
        </Button>
      ]}
    >
      <ProTable<UserBindingItem>
        columns={columns}
        actionRef={actionRef}
        formRef={formRef}
        request={async (params) => {
          const newParams: UserBindingParams = {
            ...searchParams,
            current: params.current || 1,
            pageSize: params.pageSize || 10,
            username: params.username,
            provider: params.provider,
            bound: params.bound !== undefined ? params.bound === 'true' : undefined,
          };

          setSearchParams(newParams);

          try {
            const result = await userBindingsQuery.refetch();
            const data = result.data;

            return {
              data: getPagedRecords(data),
              success: true,
              total: data?.total || 0,
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
          searchText: '搜索',
          resetText: '重置',
          collapsed: false,
          collapseRender: false,
        }}
	        pagination={{
	          pageSize: searchParams.pageSize,
	          current: searchParams.current,
	          showSizeChanger: true,
	          showQuickJumper: true,
	          showTotal: (total = 0, range) => {
	            const [start, end] = range ?? [0, 0];
	            return `第 ${start}-${end} 条/共 ${total} 条`;
	          },
	        }}
        loading={isLoading}
        // 空状态处理
        locale={{
          emptyText: userBindingsQuery.isError ? (
            <div style={{ textAlign: 'center', padding: '40px 20px' }}>
              <ExclamationCircleOutlined style={{ fontSize: 48, color: '#ff4d4f', marginBottom: 16 }} />
              <div style={{ fontSize: 16, marginBottom: 8 }}>数据加载失败</div>
              <div style={{ color: '#8c8c8c', marginBottom: 16 }}>
        {userBindingsQuery.error?.message || '网络错误，请检查网络连接'}
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
              <UserOutlined style={{ fontSize: 48, color: '#d9d9d9', marginBottom: 16 }} />
              <div style={{ fontSize: 16, marginBottom: 8 }}>暂无数据</div>
              <div style={{ color: '#8c8c8c' }}>
                {Object.keys(searchParams).some(key => key !== 'current' && key !== 'pageSize' && searchParams[key as keyof UserBindingParams])
                  ? '没有找到符合条件的用户绑定记录'
                  : '还没有用户绑定记录'}
              </div>
            </div>
          ),
        }}
        options={{
          reload: true,
          density: true,
          setting: true,
        }}
        scroll={{ x: 800 }}
      />

      <Modal
        title={currentUser ? `绑定平台 - ${currentUser.username}` : '绑定平台'}
        open={bindModalVisible}
        onCancel={() => {
          setBindModalVisible(false);
          setCurrentUser(null);
          bindForm.resetFields();
        }}
        onOk={submitBindModal}
        confirmLoading={bindUserMutation.isPending}
        destroyOnHidden
      >
        <Form form={bindForm} layout="vertical">
          <Form.Item
            name="provider"
            label="选择平台"
            rules={[{ required: true, message: '请选择绑定平台' }]}
          >
            <Select placeholder="请选择平台">
              <Select.Option value="wechat">企业微信</Select.Option>
              <Select.Option value="dingtalk">钉钉</Select.Option>
              <Select.Option value="feishu">飞书</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="subjectId"
            label="平台账号"
            rules={[{ required: true, message: '请输入平台账号' }]}
          >
            <Input placeholder="请输入平台用户标识" autoComplete="off" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={currentUser ? `指定员工 - ${currentUser.username}` : '指定员工'}
        open={employeeModalVisible}
        onCancel={() => {
          setEmployeeModalVisible(false);
          setCurrentUser(null);
          employeeForm.resetFields();
        }}
        onOk={submitEmployeeModal}
        confirmLoading={bindEmployeeMutation.isPending}
        destroyOnHidden
      >
        <Form form={employeeForm} layout="vertical">
          <Form.Item
            name="employeeId"
            label="员工ID"
            rules={[{ required: true, message: '请输入员工ID' }]}
          >
            <Input placeholder="请输入员工ID" autoComplete="off" />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default UserBindingPage;
