import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  Descriptions,
  Form,
  Input,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import dayjs from 'dayjs';
import {
  useMyEmployeeProfileQuery,
  useUpdateMyEmployeeContactMutation,
  useSubmitMyEmployeeChangeRequestMutation,
  useMyEmployeeChangeRequestsQuery,
  type EmployeeApprovalRecord,
  type EmployeeSelfContactUpdateData,
  type EmployeeSelfSensitiveChangeData,
} from '@services/queries/employee';
import { useCurrentUserSummaryQuery } from '@services/queries/session';
import { getPagedRecords } from '@types/api';

const { Text } = Typography;
const { Option } = Select;
const { TextArea } = Input;

const MASKED_VALUE_PATTERN = /\*{2,}/;
const PHONE_PATTERN = /^1\d{10}$/;
const ID_CARD_PATTERN = /(^\d{15}$)|(^\d{17}[\dXx]$)/;
const BANK_ACCOUNT_PATTERN = /^\d{8,30}$/;

type SettlementAccountType = NonNullable<EmployeeSelfSensitiveChangeData['settlementAccountType']>;

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
  if (
    normalized === 'bank_card' ||
    normalized === 'alipay' ||
    normalized === 'wechat' ||
    normalized === 'other'
  ) {
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

function getWorkflowStatusColor(status?: string) {
  switch ((status || '').toLowerCase()) {
    case 'approved':
    case 'completed':
      return 'success';
    case 'rejected':
    case 'failed':
      return 'error';
    case 'cancelled':
    case 'canceled':
      return 'default';
    default:
      return 'processing';
  }
}

const EmployeeProfile: React.FC = () => {
  const { message } = AntdApp.useApp();
  const [requestsPage, setRequestsPage] = useState({ current: 1, pageSize: 10 });

  const [contactForm] = Form.useForm<EmployeeSelfContactUpdateData>();
  const [sensitiveForm] = Form.useForm<EmployeeSelfSensitiveChangeData>();
  const sensitiveSettlementType = Form.useWatch('settlementAccountType', sensitiveForm);
  const resolvedSensitiveSettlementType = normalizeSettlementAccountType(
    String(sensitiveSettlementType ?? ''),
  );

  const currentUserQuery = useCurrentUserSummaryQuery();
  const hasEmployeeProfile = Boolean(
    currentUserQuery.data?.hasEmployeeProfile ?? currentUserQuery.data?.employeeId,
  );
  const selfServiceEnabled = Boolean(currentUserQuery.isSuccess && hasEmployeeProfile);

  const profileQuery = useMyEmployeeProfileQuery({ enabled: selfServiceEnabled });
  const changeRequestsQuery = useMyEmployeeChangeRequestsQuery(requestsPage, {
    enabled: selfServiceEnabled,
  });
  const updateContactMutation = useUpdateMyEmployeeContactMutation();
  const submitChangeRequestMutation = useSubmitMyEmployeeChangeRequestMutation();

  const employee = profileQuery.data;
  const requestRecords = useMemo(
    () => getPagedRecords(changeRequestsQuery.data),
    [changeRequestsQuery.data],
  );

  useEffect(() => {
    if (!employee) {
      return;
    }
    contactForm.setFieldsValue({
      phone: undefined,
      email: normalizeTextValue(employee.email),
    });
  }, [employee, contactForm]);

  const handleContactSubmit = async (values: EmployeeSelfContactUpdateData) => {
    const payload: EmployeeSelfContactUpdateData = {};
    const nextPhone = normalizeTextValue(values.phone);
    const nextEmail = normalizeTextValue(values.email);
    const currentEmail = normalizeTextValue(employee?.email);

    if (nextPhone && !isMaskedDisplayValue(nextPhone)) {
      payload.phone = nextPhone;
    }
    if (nextEmail && nextEmail !== currentEmail) {
      payload.email = nextEmail;
    }

    if (!payload.phone && !payload.email) {
      message.warning('请至少修改一个联系方式字段');
      return;
    }

    try {
      const updated = await updateContactMutation.mutateAsync(payload);
      contactForm.setFieldsValue({
        phone: undefined,
        email: normalizeTextValue(updated.email),
      });
      message.success('联系方式更新成功');
    } catch (error: any) {
      message.error(`联系方式更新失败：${error?.message || '网络错误'}`);
    }
  };

  const handleSensitiveSubmit = async (values: EmployeeSelfSensitiveChangeData) => {
    const payload: EmployeeSelfSensitiveChangeData = {};

    const nextName = normalizeTextValue(values.name);
    if (nextName && nextName !== normalizeTextValue(employee?.name)) {
      payload.name = nextName;
    }

    const nextIdCard = normalizeTextValue(values.idCard);
    if (nextIdCard) {
      payload.idCard = nextIdCard;
    }

    const nextType = normalizeSettlementAccountType(values.settlementAccountType);
    if (nextType && nextType !== normalizeSettlementAccountType(employee?.settlementAccountType)) {
      payload.settlementAccountType = nextType;
    }

    const nextSettlementAccount = normalizeTextValue(values.settlementAccount);
    if (nextSettlementAccount && !isMaskedDisplayValue(nextSettlementAccount)) {
      payload.settlementAccount = nextSettlementAccount;
    }

    const nextSettlementAccountName = normalizeTextValue(values.settlementAccountName);
    if (
      nextSettlementAccountName &&
      nextSettlementAccountName !== normalizeTextValue(employee?.settlementAccountName)
    ) {
      payload.settlementAccountName = nextSettlementAccountName;
    }

    const nextBankAccount = normalizeTextValue(values.bankAccount);
    if (nextBankAccount && !isMaskedDisplayValue(nextBankAccount)) {
      payload.bankAccount = nextBankAccount;
    }

    const nextBankName = normalizeTextValue(values.bankName);
    if (nextBankName && nextBankName !== normalizeTextValue(employee?.bankName)) {
      payload.bankName = nextBankName;
    }

    const nextBankBranchName = normalizeTextValue(values.bankBranchName);
    if (nextBankBranchName && nextBankBranchName !== normalizeTextValue(employee?.bankBranchName)) {
      payload.bankBranchName = nextBankBranchName;
    }

    const reason = normalizeTextValue(values.reason);
    if (reason) {
      payload.reason = reason;
    }

    if (
      isBankCardType(payload.settlementAccountType ?? nextType ?? employee?.settlementAccountType)
    ) {
      const resolvedBankAccount = payload.bankAccount ?? payload.settlementAccount;
      if (resolvedBankAccount) {
        payload.bankAccount = resolvedBankAccount;
        payload.settlementAccount = resolvedBankAccount;
      }
    }

    const hasSensitiveChanges = Boolean(
      payload.name ||
      payload.idCard ||
      payload.settlementAccountType ||
      payload.settlementAccount ||
      payload.settlementAccountName ||
      payload.bankAccount ||
      payload.bankName ||
      payload.bankBranchName,
    );

    if (!hasSensitiveChanges) {
      message.warning('请至少填写一个需要审批的敏感字段');
      return;
    }

    try {
      const result = await submitChangeRequestMutation.mutateAsync(payload);
      sensitiveForm.resetFields();
      setRequestsPage((prev) => ({ ...prev, current: 1 }));
      message.success(`敏感信息变更申请已提交（流程ID: ${result.workflowId}）`);
    } catch (error: any) {
      message.error(`申请提交失败：${error?.message || '网络错误'}`);
    }
  };

  const handleRefresh = () => {
    currentUserQuery.refetch();
    if (!selfServiceEnabled) {
      return;
    }
    profileQuery.refetch();
    changeRequestsQuery.refetch();
  };

  const employeeStatus = useMemo(() => {
    const status = (employee?.status || '').toLowerCase();
    if (status === 'active') return { text: '在职', color: 'success' };
    if (status === 'inactive') return { text: '离职', color: 'default' };
    if (status === 'suspended') return { text: '暂停', color: 'warning' };
    return { text: employee?.status || '未知', color: 'default' };
  }, [employee?.status]);

  const requestColumns = [
    {
      title: '流程ID',
      dataIndex: 'id',
      width: 100,
    },
    {
      title: '流程名称',
      dataIndex: 'workflowName',
      ellipsis: true,
      render: (value: string) => value || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 140,
      render: (_: string, record: EmployeeApprovalRecord) => (
        <Tag color={getWorkflowStatusColor(record.status)}>
          {record.statusName || record.status || '未知'}
        </Tag>
      ),
    },
    {
      title: '当前审批人',
      dataIndex: 'currentApproverName',
      width: 180,
      render: (value: string) => value || '-',
    },
    {
      title: '提交时间',
      dataIndex: 'submitTime',
      width: 180,
      render: (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '完成时间',
      dataIndex: 'completeTime',
      width: 180,
      render: (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
  ];

  if (currentUserQuery.isLoading) {
    return (
      <PageContainer
        title="个人资料维护"
        subTitle="联系方式可直接修改；姓名、身份证与收款账户类信息需提交审批"
        extra={[
          <Button key="refresh" icon={<ReloadOutlined />} loading>
            刷新
          </Button>,
        ]}
      >
        <Card loading />
      </PageContainer>
    );
  }

  if (currentUserQuery.isError) {
    return (
      <PageContainer
        title="个人资料维护"
        subTitle="联系方式可直接修改；姓名、身份证与收款账户类信息需提交审批"
        extra={[
          <Button key="refresh" icon={<ReloadOutlined />} onClick={handleRefresh}>
            刷新
          </Button>,
        ]}
      >
        <Alert
          type="error"
          showIcon
          title="获取当前账号信息失败"
          description="请刷新页面后重试；若问题持续存在，请联系管理员排查账号状态。"
        />
      </PageContainer>
    );
  }

  if (!selfServiceEnabled) {
    return (
      <PageContainer
        title="个人资料维护"
        subTitle="联系方式可直接修改；姓名、身份证与收款账户类信息需提交审批"
        extra={[
          <Button key="refresh" icon={<ReloadOutlined />} onClick={handleRefresh}>
            刷新
          </Button>,
        ]}
      >
        <Alert
          type="warning"
          showIcon
          title="当前账号未绑定员工档案"
          description="该页面仅适用于已绑定员工档案的账号。请先在用户绑定中关联员工后，再使用个人资料维护。"
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      title="个人资料维护"
      subTitle="联系方式可直接修改；姓名、身份证与收款账户类信息需提交审批"
      extra={[
        <Button
          key="refresh"
          icon={<ReloadOutlined />}
          onClick={handleRefresh}
          loading={
            currentUserQuery.isFetching || profileQuery.isFetching || changeRequestsQuery.isFetching
          }
        >
          刷新
        </Button>,
      ]}
    >
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          title="维护规则"
          description="手机号/邮箱修改后即时生效；姓名、身份证、收款账户信息会进入审批流程，审批通过后才会回写员工档案。"
        />

        <Card title="我的基础信息" loading={profileQuery.isLoading}>
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="员工ID">{employee?.employeeId || '-'}</Descriptions.Item>
            <Descriptions.Item label="姓名">{employee?.name || '-'}</Descriptions.Item>
            <Descriptions.Item label="手机号（脱敏）">
              {employee?.phoneMasked || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="邮箱">{employee?.email || '-'}</Descriptions.Item>
            <Descriptions.Item label="部门">{employee?.department || '-'}</Descriptions.Item>
            <Descriptions.Item label="岗位">{employee?.position || '-'}</Descriptions.Item>
            <Descriptions.Item label="入职日期">
              {employee?.hireDate ? dayjs(employee.hireDate).format('YYYY-MM-DD') : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="在职状态">
              <Tag color={employeeStatus.color}>{employeeStatus.text}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="收款类型">
              {employee?.settlementAccountTypeName || employee?.settlementAccountType || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="收款户名">
              {employee?.settlementAccountName || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="收款账号（脱敏）">
              {employee?.settlementAccountMasked || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="银行卡（脱敏）">
              {employee?.bankAccountMasked || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="开户银行">{employee?.bankName || '-'}</Descriptions.Item>
            <Descriptions.Item label="开户支行">
              {employee?.bankBranchName || '-'}
            </Descriptions.Item>
          </Descriptions>
        </Card>

        <Row gutter={16}>
          <Col xs={24} lg={12}>
            <Card title="联系方式（直接生效）">
              <Form<EmployeeSelfContactUpdateData>
                form={contactForm}
                layout="vertical"
                onFinish={handleContactSubmit}
                disabled={updateContactMutation.isPending}
              >
                <Form.Item
                  label={`手机号（当前：${employee?.phoneMasked || '未设置'}）`}
                  name="phone"
                  rules={[{ pattern: PHONE_PATTERN, message: '请输入11位手机号' }]}
                >
                  <Input placeholder="如需修改，请输入新手机号" allowClear />
                </Form.Item>
                <Form.Item
                  label="邮箱"
                  name="email"
                  rules={[{ type: 'email', message: '邮箱格式不正确' }]}
                >
                  <Input placeholder="请输入邮箱地址" allowClear />
                </Form.Item>
                <Space>
                  <Button
                    type="primary"
                    htmlType="submit"
                    loading={updateContactMutation.isPending}
                  >
                    保存联系方式
                  </Button>
                  <Button
                    onClick={() => {
                      contactForm.setFieldsValue({
                        phone: undefined,
                        email: normalizeTextValue(employee?.email),
                      });
                    }}
                  >
                    重置
                  </Button>
                </Space>
              </Form>
            </Card>
          </Col>

          <Col xs={24} lg={12}>
            <Card title="敏感信息变更（提交审批）">
              <Form<EmployeeSelfSensitiveChangeData>
                form={sensitiveForm}
                layout="vertical"
                onFinish={handleSensitiveSubmit}
                disabled={submitChangeRequestMutation.isPending}
              >
                <Form.Item
                  label="姓名"
                  name="name"
                  rules={[{ max: 100, message: '姓名不能超过100字符' }]}
                >
                  <Input placeholder={`当前：${employee?.name || '未设置'}`} allowClear />
                </Form.Item>

                <Form.Item
                  label="身份证号"
                  name="idCard"
                  rules={[{ pattern: ID_CARD_PATTERN, message: '请输入15或18位身份证号' }]}
                >
                  <Input placeholder="请输入身份证号" allowClear />
                </Form.Item>

                <Form.Item label="收款账户类型" name="settlementAccountType">
                  <Select allowClear placeholder="请选择收款账户类型">
                    <Option value="bank_card">银行卡</Option>
                    <Option value="alipay">支付宝</Option>
                    <Option value="wechat">微信</Option>
                    <Option value="other">其他</Option>
                  </Select>
                </Form.Item>

                <Form.Item
                  label={getSettlementAccountLabel(resolvedSensitiveSettlementType)}
                  name="settlementAccount"
                  rules={[
                    {
                      validator: async (_, value) => {
                        const text = normalizeTextValue(value);
                        if (!text) {
                          return;
                        }
                        if (
                          isBankCardType(resolvedSensitiveSettlementType) &&
                          !BANK_ACCOUNT_PATTERN.test(text)
                        ) {
                          throw new Error('银行卡账号需为8-30位数字');
                        }
                      },
                    },
                  ]}
                >
                  <Input
                    allowClear
                    placeholder={getSettlementAccountPlaceholder(resolvedSensitiveSettlementType)}
                  />
                </Form.Item>

                <Form.Item
                  label="收款账户实名"
                  name="settlementAccountName"
                  rules={[{ max: 100, message: '收款账户实名不能超过100字符' }]}
                >
                  <Input
                    placeholder={`当前：${employee?.settlementAccountName || '未设置'}`}
                    allowClear
                  />
                </Form.Item>

                {isBankCardType(resolvedSensitiveSettlementType) && (
                  <>
                    <Form.Item
                      label="银行卡号"
                      name="bankAccount"
                      rules={[{ pattern: BANK_ACCOUNT_PATTERN, message: '银行卡号需为8-30位数字' }]}
                    >
                      <Input placeholder="请输入银行卡号" allowClear />
                    </Form.Item>

                    <Form.Item
                      label="开户银行"
                      name="bankName"
                      rules={[{ max: 100, message: '开户银行不能超过100字符' }]}
                    >
                      <Input placeholder={`当前：${employee?.bankName || '未设置'}`} allowClear />
                    </Form.Item>

                    <Form.Item
                      label="开户支行"
                      name="bankBranchName"
                      rules={[{ max: 120, message: '开户支行不能超过120字符' }]}
                    >
                      <Input
                        placeholder={`当前：${employee?.bankBranchName || '未设置'}`}
                        allowClear
                      />
                    </Form.Item>
                  </>
                )}

                <Form.Item
                  label="变更原因（选填）"
                  name="reason"
                  rules={[{ max: 500, message: '原因不能超过500字符' }]}
                >
                  <TextArea rows={3} placeholder="请填写变更原因，便于审批人理解" />
                </Form.Item>

                <Space>
                  <Button
                    type="primary"
                    htmlType="submit"
                    loading={submitChangeRequestMutation.isPending}
                  >
                    提交审批申请
                  </Button>
                  <Button onClick={() => sensitiveForm.resetFields()}>重置</Button>
                </Space>
              </Form>
            </Card>
          </Col>
        </Row>

        <Card title="我的申请记录">
          <Table<EmployeeApprovalRecord>
            rowKey="id"
            columns={requestColumns}
            dataSource={requestRecords}
            loading={changeRequestsQuery.isLoading || changeRequestsQuery.isFetching}
            pagination={{
              current: requestsPage.current,
              pageSize: requestsPage.pageSize,
              total: changeRequestsQuery.data?.total || 0,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条`,
              onChange: (current, pageSize) => {
                setRequestsPage({ current, pageSize });
              },
            }}
            locale={{
              emptyText: <Text type="secondary">暂无申请记录</Text>,
            }}
            scroll={{ x: 900 }}
          />
        </Card>
      </Space>
    </PageContainer>
  );
};

export default EmployeeProfile;
