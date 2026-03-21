import React, { useState, useEffect } from 'react';
import {
  Card,
  Statistic,
  Row,
  Col,
  Typography,
  Avatar,
  List,
  Tag,
  Button,
  Space,
  Alert,
} from 'antd';
import { Link } from 'react-router-dom';
import {
  TeamOutlined,
  WalletOutlined,
  UserSwitchOutlined,
  ClockCircleOutlined,
  PlusOutlined,
  EyeOutlined,
  SettingOutlined,
  BookOutlined,
  BellOutlined,
  SyncOutlined,
  GlobalOutlined,
  TrophyOutlined,
  RiseOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons';
import NewUserGuide from '@components/Dashboard/NewUserGuide';
import {
  useDashboardMetricsQuery,
  useDashboardStatusQuery,
  useDashboardTodosQuery,
  useDashboardActivitiesQuery,
} from '@services/queries/dashboard';

const { Title, Text } = Typography;

const getErrorMessage = (error: unknown) => (error instanceof Error ? error.message : '请稍后再试');

const formatDelta = (value: number) =>
  Number(Math.abs(value)).toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 1,
  });

const formatRunRate = (value: number | undefined) => {
  if (value === undefined || value === null || Number.isNaN(value)) return '-';
  return `${Number(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  })}%`;
};

const getPriorityColor = (priority: string) => {
  const normalized = priority?.toLowerCase?.() ?? priority;
  switch (normalized) {
    case 'high':
    case '高':
      return 'red';
    case 'medium':
    case '中':
      return 'orange';
    case 'low':
    case '低':
      return 'green';
    default:
      return 'default';
  }
};

const getPriorityLabel = (priority: string) => {
  const normalized = priority?.toLowerCase?.() ?? priority;
  switch (normalized) {
    case 'high':
    case '高':
      return '高';
    case 'medium':
    case '中':
      return '中';
    case 'low':
    case '低':
      return '低';
    default:
      return priority || '未知';
  }
};

const getStatusTagColor = (status: string) => {
  const normalized = status?.toLowerCase?.() ?? status;
  switch (normalized) {
    case 'online':
    case '在线':
      return 'green';
    case 'sync':
    case '同步中':
      return 'blue';
    case 'warning':
    case '警告':
      return 'orange';
    case 'offline':
    case '离线':
      return 'red';
    default:
      return 'default';
  }
};

const getOverallStatusType = (status: string | undefined) => {
  const normalized = status?.toLowerCase?.() ?? status;
  switch (normalized) {
    case 'warning':
    case '警告':
      return 'warning';
    case 'offline':
    case '离线':
      return 'error';
    default:
      return 'success';
  }
};

const formatTodoDue = (due: string) => {
  if (!due) return '截止时间：未提供';
  return due.startsWith('截止') ? due : `截止时间: ${due}`;
};

