import React, { useCallback, useMemo, useRef, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import {
  ProTable,
  type ProColumns,
  type ActionType,
  ModalForm,
  DrawerForm,
  ProDescriptions,
} from '@ant-design/pro-components';
import {
  App as AntdApp,
  Card,
  Tag,
  Space,
  Button,
  Tooltip,
  Badge,
  Modal,
  message,
  Input,
  Select,
  Switch,
  Popconfirm,
} from 'antd';
import {
  PlayCircleOutlined,
  PauseCircleOutlined,
  DeleteOutlined,
  ReloadOutlined,
  EyeOutlined,
  ThunderboltOutlined,
  PlusOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  useScheduledTasksQuery,
  useScheduledTaskDetailQuery,
  useTaskExecutionLogsQuery,
  usePauseTaskMutation,
  useResumeTaskMutation,
  useTriggerTaskMutation,
  useDeleteTaskMutation,
  getTaskStatusInfo,
  getExecutionStatusInfo,
  formatDuration,
  formatNextExecuteTime,
  type ScheduledTask,
  type ScheduledTaskExecution,
} from '@services/queries/taskSchedule';

// ==================== 状态枚举 ====================
const statusEnum: Record<string, { text: string; color: string }> = {
  PAUSED: { text: '已暂停', color: 'default' },
  RUNNING: { text: '运行中', color: 'processing' },
  FAILED: { text: '失败', color: 'error' },
  SUCCESS: { text: '成功', color: 'success' },
};

// ==================== 工具函数 ====================
const formatDateTime = (value?: string): string =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—';

// ==================== 主组件 ====================
const TaskSchedules: React.FC = () => {
  const { message: antdMessage } = AntdApp.useApp();

  // 核心引用
  const actionRef = useRef<ActionType>();

  // Drawer 状态
  const [detailDrawerVisible, setDetailDrawerVisible] = useState(false);
  const [logsDrawerVisible, setLogsDrawerVisible] = useState(false);
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);

  // 数据查询
  const {
    data: tasksData,
    isLoading,
    refetch,
  } = useScheduledTasksQuery();

  const {
    data: taskDetail,
    isLoading: detailLoading,
  } = useScheduledTaskDetailQuery(selectedTaskId || 0, {
    enabled: !!selectedTaskId && detailDrawerVisible,
  });

  const {
    data: executionLogs,
    isLoading: logsLoading,
  } = useTaskExecutionLogsQuery(selectedTaskId || 0, 20, {
    enabled: !!selectedTaskId && logsDrawerVisible,
  });

  // Mutations
  const pauseMutation = usePauseTaskMutation();
  const resumeMutation = useResumeTaskMutation();
  const triggerMutation = useTriggerTaskMutation();
  const deleteMutation = useDeleteTaskMutation();

  const tasks = useMemo(() => tasksData || [], [tasksData]);
  const logs = useMemo(() => executionLogs || [], [executionLogs]);

  // ==================== 处理函数 ====================
  const handleViewDetail = useCallback((id: number) => {
    setSelectedTaskId(id);
    setDetailDrawerVisible(true);
  }, []);

  const handleViewLogs = useCallback((id: number) => {
    setSelectedTaskId(id);
    setLogsDrawerVisible(true);
  }, []);

  const handlePause = useCallback(async (id: number) => {
    try {
      await pauseMutation.mutateAsync(id);
      antdMessage.success('任务已暂停');
      refetch();
    } catch (error: unknown) {
      const err = error as { message?: string };
      antdMessage.error(err.message || '暂停任务失败');
    }
  }, [pauseMutation, antdMessage, refetch]);

  const handleResume = useCallback(async (id: number) => {
    try {
      await resumeMutation.mutateAsync(id);
      antdMessage.success('任务已恢复');
      refetch();
    } catch (error: unknown) {
      const err = error as { message?: string };
      antdMessage.error(err.message || '恢复任务失败');
    }
  }, [resumeMutation, antdMessage, refetch]);

  const handleTrigger = useCallback(async (id: number) => {
    try {
      await triggerMutation.mutateAsync(id);
      antdMessage.success('任务已触发执行');
      refetch();
    } catch (error: unknown) {
      const err = error as { message?: string };
      antdMessage.error(err.message || '触发任务失败');
    }
  }, [triggerMutation, antdMessage, refetch]);

  const handleDelete = useCallback((id: number) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除该任务吗？此操作不可恢复。',
      okText: '确认删除',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteMutation.mutateAsync(id);
          antdMessage.success('任务已删除');
          refetch();
        } catch (error: unknown) {
          const err = error as { message?: string };
          antdMessage.error(err.message || '删除任务失败');
        }
      },
    });
  }, [deleteMutation, antdMessage, refetch]);

  const handleRefresh = useCallback(() => {
    refetch();
    actionRef.current?.reloadAndRest?.();
  }, [refetch]);

  // ==================== 表格列定义 ====================
  const columns: ProColumns<ScheduledTask>[] = [
    {
      title: '任务名称',
      dataIndex: 'taskName',
      width: 180,
      render: (_, record) => (
        <Button type="link" onClick={() => handleViewDetail(record.id)}>
          {record.taskName}
        </Button>
      ),
    },
    {
      title: '任务标识',
      dataIndex: 'taskKey',
      width: 150,
      ellipsis: true,
    },
    {
      title: '任务组',
      dataIndex: 'taskGroup',
      width: 120,
    },
    {
      title: 'Cron 表达式',
      dataIndex: 'cronExpression',
      width: 150,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueEnum: statusEnum,
      render: (_, record) => {
        const info = getTaskStatusInfo(record.status);
        return (
          <Tag color={info.color} icon={record.status === 'RUNNING' ? <ThunderboltOutlined spin /> : undefined}>
            {info.text}
          </Tag>
        );
      },
    },
    {
      title: '最后执行',
      dataIndex: 'lastExecuteTime',
      width: 160,
      render: (_, record) => formatDateTime(record.lastExecuteTime),
    },
    {
      title: '下次执行',
      dataIndex: 'nextExecuteTime',
      width: 160,
      render: (_, record) => formatNextExecuteTime(record.nextExecuteTime),
    },
    {
      title: '最后结果',
      dataIndex: 'lastResult',
      width: 120,
      ellipsis: true,
      render: (_, record) => (
        <Badge
          status={record.lastResult === 'SUCCESS' ? 'success' : record.lastResult === 'FAILED' ? 'error' : 'default'}
          text={record.lastResult || '—'}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="查看详情">
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleViewDetail(record.id)}>
              详情
            </Button>
          </Tooltip>
          <Tooltip title="执行日志">
            <Button type="link" size="small" icon={<ClockCircleOutlined />} onClick={() => handleViewLogs(record.id)}>
              日志
            </Button>
          </Tooltip>
          {record.status === 'RUNNING' ? (
            <Tooltip title="暂停任务">
              <Button type="link" size="small" danger icon={<PauseCircleOutlined />} onClick={() => handlePause(record.id)}>
                暂停
              </Button>
            </Tooltip>
          ) : (
            <Tooltip title="恢复任务">
              <Button type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => handleResume(record.id)}>
                恢复
              </Button>
            </Tooltip>
          )}
          <Tooltip title="手动触发">
            <Button type="link" size="small" icon={<ThunderboltOutlined />} onClick={() => handleTrigger(record.id)}>
              触发
            </Button>
          </Tooltip>
          <Tooltip title="删除任务">
            <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record.id)} />
          </Tooltip>
        </Space>
      ),
    },
  ];

  // ==================== 渲染 ====================
  return (
    <PageContainer title="任务调度">
      <ProTable<ScheduledTask>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        dataSource={tasks}
        loading={isLoading}
        pagination={false}
        toolBarRender={() => [
          <Button key="refresh" icon={<ReloadOutlined />} onClick={handleRefresh}>
            刷新
          </Button>,
        ]}
      />

      {/* 详情 Drawer */}
      <DrawerForm
        title={taskDetail?.taskName || '任务详情'}
        width={600}
        open={detailDrawerVisible}
        onOpenChange={setDetailDrawerVisible}
        submitter={false}
      >
        {detailLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>
        ) : taskDetail ? (
          <div>
            <Card size="small" title="基本信息" style={{ marginBottom: 16 }}>
              <ProDescriptions column={2}>
                <ProDescriptions.Item label="任务ID">{taskDetail.id}</ProDescriptions.Item>
                <ProDescriptions.Item label="状态">
                  {getTaskStatusInfo(taskDetail.status).text}
                </ProDescriptions.Item>
                <ProDescriptions.Item label="任务标识">{taskDetail.taskKey}</ProDescriptions.Item>
                <ProDescriptions.Item label="任务组">{taskDetail.taskGroup || '默认'}</ProDescriptions.Item>
                <ProDescriptions.Item label="Cron 表达式">{taskDetail.cronExpression}</ProDescriptions.Item>
                <ProDescriptions.Item label="处理Bean">{taskDetail.handlerBean}</ProDescriptions.Item>
                <ProDescriptions.Item label="描述" span={2}>
                  {taskDetail.description || '无'}
                </ProDescriptions.Item>
              </ProDescriptions>
            </Card>

            <Card size="small" title="执行信息" style={{ marginBottom: 16 }}>
              <ProDescriptions column={2}>
                <ProDescriptions.Item label="最后执行时间">
                  {formatDateTime(taskDetail.lastExecuteTime)}
                </ProDescriptions.Item>
                <ProDescriptions.Item label="下次执行时间">
                  {formatNextExecuteTime(taskDetail.nextExecuteTime)}
                </ProDescriptions.Item>
                <ProDescriptions.Item label="最后结果">
                  {taskDetail.lastResult || '—'}
                </ProDescriptions.Item>
                <ProDescriptions.Item label="重试次数">
                  {taskDetail.retryCount} / {taskDetail.maxRetryCount}
                </ProDescriptions.Item>
              </ProDescriptions>
            </Card>

            <Card size="small" title="告警配置">
              <ProDescriptions column={2}>
                <ProDescriptions.Item label="告警启用">
                  {taskDetail.alarmEnabled ? '是' : '否'}
                </ProDescriptions.Item>
                <ProDescriptions.Item label="告警接收人">
                  {taskDetail.alarmReceivers || '未配置'}
                </ProDescriptions.Item>
              </ProDescriptions>
            </Card>
          </div>
        ) : (
          <div style={{ textAlign: 'center', padding: 40 }}>未找到任务详情</div>
        )}
      </DrawerForm>

      {/* 执行日志 Drawer */}
      <DrawerForm
        title="执行日志"
        width={700}
        open={logsDrawerVisible}
        onOpenChange={setLogsDrawerVisible}
        submitter={false}
      >
        <div>
          <div style={{ marginBottom: 16 }}>
            <Button icon={<ReloadOutlined />} onClick={() => handleViewLogs(selectedTaskId || 0)}>
              刷新
            </Button>
          </div>
          {logsLoading ? (
            <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>
          ) : logs.length > 0 ? (
            logs.map((log) => (
              <Card
                key={log.id}
                size="small"
                style={{ marginBottom: 8 }}
                extra={formatDateTime(log.startTime)}
              >
                <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                  <Space>
                    <Tag color={getExecutionStatusInfo(log.status).color}>
                      {getExecutionStatusInfo(log.status).text}
                    </Tag>
                    <span>耗时: {formatDuration(log.durationMs)}</span>
                    {log.traceId && <span>TraceID: {log.traceId}</span>}
                  </Space>
                </Space>
                {log.errorMessage && (
                  <div style={{ marginTop: 8, color: '#ff4d4f' }}>
                    {log.errorMessage}
                  </div>
                )}
                {log.result && (
                  <div style={{ marginTop: 8 }}>
                    <pre style={{ maxHeight: 100, overflow: 'auto', margin: 0, padding: 8, background: '#f5f5f5', borderRadius: 4, fontSize: 12 }}>
                      {log.result.length > 500 ? log.result.substring(0, 500) + '...' : log.result}
                    </pre>
                  </div>
                )}
              </Card>
            ))
          ) : (
            <div style={{ textAlign: 'center', padding: 40 }}>暂无执行日志</div>
          )}
        </div>
      </DrawerForm>
    </PageContainer>
  );
};

export default TaskSchedules;
