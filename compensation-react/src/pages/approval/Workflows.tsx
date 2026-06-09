import React, { useCallback, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  PageContainer,
  ProTable,
  type ProColumns,
  type ActionType,
  type ProFormInstance,
  ModalForm,
  DrawerForm,
  ProDescriptions,
} from '@ant-design/pro-components';
import {
  App as AntdApp,
  Button,
  Card,
  Empty,
  Tag,
  Space,
  Steps,
  Timeline,
  Input,
  Select,
  DatePicker,
  message,
  Popconfirm,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  EyeOutlined,
  FilterOutlined,
  ReloadOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  useApprovalWorkflowsQuery,
  usePendingApprovalsQuery,
  useMyApprovalsQuery,
  useApprovalWorkflowDetailQuery,
  useApproveMutation,
  useRejectMutation,
  getApprovalStatusInfo,
  getWorkflowTypeInfo,
  formatStepProgress,
  type ApprovalWorkflow,
  type ApprovalWorkflowDetail,
  type ApprovalQueryParams,
} from '@services/queries/approval';
import {
  buildApprovalSearchParams,
  getApprovalTableRecords,
  type ApprovalTab,
} from './workflowPageUtils';

const { Search } = Input;
const { RangePicker } = DatePicker;

// ==================== 状态枚举 ====================
const statusEnum: Record<string, { text: string; color: string }> = {
  pending: { text: '待审批', color: 'processing' },
  approved: { text: '已通过', color: 'success' },
  rejected: { text: '已拒绝', color: 'error' },
  cancelled: { text: '已取消', color: 'default' },
};

const workflowTypeEnum: Record<string, { text: string; color: string }> = {
  BATCH: { text: '批量支付', color: 'blue' },
  PAYROLL_DISTRIBUTION: { text: '薪资发放', color: 'green' },
  ADHOC: { text: '临时支付', color: 'orange' },
  OFFLINE: { text: '架构外员工', color: 'purple' },
  PERMISSION: { text: '权限授权', color: 'cyan' },
  PAYROLL_DISPUTE: { text: '薪酬异议', color: 'magenta' },
};

const formatDateTime = (value?: string): string =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—';

