import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Form,
  Input,
  Modal,
  Radio,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Timeline,
  Tree,
  Typography,
  Spin,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { DataNode } from 'antd/es/tree';
import {
  ApartmentOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DownloadOutlined,
  EditOutlined,
  EyeOutlined,
  InfoCircleOutlined,
  ReloadOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import {
  usePlatformsQuery,
  useOrgCheckQuery,
  useOrgHistoryQuery,
  useOrgFetchPreviewMutation,
  useOrgImportMutation,
  useOrgDepartmentTreeQuery,
} from '@services/queries/org';
import type {
  DepartmentNode,
  EmployeePreviewDto,
  OrgImportRequestItem,
  OrgSyncHistoryItem,
  Platform,
  PlatformOption,
} from '@types/api';

const { Text } = Typography;

type FetchMode = 'department' | 'users' | 'all';

const PLATFORM_NAME_MAP: Record<string, string> = {
  wechat: '企业微信',
  wecom: '企业微信',
  qywx: '企业微信',
  dingtalk: '钉钉',
  dingding: '钉钉',
  feishu: '飞书',
  lark: '飞书',
  alipay: '支付宝',
  sms: '短信服务',
  email: '邮件服务',
  encryption: '加密配置',
};

const getPlatformName = (platform: Platform | string | 'all') => {
  if (platform === 'all') return '全部平台';
  return PLATFORM_NAME_MAP[String(platform).toLowerCase()] ?? String(platform);
};

const getCheckStatus = (status: string | undefined) => {
  const normalized = String(status ?? '').toUpperCase();
  switch (normalized) {
    case 'OK':
    case 'SUCCESS':
      return { icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />, text: '配置正常', color: 'success' as const };
    case 'MISSING_CONFIG':
    case 'NO_CONFIG':
      return { icon: <InfoCircleOutlined style={{ color: '#faad14' }} />, text: '缺少配置', color: 'warning' as const };
    case 'UNAUTHORIZED':
    case 'AUTH_FAILED':
      return { icon: <CloseCircleOutlined style={{ color: '#ff4d4f' }} />, text: '认证失败', color: 'error' as const };
    case 'ERROR':
    case 'FAILED':
      return { icon: <CloseCircleOutlined style={{ color: '#ff4d4f' }} />, text: '检查失败', color: 'error' as const };
    default:
      return { icon: <InfoCircleOutlined />, text: '未知状态', color: 'default' as const };
  }
};

const getHistoryStatus = (record?: OrgSyncHistoryItem) => {
  if (!record) {
    return { tagColor: 'default' as const, text: '未同步', timelineColor: 'gray' };
  }
  return record.success
    ? { tagColor: 'success' as const, text: '同步成功', timelineColor: 'green' }
    : { tagColor: 'error' as const, text: '同步失败', timelineColor: 'red' };
};

const toTreeData = (nodes?: DepartmentNode[]): DataNode[] =>
  (nodes ?? []).map((node) => ({
    key: node.platformDeptId ?? node.id,
    title: node.name,
    children: toTreeData(node.children),
  }));

const OrgSyncPage: React.FC = () => {
  const { message, modal } = AntdApp.useApp();
  const [selectedPlatform, setSelectedPlatform] = useState<string>('wechat');
  const [selectedDeptKeys, setSelectedDeptKeys] = useState<React.Key[]>([]);
  const [previewData, setPreviewData] = useState<EmployeePreviewDto[]>([]);
  const [selectedEmployees, setSelectedEmployees] = useState<React.Key[]>([]);
  const [isPreviewModalVisible, setIsPreviewModalVisible] = useState(false);
  const [editingEmployee, setEditingEmployee] = useState<EmployeePreviewDto | null>(null);

  const [fetchForm] = Form.useForm<{ mode: FetchMode; userIds?: string }>();
  const [editForm] = Form.useForm<EmployeePreviewDto>();
  const fetchMode = Form.useWatch('mode', fetchForm) ?? 'department';

  useEffect(() => {
    fetchForm.setFieldsValue({ mode: 'department' });
  }, [fetchForm]);

  const platformsQuery = usePlatformsQuery();
  const platforms = platformsQuery.data ?? [];

  useEffect(() => {
    if (platforms.length > 0) {
      const exists = platforms.some((item) => String(item.code) === String(selectedPlatform));
      if (!exists) {
        setSelectedPlatform(String(platforms[0].code));
      }
    }
  }, [platforms, selectedPlatform]);

  const orgCheckQuery = useOrgCheckQuery(selectedPlatform as Platform);
  const historyQuery = useOrgHistoryQuery();
  const departmentTreeQuery = useOrgDepartmentTreeQuery(selectedPlatform as Platform);

  const departmentTreeData = useMemo(() => toTreeData(departmentTreeQuery.data), [departmentTreeQuery.data]);
  const hasDepartmentTree = departmentTreeData.length > 0;

  useEffect(() => {
    if (!hasDepartmentTree && fetchForm.getFieldValue('mode') === 'department') {
      fetchForm.setFieldsValue({ mode: 'all' });
    }
    if (hasDepartmentTree && !fetchForm.getFieldValue('mode')) {
      fetchForm.setFieldsValue({ mode: 'department' });
    }
  }, [hasDepartmentTree, fetchForm]);

  useEffect(() => {
    setSelectedDeptKeys([]);
    setPreviewData([]);
    setSelectedEmployees([]);
  }, [selectedPlatform]);

  const historyList = useMemo(() => {
    if (!historyQuery.data) return [] as OrgSyncHistoryItem[];
    if (Array.isArray(historyQuery.data)) return historyQuery.data;
    return Object.values(historyQuery.data as Record<string, OrgSyncHistoryItem>);
  }, [historyQuery.data]);

  const historyByPlatform = useMemo(() => {
    const map: Record<string, OrgSyncHistoryItem> = {};
    historyList.forEach((item) => {
      const key = String(item.platformType).toLowerCase();
      const existing = map[key];
      const currentTime = new Date(item.syncTime ?? 0).getTime();
      const existingTime = existing ? new Date(existing.syncTime ?? 0).getTime() : -Infinity;
      if (!existing || currentTime > existingTime) {
        map[key] = item;
      }
    });
    return map;
  }, [historyList]);

  const sortedHistory = useMemo(
    () => [...historyList].sort((a, b) => new Date(b.syncTime ?? 0).getTime() - new Date(a.syncTime ?? 0).getTime()),
    [historyList],
  );

  const selectedHistory = historyByPlatform[String(selectedPlatform).toLowerCase()];

  const fetchPreviewMutation = useOrgFetchPreviewMutation();
  const importMutation = useOrgImportMutation();

  const selectedPlatformMeta: PlatformOption | undefined = platforms.find(
    (item) => String(item.code) === String(selectedPlatform),
  );

  const handleFetchPreview = async () => {
    try {
      const values = await fetchForm.validateFields();
      const options: Record<string, unknown> = {};

      if (values.mode === 'department') {
        if (!selectedDeptKeys.length) {
          message.warning('请选择至少一个部门节点');
          return;
        }
        options.departmentIds = selectedDeptKeys;
      } else if (values.mode === 'users') {
        const ids = (values.userIds || '')
          .split(/[\s,;，；]+/)
          .map((item) => item.trim())
          .filter(Boolean);
        if (!ids.length) {
          message.warning('请填写至少一个平台用户ID');
          return;
        }
        options.userIds = ids;
      }

      const result = await fetchPreviewMutation.mutateAsync({
        platform: selectedPlatform as Platform,
        options,
      });

      const employeesWithKey = result.employees.map((emp, index) => ({
        ...emp,
        rowKey: emp.rowKey ?? `${emp.platformUserId}-${index}`,
      }));

      setPreviewData(employeesWithKey);
      setSelectedEmployees(employeesWithKey.map((emp) => emp.rowKey ?? emp.employeeId));
      setIsPreviewModalVisible(true);
      message.success(`成功拉取 ${result.totalEmployees} 名员工信息`);
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(`拉取预览失败：${error?.message || '请检查平台配置'}`);
    }
  };

  const handleResetFetchOptions = () => {
    fetchForm.setFieldsValue({ mode: hasDepartmentTree ? 'department' : 'all', userIds: undefined });
    setSelectedDeptKeys([]);
  };

  const handleImport = () => {
    const selectedData = previewData.filter((emp) => selectedEmployees.includes(emp.rowKey ?? emp.employeeId));

    if (selectedData.length === 0) {
      message.warning('请选择要导入的员工');
      return;
    }

    modal.confirm({
      title: '确认导入',
      content: `确定要导入 ${selectedData.length} 名员工吗？`,
      onOk: async () => {
        try {
          const items: OrgImportRequestItem[] = selectedData.map((emp) => ({
            employeeId: emp.employeeId,
            name: emp.name,
            phone: emp.phone,
            email: emp.email,
            department: emp.department ?? emp.departments?.join(' / '),
            position: emp.position,
            employmentType: emp.employmentType,
            platformUserId: emp.platformUserId,
            platformType: emp.platformType,
            username: emp.employeeId,
          }));

          const payload = await importMutation.mutateAsync({
            platformType: selectedPlatform,
            items,
            metadata: {
              mode: fetchForm.getFieldValue('mode'),
              departmentIds: selectedDeptKeys,
            },
          });

          message.success(`导入完成：成功 ${payload.success} 人，失败 ${payload.failed} 人`);

          historyQuery.refetch();
          orgCheckQuery.refetch();

          if (payload.errors && payload.errors.length > 0) {
            modal.error({
              title: '导入错误详情',
              content: (
                <div>
                  {payload.errors.map((errorMsg, index) => (
                    <div key={String(index)} style={{ marginBottom: 4, fontSize: 12 }}>
                      {errorMsg}
                    </div>
                  ))}
                </div>
              ),
            });
          }

          setIsPreviewModalVisible(false);
        } catch (error: any) {
          message.error(`导入失败：${error?.message || '系统错误'}`);
        }
      },
    });
  };

  const handleEditEmployee = (employee: EmployeePreviewDto) => {
    setEditingEmployee(employee);
    editForm.setFieldsValue({
      name: employee.name,
      employeeId: employee.employeeId,
      department: employee.department,
      position: employee.position,
      phone: employee.phone,
      email: employee.email,
      employmentType: employee.employmentType,
    });
  };

  const handleSaveEmployee = async () => {
    try {
      const values = await editForm.validateFields();
      const key = editingEmployee?.rowKey ?? editingEmployee?.employeeId;
      if (!key) return;
      const updatedData = previewData.map((emp) => {
        const matchKey = emp.rowKey ?? emp.employeeId;
        if (matchKey !== key) return emp;
        const departments = values.department ? [values.department] : emp.departments;
        return {
          ...emp,
          ...values,
          department: values.department,
          departments,
        };
      });
      setPreviewData(updatedData);
      setEditingEmployee(null);
      editForm.resetFields();
      message.success('员工信息已更新');
    } catch (error) {
      // ignore validation warning
    }
  };

  const formatDateTime = (value?: string) => {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    });
  };

  const formatTimeLabel = (value?: string) => {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    });
  };

  const previewColumns: ColumnsType<EmployeePreviewDto> = [
    {
      title: '姓名',
      dataIndex: 'name',
      key: 'name',
      render: (_, record) => (
        <Space size={8}>
          <UserOutlined />
          <Text strong>{record.name}</Text>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEditEmployee(record)}>
            编辑
          </Button>
        </Space>
      ),
    },
    {
      title: '工号',
      dataIndex: 'employeeId',
      key: 'employeeId',
    },
    {
      title: '部门',
      dataIndex: 'department',
      key: 'department',
      render: (_, record) => record.department ?? record.departments?.join(' / ') ?? '—',
    },
    {
      title: '职位',
      dataIndex: 'position',
      key: 'position',
      render: (value) => value || '—',
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      key: 'phone',
      render: (value) => value || '—',
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      key: 'email',
      render: (value) => value || '—',
    },
    {
      title: '员工类型',
      dataIndex: 'employmentType',
      key: 'employmentType',
      render: (type) => (
        <Tag color={type === 'full_time' ? 'blue' : 'orange'}>{type === 'full_time' ? '全职' : '兼职'}</Tag>
      ),
    },
  ];

  const previewModal = (
    <Modal
      title={`员工预览 - ${getPlatformName(selectedPlatform)}`}
      open={isPreviewModalVisible}
      onCancel={() => setIsPreviewModalVisible(false)}
      width="90%"
      style={{ top: 20 }}
      footer={[
        <Button key="cancel" onClick={() => setIsPreviewModalVisible(false)}>
          取消
        </Button>,
        <Button
          key="import"
          type="primary"
          icon={<DownloadOutlined />}
          loading={importMutation.isPending}
          disabled={selectedEmployees.length === 0}
          onClick={handleImport}
        >
          导入选中员工 ({selectedEmployees.length})
        </Button>,
      ]}
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          message={`从 ${getPlatformName(selectedPlatform)} 拉取到 ${previewData.length} 名员工`}
          description="请检查员工信息，可以单独编辑或批量选择后导入。导入后将创建员工档案和系统账号。"
          type="info"
          showIcon
        />

        <Table<EmployeePreviewDto>
          rowSelection={{
            selectedRowKeys: selectedEmployees,
            onChange: setSelectedEmployees,
          }}
          columns={previewColumns}
          dataSource={previewData}
          rowKey={(record) => record.rowKey ?? record.employeeId ?? record.platformUserId}
          pagination={{
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total = 0, range) => {
              const [start, end] = range ?? [0, 0];
              return `第 ${start}-${end} 条，共 ${total} 条`;
            },
          }}
          scroll={{ x: 900 }}
          size="small"
        />
      </Space>
    </Modal>
  );

  const editModal = (
    <Modal
      title="编辑员工信息"
      open={Boolean(editingEmployee)}
      onCancel={() => {
        setEditingEmployee(null);
        editForm.resetFields();
      }}
      onOk={handleSaveEmployee}
      confirmLoading={false}
      destroyOnHidden
    >
      <Form form={editForm} layout="vertical" preserve={false}>
        <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="employeeId" label="工号" rules={[{ required: true, message: '请输入工号' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="department" label="部门">
          <Input placeholder="支持手动修改部门名称" />
        </Form.Item>
        <Form.Item name="position" label="职位">
          <Input />
        </Form.Item>
        <Form.Item name="phone" label="手机号">
          <Input />
        </Form.Item>
        <Form.Item name="email" label="邮箱">
          <Input />
        </Form.Item>
        <Form.Item name="employmentType" label="员工类型" rules={[{ required: true, message: '请选择员工类型' }]}
        >
          <Select>
            <Select.Option value="full_time">全职</Select.Option>
            <Select.Option value="part_time">兼职</Select.Option>
          </Select>
        </Form.Item>
      </Form>
    </Modal>
  );

  const totalPlatforms = platforms.length;
  const successfulSyncs = historyList.filter((item) => item.success).length;
  const lastSyncRecord = sortedHistory[0];
  const lastSyncTime = lastSyncRecord ? formatDateTime(lastSyncRecord.syncTime) : '—';

  return (
    <PageContainer
      header={{
        title: '组织同步',
        subTitle: '选择平台，拉取预览并导入企业微信等第三方的组织与人员数据',
      }}
      extra={[
        <Button
          key="refresh"
          icon={<ReloadOutlined />}
          onClick={() => {
            platformsQuery.refetch();
            orgCheckQuery.refetch();
            historyQuery.refetch();
            departmentTreeQuery.refetch();
          }}
          loading={platformsQuery.isLoading || orgCheckQuery.isLoading}
        >
          刷新状态
        </Button>,
      ]}
    >
      <Space direction="vertical" style={{ width: '100%' }} size="large">
        {/* 统计卡片 - 单行显示 */}
        <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 4 }}>
          <Card size="small" style={{ flex: '0 0 auto', width: 160 }}>
            <Statistic title="接入平台" value={totalPlatforms} suffix="个" />
          </Card>
          <Card size="small" style={{ flex: '0 0 auto', width: 160 }}>
            <Statistic title="成功同步" value={successfulSyncs} suffix="次" valueStyle={{ color: '#52c41a' }} />
          </Card>
          <Card size="small" style={{ flex: '0 0 auto', width: 180 }}>
            <Statistic title="最近同步时间" value={lastSyncTime} />
          </Card>
        </div>

        <Row gutter={[16, 16]}>
          <Col xs={24} xl={16}>
            <Card
              title="同步操作"
              size="small"
              extra={
                <Space>
                  <Button type="text" icon={<EyeOutlined />} onClick={handleFetchPreview} loading={fetchPreviewMutation.isPending}>
                    拉取预览
                  </Button>
                  <Button type="text" onClick={handleResetFetchOptions} disabled={fetchPreviewMutation.isPending}>
                    重置选项
                  </Button>
                </Space>
              }
            >
              <Space direction="vertical" size="large" style={{ width: '100%' }}>
                <div>
                  <Text strong>选择平台</Text>
                  <div style={{ marginTop: 8 }}>
                    <Radio.Group
                      value={selectedPlatform}
                      onChange={(e) => setSelectedPlatform(e.target.value)}
                      optionType="button"
                      buttonStyle="solid"
                    >
                      {platforms.map((item) => (
                        <Radio.Button key={item.code} value={item.code}>
                          {item.name}
                        </Radio.Button>
                      ))}
                    </Radio.Group>
                  </div>
                </div>

                <Descriptions column={3} size="small" bordered>
                  <Descriptions.Item label="平台">
                    <Tag color="blue">{getPlatformName(selectedPlatform)}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="已配置">
                    <Tag color={selectedPlatformMeta?.configured ? 'success' : 'default'}>
                      {selectedPlatformMeta?.configured ? '已配置' : '未配置'}
                    </Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="连接状态">
                    <Space size={8} align="center">
                      <Tag color={getCheckStatus(orgCheckQuery.data?.status).color}>
                        {getCheckStatus(orgCheckQuery.data?.status).text}
                      </Tag>
                      {orgCheckQuery.data?.message && (
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {orgCheckQuery.data.message}
                        </Text>
                      )}
                    </Space>
                  </Descriptions.Item>
                  <Descriptions.Item label="最近同步" span={3}>
                    {selectedHistory ? formatDateTime(selectedHistory.syncTime) : '—'}
                  </Descriptions.Item>
                </Descriptions>

                <Alert
                  type="info"
                  showIcon
                  message="同步步骤"
                  description="1. 选择同步范围 2. 拉取预览 3. 确认并导入。建议逐步校验，避免直接导入全部数据。"
                />

                <Form form={fetchForm} layout="vertical" requiredMark={false}>
                  <Form.Item label="同步范围" name="mode">
                    <Radio.Group optionType="button" buttonStyle="solid">
                      <Radio.Button value="department" disabled={!hasDepartmentTree}>
                        按部门
                      </Radio.Button>
                      <Radio.Button value="users">按用户</Radio.Button>
                      <Radio.Button value="all">全部</Radio.Button>
                    </Radio.Group>
                  </Form.Item>

                  {fetchMode === 'department' && (
                    <Form.Item label="选择部门">
                      <Spin spinning={departmentTreeQuery.isLoading}>
                        {hasDepartmentTree ? (
                          <div style={{ maxHeight: 260, overflow: 'auto', padding: '4px 8px', border: '1px solid #f0f0f0', borderRadius: 4 }}>
                            <Tree
                              checkable
                              selectable={false}
                              treeData={departmentTreeData}
                              checkedKeys={selectedDeptKeys}
                              onCheck={(keys) =>
                                setSelectedDeptKeys(Array.isArray(keys) ? keys : (keys as any).checked)
                              }
                              defaultExpandAll
                              switcherIcon={<ApartmentOutlined />}
                            />
                          </div>
                        ) : (
                          <Empty description="该平台暂不支持部门树" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                        )}
                      </Spin>
                    </Form.Item>
                  )}

                  {fetchMode === 'users' && (
                    <Form.Item
                      label="平台用户ID"
                      name="userIds"
                      rules={[{ required: true, message: '请输入平台用户ID，每行一个' }]}
                    >
                      <Input.TextArea rows={4} placeholder="每行一个用户ID，可粘贴企业微信成员ID" />
                    </Form.Item>
                  )}

                  <Space>
                    <Button
                      type="primary"
                      icon={<EyeOutlined />}
                      onClick={handleFetchPreview}
                      loading={fetchPreviewMutation.isPending}
                    >
                      拉取预览
                    </Button>
                    <Button onClick={handleResetFetchOptions} disabled={fetchPreviewMutation.isPending}>
                      重置选项
                    </Button>
                  </Space>
                </Form>
              </Space>
            </Card>
          </Col>

          <Col xs={24} xl={8}>
            <Card title="同步历史" size="small">
              {historyQuery.isLoading ? (
                <Spin />
              ) : historyQuery.isError ? (
                <Alert
                  type="error"
                  showIcon
                  message="同步历史加载失败"
                  description={
                    historyQuery.error instanceof Error ? historyQuery.error.message : '请稍后再试'
                  }
                />
              ) : sortedHistory.length ? (
                <Timeline
                  mode="left"
                  items={sortedHistory.map((item) => {
                    const status = getHistoryStatus(item);
                    return {
                      color: status.timelineColor,
                      label: formatTimeLabel(item.syncTime),
                      children: (
                        <div>
                          <Space size={8} wrap>
                            <Text strong>{getPlatformName(item.platformType)}</Text>
                            <Tag color={status.tagColor}>{status.text}</Tag>
                          </Space>
                          {item.message && (
                            <div style={{ marginTop: 4 }}>
                              <Text type="secondary">{item.message}</Text>
                            </div>
                          )}
                          <Text type="secondary" style={{ display: 'block', marginTop: 4, fontSize: 12 }}>
                            {formatDateTime(item.syncTime)}
                          </Text>
                        </div>
                      ),
                    };
                  })}
                />
              ) : (
                <Empty description="暂无同步记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              )}
            </Card>
          </Col>
        </Row>
      </Space>

      {previewModal}
      {editModal}
    </PageContainer>
  );
};

export default OrgSyncPage;
