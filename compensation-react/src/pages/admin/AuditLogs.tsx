import React, { useCallback, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  PageContainer,
  ProTable,
  type ProColumns,
  type ActionType,
  type ProFormInstance,
  DrawerForm,
  ProDescriptions,
  StatisticCard,
} from '@ant-design/pro-components';
import {
  App as AntdApp,
  Card,
  Tag,
  Space,
  Input,
  Select,
  DatePicker,
  Button,
  Tooltip,
  Badge,
  Row,
  Col,
  Statistic,
  Alert,
  Popconfirm,
  Table,
} from 'antd';
import {
  EyeOutlined,
  ReloadOutlined,
  FilterOutlined,
  SearchOutlined,
  ClockCircleOutlined,
  WarningOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SafetyOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  useAuditLogsQuery,
  useAuditLogDetailQuery,
  useTodayLoginStatsQuery,
  useAuditSummaryQuery,
  useLoginFailureCountQuery,
  useClearLoginFailureCountMutation,
  getOperationTypeInfo,
  getBusinessTypeInfo,
  formatExecutionTime,
  getMethodColor,
  type AuditLog,
  type AuditLogQueryParams,
} from '@services/queries/auditLog';
import { getPagedRecords } from '@types/api';

const { RangePicker } = DatePicker;
const { Search } = Input;

// ==================== 状态枚举 ====================
// 注意：保留旧的操作类型用于兼容筛选历史数据
const operationEnum: Record<string, { text: string; color: string }> = {
  // 新的中文操作类型
  用户登录: { text: '用户登录', color: 'purple' },
  企业微信登录: { text: '企业微信登录', color: 'cyan' },
  dingtalk登录: { text: '钉钉登录', color: 'blue' },
  飞书登录: { text: '飞书登录', color: 'green' },
  OAuth登录: { text: 'OAuth登录', color: 'orange' },
  创建: { text: '创建', color: 'green' },
  更新: { text: '更新', color: 'blue' },
  删除: { text: '删除', color: 'red' },
  查询: { text: '查询', color: 'default' },
  登录: { text: '登录', color: 'purple' },
  登出: { text: '登出', color: 'orange' },
  导出: { text: '导出', color: 'cyan' },
  导入: { text: '导入', color: 'gold' },
  审批: { text: '审批', color: 'green' },
  拒绝: { text: '拒绝', color: 'red' },
  支付: { text: '支付', color: 'magenta' },
  // 兼容旧的操作类型
  LOGIN_PASSWORD: { text: '[旧]用户登录', color: 'default' },
  LOGIN_OAUTH_WECOM: { text: '[旧]企业微信登录', color: 'default' },
  LOGIN_OAUTH_DINGTALK: { text: '[旧]钉钉登录', color: 'default' },
  LOGIN_OAUTH_FEISHU: { text: '[旧]飞书登录', color: 'default' },
};

const methodEnum: Record<string, { text: string; color: string }> = {
  GET: { text: 'GET', color: 'blue' },
  POST: { text: 'POST', color: 'green' },
  PUT: { text: 'PUT', color: 'orange' },
  DELETE: { text: 'DELETE', color: 'red' },
  PATCH: { text: 'PATCH', color: 'purple' },
};

const resultEnum = [
  { label: '成功', value: 'OK', color: 'success' },
  { label: '失败', value: 'FAILED', color: 'error' },
];

// ==================== 工具函数 ====================
const formatDateTime = (value?: string): string =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—';

