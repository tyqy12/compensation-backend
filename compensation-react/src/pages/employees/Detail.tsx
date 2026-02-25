import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Space, Tag, Typography, Descriptions, Modal, Form, Input, InputNumber, Select, DatePicker, Row, Col, Alert, App as AntdApp } from 'antd';
import { PageContainer, ProCard } from '@ant-design/pro-components';
import {
  EditOutlined,
  EyeOutlined,
  EyeInvisibleOutlined,
  LinkOutlined,
  PhoneOutlined,
  MailOutlined,
  BankOutlined,
  CalendarOutlined,
  ReloadOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import {
  useEmployeeQuery,
  useUpdateEmployeeMutation,
  useBindPlatformMutation,
  useToggleEmployeeOfflineMutation,
  useAssignEmployeeManagerMutation,
  useEmployeeIdCardQuery,
  useEmployeeBankAccountQuery,
  type Employee,
  type EmployeeFormData,
  type PlatformBindData,
} from '@services/queries/employee';
import dayjs from 'dayjs';

const { Text } = Typography;
const { Option } = Select;

const EmployeeDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [editModalVisible, setEditModalVisible] = useState(false);
  const [bindModalVisible, setBindModalVisible] = useState(false);
  const [managerModalVisible, setManagerModalVisible] = useState(false);
  const [sensitiveDataVisible, setSensitiveDataVisible] = useState({
    idCard: false,
    bankAccount: false,
  });

  const [editForm] = Form.useForm();
  const [bindForm] = Form.useForm();
  const [managerForm] = Form.useForm<{ managerId: number }>();

  const { message, modal } = AntdApp.useApp();

  // 查询员工详情
  const employeeQuery = useEmployeeQuery(id!, { enabled: !!id });

  // 查询敏感信息（仅在需要时）
  const idCardQuery = useEmployeeIdCardQuery(
    parseInt(id!),
    { enabled: sensitiveDataVisible.idCard && !!id }
  );

  const bankAccountQuery = useEmployeeBankAccountQuery(
    parseInt(id!),
    { enabled: sensitiveDataVisible.bankAccount && !!id }
  );

  // 操作mutations
  const updateEmployeeMutation = useUpdateEmployeeMutation();
  const bindPlatformMutation = useBindPlatformMutation();
  const toggleOfflineMutation = useToggleEmployeeOfflineMutation();
  const assignManagerMutation = useAssignEmployeeManagerMutation();

  const employee = employeeQuery.data;

  // 填充编辑表单
  useEffect(() => {
    if (employee && editModalVisible) {
      editForm.setFieldsValue({
        ...employee,
        hireDate: employee.hireDate ? dayjs(employee.hireDate) : undefined,
      });
    }
  }, [employee, editModalVisible, editForm]);

  // 返回列表（保留搜索参数）
  const handleBackToList = () => {
    const queryString = searchParams.toString();
    navigate(queryString ? `/employees?${queryString}` : '/employees');
  };

  // 更新员工信息
  const handleUpdate = async (values: EmployeeFormData) => {
    if (!employee) return;

    try {
      await updateEmployeeMutation.mutateAsync({
        id: employee.id,
        ...values,
        hireDate: values.hireDate ? dayjs(values.hireDate).format('YYYY-MM-DD') : undefined,
      });
      message.success('员工信息更新成功');
      setEditModalVisible(false);
      employeeQuery.refetch();
    } catch (error: any) {
      message.error(`更新失败：${error.message || '网络错误'}`);
    }
  };

  // 绑定平台
  const handleBindPlatform = async (values: PlatformBindData) => {
    if (!employee) return;

    try {
      await bindPlatformMutation.mutateAsync({
        id: employee.id,
        ...values,
      });
      message.success('平台绑定成功');
      setBindModalVisible(false);
      bindForm.resetFields();
      employeeQuery.refetch();
    } catch (error: any) {
      message.error(`绑定失败：${error.message || '网络错误'}`);
    }
  };

  const handleToggleOffline = () => {
    if (!employee) return;
    const nextValue = !employee.offline;

    modal.confirm({
      title: nextValue ? '确认标记为离线员工？' : '取消离线标记',
      content: nextValue
        ? '标记后该员工将在审批、同步中视为离线，请确保已指定负责人。'
        : '取消后该员工将恢复为在线处理。',
      icon: <ExclamationCircleOutlined />,
      okButtonProps: { loading: toggleOfflineMutation.isPending },
      onOk: async () => {
        try {
          await toggleOfflineMutation.mutateAsync({ id: employee.id, value: nextValue });
          message.success(nextValue ? '已标记为离线' : '已取消离线标记');
          employeeQuery.refetch();
        } catch (error: any) {
          message.error(`操作失败：${error?.message || '网络错误'}`);
        }
      },
    });
  };

  const openManagerModal = () => {
    if (!employee) return;
    managerForm.setFieldsValue({ managerId: employee.managerId ?? undefined });
    setManagerModalVisible(true);
  };

  const handleAssignManager = async () => {
    if (!employee) return;
    try {
      const { managerId } = await managerForm.validateFields();
      await assignManagerMutation.mutateAsync({ id: employee.id, managerId });
      message.success('负责人已更新');
      setManagerModalVisible(false);
      managerForm.resetFields();
      employeeQuery.refetch();
    } catch (error: any) {
      if (error?.errorFields) return; // 表单校验失败
      message.error(`操作失败：${error?.message || '网络错误'}`);
    }
  };

  // 查看敏感信息
  const handleViewSensitiveData = (type: 'idCard' | 'bankAccount') => {
    modal.confirm({
      title: '查看敏感信息',
      content: (
        <div>
          <p>您即将查看员工的{type === 'idCard' ? '身份证号' : '银行账号'}信息。</p>
          <Alert
            message="此操作将被记录并审计"
            type="warning"
            showIcon
            style={{ marginTop: 8 }}
          />
        </div>
      ),
      icon: <ExclamationCircleOutlined />,
      onOk: () => {
        setSensitiveDataVisible(prev => ({
          ...prev,
          [type]: true,
        }));
      },
    });
  };

  // 隐藏敏感信息
  const handleHideSensitiveData = (type: 'idCard' | 'bankAccount') => {
    setSensitiveDataVisible(prev => ({
      ...prev,
      [type]: false,
    }));
  };

  const getStatusInfo = (status: Employee['status']) => {
    switch (status) {
      case 'active':
        return { text: '在职', color: 'success' as const };
      case 'inactive':
        return { text: '离职', color: 'default' as const };
      case 'suspended':
        return { text: '暂停', color: 'warning' as const };
      default:
        return { text: '未知', color: 'error' as const };
    }
  };

  const getPlatformName = (platformType?: Employee['platformType']) => {
    const platformMap = {
      wechat: '企业微信',
      dingtalk: '钉钉',
      feishu: '飞书',
    };
    return platformType ? platformMap[platformType] : '未绑定';
  };

  if (!id) {
    return (
      <PageContainer>
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <Text type="secondary">无效的员工ID</Text>
        </div>
      </PageContainer>
    );
  }

  if (employeeQuery.isLoading) {
    return (
      <PageContainer>
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <Text>加载中...</Text>
        </div>
      </PageContainer>
    );
  }

  if (employeeQuery.isError || !employee) {
    return (
      <PageContainer>
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <ExclamationCircleOutlined style={{ fontSize: 48, color: '#ff4d4f', marginBottom: 16 }} />
          <div style={{ fontSize: 16, marginBottom: 8 }}>员工信息加载失败</div>
          <div style={{ color: '#8c8c8c', marginBottom: 16 }}>
            {employeeQuery.error?.message || '员工不存在或网络错误'}
          </div>
          <Space>
            <Button onClick={handleBackToList}>返回列表</Button>
            <Button type="primary" onClick={() => employeeQuery.refetch()}>重新加载</Button>
          </Space>
        </div>
      </PageContainer>
    );
  }

  const statusInfo = getStatusInfo(employee.status);

  return (
    <PageContainer
      header={{
        title: employee.name,
        subTitle: `员工详情 - ${employee.employeeId}`,
        onBack: handleBackToList,
      }}
      extra={[
        <Button
          key="edit"
          icon={<EditOutlined />}
          onClick={() => setEditModalVisible(true)}
        >
          编辑信息
        </Button>,
        <Button
          key="offline"
          danger={!employee.offline}
          onClick={handleToggleOffline}
          loading={toggleOfflineMutation.isPending}
        >
          {employee.offline ? '取消离线' : '标记离线'}
        </Button>,
        <Button
          key="manager"
          onClick={openManagerModal}
          loading={assignManagerMutation.isPending}
        >
          指定负责人
        </Button>,
        !employee.platformType && (
          <Button
            key="bind"
            type="primary"
            icon={<LinkOutlined />}
            onClick={() => setBindModalVisible(true)}
          >
            绑定平台
          </Button>
        ),
        <Button
          key="refresh"
          icon={<ReloadOutlined />}
          onClick={() => employeeQuery.refetch()}
          loading={employeeQuery.isLoading}
        >
          刷新
        </Button>,
      ].filter(Boolean)}
    >
      <Row gutter={[16, 16]}>
        {/* 基本信息 */}
        <Col xs={24} lg={12}>
          <ProCard title="基本信息" headerBordered>
            <Descriptions column={1} size="middle">
              <Descriptions.Item label="员工ID">
                <Text code copyable>
                  {employee.employeeId}
                </Text>
              </Descriptions.Item>

              <Descriptions.Item label="姓名">
                <Text strong>{employee.name}</Text>
              </Descriptions.Item>

              <Descriptions.Item label="状态">
                <Tag color={statusInfo.color}>{statusInfo.text}</Tag>
              </Descriptions.Item>

              <Descriptions.Item label="部门">
                {employee.department || <Text type="secondary">-</Text>}
              </Descriptions.Item>

              <Descriptions.Item label="职位">
                {employee.position || <Text type="secondary">-</Text>}
              </Descriptions.Item>

              <Descriptions.Item label="入职日期">
                {employee.hireDate ? (
                  <Text>
                    <CalendarOutlined style={{ marginRight: 4 }} />
                    {dayjs(employee.hireDate).format('YYYY-MM-DD')}
                  </Text>
                ) : (
                  <Text type="secondary">-</Text>
                )}
              </Descriptions.Item>

              <Descriptions.Item label="离线员工">
                <Tag color={employee.offline ? 'orange' : 'green'}>
                  {employee.offline ? '是' : '否'}
                </Tag>
              </Descriptions.Item>

              <Descriptions.Item label="负责人">
                {employee.managerName ? (
                  <Text>{employee.managerName}</Text>
                ) : employee.managerId ? (
                  <Text>#{employee.managerId}</Text>
                ) : (
                  <Text type="secondary">未指定</Text>
                )}
              </Descriptions.Item>
            </Descriptions>
          </ProCard>
        </Col>

        {/* 联系信息 */}
        <Col xs={24} lg={12}>
          <ProCard title="联系信息" headerBordered>
            <Descriptions column={1} size="middle">
              <Descriptions.Item label="手机号">
                {employee.phoneMasked ? (
                  <Text>
                    <PhoneOutlined style={{ marginRight: 4 }} />
                    {employee.phoneMasked}
                  </Text>
                ) : (
                  <Text type="secondary">-</Text>
                )}
              </Descriptions.Item>

              <Descriptions.Item label="邮箱">
                {employee.email ? (
                  <Text>
                    <MailOutlined style={{ marginRight: 4 }} />
                    {employee.email}
                  </Text>
                ) : (
                  <Text type="secondary">-</Text>
                )}
              </Descriptions.Item>

              <Descriptions.Item label="身份证号">
                <Space>
                  {sensitiveDataVisible.idCard ? (
                    idCardQuery.data ? (
                      <Text code>{idCardQuery.data}</Text>
                    ) : idCardQuery.isLoading ? (
                      <Text>加载中...</Text>
                    ) : (
                      <Text type="secondary">查询失败</Text>
                    )
                  ) : (
                    <Text type="secondary">****</Text>
                  )}

                  {sensitiveDataVisible.idCard ? (
                    <Button
                      type="link"
                      size="small"
                      icon={<EyeInvisibleOutlined />}
                      onClick={() => handleHideSensitiveData('idCard')}
                    >
                      隐藏
                    </Button>
                  ) : (
                    <Button
                      type="link"
                      size="small"
                      icon={<EyeOutlined />}
                      onClick={() => handleViewSensitiveData('idCard')}
                    >
                      查看
                    </Button>
                  )}
                </Space>
              </Descriptions.Item>
            </Descriptions>
          </ProCard>
        </Col>

        {/* 平台绑定 */}
        <Col xs={24} lg={12}>
          <ProCard title="平台绑定" headerBordered>
            <Descriptions column={1} size="middle">
              <Descriptions.Item label="绑定平台">
                {employee.platformType ? (
                  <Tag color={employee.platformType === 'wechat' ? 'green' : employee.platformType === 'dingtalk' ? 'blue' : 'orange'}>
                    {getPlatformName(employee.platformType)}
                  </Tag>
                ) : (
                  <Text type="secondary">未绑定</Text>
                )}
              </Descriptions.Item>

              {employee.platformUserId && (
                <Descriptions.Item label="平台用户ID">
                  <Text code copyable>
                    {employee.platformUserId}
                  </Text>
                </Descriptions.Item>
              )}

              {employee.managerId && (
                <Descriptions.Item label="管理员ID">
                  <Text>{employee.managerId}</Text>
                </Descriptions.Item>
              )}
            </Descriptions>
          </ProCard>
        </Col>

        {/* 财务信息 */}
        <Col xs={24} lg={12}>
          <ProCard title="财务信息" headerBordered>
            <Descriptions column={1} size="middle">
              <Descriptions.Item label="银行账号">
                <Space>
                  {sensitiveDataVisible.bankAccount ? (
                    bankAccountQuery.data ? (
                      <Text code>{bankAccountQuery.data}</Text>
                    ) : bankAccountQuery.isLoading ? (
                      <Text>加载中...</Text>
                    ) : (
                      <Text type="secondary">查询失败</Text>
                    )
                  ) : employee.bankAccountMasked ? (
                    <Text>{employee.bankAccountMasked}</Text>
                  ) : (
                    <Text type="secondary">未设置</Text>
                  )}

                  {employee.bankAccountMasked && (
                    sensitiveDataVisible.bankAccount ? (
                      <Button
                        type="link"
                        size="small"
                        icon={<EyeInvisibleOutlined />}
                        onClick={() => handleHideSensitiveData('bankAccount')}
                      >
                        隐藏
                      </Button>
                    ) : (
                      <Button
                        type="link"
                        size="small"
                        icon={<EyeOutlined />}
                        onClick={() => handleViewSensitiveData('bankAccount')}
                      >
                        查看完整
                      </Button>
                    )
                  )}
                </Space>
              </Descriptions.Item>

              <Descriptions.Item label="开户银行">
                {employee.bankName ? (
                  <Text>
                    <BankOutlined style={{ marginRight: 4 }} />
                    {employee.bankName}
                  </Text>
                ) : (
                  <Text type="secondary">-</Text>
                )}
              </Descriptions.Item>
            </Descriptions>
          </ProCard>
        </Col>

        {/* 系统信息 */}
        <Col xs={24}>
          <ProCard title="系统信息" headerBordered>
            <Descriptions column={3} size="middle">
              <Descriptions.Item label="创建时间">
                {employee.createTime ? dayjs(employee.createTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
              </Descriptions.Item>

              <Descriptions.Item label="更新时间">
                {employee.updateTime ? dayjs(employee.updateTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
              </Descriptions.Item>

              <Descriptions.Item label="内部ID">
                <Text code>{employee.id}</Text>
              </Descriptions.Item>
            </Descriptions>
          </ProCard>
        </Col>
      </Row>

      {/* 编辑员工弹窗 */}
      <Modal
        title="编辑员工信息"
        open={editModalVisible}
        onCancel={() => {
          setEditModalVisible(false);
          editForm.resetFields();
        }}
        onOk={() => editForm.submit()}
        confirmLoading={updateEmployeeMutation.isPending}
        width={600}
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={handleUpdate}
        >
          <Form.Item
            name="name"
            label="姓名"
            rules={[{ required: true, message: '请输入姓名' }]}
          >
            <Input placeholder="请输入员工姓名" />
          </Form.Item>

          <Form.Item name="phone" label="手机号">
            <Input placeholder="请输入手机号" />
          </Form.Item>

          <Form.Item name="email" label="邮箱">
            <Input placeholder="请输入邮箱地址" />
          </Form.Item>

          <Form.Item name="department" label="部门">
            <Select placeholder="请选择部门">
              <Option value="技术部">技术部</Option>
              <Option value="产品部">产品部</Option>
              <Option value="运营部">运营部</Option>
              <Option value="财务部">财务部</Option>
              <Option value="人事部">人事部</Option>
              <Option value="市场部">市场部</Option>
            </Select>
          </Form.Item>

          <Form.Item name="position" label="职位">
            <Input placeholder="请输入职位" />
          </Form.Item>

          <Form.Item name="hireDate" label="入职日期">
            <DatePicker placeholder="请选择入职日期" style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="bankName" label="开户银行">
            <Input placeholder="请输入开户银行" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 绑定平台弹窗 */}
      <Modal
        title={`绑定平台 - ${employee.name}`}
        open={bindModalVisible}
        onCancel={() => {
          setBindModalVisible(false);
          bindForm.resetFields();
        }}
        onOk={() => bindForm.submit()}
        confirmLoading={bindPlatformMutation.isPending}
      >
        <Form
          form={bindForm}
          layout="vertical"
          onFinish={handleBindPlatform}
        >
          <Form.Item
            name="platformType"
            label="平台类型"
            rules={[{ required: true, message: '请选择平台类型' }]}
          >
            <Select placeholder="请选择平台类型">
              <Option value="wechat">企业微信</Option>
              <Option value="dingtalk">钉钉</Option>
              <Option value="feishu">飞书</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="platformUserId"
            label="平台用户ID"
            rules={[{ required: true, message: '请输入平台用户ID' }]}
          >
            <Input placeholder="请输入平台用户ID" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 指定负责人 */}
      <Modal
        title="指定负责人"
        open={managerModalVisible}
        confirmLoading={assignManagerMutation.isPending}
        onCancel={() => {
          setManagerModalVisible(false);
          managerForm.resetFields();
        }}
        onOk={handleAssignManager}
      >
        <Form form={managerForm} layout="vertical">
          <Form.Item
            name="managerId"
            label="负责人员工ID"
            rules={[{ required: true, message: '请输入负责人ID' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} placeholder="请输入负责人员工ID" />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default EmployeeDetail;
