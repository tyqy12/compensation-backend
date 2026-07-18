import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Avatar, Button, Descriptions, Space, Statistic, Table, Tabs, Tag, Typography } from 'antd';
import {
  BankOutlined,
  CalendarOutlined,
  EditOutlined,
  EyeInvisibleOutlined,
  EyeOutlined,
  IdcardOutlined,
  LinkOutlined,
  MailOutlined,
  PhoneOutlined,
  StopOutlined,
  TeamOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons';
import type { DescriptionsProps, TableColumnsType } from 'antd';
import type { PaymentRecordItemVO } from '@types/openapi';
import type { PagedResponse } from '@types/api';
import { getPagedRecords } from '@types/api';
import {
  type Employee,
  type EmployeeApprovalRecord,
  type EmployeePayslipRecord,
} from '@services/queries/employee';
import {
  formatAmount,
  formatDate,
  formatDateTime,
  getApprovalStatusColor,
  getConfirmationStatusColor,
  getConfirmationStatusText,
  getEmployeeInitials,
  getPaymentStatusColor,
  getPayslipStatusColor,
  getPlatformColor,
  getPlatformName,
  getSettlementAccountLabel,
  getSettlementAccountTypeName,
  type StatusColor,
} from './detailUtils';

const { Text, Title } = Typography;

type SensitiveQueryState = {
  data?: string | null;
  isLoading?: boolean;
  isError?: boolean;
};

type PagedQueryState<T> = {
  data?: PagedResponse<T>;
  isLoading?: boolean;
};

type PageState = {
  current: number;
  pageSize: number;
};

type InfoSectionProps = {
  title: string;
  description?: string;
  extra?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
};

export type EmployeeDetailContentProps = {
  employee: Employee;
  employeeProvider?: string;
  employeeSubjectId?: string;
  statusInfo: { text: string; color: StatusColor };
  employeeSettlementType?: string;
  sensitiveDataVisible: {
    idCard: boolean;
    settlementAccount: boolean;
  };
  idCardQuery: SensitiveQueryState;
  settlementAccountQuery: SensitiveQueryState;
  approvalsQuery: PagedQueryState<EmployeeApprovalRecord>;
  payslipsQuery: PagedQueryState<EmployeePayslipRecord>;
  paymentsQuery: PagedQueryState<PaymentRecordItemVO>;
  approvalsPage: PageState;
  payslipsPage: PageState;
  paymentsPage: PageState;
  onEdit: () => void;
  onBind: () => void;
  onToggleOffline: () => void;
  onAssignManager: () => void;
  onViewSensitiveData: (type: 'idCard' | 'settlementAccount') => void;
  onHideSensitiveData: (type: 'idCard' | 'settlementAccount') => void;
  onApprovalsPageChange: (page: PageState) => void;
  onPayslipsPageChange: (page: PageState) => void;
  onPaymentsPageChange: (page: PageState) => void;
  isToggleOfflinePending?: boolean;
  isAssignManagerPending?: boolean;
  canEdit: boolean;
  canBind: boolean;
  canToggleOffline: boolean;
  canAssignManager: boolean;
  canViewIdCard: boolean;
  canViewSettlementAccount: boolean;
  canViewApprovals: boolean;
  canViewPayslips: boolean;
  canViewPayments: boolean;
};

const InfoSection: React.FC<InfoSectionProps> = ({
  title,
  description,
  extra,
  children,
  className,
}) => (
  <section className={`employee-detail-section ${className || ''}`}>
    <div className="employee-section-heading">
      <div>
        <Title level={4} className="employee-section-title">
          {title}
        </Title>
        {description && <Text type="secondary">{description}</Text>}
      </div>
      {extra && <div className="employee-section-extra">{extra}</div>}
    </div>
    {children}
  </section>
);

const MissingValue: React.FC = () => <Text type="secondary">-</Text>;

const DisplayValue: React.FC<{ children?: React.ReactNode }> = ({ children }) => {
  if (children === undefined || children === null || children === '') {
    return <MissingValue />;
  }
  return <>{children}</>;
};

type SensitiveValueProps = {
  visible: boolean;
  data?: string | null;
  isLoading?: boolean;
  isError?: boolean;
  maskedValue?: string | null;
  maskWhenEmpty?: boolean;
  revealLabel?: string;
  emptyValue?: string;
  onView: () => void;
  onHide: () => void;
};

const SensitiveValue: React.FC<SensitiveValueProps> = ({
  visible,
  data,
  isLoading,
  isError,
  maskedValue,
  maskWhenEmpty = true,
  revealLabel = '查看',
  emptyValue = '未设置',
  onView,
  onHide,
}) => {
  let value: React.ReactNode;
  if (!visible) {
    value = maskedValue || (maskWhenEmpty ? '****' : <Text type="secondary">{emptyValue}</Text>);
  } else if (isLoading) {
    value = <Text type="secondary">加载中...</Text>;
  } else if (isError) {
    value = <Text type="danger">查询失败</Text>;
  } else if (data) {
    value = <Text code>{data}</Text>;
  } else {
    value = <Text type="secondary">{emptyValue}</Text>;
  }

  return (
    <Space className="employee-sensitive-value" size={8}>
      {value}
      {visible ? (
        <Button
          type="link"
          size="small"
          icon={<EyeInvisibleOutlined />}
          onClick={onHide}
          aria-label="隐藏"
        >
          隐藏
        </Button>
      ) : maskWhenEmpty || maskedValue ? (
        <Button
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={onView}
          aria-label={revealLabel}
        >
          {revealLabel}
        </Button>
      ) : null}
    </Space>
  );
};

type SummaryMetricProps = {
  title: string;
  value: React.ReactNode;
  description?: React.ReactNode;
  className?: string;
};

const SummaryMetric: React.FC<SummaryMetricProps> = ({ title, value, description, className }) => (
  <div className={`employee-summary-metric ${className || ''}`}>
    <Text type="secondary" className="employee-summary-label">
      {title}
    </Text>
    <div className="employee-summary-value">{value}</div>
    {description && <Text type="secondary">{description}</Text>}
  </div>
);

type RecordTableProps<T extends object> = {
  columns: TableColumnsType<T>;
  dataSource: T[];
  loading?: boolean;
  page: PageState;
  total?: number;
  rowKey: string | ((record: T) => React.Key);
  scrollX: number;
  onPageChange: (page: PageState) => void;
};

function RecordTable<T extends object>({
  columns,
  dataSource,
  loading,
  page,
  total = 0,
  rowKey,
  scrollX,
  onPageChange,
}: RecordTableProps<T>) {
  return (
    <Table<T>
      size="small"
      rowKey={rowKey}
      columns={columns}
      dataSource={dataSource}
      loading={loading}
      locale={{ emptyText: '暂无记录' }}
      pagination={{
        current: page.current,
        pageSize: page.pageSize,
        total,
        showSizeChanger: true,
        showTotal: (count = 0) => `共 ${count} 条`,
        onChange: (current, pageSize) =>
          onPageChange({ current, pageSize: pageSize || page.pageSize }),
      }}
      scroll={{ x: scrollX }}
    />
  );
}

const EmployeeDetailContent: React.FC<EmployeeDetailContentProps> = ({
  employee,
  employeeProvider,
  employeeSubjectId,
  statusInfo,
  employeeSettlementType,
  sensitiveDataVisible,
  idCardQuery,
  settlementAccountQuery,
  approvalsQuery,
  payslipsQuery,
  paymentsQuery,
  approvalsPage,
  payslipsPage,
  paymentsPage,
  onEdit,
  onBind,
  onToggleOffline,
  onAssignManager,
  onViewSensitiveData,
  onHideSensitiveData,
  onApprovalsPageChange,
  onPayslipsPageChange,
  onPaymentsPageChange,
  isToggleOfflinePending,
  isAssignManagerPending,
  canEdit,
  canBind,
  canToggleOffline,
  canAssignManager,
  canViewIdCard,
  canViewSettlementAccount,
  canViewApprovals,
  canViewPayslips,
  canViewPayments,
}) => {
  const navigate = useNavigate();
  const [activeRecordTab, setActiveRecordTab] = useState('approvals');
  const approvalRecords = getPagedRecords(approvalsQuery.data);
  const payslipRecords = getPagedRecords(payslipsQuery.data);
  const paymentRecords = getPagedRecords(paymentsQuery.data);
  const latestPayslip = payslipRecords[0];
  const latestPayment = paymentRecords[0];
  const settlementAccountMasked = employee.settlementAccountMasked || employee.bankAccountMasked;
  const departments = employee.departments?.length
    ? employee.departments
    : employee.department
      ? [employee.department]
      : [];

  const approvalColumns: TableColumnsType<EmployeeApprovalRecord> = [
    {
      title: '流程名称',
      dataIndex: 'workflowName',
      width: 180,
      render: (value) => value || '-',
    },
    {
      title: '类型',
      dataIndex: 'workflowTypeName',
      width: 120,
      render: (_, record) => record.workflowTypeName || record.workflowType || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (_, record) => (
        <Tag color={getApprovalStatusColor(record.status)}>
          {record.statusName || record.status || '-'}
        </Tag>
      ),
    },
    {
      title: '发起人',
      dataIndex: 'initiatorName',
      width: 120,
      render: (_, record) => record.initiatorName || record.initiatorId || '-',
    },
    {
      title: '当前审批人',
      dataIndex: 'currentApproverName',
      width: 140,
      render: (_, record) => record.currentApproverName || record.currentApproverId || '-',
    },
    {
      title: '提交时间',
      dataIndex: 'submitTime',
      width: 180,
      render: (value) => formatDateTime(value),
    },
  ];

  const payslipColumns: TableColumnsType<EmployeePayslipRecord> = [
    {
      title: '期间',
      dataIndex: 'periodLabel',
      width: 140,
      render: (value) => value || '-',
    },
    {
      title: '应发',
      dataIndex: 'grossAmount',
      width: 110,
      render: (value) => formatAmount(value),
    },
    {
      title: '个税',
      dataIndex: 'taxAmount',
      width: 110,
      render: (value) => formatAmount(value),
    },
    {
      title: '社保',
      dataIndex: 'socialAmount',
      width: 110,
      render: (value) => formatAmount(value),
    },
    {
      title: '实发',
      dataIndex: 'netAmount',
      width: 110,
      render: (value) => formatAmount(value),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value) => <Tag color={getPayslipStatusColor(value)}>{value || '-'}</Tag>,
    },
    {
      title: '确认状态',
      dataIndex: 'confirmationStatus',
      width: 130,
      render: (value) => (
        <Tag color={getConfirmationStatusColor(value)}>{getConfirmationStatusText(value)}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_, record) =>
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
  ];

  const paymentColumns: TableColumnsType<PaymentRecordItemVO> = [
    {
      title: '批次号',
      dataIndex: 'batchNo',
      width: 160,
      render: (value) => value || '-',
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 120,
      render: (value) => formatAmount(value),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value) => <Tag color={getPaymentStatusColor(value)}>{value || '-'}</Tag>,
    },
    {
      title: '渠道',
      dataIndex: 'providerCode',
      width: 120,
      render: (value) => value || 'alipay',
    },
    {
      title: '渠道单号',
      dataIndex: 'providerOrderNo',
      width: 180,
      render: (value) => value || '-',
    },
    {
      title: '支付时间',
      dataIndex: 'paymentTime',
      width: 180,
      render: (value) => formatDateTime(value),
    },
  ];

  const basicItems: DescriptionsProps['items'] = [
    {
      key: 'name',
      label: '姓名',
      children: <Text strong>{employee.name}</Text>,
    },
    {
      key: 'department',
      label: '部门',
      children: departments.length ? (
        <Space wrap size={[4, 4]}>
          {departments.map((department) => (
            <Tag key={department}>{department}</Tag>
          ))}
        </Space>
      ) : (
        <MissingValue />
      ),
    },
    {
      key: 'position',
      label: '职位',
      children: <DisplayValue>{employee.position}</DisplayValue>,
    },
    {
      key: 'employmentType',
      label: '用工类型',
      children:
        employee.employmentType === 'part_time' ? (
          '兼职'
        ) : employee.employmentType === 'full_time' ? (
          '全职'
        ) : (
          <MissingValue />
        ),
    },
    {
      key: 'hireDate',
      label: '入职日期',
      children: employee.hireDate ? (
        <Text>
          <CalendarOutlined className="employee-inline-icon" />
          {formatDate(employee.hireDate)}
        </Text>
      ) : (
        <MissingValue />
      ),
    },
    {
      key: 'manager',
      label: '负责人',
      children: employee.managerName || employee.managerId || <MissingValue />,
    },
    {
      key: 'offline',
      label: '架构外员工',
      children: (
        <Tag color={employee.offline ? 'orange' : 'green'}>{employee.offline ? '是' : '否'}</Tag>
      ),
    },
  ];

  const contactItems: DescriptionsProps['items'] = [
    {
      key: 'phone',
      label: '手机号',
      children: employee.phoneMasked ? (
        <Text>
          <PhoneOutlined className="employee-inline-icon" />
          {employee.phoneMasked}
        </Text>
      ) : (
        <MissingValue />
      ),
    },
    {
      key: 'email',
      label: '邮箱',
      children: employee.email ? (
        <Text>
          <MailOutlined className="employee-inline-icon" />
          {employee.email}
        </Text>
      ) : (
        <MissingValue />
      ),
    },
    {
      key: 'idCard',
      label: '身份证号',
      children: canViewIdCard ? (
        <SensitiveValue
          visible={sensitiveDataVisible.idCard}
          data={idCardQuery.data}
          isLoading={idCardQuery.isLoading}
          isError={idCardQuery.isError}
          onView={() => onViewSensitiveData('idCard')}
          onHide={() => onHideSensitiveData('idCard')}
        />
      ) : <MissingValue />,
    },
  ];

  const platformItems: DescriptionsProps['items'] = [
    {
      key: 'platform',
      label: '绑定平台',
      children: employeeProvider ? (
        <Tag color={getPlatformColor(employeeProvider)}>{getPlatformName(employeeProvider)}</Tag>
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
        <MissingValue />
      ),
    },
  ];

  const financialItems: DescriptionsProps['items'] = [
    {
      key: 'settlementType',
      label: '收款账户类型',
      children:
        employee.settlementAccountTypeName || getSettlementAccountTypeName(employeeSettlementType),
    },
    {
      key: 'settlementAccount',
      label: getSettlementAccountLabel(employeeSettlementType),
      children: canViewSettlementAccount ? (
        <SensitiveValue
          visible={sensitiveDataVisible.settlementAccount}
          data={settlementAccountQuery.data}
          isLoading={settlementAccountQuery.isLoading}
          isError={settlementAccountQuery.isError}
          maskedValue={settlementAccountMasked}
          maskWhenEmpty={false}
          revealLabel="查看完整"
          onView={() => onViewSensitiveData('settlementAccount')}
          onHide={() => onHideSensitiveData('settlementAccount')}
        />
      ) : <MissingValue />,
    },
    {
      key: 'settlementAccountName',
      label: '收款账户实名',
      children: <DisplayValue>{employee.settlementAccountName}</DisplayValue>,
    },
    ...(employeeSettlementType === 'bank_card'
      ? [
          {
            key: 'bankName',
            label: '开户银行',
            children: employee.bankName ? (
              <Text>
                <BankOutlined className="employee-inline-icon" />
                {employee.bankName}
              </Text>
            ) : (
              <MissingValue />
            ),
          },
          {
            key: 'bankBranchName',
            label: '开户支行',
            children: <DisplayValue>{employee.bankBranchName}</DisplayValue>,
          },
        ]
      : []),
  ];

  const systemItems: DescriptionsProps['items'] = [
    {
      key: 'createTime',
      label: '创建时间',
      children: formatDateTime(employee.createTime),
    },
    {
      key: 'updateTime',
      label: '更新时间',
      children: formatDateTime(employee.updateTime),
    },
    {
      key: 'internalId',
      label: '内部ID',
      children: <Text code>{employee.id}</Text>,
    },
  ];

  const recordTabItems = [
    canViewApprovals && {
      key: 'approvals',
      label: `审批记录 (${approvalsQuery.data?.total || 0})`,
      children: (
        <RecordTable<EmployeeApprovalRecord>
          rowKey={(record) => record.id}
          columns={approvalColumns}
          dataSource={approvalRecords}
          loading={approvalsQuery.isLoading}
          page={approvalsPage}
          total={approvalsQuery.data?.total}
          scrollX={900}
          onPageChange={onApprovalsPageChange}
        />
      ),
    },
    canViewPayslips && {
      key: 'payslips',
      label: `发薪记录 (${payslipsQuery.data?.total || 0})`,
      children: (
        <RecordTable<EmployeePayslipRecord>
          rowKey={(record) => record.lineId}
          columns={payslipColumns}
          dataSource={payslipRecords}
          loading={payslipsQuery.isLoading}
          page={payslipsPage}
          total={payslipsQuery.data?.total}
          scrollX={920}
          onPageChange={onPayslipsPageChange}
        />
      ),
    },
    canViewPayments && {
      key: 'payments',
      label: `支付记录 (${paymentsQuery.data?.total || 0})`,
      children: (
        <RecordTable<PaymentRecordItemVO>
          rowKey={(record) => String(record.id ?? `${record.batchNo}-${record.paymentTime}`)}
          columns={paymentColumns}
          dataSource={paymentRecords}
          loading={paymentsQuery.isLoading}
          page={paymentsPage}
          total={paymentsQuery.data?.total}
          scrollX={920}
          onPageChange={onPaymentsPageChange}
        />
      ),
    },
  ].filter((item): item is NonNullable<typeof item> => Boolean(item));
  const effectiveRecordTab = recordTabItems.some((item) => item?.key === activeRecordTab)
    ? activeRecordTab
    : recordTabItems[0]?.key;

  return (
    <div className="employee-detail-view">
      <section className="employee-identity-band" aria-label="员工身份信息">
        <div className="employee-identity-copy">
          <Avatar size={64} className="employee-identity-avatar">
            {getEmployeeInitials(employee.name)}
          </Avatar>
          <div className="employee-identity-info">
            <div className="employee-identity-name-row">
              <Title level={2} className="employee-identity-name">
                {employee.name}
              </Title>
              <Tag color={statusInfo.color}>{statusInfo.text}</Tag>
              {employee.offline && <Tag color="orange">架构外</Tag>}
            </div>
            <div className="employee-identity-facts">
              <span>
                <IdcardOutlined /> {employee.employeeId}
              </span>
              {employee.hireDate && (
                <span>
                  <TeamOutlined /> 入职 {formatDate(employee.hireDate)}
                </span>
              )}
            </div>
          </div>
        </div>

        <div className="employee-identity-actions">
          {canEdit && (
            <Button type="primary" icon={<EditOutlined />} onClick={onEdit}>
              编辑信息
            </Button>
          )}
          {canBind && !employeeProvider && (
            <Button icon={<LinkOutlined />} onClick={onBind}>
              绑定平台
            </Button>
          )}
          {canAssignManager && (
            <Button
              icon={<UserSwitchOutlined />}
              onClick={onAssignManager}
              loading={isAssignManagerPending}
            >
              指定负责人
            </Button>
          )}
          {canToggleOffline && (
            <Button
              danger={!employee.offline}
              icon={<StopOutlined />}
              onClick={onToggleOffline}
              loading={isToggleOfflinePending}
            >
              {employee.offline ? '取消架构外标记' : '标记为架构外员工'}
            </Button>
          )}
        </div>
      </section>

      <InfoSection
        title="概览"
        description="快速查看员工状态和最近发薪结果"
        className="employee-summary-section"
      >
        <div className="employee-summary-grid">
          <SummaryMetric
            title="入职日期"
            value={employee.hireDate ? formatDate(employee.hireDate) : '-'}
            description={employee.offline ? '架构外员工' : '架构内员工'}
          />
          <SummaryMetric
            title="最近实发金额"
            value={
              latestPayslip?.netAmount != null ? (
                <Statistic value={Number(latestPayslip.netAmount)} precision={2} suffix="元" />
              ) : (
                '-'
              )
            }
            description={latestPayslip?.periodLabel || '暂无发薪记录'}
          />
          <SummaryMetric
            title="最近支付状态"
            value={
              latestPayment?.status ? (
                <Tag color={getPaymentStatusColor(latestPayment.status)}>
                  {latestPayment.status}
                </Tag>
              ) : (
                '-'
              )
            }
            description={
              latestPayment?.paymentTime
                ? formatDateTime(latestPayment.paymentTime)
                : '暂无支付记录'
            }
          />
          <SummaryMetric
            title="绑定平台"
            value={employeeProvider ? '已绑定' : '-'}
            description={employeeProvider ? '平台账号已关联' : '暂无平台账号'}
          />
        </div>
      </InfoSection>

      <div className="employee-detail-grid">
        <main className="employee-detail-main">
          <InfoSection title="基本信息" description="员工身份、组织归属和用工信息">
            <Descriptions
              className="employee-descriptions"
              size="small"
              column={{ xs: 1, sm: 2 }}
              items={basicItems}
            />
          </InfoSection>

          <InfoSection title="联系信息" description="联系方式和受控敏感信息">
            <Descriptions
              className="employee-descriptions"
              size="small"
              column={{ xs: 1, sm: 2 }}
              items={contactItems}
            />
          </InfoSection>
        </main>

        <aside className="employee-detail-side">
          <InfoSection title="平台绑定" description="员工在第三方平台的身份映射">
            <Descriptions
              className="employee-descriptions"
              size="small"
              column={1}
              items={platformItems}
            />
          </InfoSection>

          <InfoSection title="财务信息" description="收款账户与最近发薪概况">
            <Descriptions
              className="employee-descriptions"
              size="small"
              column={1}
              items={financialItems}
            />
          </InfoSection>

          <InfoSection title="系统信息" description="记录创建和最近更新时间">
            <Descriptions
              className="employee-descriptions"
              size="small"
              column={1}
              items={systemItems}
            />
          </InfoSection>
        </aside>
      </div>

      {recordTabItems.length > 0 && (
        <InfoSection
          title="业务记录"
          description="审批、发薪和支付记录分开查看，分页互不影响"
          className="employee-records-section"
        >
          <Tabs activeKey={effectiveRecordTab} onChange={setActiveRecordTab} items={recordTabItems} />
        </InfoSection>
      )}
    </div>
  );
};

export default EmployeeDetailContent;
