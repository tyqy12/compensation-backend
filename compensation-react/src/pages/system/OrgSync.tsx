import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Modal,
  Radio,
  Row,
  Select,
  Space,
  Switch,
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
import './OrgSync.less';

const { Text } = Typography;

type FetchMode = 'department' | 'users' | 'all';
const PREVIEW_MODAL_Z_INDEX = 1000;
const EDIT_MODAL_Z_INDEX = 1100;

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
      return {
        icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
        text: '配置正常',
        color: 'success' as const,
      };
    case 'MISSING_CONFIG':
    case 'NO_CONFIG':
      return {
        icon: <InfoCircleOutlined style={{ color: '#faad14' }} />,
        text: '缺少配置',
        color: 'warning' as const,
      };
    case 'UNAUTHORIZED':
    case 'AUTH_FAILED':
      return {
        icon: <CloseCircleOutlined style={{ color: '#ff4d4f' }} />,
        text: '认证失败',
        color: 'error' as const,
      };
    case 'ERROR':
    case 'FAILED':
      return {
        icon: <CloseCircleOutlined style={{ color: '#ff4d4f' }} />,
        text: '检查失败',
        color: 'error' as const,
      };
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
  const [includeImported, setIncludeImported] = useState(false);
  const [isPreviewModalVisible, setIsPreviewModalVisible] = useState(false);
  const [editingEmployee, setEditingEmployee] = useState<EmployeePreviewDto | null>(null);

  const [fetchForm] = Form.useForm<{ mode: FetchMode; userIds?: string }>();
  const [editForm] = Form.useForm<EmployeePreviewDto>();

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

  const departmentTreeData = useMemo(
    () => toTreeData(departmentTreeQuery.data),
    [departmentTreeQuery.data],
  );
  const hasDepartmentTree = departmentTreeData.length > 0;
  const supportsDepartmentMode = useMemo(
    () => ['wechat', 'dingtalk', 'feishu'].includes(String(selectedPlatform).toLowerCase()),
    [selectedPlatform],
  );
  const [fetchMode, setFetchMode] = useState<FetchMode>(
    supportsDepartmentMode ? 'department' : 'all',
  );

  useEffect(() => {
    const currentMode = fetchForm.getFieldValue('mode') as FetchMode | undefined;
    if (!supportsDepartmentMode && currentMode === 'department') {
      fetchForm.setFieldsValue({ mode: 'all' });
      setFetchMode('all');
    }
  }, [supportsDepartmentMode, fetchForm]);

  useEffect(() => {
    setSelectedDeptKeys([]);
    setPreviewData([]);
    setSelectedEmployees([]);
    setIncludeImported(false);
  }, [selectedPlatform]);

  const historyList = useMemo(() => {
    if (!historyQuery.data) return [] as OrgSyncHistoryItem[];
    if (Array.isArray(historyQuery.data)) return historyQuery.data;
    return Object.values(historyQuery.data as Record<string, OrgSyncHistoryItem>);
  }, [historyQuery.data]);

  const historyByPlatform = useMemo(() => {
    const map: Record<string, OrgSyncHistoryItem> = {};
    historyList.forEach((item) => {
      const key = String(item.provider ?? '').toLowerCase();
      if (!key) {
        return;
      }
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
    () =>
      [...historyList].sort(
        (a, b) => new Date(b.syncTime ?? 0).getTime() - new Date(a.syncTime ?? 0).getTime(),
      ),
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
        rowKey: emp.rowKey ?? `${emp.subjectId ?? emp.employeeId}-${index}`,
      }));

      const defaultSelected = employeesWithKey
        .filter((emp) => !emp.alreadyImported)
        .map((emp) => emp.rowKey ?? emp.employeeId ?? emp.subjectId);

      setPreviewData(employeesWithKey);
      setIncludeImported(false);
      setSelectedEmployees(defaultSelected);
      setIsPreviewModalVisible(true);
      const newEmployees =
        result.newEmployees ?? employeesWithKey.filter((emp) => !emp.alreadyImported).length;
      const existingEmployees =
        result.existingEmployees ?? Math.max(0, employeesWithKey.length - newEmployees);
      message.success(
        `成功拉取 ${result.totalEmployees} 名员工信息（未导入 ${newEmployees}，已导入 ${existingEmployees}）`,
      );
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(`拉取预览失败：${error?.message || '请检查平台配置'}`);
    }
  };

  const handleResetFetchOptions = () => {
    fetchForm.setFieldsValue({
      mode: supportsDepartmentMode ? 'department' : 'all',
      userIds: undefined,
    });
    setFetchMode(supportsDepartmentMode ? 'department' : 'all');
    setSelectedDeptKeys([]);
  };

  const handleImport = () => {
    const selectedData = previewData.filter((emp) =>
      selectedEmployees.includes(emp.rowKey ?? emp.employeeId ?? emp.subjectId),
    );
    const importMode = includeImported ? 'upsert' : 'new_only';

    if (selectedData.length === 0) {
      message.warning('请选择要导入的员工');
      return;
    }

    modal.confirm({
      title: '确认导入',
      content: includeImported
        ? `确定要导入 ${selectedData.length} 名员工吗？当前模式会更新已导入员工信息。`
        : `确定要导入 ${selectedData.length} 名员工吗？当前模式仅导入未导入员工。`,
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
            subjectId: emp.subjectId,
            provider: emp.provider ?? selectedPlatform,
            username: emp.employeeId,
          }));

          const payload = await importMutation.mutateAsync({
            provider: selectedPlatform,
            importMode,
            items,
            metadata: {
              mode: fetchForm.getFieldValue('mode'),
              departmentIds: selectedDeptKeys,
              importMode,
              includeImported,
            },
          });

          message.success(
            `导入完成：成功 ${payload.success} 人（新增 ${payload.created ?? 0}，更新 ${payload.updated ?? 0}），跳过 ${payload.skipped ?? 0} 人，失败 ${payload.failed} 人`,
          );

          historyQuery.refetch();
          orgCheckQuery.refetch();

          if (payload.skippedItems && payload.skippedItems.length > 0) {
            modal.info({
              title: '已跳过员工',
              content: (
                <div>
                  {payload.skippedItems.map((item, index) => (
                    <div key={`skip-${String(index)}`} style={{ marginBottom: 4, fontSize: 12 }}>
                      {item}
                    </div>
                  ))}
                </div>
              ),
            });
          }

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
      width: 150,
      render: (_, record) => (
        <Space size={8}>
          <UserOutlined />
          <Text strong>{record.name}</Text>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEditEmployee(record)}
          >
            编辑
          </Button>
        </Space>
      ),
    },
    {
      title: '工号',
      dataIndex: 'employeeId',
      key: 'employeeId',
      width: 110,
    },
    {
      title: '导入状态',
      key: 'importStatus',
      width: 120,
      render: (_, record) => {
        if (record.alreadyImported) {
          return (
            <Space size={6}>
              <Tag color="default">已导入</Tag>
              {record.existingEmployeeNo ? (
                <Text type="secondary">{record.existingEmployeeNo}</Text>
              ) : null}
            </Space>
          );
        }
        return <Tag color="success">未导入</Tag>;
      },
    },
    {
      title: '部门',
      dataIndex: 'department',
      key: 'department',
      width: 160,
      render: (_, record) => record.department ?? record.departments?.join(' / ') ?? '—',
    },
    {
      title: '职位',
      dataIndex: 'position',
      key: 'position',
      width: 120,
      render: (value) => value || '—',
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      key: 'phone',
      width: 140,
      render: (value) => value || '—',
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      key: 'email',
      width: 210,
      render: (value) => value || '—',
    },
    {
      title: '员工类型',
      dataIndex: 'employmentType',
      key: 'employmentType',
      width: 100,
      render: (type) => (
        <Tag color={type === 'full_time' ? 'blue' : 'orange'}>
          {type === 'full_time' ? '全职' : '兼职'}
        </Tag>
      ),
    },
  ];

  const importedCount = useMemo(
    () => previewData.filter((employee) => Boolean(employee.alreadyImported)).length,
    [previewData],
  );
  const newCount = Math.max(0, previewData.length - importedCount);

  const resetSelectedByMode = (nextIncludeImported: boolean) => {
    const selectable = nextIncludeImported
      ? previewData
      : previewData.filter((employee) => !employee.alreadyImported);
    setSelectedEmployees(
      selectable.map((employee) => employee.rowKey ?? employee.employeeId ?? employee.subjectId),
    );
  };

  const previewModal = (
    <Modal
      className="org-sync-preview-modal"
      title={
        <div className="org-sync-modal-heading">
          <Text className="org-sync-modal-eyebrow">IMPORT PREVIEW</Text>
          <Typography.Title level={4} className="org-sync-modal-title">
            {`员工预览 - ${getPlatformName(selectedPlatform)}`}
          </Typography.Title>
          <Text type="secondary" className="org-sync-modal-subtitle">
            检查员工资料和导入状态，确认后再写入员工档案。
          </Text>
        </div>
      }
      open={isPreviewModalVisible}
      onCancel={() => setIsPreviewModalVisible(false)}
      zIndex={PREVIEW_MODAL_Z_INDEX}
      width="90%"
      style={{ top: 20 }}
      footer={
        <div className="org-sync-modal-footer">
          <Text type="secondary">
            {selectedEmployees.length > 0
              ? `当前选择 ${selectedEmployees.length} 人`
              : '请选择要导入的员工'}
          </Text>
          <Space>
            <Button key="cancel" onClick={() => setIsPreviewModalVisible(false)}>
              取消
            </Button>
            <Button
              key="import"
              type="primary"
              icon={<DownloadOutlined />}
              loading={importMutation.isPending}
              disabled={selectedEmployees.length === 0}
              onClick={handleImport}
            >
              导入选中员工 ({selectedEmployees.length})
            </Button>
          </Space>
        </div>
      }
    >
      <div className="org-sync-preview-body">
        <div className="org-sync-preview-summary">
          <div className="org-sync-preview-summary-main">
            <div className="org-sync-preview-summary-icon">
              <EyeOutlined />
            </div>
            <div className="org-sync-preview-summary-copy">
              <Text strong>预览已准备</Text>
              <Text type="secondary">
                从 {getPlatformName(selectedPlatform)} 拉取到 {previewData.length}{' '}
                名员工，确认无误后再执行导入。
              </Text>
            </div>
          </div>

          <div className="org-sync-preview-stat-list">
            <div className="org-sync-preview-stat">
              <Text type="secondary">员工总数</Text>
              <Text strong>{previewData.length}</Text>
            </div>
            <div className="org-sync-preview-stat">
              <Text type="secondary">待新增</Text>
              <Text strong className="is-positive">
                {newCount}
              </Text>
            </div>
            <div className="org-sync-preview-stat">
              <Text type="secondary">已导入</Text>
              <Text strong>{importedCount}</Text>
            </div>
          </div>

          <div className="org-sync-preview-option">
            <div>
              <Text strong>包含已导入员工</Text>
              <Text type="secondary">开启后会执行更新</Text>
            </div>
            <Switch
              checked={includeImported}
              onChange={(checked) => {
                setIncludeImported(checked);
                resetSelectedByMode(checked);
              }}
            />
          </div>
        </div>

        <Table<EmployeePreviewDto>
          className="org-sync-preview-table"
          rowSelection={{
            selectedRowKeys: selectedEmployees,
            onChange: setSelectedEmployees,
            getCheckboxProps: (record) => ({
              disabled: !includeImported && Boolean(record.alreadyImported),
            }),
          }}
          columns={previewColumns}
          dataSource={previewData}
          rowKey={(record) => record.rowKey ?? record.employeeId ?? record.subjectId}
          pagination={{
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total = 0, range) => {
              const [start, end] = range ?? [0, 0];
              return `第 ${start}-${end} 条，共 ${total} 条`;
            },
          }}
          rowClassName={(record) => (record.alreadyImported ? 'is-already-imported' : '')}
          scroll={{ x: 1100 }}
          size="medium"
        />
      </div>
    </Modal>
  );

  const editModal = (
    <Modal
      className="org-sync-edit-modal"
      title={
        <div className="org-sync-modal-heading">
          <Text className="org-sync-modal-eyebrow">EMPLOYEE PROFILE</Text>
          <Typography.Title level={4} className="org-sync-modal-title">
            编辑员工信息
          </Typography.Title>
          <Text type="secondary" className="org-sync-modal-subtitle">
            修改只作用于当前预览记录，确认导入后才会提交到员工档案。
          </Text>
        </div>
      }
      open={Boolean(editingEmployee)}
      onCancel={() => {
        setEditingEmployee(null);
      }}
      onOk={handleSaveEmployee}
      confirmLoading={false}
      zIndex={EDIT_MODAL_Z_INDEX}
      width={560}
      okText="保存修改"
      cancelText="取消"
      okButtonProps={{ icon: <CheckCircleOutlined /> }}
      forceRender
      afterOpenChange={(open) => {
        if (open && editingEmployee) {
          editForm.setFieldsValue({
            name: editingEmployee.name,
            employeeId: editingEmployee.employeeId,
            department: editingEmployee.department ?? editingEmployee.departments?.[0],
            position: editingEmployee.position,
            phone: editingEmployee.phone,
            email: editingEmployee.email,
            employmentType: editingEmployee.employmentType,
          });
          return;
        }
        if (!open) {
          editForm.resetFields();
        }
      }}
    >
      <div className="org-sync-edit-context">
        <div className="org-sync-edit-avatar">
          <UserOutlined />
        </div>
        <div>
          <Text strong>{editingEmployee?.name ?? '员工信息'}</Text>
          <Text type="secondary">当前正在编辑预览中的员工资料</Text>
        </div>
      </div>

      <Form form={editForm} layout="vertical" preserve={false} className="org-sync-edit-form">
        <div className="org-sync-edit-grid">
          <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="employeeId"
            label="工号"
            rules={[{ required: true, message: '请输入工号' }]}
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
          <Form.Item
            name="employmentType"
            label="员工类型"
            className="org-sync-edit-field-full"
            rules={[{ required: true, message: '请选择员工类型' }]}
          >
            <Select>
              <Select.Option value="full_time">全职</Select.Option>
              <Select.Option value="part_time">兼职</Select.Option>
            </Select>
          </Form.Item>
        </div>
      </Form>
    </Modal>
  );

  const totalPlatforms = platforms.length;
  const successfulSyncs = historyList.filter((item) => item.success).length;
  const lastSyncRecord = sortedHistory[0];
  const lastSyncTime = lastSyncRecord ? formatDateTime(lastSyncRecord.syncTime) : '—';
  const connectionStatus = getCheckStatus(orgCheckQuery.data?.status);
  const selectedPlatformName = getPlatformName(selectedPlatform);

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
      <div className="org-sync-page">
        <section className="org-sync-summary-grid" aria-label="同步概览">
          <div className="org-sync-stat-card">
            <div className="org-sync-stat-icon org-sync-stat-icon-platform">
              <ApartmentOutlined />
            </div>
            <div className="org-sync-stat-content">
              <Text className="org-sync-stat-label">接入平台</Text>
              <div className="org-sync-stat-value">
                <span>{totalPlatforms}</span>
                <Text type="secondary">个</Text>
              </div>
              <Text type="secondary" className="org-sync-stat-hint">
                当前可用的数据来源
              </Text>
            </div>
          </div>

          <div className="org-sync-stat-card">
            <div className="org-sync-stat-icon org-sync-stat-icon-success">
              <CheckCircleOutlined />
            </div>
            <div className="org-sync-stat-content">
              <Text className="org-sync-stat-label">成功同步</Text>
              <div className="org-sync-stat-value">
                <span>{successfulSyncs}</span>
                <Text type="secondary">次</Text>
              </div>
              <Text type="secondary" className="org-sync-stat-hint">
                所有平台的历史成功记录
              </Text>
            </div>
          </div>

          <div className="org-sync-stat-card org-sync-stat-card-time">
            <div className="org-sync-stat-icon org-sync-stat-icon-time">
              <ReloadOutlined />
            </div>
            <div className="org-sync-stat-content">
              <Text className="org-sync-stat-label">最近同步时间</Text>
              <Text className="org-sync-stat-date">{lastSyncTime}</Text>
              <Text type="secondary" className="org-sync-stat-hint">
                最近一次组织数据变更
              </Text>
            </div>
          </div>
        </section>

        <Row gutter={[16, 16]} className="org-sync-workspace">
          <Col xs={24} xl={16}>
            <Card className="org-sync-operation-card">
              <div className="org-sync-panel-heading">
                <div>
                  <Text className="org-sync-panel-kicker">SYNC WORKFLOW</Text>
                  <Typography.Title level={4} className="org-sync-panel-title">
                    同步操作
                  </Typography.Title>
                  <Text type="secondary">先拉取预览，再确认导入，确保组织数据变更可控。</Text>
                </div>
                <Tag color="blue" className="org-sync-current-platform">
                  {selectedPlatformName}
                </Tag>
              </div>

              <div className="org-sync-form-section org-sync-platform-section">
                <div className="org-sync-section-heading">
                  <div>
                    <Text strong>选择平台</Text>
                    <Text type="secondary">从已接入的第三方平台读取数据</Text>
                  </div>
                  <Text type="secondary" className="org-sync-section-index">
                    01
                  </Text>
                </div>
                <Radio.Group
                  className="org-sync-platform-switch"
                  value={selectedPlatform}
                  onChange={(e) => setSelectedPlatform(e.target.value)}
                  optionType="button"
                  buttonStyle="solid"
                >
                  {platforms.map((item) => (
                    <Radio.Button key={item.code} value={item.code}>
                      <span>{item.name}</span>
                      {item.configured !== undefined && (
                        <span
                          aria-hidden="true"
                          className={`org-sync-config-dot ${item.configured ? 'is-configured' : ''}`}
                        />
                      )}
                    </Radio.Button>
                  ))}
                </Radio.Group>
              </div>

              <div className="org-sync-workflow-divider" />

              <div className="org-sync-form-section">
                <div className="org-sync-section-heading">
                  <div>
                    <Text strong>同步范围</Text>
                    <Text type="secondary">选择本次需要拉取的组织和人员范围</Text>
                  </div>
                  <Text type="secondary" className="org-sync-section-index">
                    02
                  </Text>
                </div>

                <div className="org-sync-steps" aria-label="同步步骤">
                  <div className="org-sync-step is-active">
                    <span className="org-sync-step-number">1</span>
                    <Text>选择范围</Text>
                  </div>
                  <div className="org-sync-step-connector" />
                  <div className="org-sync-step">
                    <span className="org-sync-step-number">2</span>
                    <Text>拉取预览</Text>
                  </div>
                  <div className="org-sync-step-connector" />
                  <div className="org-sync-step">
                    <span className="org-sync-step-number">3</span>
                    <Text>确认导入</Text>
                  </div>
                </div>

                <Form
                  form={fetchForm}
                  layout="vertical"
                  requiredMark={false}
                  initialValues={{ mode: supportsDepartmentMode ? 'department' : 'all' }}
                  onValuesChange={(changedValues) => {
                    if (changedValues.mode) {
                      setFetchMode(changedValues.mode as FetchMode);
                    }
                  }}
                >
                  <Form.Item label="同步范围" name="mode" className="org-sync-mode-item">
                    <Radio.Group
                      className="org-sync-mode-switch"
                      optionType="button"
                      buttonStyle="solid"
                    >
                      <Radio.Button value="department" disabled={!supportsDepartmentMode}>
                        按部门
                      </Radio.Button>
                      <Radio.Button value="users">按用户</Radio.Button>
                      <Radio.Button value="all">全部</Radio.Button>
                    </Radio.Group>
                  </Form.Item>
                  {!supportsDepartmentMode && (
                    <div className="org-sync-inline-note">
                      <InfoCircleOutlined />
                      <Text type="secondary">当前平台不支持按部门拉取，仅支持按用户或全部。</Text>
                    </div>
                  )}

                  {fetchMode === 'department' && (
                    <Form.Item label="选择部门" className="org-sync-data-input">
                      <Spin spinning={departmentTreeQuery.isLoading}>
                        {hasDepartmentTree ? (
                          <div className="org-sync-tree-shell">
                            <div className="org-sync-tree-toolbar">
                              <Text type="secondary">勾选需要同步的部门节点</Text>
                              <Text className="org-sync-tree-count">
                                已选 {selectedDeptKeys.length} 个
                              </Text>
                            </div>
                            <Tree
                              className="org-sync-tree"
                              checkable
                              selectable={false}
                              treeData={departmentTreeData}
                              checkedKeys={selectedDeptKeys}
                              onCheck={(keys) =>
                                setSelectedDeptKeys(
                                  Array.isArray(keys) ? keys : (keys as any).checked,
                                )
                              }
                              defaultExpandAll
                              switcherIcon={<ApartmentOutlined />}
                            />
                          </div>
                        ) : (
                          <div className="org-sync-empty-state">
                            <Empty
                              description="暂未获取到部门树，请检查平台配置后点击刷新状态重试"
                              image={Empty.PRESENTED_IMAGE_SIMPLE}
                            />
                          </div>
                        )}
                      </Spin>
                    </Form.Item>
                  )}

                  {fetchMode === 'users' && (
                    <Form.Item
                      label="平台用户ID"
                      name="userIds"
                      className="org-sync-data-input"
                      rules={[{ required: true, message: '请输入平台用户ID，每行一个' }]}
                    >
                      <Input.TextArea rows={4} placeholder="每行一个用户ID，可粘贴企业微信成员ID" />
                    </Form.Item>
                  )}

                  {fetchMode === 'all' && (
                    <div className="org-sync-all-note">
                      <InfoCircleOutlined />
                      <div>
                        <Text strong>全量拉取</Text>
                        <Text type="secondary">
                          将读取当前平台可见的全部组织和人员数据，建议先检查预览结果。
                        </Text>
                      </div>
                    </div>
                  )}

                  <div className="org-sync-form-actions">
                    <Button
                      type="primary"
                      icon={<EyeOutlined />}
                      onClick={handleFetchPreview}
                      loading={fetchPreviewMutation.isPending}
                    >
                      拉取预览
                    </Button>
                    <Button
                      icon={<ReloadOutlined />}
                      onClick={handleResetFetchOptions}
                      disabled={fetchPreviewMutation.isPending}
                    >
                      重置选项
                    </Button>
                  </div>
                </Form>
              </div>
            </Card>
          </Col>

          <Col xs={24} xl={8}>
            <div className="org-sync-side-column">
              <Card className="org-sync-health-card">
                <div className="org-sync-side-heading">
                  <div>
                    <Text className="org-sync-panel-kicker">PLATFORM HEALTH</Text>
                    <Typography.Title level={4} className="org-sync-side-title">
                      平台状态
                    </Typography.Title>
                  </div>
                  <span
                    className={`org-sync-health-pulse is-${connectionStatus.color}`}
                    aria-hidden="true"
                  />
                </div>

                <div className="org-sync-health-summary">
                  <div className={`org-sync-health-icon is-${connectionStatus.color}`}>
                    {connectionStatus.icon}
                  </div>
                  <div>
                    <Text type="secondary">当前平台</Text>
                    <Typography.Title level={4} className="org-sync-health-platform">
                      {selectedPlatformName}
                    </Typography.Title>
                    <Tag color={connectionStatus.color}>{connectionStatus.text}</Tag>
                  </div>
                </div>

                <div className="org-sync-health-details">
                  <div>
                    <Text type="secondary">配置状态</Text>
                    <Tag color={selectedPlatformMeta?.configured ? 'success' : 'default'}>
                      {selectedPlatformMeta?.configured ? '已配置' : '未配置'}
                    </Tag>
                  </div>
                  <div>
                    <Text type="secondary">最近同步</Text>
                    <Text>{selectedHistory ? formatDateTime(selectedHistory.syncTime) : '—'}</Text>
                  </div>
                </div>

                {orgCheckQuery.data?.message && (
                  <div className="org-sync-health-message">
                    <InfoCircleOutlined />
                    <Text type="secondary">{orgCheckQuery.data.message}</Text>
                  </div>
                )}
              </Card>

              <Card
                className="org-sync-history-card"
                title={
                  <div className="org-sync-history-heading">
                    <div>
                      <Typography.Title level={4} className="org-sync-side-title">
                        同步历史
                      </Typography.Title>
                      <Text type="secondary">按时间倒序展示最近记录</Text>
                    </div>
                    <span className="org-sync-history-count">{sortedHistory.length}</span>
                  </div>
                }
              >
                {historyQuery.isLoading ? (
                  <div className="org-sync-history-loading">
                    <Spin />
                  </div>
                ) : historyQuery.isError ? (
                  <Alert
                    type="error"
                    showIcon
                    title="同步历史加载失败"
                    description={
                      historyQuery.error instanceof Error
                        ? historyQuery.error.message
                        : '请稍后再试'
                    }
                  />
                ) : sortedHistory.length ? (
                  <Timeline
                    className="org-sync-history-timeline"
                    mode="start"
                    items={sortedHistory.map((item) => {
                      const status = getHistoryStatus(item);
                      return {
                        color: status.timelineColor,
                        title: formatTimeLabel(item.syncTime),
                        content: (
                          <div className="org-sync-history-item">
                            <Space size={8} wrap>
                              <Text strong>{getPlatformName(item.provider ?? 'unknown')}</Text>
                              <Tag color={status.tagColor}>{status.text}</Tag>
                            </Space>
                            {item.message && (
                              <div className="org-sync-history-message">
                                <Text type="secondary">{item.message}</Text>
                              </div>
                            )}
                            <Text type="secondary" className="org-sync-history-datetime">
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
            </div>
          </Col>
        </Row>
      </div>

      {previewModal}
      {editModal}
    </PageContainer>
  );
};

export default OrgSyncPage;
