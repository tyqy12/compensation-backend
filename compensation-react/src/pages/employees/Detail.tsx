import React, { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  Alert,
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Typography,
} from 'antd';
import { PageContainer } from '@ant-design/pro-components';
import { ExclamationCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import {
  useAssignEmployeeManagerMutation,
  useBindPlatformMutation,
  useEmployeeApprovalsQuery,
  useEmployeeIdCardQuery,
  useEmployeePaymentsQuery,
  useEmployeePayslipsQuery,
  useEmployeeQuery,
  useEmployeeSettlementAccountQuery,
  useToggleEmployeeOfflineMutation,
  useUpdateEmployeeMutation,
  type Employee,
  type EmployeeFormData,
  type PlatformBindData,
} from '@services/queries/employee';
import EmployeeDetailContent from './DetailContent';
import {
  getSettlementAccountLabel,
  getSettlementAccountPlaceholder,
  getStatusInfo,
  isBankCardType,
  isMaskedDisplayValue,
  normalizeSettlementAccountType,
  normalizeTextValue,
  type SettlementAccountType,
} from './detailUtils';
import { useHasAction } from '@services/queries/rbac';
import './Detail.less';

const { Text } = Typography;
const { Option } = Select;
const BANK_ACCOUNT_PATTERN = /^\d{8,30}$/;

type EmployeeSensitiveFields = {
  phone?: string;
  settlementAccount?: string;
  bankAccount?: string;
};

type EmployeeEditFormValues = Omit<Partial<EmployeeFormData>, 'hireDate'> & {
  hireDate?: Dayjs;
};

const getErrorMessage = (error: unknown) => {
  if (error instanceof Error) {
    return error.message;
  }
  if (error && typeof error === 'object' && 'message' in error) {
    return String(error.message);
  }
  return '网络错误';
};

function buildEditFormValues(target: Employee): EmployeeEditFormValues {
  const detail = target as Employee & EmployeeSensitiveFields;
  return {
    name: target.name,
    phone: normalizeTextValue(detail.phone) ?? normalizeTextValue(target.phoneMasked),
    email: normalizeTextValue(target.email),
    departments: target.departments?.length
      ? target.departments
      : normalizeTextValue(target.department)?.split(/[,，、/]+/).map((value) => value.trim()).filter(Boolean),
    position: normalizeTextValue(target.position),
    employmentType: target.employmentType,
    status: target.status,
    hireDate: target.hireDate ? dayjs(target.hireDate) : undefined,
    settlementAccountType: target.settlementAccountType,
    settlementAccount:
      normalizeTextValue(detail.settlementAccount) ??
      normalizeTextValue(target.settlementAccountMasked) ??
      normalizeTextValue(target.bankAccountMasked),
    settlementAccountName: normalizeTextValue(target.settlementAccountName),
    bankName: normalizeTextValue(target.bankName),
    bankBranchName: normalizeTextValue(target.bankBranchName),
    bankAccount:
      normalizeTextValue(detail.bankAccount) ??
      normalizeTextValue(target.bankAccountMasked) ??
      normalizeTextValue(target.settlementAccountMasked),
  };
}

const EmployeeDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const employeeNumericId = Number(id);
  const hasNumericEmployeeId = Number.isSafeInteger(employeeNumericId) && employeeNumericId > 0;

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

  const [editForm] = Form.useForm<EmployeeEditFormValues>();
  const [bindForm] = Form.useForm<PlatformBindData>();
  const [managerForm] = Form.useForm<{ managerId: number }>();
  const editSettlementType = Form.useWatch('settlementAccountType', editForm);
  const { message, modal } = AntdApp.useApp();

  const canEdit = useHasAction('api.employee.update');
  const canBind = useHasAction('api.employee.bind-platform');
  const canAssignManager = useHasAction('api.admin.employee.manager');
  const canToggleOffline = useHasAction('api.admin.employee.offline');
  const canViewIdCard = useHasAction('api.employee.decrypt-id-card');
  const canViewSettlementAccount = useHasAction('api.employee.decrypt-settlement');
  const canViewApprovals = useHasAction('api.employee.approvals');
  const canViewPayslips = useHasAction('api.employee.payslips');
  const canViewPayments = useHasAction('api.employee.payments');

  const employeeQuery = useEmployeeQuery(id ?? '', { enabled: Boolean(id) });
  const employee = employeeQuery.data;
  const resolvedEmployeeNumericId = employee?.id ?? (hasNumericEmployeeId ? employeeNumericId : 0);
  const hasResolvedEmployeeId = resolvedEmployeeNumericId > 0;

  const idCardQuery = useEmployeeIdCardQuery(resolvedEmployeeNumericId, {
    enabled: sensitiveDataVisible.idCard && hasResolvedEmployeeId && canViewIdCard,
  });
  const settlementAccountQuery = useEmployeeSettlementAccountQuery(resolvedEmployeeNumericId, {
    enabled: sensitiveDataVisible.settlementAccount && hasResolvedEmployeeId && canViewSettlementAccount,
  });
  const approvalsQuery = useEmployeeApprovalsQuery(resolvedEmployeeNumericId, approvalsPage, {
    enabled: hasResolvedEmployeeId && canViewApprovals,
  });
  const payslipsQuery = useEmployeePayslipsQuery(resolvedEmployeeNumericId, payslipsPage, {
    enabled: hasResolvedEmployeeId && canViewPayslips,
  });
  const paymentsQuery = useEmployeePaymentsQuery(resolvedEmployeeNumericId, paymentsPage, {
    enabled: hasResolvedEmployeeId && canViewPayments,
  });

  const updateEmployeeMutation = useUpdateEmployeeMutation();
  const bindPlatformMutation = useBindPlatformMutation();
  const toggleOfflineMutation = useToggleEmployeeOfflineMutation();
  const assignManagerMutation = useAssignEmployeeManagerMutation();

  const employeeProvider = employee?.provider || undefined;
  const employeeSubjectId = employee?.subjectId;
  const resolvedEditSettlementType = normalizeSettlementAccountType(
    String(editSettlementType ?? employee?.settlementAccountType ?? ''),
  );

  useEffect(() => {
    if (employee && editModalVisible) {
      editForm.setFieldsValue(buildEditFormValues(employee));
    }
  }, [employee, editModalVisible, editForm]);

  const handleBackToList = () => {
    const queryString = searchParams.toString();
    navigate(queryString ? `/employees?${queryString}` : '/employees');
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
      editForm.setFieldsValue({ settlementAccount: undefined });
      return;
    }
    editForm.setFieldsValue({
      bankAccount: undefined,
      bankName: undefined,
      bankBranchName: undefined,
    });
  };

  const handleUpdate = async (values: EmployeeEditFormValues) => {
    if (!employee) {
      return;
    }

    try {
      const { hireDate, ...rawValues } = values;
      const formValues = { ...rawValues } as Partial<EmployeeFormData>;

      formValues.name = normalizeTextValue(formValues.name);
      formValues.email = normalizeTextValue(formValues.email);
      formValues.department = normalizeTextValue(formValues.department);
      if (Array.isArray(formValues.departments)) {
        const departments = formValues.departments
          .map((value) => normalizeTextValue(value))
          .filter((value): value is string => Boolean(value));
        formValues.departments = departments;
        formValues.department = departments[0];
      }
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
      const sanitizedSettlementAccount =
        !settlementAccountValue ||
        settlementAccountValue === employee.settlementAccountMasked ||
        settlementAccountValue === employee.bankAccountMasked ||
        isMaskedDisplayValue(settlementAccountValue)
          ? undefined
          : settlementAccountValue;
      const sanitizedBankAccount =
        !bankAccountValue ||
        bankAccountValue === employee.bankAccountMasked ||
        bankAccountValue === employee.settlementAccountMasked ||
        isMaskedDisplayValue(bankAccountValue)
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
        hireDate: hireDate ? dayjs(hireDate).format('YYYY-MM-DD') : undefined,
      } as EmployeeFormData & { id: number });
      message.success('员工信息更新成功');
      setEditModalVisible(false);
      await employeeQuery.refetch();
    } catch (error) {
      message.error(`更新失败：${getErrorMessage(error)}`);
    }
  };

  const handleBindPlatform = async (values: PlatformBindData) => {
    if (!employee) {
      return;
    }

    try {
      const result = await bindPlatformMutation.mutateAsync({ id: employee.id, ...values });
      if (result?.result === 'PENDING_APPROVAL') {
        message.info(result.message || '平台账号冲突，已提交审批');
      } else if (result?.result === 'ALREADY_BOUND') {
        message.info(result.message || '该平台账号已经绑定');
      } else if (result?.result && result.result !== 'SUCCESS') {
        message.error(result.message || '平台绑定失败');
        return;
      } else {
        message.success(result?.message || '平台绑定成功');
      }
      setBindModalVisible(false);
      bindForm.resetFields();
      await employeeQuery.refetch();
    } catch (error) {
      message.error(`绑定失败：${getErrorMessage(error)}`);
    }
  };

  const handleToggleOffline = () => {
    if (!employee) {
      return;
    }
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
          await employeeQuery.refetch();
        } catch (error) {
          message.error(`操作失败：${getErrorMessage(error)}`);
        }
      },
    });
  };

  const openManagerModal = () => {
    if (!employee) {
      return;
    }
    managerForm.setFieldsValue({ managerId: employee.managerId ?? undefined });
    setManagerModalVisible(true);
  };

  const handleAssignManager = async () => {
    if (!employee) {
      return;
    }
    try {
      const { managerId } = await managerForm.validateFields();
      await assignManagerMutation.mutateAsync({ id: employee.id, managerId });
      message.success('负责人已更新');
      setManagerModalVisible(false);
      managerForm.resetFields();
      await employeeQuery.refetch();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return;
      }
      message.error(`操作失败：${getErrorMessage(error)}`);
    }
  };

  const handleViewSensitiveData = (type: 'idCard' | 'settlementAccount') => {
    modal.confirm({
      title: '查看敏感信息',
      content: (
        <div>
          <p>您即将查看员工的{type === 'idCard' ? '身份证号' : '收款账户'}信息。</p>
          <Alert title="此操作将被记录并审计" type="warning" showIcon style={{ marginTop: 8 }} />
        </div>
      ),
      icon: <ExclamationCircleOutlined />,
      onOk: () => {
        setSensitiveDataVisible((previous) => ({ ...previous, [type]: true }));
      },
    });
  };

  const handleHideSensitiveData = (type: 'idCard' | 'settlementAccount') => {
    setSensitiveDataVisible((previous) => ({ ...previous, [type]: false }));
  };

  const handleRefresh = () => {
    void employeeQuery.refetch();
    if (hasResolvedEmployeeId) {
      void approvalsQuery.refetch();
      void payslipsQuery.refetch();
      void paymentsQuery.refetch();
    }
  };

  if (!id) {
    return (
      <PageContainer className="employee-detail-page">
        <div className="employee-detail-state">
          <Text type="secondary">无效的员工ID</Text>
        </div>
      </PageContainer>
    );
  }

  if (employeeQuery.isLoading) {
    return (
      <PageContainer className="employee-detail-page">
        <div className="employee-detail-state">
          <Text>加载中...</Text>
        </div>
      </PageContainer>
    );
  }

  if (employeeQuery.isError || !employee) {
    return (
      <PageContainer className="employee-detail-page">
        <div className="employee-detail-state">
          <ExclamationCircleOutlined className="employee-detail-state-icon" />
          <div className="employee-detail-state-title">员工信息加载失败</div>
          <div className="employee-detail-state-message">
            {employeeQuery.error ? getErrorMessage(employeeQuery.error) : '员工不存在或网络错误'}
          </div>
          <Space>
            <Button onClick={handleBackToList}>返回列表</Button>
            <Button type="primary" onClick={() => void employeeQuery.refetch()}>
              重新加载
            </Button>
          </Space>
        </div>
      </PageContainer>
    );
  }

  const statusInfo = getStatusInfo(employee.status);
  const employeeSettlementType = normalizeSettlementAccountType(employee.settlementAccountType);

  return (
    <PageContainer
      className="employee-detail-page"
      header={{
        title: employee.name,
        subTitle: `员工详情 - ${employee.employeeId}`,
        onBack: handleBackToList,
        extra: (
          <Button
            type="text"
            icon={<ReloadOutlined />}
            onClick={handleRefresh}
            loading={employeeQuery.isLoading}
            aria-label="刷新"
          >
            刷新
          </Button>
        ),
      }}
    >
      <EmployeeDetailContent
        employee={employee}
        employeeProvider={employeeProvider}
        employeeSubjectId={employeeSubjectId}
        statusInfo={statusInfo}
        employeeSettlementType={employeeSettlementType}
        sensitiveDataVisible={sensitiveDataVisible}
        idCardQuery={idCardQuery}
        settlementAccountQuery={settlementAccountQuery}
        approvalsQuery={approvalsQuery}
        payslipsQuery={payslipsQuery}
        paymentsQuery={paymentsQuery}
        approvalsPage={approvalsPage}
        payslipsPage={payslipsPage}
        paymentsPage={paymentsPage}
        onEdit={openEditModal}
        onBind={() => setBindModalVisible(true)}
        onToggleOffline={handleToggleOffline}
        onAssignManager={openManagerModal}
        onViewSensitiveData={handleViewSensitiveData}
        onHideSensitiveData={handleHideSensitiveData}
        onApprovalsPageChange={setApprovalsPage}
        onPayslipsPageChange={setPayslipsPage}
        onPaymentsPageChange={setPaymentsPage}
        isToggleOfflinePending={toggleOfflineMutation.isPending}
        isAssignManagerPending={assignManagerMutation.isPending}
        canEdit={canEdit}
        canBind={canBind}
        canToggleOffline={canToggleOffline}
        canAssignManager={canAssignManager}
        canViewIdCard={canViewIdCard}
        canViewSettlementAccount={canViewSettlementAccount}
        canViewApprovals={canViewApprovals}
        canViewPayslips={canViewPayslips}
        canViewPayments={canViewPayments}
      />

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
        <Form form={editForm} layout="vertical" onFinish={handleUpdate}>
          <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input placeholder="请输入员工姓名" />
          </Form.Item>

          <Form.Item name="phone" label="手机号">
            <Input placeholder="请输入手机号" />
          </Form.Item>

          <Form.Item name="email" label="邮箱">
            <Input placeholder="请输入邮箱地址" />
          </Form.Item>

          <Form.Item name="departments" label="部门">
            <Select mode="tags" placeholder="输入部门名称后回车" tokenSeparators={[',', '，', '/', '、']} />
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
                      if (
                        !normalized ||
                        isMaskedDisplayValue(normalized) ||
                        BANK_ACCOUNT_PATTERN.test(normalized)
                      ) {
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
              rules={[
                {
                  required: true,
                  message: `请输入${getSettlementAccountLabel(resolvedEditSettlementType)}`,
                },
              ]}
            >
              <Input placeholder={getSettlementAccountPlaceholder(resolvedEditSettlementType)} />
            </Form.Item>
          )}

          <Form.Item name="settlementAccountName" label="收款账户实名">
            <Input placeholder="请输入收款账户实名（选填）" />
          </Form.Item>
        </Form>
      </Modal>

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
        <Form form={bindForm} layout="vertical" onFinish={handleBindPlatform}>
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
