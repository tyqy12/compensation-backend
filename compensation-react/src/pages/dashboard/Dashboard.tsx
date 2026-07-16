import React, { useState, useEffect } from 'react';
import { Card, Statistic, Typography, Avatar, Tag, Button, Alert, Progress } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
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
  ArrowRightOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons';
import NewUserGuide from '@components/Dashboard/NewUserGuide';
import {
  useDashboardMetricsQuery,
  useDashboardStatusQuery,
  useDashboardTodosQuery,
  useDashboardActivitiesQuery,
} from '@services/queries/dashboard';
import './Dashboard.less';

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

const getStatusProgressColor = (status: string) => {
  switch (getStatusTagColor(status)) {
    case 'green':
      return 'var(--success)';
    case 'blue':
      return 'var(--primary)';
    case 'orange':
      return 'var(--warning)';
    case 'red':
      return 'var(--danger)';
    default:
      return 'var(--muted-light)';
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

const DashboardSectionHeader: React.FC<{
  title: string;
  subtitle?: string;
  extra?: React.ReactNode;
}> = ({ title, subtitle, extra }) => (
  <div className="dashboard-panel-heading">
    <div>
      <Typography.Title level={4} className="dashboard-panel-title">
        {title}
      </Typography.Title>
      {subtitle && <Typography.Text type="secondary">{subtitle}</Typography.Text>}
    </div>
    {extra && <div className="dashboard-panel-extra">{extra}</div>}
  </div>
);

const Dashboard: React.FC = () => {
  const [showGuide, setShowGuide] = useState(false);
  const navigate = useNavigate();

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
    {
      title: '新建支付批次',
      description: '创建并跟踪发放流程',
      icon: <PlusOutlined />,
      href: '/payments/batches',
      color: 'var(--primary)',
    },
    {
      title: '员工管理',
      description: '维护员工资料',
      icon: <TeamOutlined />,
      href: '/employees',
      color: 'var(--success)',
    },
    {
      title: '用户绑定',
      description: '处理平台账号绑定',
      icon: <UserSwitchOutlined />,
      href: '/admin/user-binding',
      color: 'var(--violet)',
    },
    {
      title: '系统配置',
      description: '管理第三方集成',
      icon: <SettingOutlined />,
      href: '/system/integration',
      color: 'var(--warning)',
    },
    {
      title: '组织同步',
      description: '同步组织架构',
      icon: <SyncOutlined />,
      href: '/system/org-sync',
      color: 'var(--teal)',
    },
    {
      title: '查看报告',
      description: '查看业务数据',
      icon: <EyeOutlined />,
      href: '/reports',
      color: 'var(--danger)',
    },
  ];

  const metricCards = [
    {
      key: 'employeeTotal',
      title: '员工总数',
      value: metrics?.employeeTotal ?? 0,
      suffix: '人',
      prefix: <TeamOutlined />,
      precision: 0,
      valueStyle: { color: 'var(--primary)' },
      accent: 'var(--primary)',
      change: metrics?.employeeGrowthRate ?? 0,
    },
    {
      key: 'monthlyPaymentAmount',
      title: '本月支付',
      value: metrics?.monthlyPaymentAmount ?? 0,
      suffix: '元',
      prefix: <WalletOutlined />,
      precision: 2,
      valueStyle: { color: 'var(--teal)' },
      accent: 'var(--teal)',
      change: metrics?.monthlyPaymentGrowthRate ?? 0,
    },
    {
      key: 'pendingBatchCount',
      title: '待处理批次',
      value: metrics?.pendingBatchCount ?? 0,
      suffix: '个',
      prefix: <ClockCircleOutlined />,
      precision: 0,
      valueStyle: { color: 'var(--warning)' },
      accent: 'var(--warning)',
      change: metrics?.pendingBatchChangeRate ?? 0,
    },
    {
      key: 'userBindingRate',
      title: '用户绑定率',
      value: metrics?.userBindingRate ?? 0,
      suffix: '%',
      prefix: <UserSwitchOutlined />,
      precision: 1,
      valueStyle: { color: 'var(--violet)' },
      accent: 'var(--violet)',
      change: metrics?.userBindingGrowthRate ?? 0,
    },
  ];

  return (
    <main className="dashboard-page">
      {metricsQuery.isError && (
        <Alert
          type="error"
          showIcon
          title="指标数据加载失败"
          description={getErrorMessage(metricsQuery.error)}
          className="dashboard-page-alert"
        />
      )}

      <section className="dashboard-hero" aria-labelledby="dashboard-title">
        <div className="dashboard-hero-copy">
          <Text className="dashboard-eyebrow">薪酬运营中心</Text>
          <Title level={2} id="dashboard-title" className="dashboard-title">
            早上好，管理员
          </Title>
          <Text type="secondary" className="dashboard-subtitle">
            今天是{' '}
            {new Date().toLocaleDateString('zh-CN', {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
              weekday: 'long',
            })}
            ，当前有 <Text strong>{todoCount} 项待办</Text> 需要处理
          </Text>
        </div>
        <div className="dashboard-hero-actions">
          <Button
            type="primary"
            icon={<WalletOutlined />}
            onClick={() => navigate('/payments/batches')}
          >
            进入支付批次
          </Button>
          <Button
            type="default"
            icon={<QuestionCircleOutlined />}
            onClick={() => setShowGuide(true)}
          >
            新手引导
          </Button>
        </div>
      </section>

      <section className="dashboard-metrics-grid" aria-label="核心业务指标">
        {metricCards.map((item) => {
          const change = item.change ?? 0;
          const isPositive = change > 0;
          const isNegative = change < 0;
          const trendText =
            change === 0
              ? '较上月 持平'
              : `较上月 ${isPositive ? '增长' : '下降'} ${formatDelta(change)}%`;

          return (
            <Card
              key={item.key}
              className="dashboard-metric-card"
              variant="borderless"
              loading={metricsQuery.isLoading}
            >
              <div className="dashboard-metric-heading">
                <span className="dashboard-metric-icon" style={{ color: item.accent }}>
                  {item.prefix}
                </span>
                <Text type="secondary">{item.title}</Text>
              </div>
              <Statistic
                value={item.value}
                suffix={item.suffix}
                precision={item.precision}
                styles={{ content: item.valueStyle }}
              />
              {!metricsQuery.isLoading && (
                <div
                  className={`dashboard-metric-trend${isNegative ? ' dashboard-metric-trend-negative' : ''}`}
                >
                  {isPositive && <ArrowUpOutlined />}
                  {isNegative && <ArrowDownOutlined />}
                  {!isPositive && !isNegative && <span className="dashboard-trend-placeholder" />}
                  <Text type="secondary">{trendText}</Text>
                </div>
              )}
            </Card>
          );
        })}
      </section>

      <div className="dashboard-workspace">
        <div className="dashboard-main-column">
          <Card className="dashboard-panel" variant="borderless">
            <DashboardSectionHeader title="快捷入口" subtitle="高频任务一键进入" />
            <div className="dashboard-quick-grid">
              {quickActions.map((action) => (
                <Link key={action.href} to={action.href} className="dashboard-quick-action">
                  <span className="dashboard-quick-icon" style={{ color: action.color }}>
                    {action.icon}
                  </span>
                  <span className="dashboard-quick-copy">
                    <Text strong>{action.title}</Text>
                    <Text type="secondary">{action.description}</Text>
                  </span>
                  <ArrowRightOutlined className="dashboard-quick-arrow" />
                </Link>
              ))}
            </div>
          </Card>

          <Card className="dashboard-panel" variant="borderless" loading={todosQuery.isLoading}>
            <DashboardSectionHeader
              title="待办清单"
              subtitle={`${todoCount} 项待处理事项`}
              extra={<Link to="/tasks">查看全部</Link>}
            />
            {todosQuery.isError ? (
              <Alert
                type="error"
                showIcon
                title="待办事项加载失败"
                description={getErrorMessage(todosQuery.error)}
              />
            ) : (
              <div className="dashboard-list">
                {todos.length > 0 ? (
                  todos.map((item) => (
                    <div className="dashboard-list-item" key={`${item.title}-${item.due}`}>
                      <div className="dashboard-list-meta">
                        <div className="dashboard-list-title">
                          <Text>{item.title}</Text>
                          <Tag color={getPriorityColor(item.priority)}>
                            {getPriorityLabel(item.priority)}
                          </Tag>
                        </div>
                        <Text type="secondary">{formatTodoDue(item.due)}</Text>
                      </div>
                      <Button type="link" size="small" icon={<ArrowRightOutlined />}>
                        处理
                      </Button>
                    </div>
                  ))
                ) : (
                  <div className="dashboard-list-empty">暂无待办事项</div>
                )}
              </div>
            )}
          </Card>

          <Card
            className="dashboard-panel"
            variant="borderless"
            loading={activitiesQuery.isLoading}
          >
            <DashboardSectionHeader title="最近活动" subtitle="系统中的最新操作记录" />
            {activitiesQuery.isError ? (
              <Alert
                type="error"
                showIcon
                title="活动数据加载失败"
                description={getErrorMessage(activitiesQuery.error)}
              />
            ) : (
              <div className="dashboard-list dashboard-activity-list">
                {activities.length > 0 ? (
                  activities.map((item) => (
                    <div className="dashboard-list-item" key={`${item.actor}-${item.timeAgo}`}>
                      <Avatar className="dashboard-activity-avatar">
                        {item.initial || item.actor?.charAt(0) || '系'}
                      </Avatar>
                      <div className="dashboard-list-meta">
                        <Text>
                          <Text strong>{item.actor || '系统'}</Text> {item.description}
                        </Text>
                        <Text type="secondary">{item.timeAgo}</Text>
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="dashboard-list-empty">暂无活动记录</div>
                )}
              </div>
            )}
          </Card>
        </div>

        <aside className="dashboard-side-column">
          <Card className="dashboard-panel" variant="borderless" loading={statusQuery.isLoading}>
            <DashboardSectionHeader title="系统状态" subtitle="关键服务运行概览" />
            {statusQuery.isError ? (
              <Alert
                type="error"
                showIcon
                title="系统状态加载失败"
                description={getErrorMessage(statusQuery.error)}
              />
            ) : (
              <div className="dashboard-status-content">
                {statusData?.overallStatus && (
                  <Alert
                    type={getOverallStatusType(statusData.overallStatus)}
                    showIcon
                    title={`系统总体状态：${statusData.overallStatus}`}
                  />
                )}
                <div className="dashboard-status-list">
                  {systemComponents.map((component, index) => (
                    <div className="dashboard-status-row" key={`${component.name}-${index}`}>
                      <div className="dashboard-status-name">
                        <span
                          className={`dashboard-status-dot dashboard-status-dot-${getStatusTagColor(component.status)}`}
                        />
                        <Text>{component.name}</Text>
                        <Tag color={getStatusTagColor(component.status)}>{component.status}</Tag>
                      </div>
                      <div className="dashboard-status-rate">
                        <Text type="secondary">运行率 {formatRunRate(component.runRate)}</Text>
                        <Progress
                          percent={component.runRate}
                          showInfo={false}
                          size="small"
                          strokeColor={getStatusProgressColor(component.status)}
                        />
                      </div>
                    </div>
                  ))}
                </div>
                {systemComponents.length === 0 && !statusQuery.isLoading && (
                  <Alert type="info" title="暂无系统状态数据" showIcon />
                )}
              </div>
            )}
          </Card>

          <Card className="dashboard-panel" variant="borderless">
            <DashboardSectionHeader title="使用帮助" subtitle="快速找到支持资源" />
            <div className="dashboard-help-links">
              <Button type="link" icon={<BookOutlined />}>
                查看用户手册
              </Button>
              <Button type="link" icon={<BellOutlined />}>
                常见问题解答
              </Button>
              <Button type="link" icon={<GlobalOutlined />}>
                联系技术支持
              </Button>
            </div>
          </Card>

          <Card className="dashboard-panel" variant="borderless">
            <DashboardSectionHeader title="产品动态" subtitle="了解近期变化" />
            <div className="dashboard-updates">
              <Alert
                title="新功能上线"
                description="批量支付功能已上线，支持一键处理多个批次，提升工作效率。"
                type="info"
                icon={<TrophyOutlined />}
              />
              <Alert
                title="系统优化"
                description="支付处理速度提升50%，用户体验显著改善。"
                type="success"
                icon={<RiseOutlined />}
              />
            </div>
          </Card>
        </aside>
      </div>

      <NewUserGuide visible={showGuide} onClose={() => setShowGuide(false)} />
    </main>
  );
};

export default Dashboard;