// ==================== 主组件 ====================
const ApprovalWorkflows: React.FC = () => {
  const { message: antdMessage } = AntdApp.useApp();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  // Tab 状态: list | pending | my
  const [activeTab, setActiveTab] = useState<ApprovalTab>(() => {
    const tab = searchParams.get('tab');
    if (tab === 'pending' || tab === 'my') return tab;
    return 'list';
  });

  // URL 参数状态
  const [queryParams, setQueryParams] = useState<ApprovalQueryParams>(() => ({
    current: Number(searchParams.get('page') || '1'),
    pageSize: Number(searchParams.get('size') || '10'),
    status: searchParams.get('status') || undefined,
    workflowType: searchParams.get('workflowType') || undefined,
    keyword: searchParams.get('keyword') || undefined,
    startDate: searchParams.get('startDate') || undefined,
    endDate: searchParams.get('endDate') || undefined,
    sortBy: searchParams.get('sortBy') || 'submitTime',
    order: (searchParams.get('order') as 'asc' | 'desc' | null) || 'desc',
  }));

  // 核心引用
  const actionRef = useRef<ActionType>();
  const formRef = useRef<ProFormInstance>();

  // Drawer 状态
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [selectedWorkflowId, setSelectedWorkflowId] = useState<number | null>(null);
  const [approveComment, setApproveComment] = useState('');
  const [rejectComment, setRejectComment] = useState('');

  // Mutations
  const approveMutation = useApproveMutation();
  const rejectMutation = useRejectMutation();

  // ==================== URL 同步 ====================
  const updateUrlParams = useCallback((params: ApprovalQueryParams, tab: ApprovalTab = activeTab) => {
    setSearchParams(buildApprovalSearchParams(params, tab));
  }, [activeTab, setSearchParams]);

  const applyQueryParams = useCallback((nextParams: ApprovalQueryParams, tab: ApprovalTab = activeTab) => {
    setQueryParams(nextParams);
    updateUrlParams(nextParams, tab);
  }, [activeTab, updateUrlParams]);

  // ==================== 数据查询 ====================
  const {
    data: workflowsData,
    isLoading: workflowsLoading,
    refetch: refetchWorkflows,
  } = useApprovalWorkflowsQuery(queryParams, { enabled: activeTab === 'list' });

  const {
    data: pendingData,
    isLoading: pendingLoading,
    refetch: refetchPending,
  } = usePendingApprovalsQuery({ enabled: activeTab === 'pending' });

  const {
    data: myData,
    isLoading: myLoading,
    refetch: refetchMy,
  } = useMyApprovalsQuery(queryParams, { enabled: activeTab === 'my' });

  const {
    data: detailData,
    isLoading: detailLoading,
  } = useApprovalWorkflowDetailQuery(selectedWorkflowId || 0, {
    enabled: !!selectedWorkflowId && drawerVisible,
  });

  // 刷新所有数据
  const refreshAll = useCallback(() => {
    refetchWorkflows();
    refetchPending();
    refetchMy();
    actionRef.current?.reloadAndRest?.();
  }, [refetchWorkflows, refetchPending, refetchMy]);

  // ==================== 当前视图数据 ====================
  const currentData = useMemo(() => {
    switch (activeTab) {
      case 'pending':
        return { data: pendingData, loading: pendingLoading, total: pendingData?.length ?? 0 };
      case 'my':
        return { data: myData, loading: myLoading, total: myData?.total ?? 0 };
      default:
        return { data: workflowsData, loading: workflowsLoading, total: workflowsData?.total ?? 0 };
    }
  }, [activeTab, pendingData, myData, workflowsData, pendingLoading, myLoading, workflowsLoading]);

  const records = useMemo(() => getApprovalTableRecords(currentData.data), [currentData.data]);

  // ==================== 处理函数 ====================
  const handleTabChange = useCallback((newTab: ApprovalTab) => {
    const nextParams = { ...queryParams, current: 1 };
    setActiveTab(newTab);
    applyQueryParams(nextParams, newTab);
  }, [applyQueryParams, queryParams]);

  const handleViewDetail = useCallback((id: number) => {
    setSelectedWorkflowId(id);
    setDrawerVisible(true);
  }, []);

  const handleApprove = useCallback(async () => {
    if (!selectedWorkflowId) return;
    try {
      await approveMutation.mutateAsync({ id: selectedWorkflowId, comment: approveComment });
      antdMessage.success('审批通过成功');
      setDrawerVisible(false);
      setApproveComment('');
      refreshAll();
    } catch (error: unknown) {
      const err = error as { message?: string };
      antdMessage.error(err.message || '审批通过失败');
    }
  }, [selectedWorkflowId, approveComment, approveMutation, antdMessage, refreshAll]);

  const handleReject = useCallback(async () => {
    if (!selectedWorkflowId) return;
    if (!rejectComment.trim()) {
      antdMessage.warning('请输入拒绝原因');
      return;
    }
    try {
      await rejectMutation.mutateAsync({ id: selectedWorkflowId, comment: rejectComment });
      antdMessage.success('已拒绝该审批');
      setDrawerVisible(false);
      setRejectComment('');
      refreshAll();
    } catch (error: unknown) {
      const err = error as { message?: string };
      antdMessage.error(err.message || '拒绝审批失败');
    }
  }, [selectedWorkflowId, rejectComment, rejectMutation, antdMessage, refreshAll]);

  // ==================== 表格列定义 ====================
  const columns: ProColumns<ApprovalWorkflow>[] = [
    {
      title: '流程名称',
      dataIndex: 'workflowName',
      width: 200,
      render: (_, record) => (
        <Button type="link" onClick={() => handleViewDetail(record.id)}>
          {record.workflowName}
        </Button>
      ),
    },
    {
      title: '流程类型',
      dataIndex: 'workflowType',
      width: 120,
      valueEnum: workflowTypeEnum,
      render: (_, record) => {
        const info = getWorkflowTypeInfo(record.workflowType as any);
        return <Tag color={info.color}>{info.text}</Tag>;
      },
    },
    {
      title: '业务类型',
      dataIndex: 'businessType',
      width: 100,
      render: (_, record) => {
        const typeMap: Record<string, string> = {
          payroll: '薪资发放',
          offline: '架构外员工',
        };
        return typeMap[record.businessType] || record.businessType || '—';
      },
    },
    {
      title: '业务标识',
      dataIndex: 'businessKey',
      width: 150,
      ellipsis: true,
    },
    {
      title: '审批进度',
      dataIndex: 'step',
      width: 120,
      render: (_, record) => (
        <Space>
          <span>{formatStepProgress(record.currentStep, record.totalSteps)}</span>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueEnum: statusEnum,
      render: (_, record) => {
        const info = getApprovalStatusInfo(record.status as any);
        return <Tag color={info.color} icon={record.status === 'pending' ? <SyncOutlined spin /> : undefined}>{info.text}</Tag>;
      },
    },
    {
      title: '发起人',
      dataIndex: 'initiatorName',
      width: 120,
      render: (_, record) => record.initiatorName || record.initiatorId || '—',
    },
    {
      title: '提交时间',
      dataIndex: 'submitTime',
      width: 160,
      sorter: true,
      render: (_, record) => formatDateTime(record.submitTime),
    },
    {
      title: '完成时间',
      dataIndex: 'completeTime',
      width: 160,
      render: (_, record) => formatDateTime(record.completeTime),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record.id)}
          >
            查看
          </Button>
        </Space>
      ),
    },
  ];

  // ==================== 搜索表单 ====================
  const searchForm = (
    <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
      <Search
        placeholder="搜索流程名称、业务标识"
        style={{ width: 250 }}
        allowClear
        value={queryParams.keyword}
        onChange={(e) => setQueryParams(prev => ({ ...prev, keyword: e.target.value || undefined }))}
        onSearch={(keyword) => {
          const nextParams = { ...queryParams, keyword: keyword || undefined, current: 1 };
          applyQueryParams(nextParams);
        }}
      />
      <Select
        placeholder="审批状态"
        style={{ width: 120 }}
        allowClear
        value={queryParams.status}
        onChange={(val) => {
          const nextParams = { ...queryParams, status: val, current: 1 };
          applyQueryParams(nextParams);
        }}
        options={Object.entries(statusEnum).map(([k, v]) => ({ label: v.text, value: k }))}
      />
      <Select
        placeholder="流程类型"
        style={{ width: 120 }}
        allowClear
        value={queryParams.workflowType}
        onChange={(val) => {
          const nextParams = { ...queryParams, workflowType: val, current: 1 };
          applyQueryParams(nextParams);
        }}
        options={Object.entries(workflowTypeEnum).map(([k, v]) => ({ label: v.text, value: k }))}
      />
      <RangePicker
        value={
          queryParams.startDate && queryParams.endDate
            ? [dayjs(queryParams.startDate), dayjs(queryParams.endDate)]
            : undefined
        }
        onChange={(dates, dateStrings) => {
          const nextParams = {
            ...queryParams,
            current: 1,
            startDate: dateStrings[0] || undefined,
            endDate: dateStrings[1] || undefined,
          };
          applyQueryParams(nextParams);
        }}
        placeholder={['开始日期', '结束日期']}
      />
      <Button
        icon={<ReloadOutlined />}
        onClick={() => {
          const nextParams = {
            ...queryParams,
            current: 1,
            status: undefined,
            workflowType: undefined,
            keyword: undefined,
            startDate: undefined,
            endDate: undefined,
          };
          applyQueryParams(nextParams);
          refreshAll();
        }}
      >
        重置
      </Button>
    </div>
  );

  // ==================== 渲染 ====================
  return (
    <PageContainer
      title="审批管理"
      tabActiveKey={activeTab}
      onTabChange={(key) => handleTabChange(key as ApprovalTab)}
      tabs={{
        items: [
          { key: 'list', label: '全部审批' },
          { key: 'pending', label: '待我审批' },
          { key: 'my', label: '我发起的' },
        ],
      }}
    >
      {searchForm}

      <ProTable<ApprovalWorkflow>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        dataSource={records}
        loading={currentData.loading}
        pagination={{
          current: queryParams.current,
          pageSize: queryParams.pageSize,
          total: currentData.total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条`,
        }}
        onChange={(pagination, filters, sorter: any) => {
          const current = pagination.current || 1;
          const pageSize = pagination.pageSize || 10;
          const sortBy = Array.isArray(sorter) ? sorter[0]?.field : sorter.field;
          const order = Array.isArray(sorter) ? (sorter[0]?.order === 'ascend' ? 'asc' : 'desc') : (sorter.order === 'ascend' ? 'asc' : 'desc');

          const nextParams = { ...queryParams, current, pageSize, sortBy, order };
          applyQueryParams(nextParams);
        }}
        toolBarRender={() => [
          <Button
            key="refresh"
            icon={<ReloadOutlined />}
            onClick={refreshAll}
          >
            刷新
          </Button>,
        ]}
      />

      {/* 详情 Drawer */}
      <DrawerForm
        title={detailData?.workflowName || '审批详情'}
        width={600}
        open={drawerVisible}
        onOpenChange={setDrawerVisible}
        submitter={false}
      >
        {detailLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>
        ) : detailData ? (
          <div>
            {/* 基本信息 */}
            <Card size="small" title="基本信息" style={{ marginBottom: 16 }}>
              <ProDescriptions column={2}>
                <ProDescriptions.Item label="流程类型">
                  {getWorkflowTypeInfo(detailData.workflowType as any).text}
                </ProDescriptions.Item>
                <ProDescriptions.Item label="状态">
                  <Tag color={getApprovalStatusInfo(detailData.status as any).color}>
                    {getApprovalStatusInfo(detailData.status as any).text}
                  </Tag>
                </ProDescriptions.Item>
                <ProDescriptions.Item label="业务类型">
                  {detailData.businessType === 'payroll' ? '薪资发放' : detailData.businessType || '—'}
                </ProDescriptions.Item>
                <ProDescriptions.Item label="业务标识">
                  {detailData.businessKey}
                </ProDescriptions.Item>
                <ProDescriptions.Item label="发起人">
                  {detailData.initiatorName || detailData.initiatorId}
                </ProDescriptions.Item>
                <ProDescriptions.Item label="提交时间">
                  {formatDateTime(detailData.submitTime)}
                </ProDescriptions.Item>
              </ProDescriptions>
            </Card>

            {/* 审批进度 */}
            <Card size="small" title="审批步骤" style={{ marginBottom: 16 }}>
              <Steps
                current={detailData.currentStep - 1}
                status={detailData.status === 'rejected' ? 'error' : detailData.status === 'approved' ? 'finish' : 'process'}
                items={detailData.steps?.map((step) => ({
                  title: step.stepName,
                  description: step.approverName,
                  icon: step.status === 'approved'
                    ? <CheckCircleOutlined style={{ color: '#52c41a' }} />
                    : step.status === 'rejected'
                      ? <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
                      : undefined,
                }))}
              />
            </Card>

            {/* 步骤时间线 */}
            {detailData.steps && detailData.steps.length > 0 && (
              <Card size="small" title="审批记录" style={{ marginBottom: 16 }}>
                <Timeline
                  items={detailData.steps.map((step) => ({
                    color: step.status === 'approved' ? 'green' : step.status === 'rejected' ? 'red' : 'gray',
                    children: (
                      <div>
                        <div style={{ fontWeight: 500 }}>{step.stepName} - {step.approverName}</div>
                        <div style={{ color: '#888', fontSize: 12 }}>
                          {getApprovalStatusInfo(step.status as any).text}
                          {step.approveTime && ` • ${formatDateTime(step.approveTime)}`}
                        </div>
                        {step.approveComment && (
                          <div style={{ marginTop: 4, padding: 8, background: '#f5f5f5', borderRadius: 4 }}>
                            {step.approveComment}
                          </div>
                        )}
                        {step.rejectReason && (
                          <div style={{ marginTop: 4, padding: 8, background: '#fff2f0', borderRadius: 4, color: '#ff4d4f' }}>
                            拒绝原因：{step.rejectReason}
                          </div>
                        )}
                      </div>
                    ),
                  }))}
                />
              </Card>
            )}

            {/* 审批操作 */}
            {detailData.status === 'pending' && (
              <Card size="small" title="审批操作">
                <div style={{ marginBottom: 16 }}>
                  <span style={{ marginRight: 8 }}>通过：</span>
                  <Input.TextArea
                    placeholder="可选：输入审批意见"
                    rows={2}
                    value={approveComment}
                    onChange={(e) => setApproveComment(e.target.value)}
                    style={{ marginBottom: 8 }}
                  />
                  <Popconfirm
                    title="确认审批"
                    description="确定要通过该审批吗？"
                    onConfirm={handleApprove}
                    okText="确定"
                    cancelText="取消"
                  >
                    <Button type="primary" loading={approveMutation.isPending}>
                      通过
                    </Button>
                  </Popconfirm>
                </div>
                <div>
                  <span style={{ marginRight: 8 }}>拒绝：</span>
                  <Input.TextArea
                    placeholder="必须：输入拒绝原因"
                    rows={2}
                    value={rejectComment}
                    onChange={(e) => setRejectComment(e.target.value)}
                    style={{ marginBottom: 8 }}
                  />
                  <Popconfirm
                    title="确认拒绝"
                    description="确定要拒绝该审批吗？"
                    onConfirm={handleReject}
                    okText="确定"
                    cancelText="取消"
                  >
                    <Button danger loading={rejectMutation.isPending}>
                      拒绝
                    </Button>
                  </Popconfirm>
                </div>
              </Card>
            )}
          </div>
        ) : (
          <Empty description="未找到审批流程" />
        )}
      </DrawerForm>
    </PageContainer>
  );
};

export default ApprovalWorkflows;