const Dashboard: React.FC = () => {
  const [showGuide, setShowGuide] = useState(false);

  useEffect(() => {
    const hasCompletedGuide = localStorage.getItem('user_guide_completed');
    if (!hasCompletedGuide) {
      const timer = setTimeout(() => {
        setShowGuide(true);
      }, 1000);
      return () => clearTimeout(timer);
    }
  }, []);

  const metricsQuery = useDashboardMetricsQuery();
  const statusQuery = useDashboardStatusQuery();
  const todosQuery = useDashboardTodosQuery();
  const activitiesQuery = useDashboardActivitiesQuery();

  const metrics = metricsQuery.data;
  const todos = todosQuery.data ?? [];
  const activities = activitiesQuery.data ?? [];
  const statusData = statusQuery.data;
  const systemComponents = statusData?.components ?? [];

  const todoCount = todos.length;

  const quickActions = [
    { title: '新建支付批次', icon: <PlusOutlined />, href: '/payments/batches', color: '#1890ff' },
    { title: '员工管理', icon: <TeamOutlined />, href: '/employees', color: '#52c41a' },
    { title: '用户绑定', icon: <UserSwitchOutlined />, href: '/admin/user-binding', color: '#722ed1' },
    { title: '系统配置', icon: <SettingOutlined />, href: '/system/integration', color: '#fa8c16' },
    { title: '组织同步', icon: <SyncOutlined />, href: '/system/org-sync', color: '#13c2c2' },
    { title: '查看报告', icon: <EyeOutlined />, href: '/reports', color: '#eb2f96' },
  ];

  const metricCards = [
    {
      key: 'employeeTotal',
      title: '员工总数',
      value: metrics?.employeeTotal ?? 0,
      suffix: '人',
      prefix: <TeamOutlined style={{ color: '#1890ff' }} />,
      precision: 0,
      valueStyle: { color: '#1890ff' },
      change: metrics?.employeeGrowthRate ?? 0,
    },
    {
      key: 'monthlyPaymentAmount',
      title: '本月支付',
      value: metrics?.monthlyPaymentAmount ?? 0,
      suffix: '元',
      prefix: <WalletOutlined style={{ color: '#52c41a' }} />,
      precision: 2,
      valueStyle: { color: '#52c41a' },
      change: metrics?.monthlyPaymentGrowthRate ?? 0,
    },
    {
      key: 'pendingBatchCount',
      title: '待处理批次',
      value: metrics?.pendingBatchCount ?? 0,
      suffix: '个',
      prefix: <ClockCircleOutlined style={{ color: '#faad14' }} />,
      precision: 0,
      valueStyle: { color: '#faad14' },
      change: metrics?.pendingBatchChangeRate ?? 0,
    },
    {
      key: 'userBindingRate',
      title: '用户绑定率',
      value: metrics?.userBindingRate ?? 0,
      suffix: '%',
      prefix: <UserSwitchOutlined style={{ color: '#722ed1' }} />,
      precision: 1,
      valueStyle: { color: '#722ed1' },
      change: metrics?.userBindingGrowthRate ?? 0,
    },
  ];

  const smallScreen = typeof window !== 'undefined' && window.innerWidth < 576;
  const mediumScreen = typeof window !== 'undefined' && window.innerWidth < 768;

  return (
    <div
      style={{
        padding: mediumScreen ? '16px' : '24px',
        backgroundColor: '#f5f5f5',
        minHeight: '100vh',
      }}
    >
      {metricsQuery.isError && (
        <Alert
          type="error"
          showIcon
          message="指标数据加载失败"
          description={getErrorMessage(metricsQuery.error)}
          style={{ marginBottom: 16 }}
        />
      )}

      <div
        style={{
          marginBottom: '24px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          flexDirection: smallScreen ? 'column' : 'row',
          gap: smallScreen ? '12px' : '0',
        }}
      >
        <div style={{ flex: 1 }}>
          <Title level={smallScreen ? 3 : 2} style={{ margin: 0, marginBottom: '8px' }}>
            早上好，管理员 👋
          </Title>
          <Text type="secondary" style={{ fontSize: smallScreen ? '14px' : '16px' }}>
            今天是 {new Date().toLocaleDateString('zh-CN', {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
              weekday: 'long',
            })}，您有 {todoCount} 项待办事项需要处理
          </Text>
        </div>
        <Button
          type="default"
          icon={<QuestionCircleOutlined />}
          onClick={() => setShowGuide(true)}
          size={smallScreen ? 'small' : 'middle'}
        >
          新手引导
        </Button>
      </div>

      {/* 统计卡片 - 单行显示 */}
      <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 4, marginBottom: '24px' }}>
        {metricCards.map((item) => {
          const change = item.change ?? 0;
          const isPositive = change > 0;
          const isNegative = change < 0;
          const trendText =
            change === 0
              ? '较上月 持平'
              : `较上月 ${isPositive ? '增长' : '下降'} ${formatDelta(change)}%`;

          return (
            <Card key={item.key} style={{ flex: '0 0 auto', width: 180, height: '100%' }} loading={metricsQuery.isLoading}>
              <Statistic
                title={item.title}
                value={item.value}
                suffix={item.suffix}
                prefix={item.prefix}
                precision={item.precision}
                valueStyle={item.valueStyle}
              />
              {!metricsQuery.isLoading && (
                <div style={{ marginTop: '8px', display: 'flex', alignItems: 'center' }}>
                  {isPositive && <ArrowUpOutlined style={{ color: '#52c41a', marginRight: 4 }} />}
                  {isNegative && <ArrowDownOutlined style={{ color: '#f5222d', marginRight: 4 }} />}
                  {!isPositive && !isNegative && (
                    <ArrowUpOutlined style={{ visibility: 'hidden', marginRight: 4 }} />
                  )}
                  <Text type="secondary" style={{ fontSize: '12px' }}>
                    {trendText}
                  </Text>
                </div>
              )}
            </Card>
          );
        })}
      </div>

      <Row gutter={[16, 16]}>
        {/* 左侧区域 - 垂直排列 */}
        <Col xs={24} lg={16}>
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Card title="快捷入口" size="small">
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {quickActions.map((action, index) => (
                  <Link key={index} to={action.href} style={{ flex: '0 0 auto', width: smallScreen ? 'calc(50% - 4px)' : 'calc(25% - 6px)' }}>
                    <Card
                      hoverable
                      style={{ textAlign: 'center', border: '1px solid #f0f0f0' }}
                      styles={{
                        body: {
                          padding: smallScreen ? '12px 4px' : '16px 8px',
                        },
                      }}
                    >
                      <div
                        style={{
                          fontSize: smallScreen ? '18px' : '24px',
                          color: action.color,
                          marginBottom: '8px',
                        }}
                      >
                        {action.icon}
                      </div>
                      <Text
                        style={{
                          fontSize: smallScreen ? '10px' : '12px',
                          display: 'block',
                          lineHeight: 1.2,
                        }}
                      >
                        {action.title}
                      </Text>
                    </Card>
                  </Link>
                ))}
              </div>
            </Card>

            <Card
              title="待办清单"
              size="small"
              extra={<Link to="/tasks">查看全部</Link>}
              loading={todosQuery.isLoading}
            >
              {todosQuery.isError ? (
                <Alert
                  type="error"
                  showIcon
                  message="待办事项加载失败"
                  description={getErrorMessage(todosQuery.error)}
                />
              ) : (
                <List
                  dataSource={todos}
                  locale={{ emptyText: '暂无待办事项' }}
                  renderItem={(item) => (
                    <List.Item>
                      <List.Item.Meta
                        title={
                          <Space>
                            <Text>{item.title}</Text>
                            <Tag color={getPriorityColor(item.priority)}>
                              {getPriorityLabel(item.priority)}
                            </Tag>
                          </Space>
                        }
                        description={formatTodoDue(item.due)}
                      />
                      <Button type="link" size="small">
                        处理
                      </Button>
                    </List.Item>
                  )}
                />
              )}
            </Card>

            <Card title="最近活动" size="small" loading={activitiesQuery.isLoading}>
              {activitiesQuery.isError ? (
                <Alert
                  type="error"
                  showIcon
                  message="活动数据加载失败"
                  description={getErrorMessage(activitiesQuery.error)}
                />
              ) : (
                <List
                  dataSource={activities}
                  locale={{ emptyText: '暂无活动记录' }}
                  renderItem={(item) => (
                    <List.Item>
                      <List.Item.Meta
                        avatar={
                          <Avatar style={{ backgroundColor: '#1890ff' }}>
                            {item.initial || item.actor?.charAt(0) || '系'}
                          </Avatar>
                        }
                        title={
                          <Text>
                            <Text strong>{item.actor || '系统'}</Text> {item.description}
                          </Text>
                        }
                        description={item.timeAgo}
                      />
                    </List.Item>
                  )}
                />
              )}
            </Card>
          </Space>
        </Col>

        {/* 右侧区域 - 垂直排列 */}
        <Col xs={24} lg={8}>
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Card title="系统状态" size="small" loading={statusQuery.isLoading}>
              {statusQuery.isError ? (
                <Alert
                  type="error"
                  showIcon
                  message="系统状态加载失败"
                  description={getErrorMessage(statusQuery.error)}
                />
              ) : (
                <Space direction="vertical" style={{ width: '100%' }}>
                  {statusData?.overallStatus && (
                    <Alert
                      type={getOverallStatusType(statusData.overallStatus)}
                      showIcon
                      message={`系统总体状态：${statusData.overallStatus}`}
                    />
                  )}
                  {systemComponents.map((component, index) => (
                    <div
                      key={`${component.name}-${index}`}
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                      }}
                    >
                      <Space>
                        <Tag color={getStatusTagColor(component.status)}>
                          {component.status}
                        </Tag>
                        <Text>{component.name}</Text>
                      </Space>
                      <Text type="secondary" style={{ fontSize: '12px' }}>
                        运行率 {formatRunRate(component.runRate)}
                      </Text>
                    </div>
                  ))}
                  {systemComponents.length === 0 && !statusQuery.isLoading && (
                    <Alert type="info" message="暂无系统状态数据" showIcon />
                  )}
                </Space>
              )}
            </Card>

            <Card title="使用帮助" size="small">
              <Space direction="vertical" style={{ width: '100%' }}>
                <Button type="link" icon={<BookOutlined />} style={{ padding: 0, height: 'auto', textAlign: 'left' }}>
                  查看用户手册
                </Button>
                <Button type="link" icon={<BellOutlined />} style={{ padding: 0, height: 'auto', textAlign: 'left' }}>
                  常见问题解答
                </Button>
                <Button type="link" icon={<GlobalOutlined />} style={{ padding: 0, height: 'auto', textAlign: 'left' }}>
                  联系技术支持
                </Button>
              </Space>
            </Card>

            <Card title="产品动态" size="small">
              <Space direction="vertical" style={{ width: '100%' }}>
                <Alert
                  message="新功能上线"
                  description="批量支付功能已上线，支持一键处理多个批次，提升工作效率。"
                  type="info"
                  icon={<TrophyOutlined />}
                  style={{ marginBottom: '12px' }}
                />
                <Alert
                  message="系统优化"
                  description="支付处理速度提升50%，用户体验显著改善。"
                  type="success"
                  icon={<RiseOutlined />}
                />
              </Space>
            </Card>
          </Space>
        </Col>
      </Row>

      <NewUserGuide visible={showGuide} onClose={() => setShowGuide(false)} />
    </div>
  );
};

export default Dashboard;
