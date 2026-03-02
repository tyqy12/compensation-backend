import React, { useState, useRef } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { Button, Space, Tag, Typography, Tooltip, Modal, Form, Input, Select, DatePicker, App as AntdApp } from 'antd';
import {
  PageContainer,
  ProTable,
  type ProColumns,
  type ActionType,
  type ProFormInstance
} from '@ant-design/pro-components';
import {
  UserOutlined,
  PlusOutlined,
  EyeOutlined,
  LinkOutlined,
  StopOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  ReloadOutlined,
  ImportOutlined,
} from '@ant-design/icons';
import {
  useEmployeesQuery,
  useCreateEmployeeMutation,
  useUpdateEmployeeStatusMutation,
  useBindPlatformMutation,
  useBatchImportEmployeesMutation,
  fetchEmployees,
  type Employee,
  type EmployeeQueryParams,
  type EmployeeFormData,
  type PlatformBindData,
} from '@services/queries/employee';
import dayjs from 'dayjs';
import { useHasAction } from '@services/queries/rbac';
import { getPagedRecords } from '@types/api';

const { Text } = Typography;
const { Option } = Select;

const EmployeesList: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  const parsePositiveInt = (raw: string | null | undefined, fallback: number) => {
    const parsed = Number.parseInt(String(raw ?? ''), 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
  };

  // 从URL恢复搜索参数
  const [queryParams, setQueryParams] = useState<EmployeeQueryParams>(() => {
    const params: EmployeeQueryParams = {
      // 兼容不同历史参数命名，避免 NaN/0 导致分页组件内部异常
      current: parsePositiveInt(searchParams.get('current') ?? searchParams.get('page'), 1),
      pageSize: parsePositiveInt(searchParams.get('pageSize') ?? searchParams.get('size'), 10),
      keyword: searchParams.get('keyword') || undefined,
      department: searchParams.get('department') || undefined,
      status: (searchParams.get('status') as any) || undefined,
      platformType: (searchParams.get('platformType') as any) || undefined,
      isOffline: searchParams.get('isOffline') === 'true' ? true : undefined,
      sortBy: searchParams.get('sortBy') || 'createTime',
      order: (searchParams.get('order') as 'asc' | 'desc') || 'desc',
    };
    return params;
  });

  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [bindModalVisible, setBindModalVisible] = useState(false);
  const [importModalVisible, setImportModalVisible] = useState(false);
  const [importPayload, setImportPayload] = useState('');
  const [bindingEmployee, setBindingEmployee] = useState<Employee | null>(null);

  const actionRef = useRef<ActionType>();
  const formRef = useRef<ProFormInstance>();
  const [createForm] = Form.useForm();
  const [bindForm] = Form.useForm();

  const { message, modal } = AntdApp.useApp();
  const canCreate = useHasAction('api.employee.create');
  const canBind = useHasAction('api.employee.bind-platform');
  const canUpdateStatus = useHasAction('api.employee.status');
  const canImport = useHasAction('api.employee.batch-import');

  // 查询员工列表
  const employeesQuery = useEmployeesQuery(queryParams);

  // 操作mutations
  const createEmployeeMutation = useCreateEmployeeMutation();
  const updateStatusMutation = useUpdateEmployeeStatusMutation();
  const bindPlatformMutation = useBindPlatformMutation();
  const batchImportMutation = useBatchImportEmployeesMutation();

  // 更新URL参数
  const updateUrlParams = (params: EmployeeQueryParams) => {
    const newSearchParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '' && value !== null) {
        newSearchParams.set(key, String(value));
      }
    });
    setSearchParams(newSearchParams);
  };

  // 创建员工
  const handleCreate = async (values: EmployeeFormData) => {
    try {
      await createEmployeeMutation.mutateAsync({
        ...values,
        hireDate: values.hireDate ? dayjs(values.hireDate).format('YYYY-MM-DD') : undefined,
      });
      message.success('员工创建成功');
      setCreateModalVisible(false);
      createForm.resetFields();
      actionRef.current?.reload();
    } catch (error: any) {
      message.error(`创建失败：${error.message || '网络错误'}`);
    }
  };

  const handleBatchImport = async () => {
    try {
      const parsed = JSON.parse(importPayload);
      if (!Array.isArray(parsed) || parsed.length === 0) {
        message.error('导入数据必须是非空 JSON 数组');
        return;
      }
      await batchImportMutation.mutateAsync({ employees: parsed });
      message.success(`批量导入成功，共处理 ${parsed.length} 条`);
      setImportModalVisible(false);
      setImportPayload('');
      actionRef.current?.reload();
    } catch (error: any) {
      if (error instanceof SyntaxError) {
        message.error('JSON 格式不正确，请检查后重试');
        return;
      }
      message.error(`批量导入失败：${error?.message || '网络错误'}`);
    }
  };

  // 更新员工状态
  const handleStatusChange = async (employee: Employee, status: 'active' | 'inactive' | 'suspended') => {
    const actionText = status === 'active' ? '激活' : status === 'inactive' ? '停用' : '暂停';

    modal.confirm({
      title: `确认${actionText}员工`,
      content: `确定要${actionText}员工"${employee.name}"吗？`,
      icon: <ExclamationCircleOutlined />,
      onOk: async () => {
        try {
          await updateStatusMutation.mutateAsync({ id: employee.id, status });
          message.success(`员工${actionText}成功`);
          actionRef.current?.reload();
        } catch (error: any) {
          message.error(`${actionText}失败：${error.message || '网络错误'}`);
        }
      },
    });
  };

  // 绑定平台
  const handleBindPlatform = async (values: PlatformBindData) => {
    if (!bindingEmployee) return;

    try {
      await bindPlatformMutation.mutateAsync({
        id: bindingEmployee.id,
        ...values,
      });
      message.success('平台绑定成功');
      setBindModalVisible(false);
      setBindingEmployee(null);
      bindForm.resetFields();
      actionRef.current?.reload();
    } catch (error: any) {
      message.error(`绑定失败：${error.message || '网络错误'}`);
    }
  };

  const getStatusInfo = (status: Employee['status']) => {
    switch (status) {
      case 'active':
        return { icon: <CheckCircleOutlined />, text: '在职', color: 'success' as const };
      case 'inactive':
        return { icon: <CloseCircleOutlined />, text: '离职', color: 'default' as const };
      case 'suspended':
        return { icon: <StopOutlined />, text: '暂停', color: 'warning' as const };
      default:
        return { icon: <ExclamationCircleOutlined />, text: '未知', color: 'error' as const };
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

  const columns: ProColumns<Employee>[] = [
    {
      title: '员工ID',
      dataIndex: 'employeeId',
      width: 120,
      copyable: true,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '姓名',
      dataIndex: 'name',
      formItemProps: {
        rules: [{ required: true, message: '请输入姓名' }],
      },
      render: (_, record) => (
        <Link to={`/employees/${record.id}?${searchParams.toString()}`}>
          <Text strong>{record.name}</Text>
        </Link>
      ),
    },
    {
      title: '关键字',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: {
        placeholder: '搜索姓名或员工ID',
      },
    },
    {
      title: '部门',
      dataIndex: 'department',
      valueType: 'select',
      valueEnum: {
        '技术部': { text: '技术部' },
        '产品部': { text: '产品部' },
        '运营部': { text: '运营部' },
        '财务部': { text: '财务部' },
        '人事部': { text: '人事部' },
        '市场部': { text: '市场部' },
      },
      render: (department) => department || <Text type="secondary">-</Text>,
    },
    {
      title: '职位',
      dataIndex: 'position',
      hideInSearch: true,
      render: (position) => position || <Text type="secondary">-</Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: {
        active: { text: '在职', status: 'Success' },
        inactive: { text: '离职', status: 'Default' },
        suspended: { text: '暂停', status: 'Warning' },
      },
      render: (_, record) => {
        const statusInfo = getStatusInfo(record.status);
        return (
          <Tag icon={statusInfo.icon} color={statusInfo.color}>
            {statusInfo.text}
          </Tag>
        );
      },
    },
    {
      title: '平台绑定',
      dataIndex: 'platformType',
      valueType: 'select',
      valueEnum: {
        wechat: { text: '企业微信' },
        dingtalk: { text: '钉钉' },
        feishu: { text: '飞书' },
      },
      render: (_, record) => (
        record.platformType ? (
          <Tag color={record.platformType === 'wechat' ? 'green' : record.platformType === 'dingtalk' ? 'blue' : 'orange'}>
            {getPlatformName(record.platformType)}
          </Tag>
        ) : (
          <Text type="secondary">未绑定</Text>
        )
      ),
    },
    {
      title: '离线员工',
      dataIndex: 'isOffline',
      valueType: 'select',
      valueEnum: {
        true: { text: '是' },
        false: { text: '否' },
      },
      hideInTable: true,
    },
    {
      title: '手机号',
      dataIndex: 'phoneMasked',
      hideInSearch: true,
      width: 120,
      render: (phoneMasked) => phoneMasked || <Text type="secondary">-</Text>,
    },
    {
      title: '入职日期',
      dataIndex: 'hireDate',
      valueType: 'date',
      hideInSearch: true,
      render: (_, record) => record.hireDate ? dayjs(record.hireDate).format('YYYY-MM-DD') : <Text type="secondary">-</Text>,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 180,
      render: (_, record) => [
        <Tooltip key="view" title="查看详情">
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => navigate(`/employees/${record.id}?${searchParams.toString()}`)}
          >
            查看
          </Button>
        </Tooltip>,
        !record.platformType && canBind && (
          <Tooltip key="bind" title="绑定平台">
            <Button
              type="link"
              size="small"
              icon={<LinkOutlined />}
              onClick={() => {
                setBindingEmployee(record);
                setBindModalVisible(true);
              }}
            >
              绑定
            </Button>
          </Tooltip>
        ),
        canUpdateStatus && (record.status === 'active' ? (
          <Tooltip key="suspend" title="暂停员工">
            <Button
              type="link"
              size="small"
              danger
              icon={<StopOutlined />}
              onClick={() => handleStatusChange(record, 'suspended')}
            >
              暂停
            </Button>
          </Tooltip>
        ) : (
          <Tooltip key="activate" title="激活员工">
            <Button
              type="link"
              size="small"
              icon={<CheckCircleOutlined />}
              onClick={() => handleStatusChange(record, 'active')}
            >
              激活
            </Button>
          </Tooltip>
        )),
      ].filter(Boolean),
    },
  ];

  const isLoading = employeesQuery.isLoading ||
                   createEmployeeMutation.isPending ||
                   updateStatusMutation.isPending ||
                   bindPlatformMutation.isPending ||
                   batchImportMutation.isPending;

  return (
    <PageContainer
      header={{
        title: '员工管理',
        subTitle: '管理公司员工信息和平台绑定',
      }}
      extra={[
        canImport && <Button
          key="import"
          icon={<ImportOutlined />}
          onClick={() => setImportModalVisible(true)}
        >
          批量导入
        </Button>,
        canCreate && <Button
          key="create"
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setCreateModalVisible(true)}
        >
          新增员工
        </Button>,
        <Button
          key="refresh"
          icon={<ReloadOutlined />}
          onClick={() => actionRef.current?.reload()}
          loading={employeesQuery.isLoading}
        >
          刷新
        </Button>,
      ]}
    >
      <ProTable<Employee>
        columns={columns}
        actionRef={actionRef}
        formRef={formRef}
        request={async (params, sort) => {
          const newParams: EmployeeQueryParams = {
            current: params.current || 1,
            pageSize: params.pageSize || 10,
            keyword: params.keyword,
            department: params.department,
            status: params.status,
            platformType: params.platformType,
            isOffline:
              params.isOffline === true || params.isOffline === 'true'
                ? true
                : params.isOffline === false || params.isOffline === 'false'
                  ? false
                  : undefined,
            sortBy: Object.keys(sort || {})[0] || 'createTime',
            order: Object.keys(sort || {}).length > 0 ?
              (Object.values(sort || {})[0] === 'ascend' ? 'asc' : 'desc') : 'desc',
          };

          setQueryParams(newParams);
          updateUrlParams(newParams);

          try {
            const data = await fetchEmployees(newParams);

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
          pageSize: queryParams.pageSize,
          current: queryParams.current,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total = 0, range) => {
            const [start, end] = range ?? [0, 0];
            return `第 ${start}-${end} 条/共 ${total} 条`;
          },
        }}
        rowSelection={{
          selectedRowKeys,
          onChange: setSelectedRowKeys,
          getCheckboxProps: () => ({
            disabled: isLoading,
          }),
        }}
        tableAlertRender={({ selectedRowKeys = [] }) => (
          <Space>
            <span>已选择 {selectedRowKeys.length} 项</span>
            <Button
              type="link"
              size="small"
              onClick={() => setSelectedRowKeys([])}
            >
              取消选择
            </Button>
          </Space>
        )}
        loading={isLoading}
        locale={{
          emptyText: employeesQuery.isError ? (
            <div style={{ textAlign: 'center', padding: '40px 20px' }}>
              <ExclamationCircleOutlined style={{ fontSize: 48, color: '#ff4d4f', marginBottom: 16 }} />
              <div style={{ fontSize: 16, marginBottom: 8 }}>数据加载失败</div>
              <div style={{ color: '#8c8c8c', marginBottom: 16 }}>
                {employeesQuery.error?.message || '网络错误，请检查网络连接'}
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
                {Object.keys(queryParams).some(key =>
                  key !== 'current' && key !== 'pageSize' && key !== 'sortBy' && key !== 'order' &&
                  queryParams[key as keyof EmployeeQueryParams]
                )
                  ? '没有找到符合条件的员工记录'
                  : '还没有员工记录，点击"新增员工"开始添加'}
              </div>
            </div>
          ),
        }}
        options={{
          reload: true,
          density: true,
          setting: true,
        }}
        scroll={{ x: 1200 }}
      />

      {/* 新增员工弹窗 */}
      <Modal
        title="新增员工"
        open={createModalVisible}
        onCancel={() => {
          setCreateModalVisible(false);
          createForm.resetFields();
        }}
        onOk={() => createForm.submit()}
        confirmLoading={createEmployeeMutation.isPending}
        width={600}
      >
        <Form
          form={createForm}
          layout="vertical"
          initialValues={{ employmentType: 'full_time', offline: false, settlementAccountType: 'bank_card' }}
          onFinish={handleCreate}
        >
          <Form.Item
            name="employeeId"
            label="员工ID"
            rules={[{ required: true, message: '请输入员工ID' }]}
          >
            <Input placeholder="请输入唯一的员工ID" />
          </Form.Item>

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

          <Form.Item
            name="employmentType"
            label="用工类型"
            rules={[{ required: true, message: '请选择用工类型' }]}
          >
            <Select placeholder="请选择用工类型">
              <Option value="full_time" title="全职">全职</Option>
              <Option value="part_time" title="兼职">兼职</Option>
            </Select>
          </Form.Item>

          <Form.Item name="username" label="系统用户名">
            <Input placeholder="如 zhangfei 或 wbzhangfei" />
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

          <Form.Item name="offline" label="离线员工">
            <Select placeholder="是否为离线员工">
              <Option value={false}>否</Option>
              <Option value={true}>是</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="批量导入员工"
        open={importModalVisible}
        onCancel={() => {
          setImportModalVisible(false);
          setImportPayload('');
        }}
        onOk={handleBatchImport}
        confirmLoading={batchImportMutation.isPending}
        width={760}
      >
        <div style={{ marginBottom: 8, color: '#8c8c8c' }}>
          请输入 JSON 数组，字段至少包含 <Text code>employeeId</Text>、<Text code>name</Text>。
        </div>
        <Input.TextArea
          rows={14}
          value={importPayload}
          onChange={(event) => setImportPayload(event.target.value)}
          placeholder={`[
  {"employeeId":"E1001","name":"张三","employmentType":"full_time"},
  {"employeeId":"E1002","name":"李四","employmentType":"part_time","username":"wblisi"}
]`}
        />
      </Modal>

      {/* 绑定平台弹窗 */}
      <Modal
        title={`绑定平台 - ${bindingEmployee?.name}`}
        open={bindModalVisible}
        onCancel={() => {
          setBindModalVisible(false);
          setBindingEmployee(null);
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
    </PageContainer>
  );
};

export default EmployeesList;