// ==================== 主组件 ====================
const AuditLogs: React.FC = () => {
  const { message: antdMessage, modal } = AntdApp.useApp();
  const [searchParams, setSearchParams] = useSearchParams();

  // URL 参数状态
  const [queryParams, setQueryParams] = useState<AuditLogQueryParams>(() => ({
    page: Number(searchParams.get('page') || '1'),
    pageSize: Number(searchParams.get('size') || '10'),
    username: searchParams.get('username') || undefined,
    operation: searchParams.get('operation') || undefined,
    businessType: searchParams.get('businessType') || undefined,
    responseResult: searchParams.get('responseResult') || undefined,
    startTime: searchParams.get('startTime') || undefined,
    endTime: searchParams.get('endTime') || undefined,
    keyword: searchParams.get('keyword') || undefined,
  }));

  // 核心引用
  const actionRef = useRef<ActionType>();
  const formRef = useRef<ProFormInstance>();

  // Drawer 状态
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [selectedLogId, setSelectedLogId] = useState<number | null>(null);

  // 数据查询
  const { data: auditData, isLoading, refetch } = useAuditLogsQuery(queryParams);

  const { data: detailData, isLoading: detailLoading } = useAuditLogDetailQuery(
    selectedLogId || 0,
    {
      enabled: !!selectedLogId && drawerVisible,
    },
  );

  // 统计查询
  const { data: todayLoginStats } = useTodayLoginStatsQuery();
  const { data: auditSummary } = useAuditSummaryQuery();
  const { data: loginFailures } = useLoginFailureCountQuery();
  const clearFailureMutation = useClearLoginFailureCountMutation();

  // ==================== URL 同步 ====================
  const updateUrlParams = useCallback(
    (params: AuditLogQueryParams) => {
      const next = new URLSearchParams();
      if (params.page) next.set('page', String(params.page));
      if (params.pageSize) next.set('size', String(params.pageSize));
      if (params.username) next.set('username', params.username);
      if (params.operation) next.set('operation', params.operation);
      if (params.businessType) next.set('businessType', params.businessType);
      if (params.responseResult) next.set('responseResult', params.responseResult);
      if (params.startTime) next.set('startTime', params.startTime);
      if (params.endTime) next.set('endTime', params.endTime);
      if (params.keyword) next.set('keyword', params.keyword);
      setSearchParams(next);
    },
    [setSearchParams],
  );

  const records = useMemo(() => getPagedRecords(auditData), [auditData]);

  // ==================== 处理函数 ====================
  const handleViewDetail = useCallback((id: number) => {
    setSelectedLogId(id);
    setDrawerVisible(true);
  }, []);

  const handleRefresh = useCallback(() => {
    refetch();
    actionRef.current?.reloadAndRest?.();
  }, [refetch]);

  const handleClearFailure = useCallback(
    (username: string) => {
      modal.confirm({
        title: '确认清除',
        content: `确定要清除用户 "${username}" 的登录失败计数吗？`,
        onOk: () => {
          clearFailureMutation.mutate(username, {
            onSuccess: () => {
              antdMessage.success('已清除登录失败计数');
            },
            onError: () => {
              antdMessage.error('清除失败');
            },
          });
        },
      });
    },
    [clearFailureMutation, modal, antdMessage],
  );

  // ==================== 统计面板 ====================
  const renderStatsPanel = () => (
    <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
      {/* 今日登录统计 */}
      <Col xs={24} sm={12} md={6}>
        <Card size="small" title="今日登录统计">
          <Row gutter={16}>
            <Col span={12}>
              <Statistic
                title="成功"
                value={todayLoginStats?.successCount || 0}
                styles={{ content: { color: '#52c41a' } }}
                prefix={<CheckCircleOutlined />}
              />
            </Col>
            <Col span={12}>
              <Statistic
                title="失败"
                value={todayLoginStats?.failedCount || 0}
                styles={{ content: { color: '#ff4d4f' } }}
                prefix={<CloseCircleOutlined />}
              />
            </Col>
          </Row>
          <div style={{ marginTop: 8, textAlign: 'center' }}>
            <Tag
              color={
                Number(todayLoginStats?.successRate?.replace('%', '') || 0) >= 90
                  ? 'green'
                  : 'orange'
              }
            >
              成功率: {todayLoginStats?.successRate || '0%'}
            </Tag>
          </div>
        </Card>
      </Col>

      {/* 今日总览 */}
      <Col xs={24} sm={12} md={6}>
        <Card size="small" title="今日操作总览">
          <Statistic
            title="总操作数"
            value={auditSummary?.totalCount || 0}
            prefix={<SafetyOutlined />}
          />
          <div style={{ marginTop: 8 }}>
            <Tag color="blue">成功: {auditSummary?.successCount || 0}</Tag>
            <Tag color="red">失败: {auditSummary?.failedCount || 0}</Tag>
          </div>
        </Card>
      </Col>

      {/* 登录失败监控 */}
      <Col xs={24} sm={12} md={12}>
        <Card
          size="small"
          title={
            <Space>
              <WarningOutlined style={{ color: '#faad14' }} />
              <span>登录失败监控</span>
              {loginFailures && Object.keys(loginFailures).length > 0 && (
                <Badge
                  count={Object.keys(loginFailures).length}
                  style={{ backgroundColor: '#faad14' }}
                />
              )}
            </Space>
          }
          extra={
            Object.keys(loginFailures || {}).length > 0 && (
              <Button type="link" size="small" onClick={handleRefresh}>
                刷新
              </Button>
            )
          }
        >
          {loginFailures && Object.keys(loginFailures).length > 0 ? (
            <Table
              size="small"
              pagination={false}
              columns={[
                { title: '用户名', dataIndex: 'username', key: 'username' },
                {
                  title: '失败次数',
                  dataIndex: 'count',
                  key: 'count',
                  render: (count: number) => (
                    <Badge
                      count={count}
                      style={{ backgroundColor: count >= 5 ? '#ff4d4f' : '#faad14' }}
                    />
                  ),
                },
                {
                  title: '操作',
                  key: 'action',
                  render: (_, record: { username: string }) => (
                    <Popconfirm
                      title="确认清除"
                      description="确定要清除该用户的登录失败计数吗？"
                      onConfirm={() => handleClearFailure(record.username)}
                    >
                      <Button type="link" size="small" danger>
                        清除
                      </Button>
                    </Popconfirm>
                  ),
                },
              ]}
              dataSource={Object.entries(loginFailures).map(([username, count]) => ({
                username,
                count,
                key: username,
              }))}
            />
          ) : (
            <div style={{ textAlign: 'center', color: '#52c41a' }}>
              <CheckCircleOutlined style={{ fontSize: 24, marginBottom: 8 }} />
              <div>暂无异常登录记录</div>
            </div>
          )}
        </Card>
      </Col>
    </Row>
  );

  // ==================== 表格列定义 ====================
  const columns: ProColumns<AuditLog>[] = [
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 170,
      sorter: true,
      render: (_, record) => (
        <Tooltip title={formatDateTime(record.createTime)}>
          <Space>
            <ClockCircleOutlined />
            <span>{dayjs(record.createTime).format('MM-DD HH:mm:ss')}</span>
          </Space>
        </Tooltip>
      ),
    },
    {
      title: '用户名',
      dataIndex: 'username',
      width: 120,
    },
    {
      title: '操作类型',
      dataIndex: 'operation',
      width: 120,
      render: (_, record) => {
        const info = getOperationTypeInfo(record.operation);
        return <Tag color={info.color}>{info.text}</Tag>;
      },
    },
    {
      title: '结果',
      dataIndex: 'responseResult',
      width: 80,
      render: (_, record) => (
        <Tag color={record.responseResult === 'OK' ? 'success' : 'error'}>
          {record.responseResult === 'OK' ? '成功' : '失败'}
        </Tag>
      ),
    },
    {
      title: '请求方法',
      dataIndex: 'method',
      width: 90,
      render: (_, record) => <Tag color={getMethodColor(record.method)}>{record.method}</Tag>,
    },
    {
      title: '请求地址',
      dataIndex: 'requestUrl',
      width: 200,
      ellipsis: true,
    },
    {
      title: '业务类型',
      dataIndex: 'businessType',
      width: 100,
      render: (_, record) => {
        const info = getBusinessTypeInfo(record.businessType);
        return <Tag color={info.color}>{info.text}</Tag>;
      },
    },
    {
      title: 'IP地址',
      dataIndex: 'requestIp',
      width: 130,
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record.id)}
          >
            详情
          </Button>
        </Space>
      ),
    },
  ];

  // ==================== 搜索表单 ====================
  const searchForm = (
    <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
      <Search
        placeholder="关键词搜索"
        style={{ width: 180 }}
        allowClear
        value={queryParams.keyword}
        onChange={(e) =>
          setQueryParams((prev) => ({ ...prev, keyword: e.target.value || undefined }))
        }
        onSearch={(keyword) => {
          setQueryParams((prev) => ({ ...prev, page: 1 }));
          updateUrlParams({ ...queryParams, keyword, page: 1 });
        }}
      />
      <Search
        placeholder="用户名"
        style={{ width: 120 }}
        allowClear
        value={queryParams.username}
        onChange={(e) =>
          setQueryParams((prev) => ({ ...prev, username: e.target.value || undefined }))
        }
        onSearch={(username) => {
          setQueryParams((prev) => ({ ...prev, page: 1 }));
          updateUrlParams({ ...queryParams, username, page: 1 });
        }}
      />
      <Select
        placeholder="操作类型"
        style={{ width: 120 }}
        allowClear
        value={queryParams.operation}
        onChange={(val) => {
          setQueryParams((prev) => ({ ...prev, page: 1 }));
          updateUrlParams({ ...queryParams, operation: val, page: 1 });
        }}
        options={Object.entries(operationEnum).map(([k, v]) => ({ label: v.text, value: k }))}
      />
      <Select
        placeholder="结果"
        style={{ width: 80 }}
        allowClear
        value={queryParams.responseResult}
        onChange={(val) => {
          setQueryParams((prev) => ({ ...prev, page: 1 }));
          updateUrlParams({ ...queryParams, responseResult: val, page: 1 });
        }}
        options={resultEnum}
      />
      <Select
        placeholder="业务类型"
        style={{ width: 120 }}
        allowClear
        value={queryParams.businessType}
        onChange={(val) => {
          setQueryParams((prev) => ({ ...prev, page: 1 }));
          updateUrlParams({ ...queryParams, businessType: val, page: 1 });
        }}
        options={[
          { label: '员工管理', value: 'employee' },
          { label: '薪酬管理', value: 'payroll' },
          { label: '支付管理', value: 'payment' },
          { label: '审批流程', value: 'approval' },
          { label: '系统配置', value: 'system' },
          { label: '权限管理', value: 'AUTH' },
        ]}
      />
      <RangePicker
        showTime
        value={
          queryParams.startTime && queryParams.endTime
            ? [dayjs(queryParams.startTime), dayjs(queryParams.endTime)]
            : undefined
        }
        onChange={(dates, dateStrings) => {
          setQueryParams((prev) => ({
            ...prev,
            page: 1,
            startTime: dateStrings[0] || undefined,
            endTime: dateStrings[1] || undefined,
          }));
          updateUrlParams({
            ...queryParams,
            startTime: dateStrings[0],
            endTime: dateStrings[1],
            page: 1,
          });
        }}
        placeholder={['开始时间', '结束时间']}
      />
      <Button
        icon={<ReloadOutlined />}
        onClick={() => {
          setQueryParams((prev) => ({
            ...prev,
            page: 1,
            username: undefined,
            operation: undefined,
            businessType: undefined,
            responseResult: undefined,
            startTime: undefined,
            endTime: undefined,
            keyword: undefined,
          }));
          updateUrlParams({ page: 1 });
          handleRefresh();
        }}
      >
        重置
      </Button>
    </div>
  );

  // ==================== 渲染 ====================
  return (
    <PageContainer title="审计日志">
      {renderStatsPanel()}
      {searchForm}

      <ProTable<AuditLog>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        dataSource={records}
        loading={isLoading}
        pagination={{
          current: queryParams.page,
          pageSize: queryParams.pageSize,
          total: auditData?.total ?? 0,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条`,
        }}
        onChange={(pagination, filters, sorter: any) => {
          const page = pagination.current || 1;
          const pageSize = pagination.pageSize || 10;
          const sortBy = Array.isArray(sorter) ? sorter[0]?.field : sorter.field;
          const order = Array.isArray(sorter)
            ? sorter[0]?.order === 'ascend'
              ? 'asc'
              : 'desc'
            : sorter.order === 'ascend'
              ? 'asc'
              : 'desc';

          setQueryParams((prev) => ({ ...prev, page, pageSize, sortBy, order }));
          updateUrlParams({ ...queryParams, page, pageSize, sortBy, order });
        }}
        toolBarRender={() => [
          <Button key="refresh" icon={<ReloadOutlined />} onClick={handleRefresh}>
            刷新
          </Button>,
        ]}
      />

      {/* 详情 Drawer */}
      <DrawerForm
        title="日志详情"
        width={700}
        open={drawerVisible}
        onOpenChange={setDrawerVisible}
        submitter={false}
      >
        {detailLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>
        ) : detailData ? (
          <div>
            <Card size="small" title="基本信息" style={{ marginBottom: 16 }}>
              <ProDescriptions column={2}>
                <ProDescriptions.Item label="ID">{detailData.id}</ProDescriptions.Item>
                <ProDescriptions.Item label="用户名">{detailData.username}</ProDescriptions.Item>
                <ProDescriptions.Item label="操作类型">
                  {getOperationTypeInfo(detailData.operation).text}
                </ProDescriptions.Item>
                <ProDescriptions.Item label="请求方法">
                  <Tag color={getMethodColor(detailData.method)}>{detailData.method}</Tag>
                </ProDescriptions.Item>
                <ProDescriptions.Item label="结果">
                  <Tag color={detailData.responseResult === 'OK' ? 'success' : 'error'}>
                    {detailData.responseResult === 'OK' ? '成功' : '失败'}
                  </Tag>
                </ProDescriptions.Item>
                <ProDescriptions.Item label="业务类型">
                  {getBusinessTypeInfo(detailData.businessType).text}
                </ProDescriptions.Item>
                <ProDescriptions.Item label="业务标识">
                  {detailData.businessKey}
                </ProDescriptions.Item>
                <ProDescriptions.Item label="请求IP">{detailData.requestIp}</ProDescriptions.Item>
                <ProDescriptions.Item label="执行时间">
                  {formatExecutionTime(detailData.executionTime)}
                </ProDescriptions.Item>
                <ProDescriptions.Item label="创建时间">
                  {formatDateTime(detailData.createTime)}
                </ProDescriptions.Item>
              </ProDescriptions>
            </Card>

            <Card size="small" title="请求信息" style={{ marginBottom: 16 }}>
              <ProDescriptions column={1} size="small">
                <ProDescriptions.Item label="请求地址">
                  {detailData.requestUrl}
                </ProDescriptions.Item>
                <ProDescriptions.Item label="请求参数">
                  <pre
                    style={{
                      maxHeight: 200,
                      overflow: 'auto',
                      margin: 0,
                      padding: 8,
                      background: '#f5f5f5',
                      borderRadius: 4,
                    }}
                  >
                    {detailData.requestParams || '无'}
                  </pre>
                </ProDescriptions.Item>
              </ProDescriptions>
            </Card>

            {detailData.responseResult && (
              <Card size="small" title="响应结果" style={{ marginBottom: 16 }}>
                <pre
                  style={{
                    maxHeight: 200,
                    overflow: 'auto',
                    margin: 0,
                    padding: 8,
                    background: '#f5f5f5',
                    borderRadius: 4,
                  }}
                >
                  {detailData.responseResult.length > 2000
                    ? detailData.responseResult.substring(0, 2000) + '...'
                    : detailData.responseResult}
                </pre>
              </Card>
            )}

            {detailData.errorMsg && (
              <Card size="small" title="错误信息" style={{ marginBottom: 16 }}>
                <pre
                  style={{
                    maxHeight: 200,
                    overflow: 'auto',
                    margin: 0,
                    padding: 8,
                    background: '#fff2f0',
                    borderRadius: 4,
                    color: '#ff4d4f',
                  }}
                >
                  {detailData.errorMsg}
                </pre>
              </Card>
            )}

            <Card size="small" title="客户端信息">
              <ProDescriptions column={1} size="small">
                <ProDescriptions.Item label="User-Agent">
                  {detailData.userAgent || '未知'}
                </ProDescriptions.Item>
              </ProDescriptions>
            </Card>
          </div>
        ) : (
          <div style={{ textAlign: 'center', padding: 40 }}>未找到日志详情</div>
        )}
      </DrawerForm>
    </PageContainer>
  );
};

export default AuditLogs;
