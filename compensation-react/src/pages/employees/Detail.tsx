import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Button,
  Space,
  Tag,
  Typography,
  Descriptions,
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  DatePicker,
  Row,
  Col,
  Alert,
  App as AntdApp,
  Table,
  Card,
  Tabs,
  Divider,
  DescriptionsProps,
  TabsProps,
} from 'antd';
import { PageContainer } from '@ant-design/pro-components';
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

type EmployeeSensitiveFields = {
  phone?: string;
  settlementAccount?: string;
  bankAccount?: string;
};

const MASKED_VALUE_PATTERN = /\*{2,}/;
const BANK_ACCOUNT_PATTERN = /^\d{8,30}$/;

type SettlementAccountType = NonNullable<EmployeeFormData['settlementAccountType']>;

function normalizeTextValue(value?: string | null) {
  if (value == null) {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

function isMaskedDisplayValue(value?: string | null) {
  if (!value) {
    return false;
  }
  return MASKED_VALUE_PATTERN.test(value);
}

function normalizeSettlementAccountType(value?: string | null): SettlementAccountType | undefined {
  const normalized = normalizeTextValue(value)?.toLowerCase();
  if (!normalized) {
    return undefined;
  }
  if (normalized === 'bank_card' || normalized === 'alipay' || normalized === 'wechat' || normalized === 'other') {
    return normalized;
  }
  return undefined;
}

function isBankCardType(type?: string | null) {
  return normalizeSettlementAccountType(type) === 'bank_card';
}

function getSettlementAccountLabel(type?: string | null) {
  const normalized = normalizeSettlementAccountType(type);
  if (normalized === 'alipay') return '支付宝账号';
  if (normalized === 'wechat') return '微信账号';
  if (normalized === 'other') return '收款账户';
  return '收款账户';
}

function getSettlementAccountPlaceholder(type?: string | null) {
  const normalized = normalizeSettlementAccountType(type);
  if (normalized === 'alipay') return '请输入支付宝账号（手机号/邮箱）';
  if (normalized === 'wechat') return '请输入微信账号';
  if (normalized === 'other') return '请输入收款账户';
  return '请输入收款账户';
}

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
  const editSettlementType = Form.useWatch('settlementAccountType', editForm);

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
  const employeeProvider = employee?.provider as Employee['provider'] | undefined;
  const employeeSubjectId = employee?.subjectId;
  const resolvedEditSettlementType = normalizeSettlementAccountType(
    String(editSettlementType ?? employee?.settlementAccountType ?? ''),
  );

  const buildEditFormValues = (target: Employee): Partial<EmployeeFormData> => {
    const detail = target as Employee & EmployeeSensitiveFields;
    return {
      name: target.name,
      phone: normalizeTextValue(detail.phone) ?? normalizeTextValue(target.phoneMasked),
      email: normalizeTextValue(target.email),
      department: normalizeTextValue(target.department),
      position: normalizeTextValue(target.position),
      employmentType: target.employmentType,
      status: target.status,
      hireDate: target.hireDate ? dayjs(target.hireDate) : undefined,
      settlementAccountType: target.settlementAccountType,
      settlementAccount:
        normalizeTextValue(detail.settlementAccount)
        ?? normalizeTextValue(target.settlementAccountMasked)
        ?? normalizeTextValue(target.bankAccountMasked),
      settlementAccountName: normalizeTextValue(target.settlementAccountName),
      bankName: normalizeTextValue(target.bankName),
      bankBranchName: normalizeTextValue(target.bankBranchName),
      bankAccount:
        normalizeTextValue(detail.bankAccount)
        ?? normalizeTextValue(target.bankAccountMasked)
        ?? normalizeTextValue(target.settlementAccountMasked),
    };
  };

  const openEditModal = () => {
    if (!employee) {
      return;
    }
    editForm.setFieldsValue(buildEditFormValues(employee));
    setEditModalVisible(true);
  };

  const handleEditSettlementTypeChange = (nextType: SettlementAccountType) => {
    if (isBankCardType(nextType)) {
      editForm.setFieldsValue({
        settlementAccount: undefined,
      });
      return;
    }
    editForm.setFieldsValue({
      bankAccount: undefined,
      bankName: undefined,
      bankBranchName: undefined,
    });
  };

  // 填充编辑表单
  useEffect(() => {
    if (employee && editModalVisible) {
      editForm.setFieldsValue(buildEditFormValues(employee));
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
      const formValues = {
        ...values,
      } as Partial<EmployeeFormData>;

      formValues.name = normalizeTextValue(formValues.name);
      formValues.email = normalizeTextValue(formValues.email);
      formValues.department = normalizeTextValue(formValues.department);
      formValues.position = normalizeTextValue(formValues.position);
      formValues.settlementAccountName = normalizeTextValue(formValues.settlementAccountName);
      formValues.bankName = normalizeTextValue(formValues.bankName);
      formValues.bankBranchName = normalizeTextValue(formValues.bankBranchName);

      const phoneValue = normalizeTextValue(formValues.phone);
      if (!phoneValue || phoneValue === employee.phoneMasked || isMaskedDisplayValue(phoneValue)) {
        delete formValues.phone;
      } else {
        formValues.phone = phoneValue;
      }

      const currentSettlementType = normalizeSettlementAccountType(
        String(formValues.settlementAccountType ?? employee.settlementAccountType ?? ''),
      );
      const settlementAccountValue = normalizeTextValue(formValues.settlementAccount);
      const bankAccountValue = normalizeTextValue(formValues.bankAccount);

      const sanitizedSettlementAccount = !settlementAccountValue
        || settlementAccountValue === employee.settlementAccountMasked
        || settlementAccountValue === employee.bankAccountMasked
        || isMaskedDisplayValue(settlementAccountValue)
        ? undefined
        : settlementAccountValue;

      const sanitizedBankAccount = !bankAccountValue
        || bankAccountValue === employee.bankAccountMasked
        || bankAccountValue === employee.settlementAccountMasked
        || isMaskedDisplayValue(bankAccountValue)
        ? undefined
        : bankAccountValue;

      if (isBankCardType(currentSettlementType)) {
        const resolvedBankAccount = sanitizedBankAccount ?? sanitizedSettlementAccount;
        if (resolvedBankAccount) {
          formValues.bankAccount = resolvedBankAccount;
          formValues.settlementAccount = resolvedBankAccount;
        } else {
          delete formValues.bankAccount;
          delete formValues.settlementAccount;
        }
      } else {
        const resolvedSettlement = sanitizedSettlementAccount ?? sanitizedBankAccount;
        if (resolvedSettlement) {
          formValues.settlementAccount = resolvedSettlement;
        } else {
          delete formValues.settlementAccount;
        }
        delete formValues.bankAccount;
        delete formValues.bankName;
        delete formValues.bankBranchName;
      }

      await updateEmployeeMutation.mutateAsync({
        id: employee.id,
        ...formValues,
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
      title: nextValue ? '确认标记为架构外员工？' : '取消架构外标记',
      content: nextValue
        ? '标记后该员工将在审批、同步中按架构外员工处理，请确保已指定负责人。'
        : '取消后该员工将恢复为架构内员工处理。',
      icon: <ExclamationCircleOutlined />,
      okButtonProps: { loading: toggleOfflineMutation.isPending },
      onOk: async () => {
        try {
          await toggleOfflineMutation.mutateAsync({ id: employee.id, value: nextValue });
          message.success(nextValue ? '已标记为架构外员工' : '已取消架构外标记');
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

  const getPlatformName = (platformType?: Employee['provider']) => {
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
  const employeeSettlementType = normalizeSettlementAccountType(employee.settlementAccountType);
  const overviewItems: DescriptionsProps['items'] = [
    {
      key: 'employeeId',
      label: '员工ID',
      children: (
        <Text code copyable>
          {employee.employeeId}
        </Text>
      ),
    },
    {
      key: 'status',
      label: '状态',
      children: <Tag color={statusInfo.color}>{statusInfo.text}</Tag>,
    },
    {
      key: 'offline',
      label: '架构外员工',
      children: <Tag color={employee.offline ? 'orange' : 'green'}>{employee.offline ? '是' : '否'}</Tag>,
    },
    {
      key: 'latestNetAmount',
      label: '最近实发金额',
      children: latestPayslip?.netAmount != null ? (
        <Text>{Number(latestPayslip.netAmount).toFixed(2)}</Text>
      ) : (
        <Text type="secondary">-</Text>
      ),
    },
    {
      key: 'latestPaymentStatus',
      label: '最近支付状态',
      children: latestPayment?.status ? (
        <Tag color={paymentStatusColor(latestPayment.status)}>{latestPayment.status}</Tag>
      ) : (
        <Text type="secondary">-</Text>
      ),
    },
  ];

  const profileItems: DescriptionsProps['items'] = [
    {
      key: 'name',
      label: '姓名',
      children: <Text strong>{employee.name}</Text>,
    },
    {
      key: 'department',
      label: '部门',
      children: employee.department || <Text type="secondary">-</Text>,
    },
    {
      key: 'position',
      label: '职位',
      children: employee.position || <Text type="secondary">-</Text>,
    },
    {
      key: 'employmentType',
      label: '用工类型',
      children: employee.employmentType === 'part_time'
        ? '兼职'
        : employee.employmentType === 'full_time'
          ? '全职'
          : <Text type="secondary">-</Text>,
    },
    {
      key: 'hireDate',
      label: '入职日期',
      children: employee.hireDate ? (
        <Text>
          <CalendarOutlined style={{ marginRight: 4 }} />
          {dayjs(employee.hireDate).format('YYYY-MM-DD')}
        </Text>
      ) : (
        <Text type="secondary">-</Text>
      ),
    },
  ];

  const contactItems: DescriptionsProps['items'] = [
    {
      key: 'phoneMasked',
      label: '手机号',
      children: employee.phoneMasked ? (
        <Text>
          <PhoneOutlined style={{ marginRight: 4 }} />
          {employee.phoneMasked}
        </Text>
      ) : (
        <Text type="secondary">-</Text>
      ),
    },
    {
      key: 'email',
      label: '邮箱',
      children: employee.email ? (
        <Text>
          <MailOutlined style={{ marginRight: 4 }} />
          {employee.email}
        </Text>
      ) : (
        <Text type="secondary">-</Text>
      ),
    },
    {
      key: 'idCard',
      label: '身份证号',
      span: 2,
      children: (
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
      ),
    },
  ];

  const platformItems: DescriptionsProps['items'] = [
    {
      key: 'platform',
      label: '绑定平台',
      children: employeeProvider ? (
        <Tag color={employeeProvider === 'wechat' ? 'green' : employeeProvider === 'dingtalk' ? 'blue' : 'orange'}>
          {getPlatformName(employeeProvider)}
        </Tag>
      ) : (
        <Text type="secondary">未绑定</Text>
      ),
    },
    {
      key: 'subject',
      label: '平台用户ID',
      children: employeeSubjectId ? (
        <Text code copyable>
          {employeeSubjectId}
        </Text>
      ) : (
        <Text type="secondary">-</Text>
      ),
    },
  ];

  const financialItems: DescriptionsProps['items'] = [
    {
      key: 'settlementType',
      label: '收款账户类型',
      children: employee.settlementAccountTypeName || getSettlementAccountTypeName(employee.settlementAccountType),
    },
    {
      key: 'settlementAccount',
      label: getSettlementAccountLabel(employeeSettlementType),
      children: (
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
      ),
    },
    {
      key: 'settlementAccountName',
      label: '收款账户实名',
      children: employee.settlementAccountName || <Text type="secondary">-</Text>,
    },
    ...(isBankCardType(employeeSettlementType)
      ? [
        {
          key: 'bankName',
          label: '开户银行',
          children: employee.bankName ? (
            <Text>
              <BankOutlined style={{ marginRight: 4 }} />
              {employee.bankName}
            </Text>
          ) : (
            <Text type="secondary">-</Text>
          ),
        },
        {
          key: 'bankBranchName',
          label: '开户支行',
          children: employee.bankBranchName || <Text type="secondary">-</Text>,
        },
      ]
      : []),
    {
      key: 'latestPeriod',
      label: '最近发薪期间',
      children: latestPayslip?.periodLabel || <Text type="secondary">-</Text>,
    },
    {
      key: 'latestConfirmation',
      label: '最近确认状态',
      children: latestPayslip?.confirmationStatus ? (
        <Tag color={confirmationStatusColor(latestPayslip.confirmationStatus)}>
          {confirmationStatusText(latestPayslip.confirmationStatus)}
        </Tag>
      ) : (
        <Text type="secondary">-</Text>
      ),
    },
    {
      key: 'latestPaymentTime',
      label: '最近支付时间',
      children: latestPayment?.paymentTime
        ? dayjs(latestPayment.paymentTime).format('YYYY-MM-DD HH:mm:ss')
        : <Text type="secondary">-</Text>,
    },
  ];

  const systemItems: DescriptionsProps['items'] = [
    {
      key: 'createTime',
      label: '创建时间',
      children: employee.createTime ? dayjs(employee.createTime).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      key: 'updateTime',
      label: '更新时间',
      children: employee.updateTime ? dayjs(employee.updateTime).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      key: 'internalId',
      label: '内部ID',
      children: <Text code>{employee.id}</Text>,
    },
  ];

  const recordTabItems: TabsProps['items'] = [
    {
      key: 'approvals',
      label: `审批记录 (${approvalsQuery.data?.total || 0})`,
      children: (
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
      ),
    },
    {
      key: 'payslips',
      label: `发薪记录 (${payslipsQuery.data?.total || 0})`,
      children: (
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
      ),
    },
    {
      key: 'payments',
      label: `支付记录 (${paymentsQuery.data?.total || 0})`,
      children: (
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
      ),
    },
  ];

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
          onClick={openEditModal}
        >
          编辑信息
        </Button>,
        <Button
          key="offline"
          danger={!employee.offline}
          onClick={handleToggleOffline}
          loading={toggleOfflineMutation.isPending}
        >
          {employee.offline ? '取消架构外标记' : '标记为架构外员工'}
        </Button>,
        <Button
          key="manager"
          onClick={openManagerModal}
          loading={assignManagerMutation.isPending}
        >
          指定负责人
        </Button>,
        !employeeProvider && (
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
        <Col xs={24}>
          <Card title="概览" size="small">
            <Descriptions
              size="small"
              column={{ xs: 1, sm: 2, md: 4 }}
              items={overviewItems}
            />
          </Card>
        </Col>

        <Col xs={24} xl={15}>
          <Card title="基本信息" size="small">
            <Descriptions
              size="small"
              column={{ xs: 1, sm: 2 }}
              items={profileItems}
            />
            <Divider orientation="left" style={{ margin: '16px 0' }}>
              联系信息
            </Divider>
            <Descriptions
              size="small"
              column={{ xs: 1, sm: 2 }}
              items={contactItems}
            />
          </Card>
        </Col>

        <Col xs={24} xl={9}>
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card title="平台绑定" size="small">
              <Descriptions
                size="small"
                column={1}
                items={platformItems}
              />
            </Card>

            <Card title="财务信息" size="small">
              <Descriptions
                size="small"
                column={1}
                items={financialItems}
              />
            </Card>

            <Card title="系统信息" size="small">
              <Descriptions
                size="small"
                column={1}
                items={systemItems}
              />
            </Card>
          </Space>
        </Col>

        <Col xs={24}>
          <Card title="业务记录" size="small" styles={{ body: { paddingTop: 8 } }}>
            <Tabs items={recordTabItems} />
          </Card>
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
        forceRender
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
            <Input placeholder="请输入部门" />
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

          <Form.Item
            name="settlementAccountType"
            label="收款账户类型"
            rules={[{ required: true, message: '请选择收款账户类型' }]}
          >
            <Select
              placeholder="请选择收款账户类型"
              onChange={(value) => handleEditSettlementTypeChange(value as SettlementAccountType)}
            >
              <Option value="bank_card">银行卡</Option>
              <Option value="alipay">支付宝</Option>
              <Option value="wechat">微信</Option>
              <Option value="other">其他</Option>
            </Select>
          </Form.Item>

          {isBankCardType(resolvedEditSettlementType) ? (
            <>
              <Form.Item
                name="bankAccount"
                label="银行卡号"
                preserve={false}
                rules={[
                  { required: true, message: '请输入银行卡号' },
                  {
                    validator: (_, value) => {
                      const normalized = normalizeTextValue(value);
                      if (!normalized || isMaskedDisplayValue(normalized) || BANK_ACCOUNT_PATTERN.test(normalized)) {
                        return Promise.resolve();
                      }
                      return Promise.reject(new Error('请输入8-30位数字银行卡号'));
                    },
                  },
                ]}
              >
                <Input placeholder="请输入银行卡号" />
              </Form.Item>

              <Form.Item
                name="bankName"
                label="开户银行"
                preserve={false}
                rules={[{ required: true, message: '请输入开户银行' }]}
              >
                <Input placeholder="请输入开户银行" />
              </Form.Item>

              <Form.Item
                name="bankBranchName"
                label="开户支行"
                preserve={false}
                rules={[{ required: true, message: '请输入开户支行' }]}
              >
                <Input placeholder="请输入开户支行" />
              </Form.Item>
            </>
          ) : (
            <Form.Item
              name="settlementAccount"
              label={getSettlementAccountLabel(resolvedEditSettlementType)}
              preserve={false}
              rules={[{ required: true, message: `请输入${getSettlementAccountLabel(resolvedEditSettlementType)}` }]}
            >
              <Input placeholder={getSettlementAccountPlaceholder(resolvedEditSettlementType)} />
            </Form.Item>
          )}

          <Form.Item name="settlementAccountName" label="收款账户实名">
            <Input placeholder="请输入收款账户实名（选填）" />
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
        forceRender
      >
        <Form
          form={bindForm}
          layout="vertical"
          onFinish={handleBindPlatform}
        >
          <Form.Item
            name="provider"
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
            name="subjectId"
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
        forceRender
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
