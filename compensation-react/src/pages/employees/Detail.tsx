import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Space, Tag, Typography, Descriptions, Modal, Form, Input, InputNumber, Select, DatePicker, Row, Col, Alert, App as AntdApp, Table } from 'antd';
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
  useEmployeeSettlementAccountQuery,
  useEmployeeApprovalsQuery,
  useEmployeePayslipsQuery,
  useEmployeePaymentsQuery,
  type Employee,
  type EmployeeApprovalRecord,
  type EmployeePayslipRecord,
  type EmployeeFormData,
  type PlatformBindData,
} from '@services/queries/employee';
import type { PaymentRecordItemVO } from '@types/openapi';
import { getPagedRecords } from '@types/api';
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
  const [approvalsPage, setApprovalsPage] = useState({ current: 1, pageSize: 10 });
  const [payslipsPage, setPayslipsPage] = useState({ current: 1, pageSize: 10 });
  const [paymentsPage, setPaymentsPage] = useState({ current: 1, pageSize: 10 });
  const [sensitiveDataVisible, setSensitiveDataVisible] = useState({
    idCard: false,
    settlementAccount: false,
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

  const settlementAccountQuery = useEmployeeSettlementAccountQuery(
    parseInt(id!),
    { enabled: sensitiveDataVisible.settlementAccount && !!id }
  );

  const approvalsQuery = useEmployeeApprovalsQuery(
    parseInt(id!),
    approvalsPage,
    { enabled: !!id }
  );

  const payslipsQuery = useEmployeePayslipsQuery(
    parseInt(id!),
    payslipsPage,
    { enabled: !!id }
  );

  const paymentsQuery = useEmployeePaymentsQuery(
    parseInt(id!),
    paymentsPage,
    { enabled: !!id }
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
  const handleViewSensitiveData = (type: 'idCard' | 'settlementAccount') => {
    modal.confirm({
      title: '查看敏感信息',
      content: (
        <div>
          <p>您即将查看员工的{type === 'idCard' ? '身份证号' : '收款账户'}信息。</p>
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
  const handleHideSensitiveData = (type: 'idCard' | 'settlementAccount') => {
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

  const getSettlementAccountTypeName = (type?: Employee['settlementAccountType']) => {
    if (!type) {
      return '-';
    }
    const typeMap: Record<string, string> = {
      bank_card: '银行卡',
      alipay: '支付宝',
      wechat: '微信',
      other: '其他',
    };
    return typeMap[type] || type;
  };

  const approvalStatusColor = (status?: string) => {
    switch ((status || '').toLowerCase()) {
      case 'approved':
        return 'success';
      case 'rejected':
      case 'cancelled':
        return 'error';
      case 'pending':
      default:
        return 'processing';
    }
  };

  const payslipStatusColor = (status?: string) => {
    switch ((status || '').toLowerCase()) {
      case 'paid':
      case 'success':
        return 'success';
      case 'failed':
      case 'error':
        return 'error';
      case 'processing':
        return 'processing';
      default:
        return 'default';
    }
  };

  const paymentStatusColor = (status?: string) => {
    switch ((status || '').toLowerCase()) {
      case 'success':
      case 'paid':
        return 'success';
      case 'failed':
      case 'error':
        return 'error';
      case 'processing':
      case 'pending':
        return 'processing';
      default:
        return 'default';
    }
  };

  const confirmationStatusColor = (status?: string) => {
    switch ((status || '').toLowerCase()) {
      case 'confirmed':
      case 'objected_approved':
        return 'success';
      case 'objected':
        return 'processing';
      case 'objected_rejected':
        return 'warning';
      case 'pending':
      default:
        return 'default';
    }
  };

  const confirmationStatusText = (status?: string) => {
    const key = (status || '').toLowerCase();
    const mapping: Record<string, string> = {
      pending: '待确认',
      confirmed: '已确认',
      objected: '异议处理中',
      objected_approved: '异议通过',
      objected_rejected: '异议驳回',
    };
    return mapping[key] || status || '-';
  };

  const approvalColumns = [
    {
      title: '流程名称',
      dataIndex: 'workflowName',
      width: 180,
      render: (value: string) => value || '-',
    },
    {
      title: '类型',
      dataIndex: 'workflowTypeName',
      width: 120,
      render: (_: string, record: EmployeeApprovalRecord) => record.workflowTypeName || record.workflowType || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (_: string, record: EmployeeApprovalRecord) => (
        <Tag color={approvalStatusColor(record.status)}>{record.statusName || record.status || '-'}</Tag>
      ),
    },
    {
      title: '发起人',
      dataIndex: 'initiatorName',
      width: 120,
      render: (_: string, record: EmployeeApprovalRecord) => record.initiatorName || record.initiatorId || '-',
    },
    {
      title: '当前审批人',
      dataIndex: 'currentApproverName',
      width: 140,
      render: (_: string, record: EmployeeApprovalRecord) =>
        record.currentApproverName || record.currentApproverId || '-',
    },
    {
      title: '提交时间',
      dataIndex: 'submitTime',
      width: 180,
      render: (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
  ];

  const payslipColumns = [
    {
      title: '期间',
      dataIndex: 'periodLabel',
      width: 150,
      render: (value: string) => value || '-',
    },
    {
      title: '应发',
      dataIndex: 'grossAmount',
      width: 120,
      render: (value: number) => (value != null ? value.toFixed(2) : '-'),
    },
    {
      title: '个税',
      dataIndex: 'taxAmount',
      width: 120,
      render: (value: number) => (value != null ? value.toFixed(2) : '-'),
    },
    {
      title: '社保',
      dataIndex: 'socialAmount',
      width: 120,
      render: (value: number) => (value != null ? value.toFixed(2) : '-'),
    },
    {
      title: '实发',
      dataIndex: 'netAmount',
      width: 120,
      render: (value: number) => (value != null ? value.toFixed(2) : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => <Tag color={payslipStatusColor(value)}>{value || '-'}</Tag>,
    },
    {
      title: '确认状态',
      dataIndex: 'confirmationStatus',
      width: 130,
      render: (value: string) => <Tag color={confirmationStatusColor(value)}>{confirmationStatusText(value)}</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_: unknown, record: EmployeePayslipRecord) =>
        record.batchId ? (
          <Button
            type="link"
            size="small"
            onClick={() => navigate(`/payroll/confirmations?batchId=${record.batchId}`)}
          >
            去确认
          </Button>
        ) : (
          '-'
        ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180,
      render: (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
  ];

  const paymentColumns = [
    {
      title: '批次号',
      dataIndex: 'batchNo',
      width: 160,
      render: (value: string) => value || '-',
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 120,
      render: (value: number) => (value != null ? value.toFixed(2) : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => <Tag color={paymentStatusColor(value)}>{value || '-'}</Tag>,
    },
    {
      title: '渠道',
      dataIndex: 'providerCode',
      width: 120,
      render: (value: string) => value || 'alipay',
    },
    {
      title: '渠道单号',
      dataIndex: 'providerOrderNo',
      width: 180,
      render: (value: string) => value || '-',
    },
    {
      title: '支付时间',
      dataIndex: 'paymentTime',
      width: 180,
      render: (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
  ];

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
  const approvalRecords = getPagedRecords(approvalsQuery.data);
  const payslipRecords = getPagedRecords(payslipsQuery.data);
  const paymentRecords = getPagedRecords(paymentsQuery.data);
  const settlementAccountMasked = employee.settlementAccountMasked || employee.bankAccountMasked;
  const latestPayslip = payslipRecords[0];
  const latestPayment = paymentRecords[0];

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
          onClick={() => {
            employeeQuery.refetch();
            approvalsQuery.refetch();
            payslipsQuery.refetch();
            paymentsQuery.refetch();
          }}
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

              <Descriptions.Item label="用工类型">
                {employee.employmentType === 'part_time'
                  ? '兼职'
                  : employee.employmentType === 'full_time'
                    ? '全职'
                    : <Text type="secondary">-</Text>}
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
              <Descriptions.Item label="收款账户类型">
                {employee.settlementAccountTypeName || getSettlementAccountTypeName(employee.settlementAccountType)}
              </Descriptions.Item>

              <Descriptions.Item label="收款账户">
                <Space>
                  {sensitiveDataVisible.settlementAccount ? (
                    settlementAccountQuery.data ? (
                      <Text code>{settlementAccountQuery.data}</Text>
                    ) : settlementAccountQuery.isLoading ? (
                      <Text>加载中...</Text>
                    ) : (
                      <Text type="secondary">查询失败</Text>
                    )
                  ) : settlementAccountMasked ? (
                    <Text>{settlementAccountMasked}</Text>
                  ) : (
                    <Text type="secondary">未设置</Text>
                  )}

                  {settlementAccountMasked && (
                    sensitiveDataVisible.settlementAccount ? (
                      <Button
                        type="link"
                        size="small"
                        icon={<EyeInvisibleOutlined />}
                        onClick={() => handleHideSensitiveData('settlementAccount')}
                      >
                        隐藏
                      </Button>
                    ) : (
                      <Button
                        type="link"
                        size="small"
                        icon={<EyeOutlined />}
                        onClick={() => handleViewSensitiveData('settlementAccount')}
                      >
                        查看完整
                      </Button>
                    )
                  )}
                </Space>
              </Descriptions.Item>

              <Descriptions.Item label="收款账户实名">
                {employee.settlementAccountName || <Text type="secondary">-</Text>}
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

              <Descriptions.Item label="开户支行">
                {employee.bankBranchName || <Text type="secondary">-</Text>}
              </Descriptions.Item>

              <Descriptions.Item label="最近发薪期间">
                {latestPayslip?.periodLabel || <Text type="secondary">-</Text>}
              </Descriptions.Item>

              <Descriptions.Item label="最近确认状态">
                {latestPayslip?.confirmationStatus ? (
                  <Tag color={confirmationStatusColor(latestPayslip.confirmationStatus)}>
                    {confirmationStatusText(latestPayslip.confirmationStatus)}
                  </Tag>
                ) : (
                  <Text type="secondary">-</Text>
                )}
              </Descriptions.Item>

              <Descriptions.Item label="最近实发金额">
                {latestPayslip?.netAmount != null ? (
                  <Text>{Number(latestPayslip.netAmount).toFixed(2)}</Text>
                ) : (
                  <Text type="secondary">-</Text>
                )}
              </Descriptions.Item>

              <Descriptions.Item label="最近支付状态">
                {latestPayment?.status ? (
                  <Tag color={paymentStatusColor(latestPayment.status)}>{latestPayment.status}</Tag>
                ) : (
                  <Text type="secondary">-</Text>
                )}
              </Descriptions.Item>

              <Descriptions.Item label="最近支付时间">
                {latestPayment?.paymentTime
                  ? dayjs(latestPayment.paymentTime).format('YYYY-MM-DD HH:mm:ss')
                  : <Text type="secondary">-</Text>}
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

        <Col xs={24}>
          <ProCard title="审批记录" headerBordered>
            <Table<EmployeeApprovalRecord>
              size="small"
              rowKey={(record) => record.id}
              columns={approvalColumns}
              dataSource={approvalRecords}
              loading={approvalsQuery.isLoading}
              pagination={{
                current: approvalsPage.current,
                pageSize: approvalsPage.pageSize,
                total: approvalsQuery.data?.total || 0,
                showSizeChanger: true,
                showTotal: (total = 0) => `共 ${total} 条`,
                onChange: (current, pageSize) =>
                  setApprovalsPage({ current, pageSize: pageSize || approvalsPage.pageSize }),
              }}
              scroll={{ x: 980 }}
            />
          </ProCard>
        </Col>

        <Col xs={24}>
          <ProCard title="发薪记录" headerBordered>
            <Table<EmployeePayslipRecord>
              size="small"
              rowKey={(record) => record.lineId}
              columns={payslipColumns}
              dataSource={payslipRecords}
              loading={payslipsQuery.isLoading}
              pagination={{
                current: payslipsPage.current,
                pageSize: payslipsPage.pageSize,
                total: payslipsQuery.data?.total || 0,
                showSizeChanger: true,
                showTotal: (total = 0) => `共 ${total} 条`,
                onChange: (current, pageSize) =>
                  setPayslipsPage({ current, pageSize: pageSize || payslipsPage.pageSize }),
              }}
              scroll={{ x: 1120 }}
            />
          </ProCard>
        </Col>

        <Col xs={24}>
          <ProCard title="支付记录" headerBordered>
            <Table<PaymentRecordItemVO>
              size="small"
              rowKey={(record) => String(record.id)}
              columns={paymentColumns}
              dataSource={paymentRecords}
              loading={paymentsQuery.isLoading}
              pagination={{
                current: paymentsPage.current,
                pageSize: paymentsPage.pageSize,
                total: paymentsQuery.data?.total || 0,
                showSizeChanger: true,
                showTotal: (total = 0) => `共 ${total} 条`,
                onChange: (current, pageSize) =>
                  setPaymentsPage({ current, pageSize: pageSize || paymentsPage.pageSize }),
              }}
              scroll={{ x: 920 }}
            />
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

          <Form.Item name="employmentType" label="用工类型">
            <Select placeholder="请选择用工类型">
              <Option value="full_time">全职</Option>
              <Option value="part_time">兼职</Option>
            </Select>
          </Form.Item>

          <Form.Item name="status" label="员工状态">
            <Select placeholder="请选择员工状态">
              <Option value="active">在职</Option>
              <Option value="suspended">暂停</Option>
              <Option value="inactive">离职</Option>
            </Select>
          </Form.Item>

          <Form.Item name="hireDate" label="入职日期">
            <DatePicker placeholder="请选择入职日期" style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="settlementAccountType" label="收款账户类型">
            <Select placeholder="请选择收款账户类型">
              <Option value="bank_card">银行卡</Option>
              <Option value="alipay">支付宝</Option>
              <Option value="wechat">微信</Option>
              <Option value="other">其他</Option>
            </Select>
          </Form.Item>

          <Form.Item name="settlementAccount" label="收款账户">
            <Input placeholder="请输入收款账户（银行卡号/支付宝账号等）" />
          </Form.Item>

          <Form.Item name="settlementAccountName" label="收款账户实名">
            <Input placeholder="请输入收款账户实名（选填）" />
          </Form.Item>

          <Form.Item name="bankName" label="开户银行">
            <Input placeholder="请输入开户银行（银行卡场景）" />
          </Form.Item>

          <Form.Item name="bankBranchName" label="开户支行">
            <Input placeholder="请输入开户支行（银行卡场景）" />
          </Form.Item>

          <Form.Item name="bankAccount" label="银行卡号（兼容字段）">
            <Input placeholder="仅银行卡场景需要，通常可与收款账户保持一致" />
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
