import React, { useMemo } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Card, Row, Col, Statistic, Progress, Tag, Space, Typography, Spin } from 'antd';
import {
  CloudServerOutlined,
  DatabaseOutlined,
  ApiOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import {
  useMonitorSummaryQuery,
  formatBytes,
  formatUptime,
  getHeapUsageColor,
  getDbStatus,
  getRedisStatus,
} from '@services/queries/monitor';

const { Title, Text } = Typography;

// ==================== 工具函数 ====================
const formatNumber = (value: number): string => {
  if (value >= 1000000) return (value / 1000000).toFixed(1) + 'M';
  if (value >= 1000) return (value / 1000).toFixed(1) + 'K';
  return value.toString();
};

// ==================== 主组件 ====================
const Monitor: React.FC = () => {
  const { data: summary, isLoading, refetch } = useMonitorSummaryQuery({ refetchInterval: 10000 });

  const app = summary?.app;
  const jvm = summary?.jvm;
  const db = summary?.datasource;
  const redis = summary?.redis;

  // JVM 堆使用率
  const heapUsage = useMemo(() => {
    if (!jvm) return 0;
    return Math.round((jvm.heapUsed / jvm.heapMax) * 100);
  }, [jvm]);

  // 环境标签颜色
  const profileColor = useMemo(() => {
    if (!app?.profiles) return 'default';
    const profile = app.profiles[0]?.toLowerCase();
    if (profile === 'prod' || profile === 'production') return 'red';
    if (profile === 'staging') return 'orange';
    return 'blue';
  }, [app?.profiles]);

  if (isLoading) {
    return (
      <PageContainer title="系统监控">
        <div style={{ textAlign: 'center', padding: 100 }}>
          <Spin size="large" />
          <div style={{ marginTop: 16 }}>加载监控数据中...</div>
        </div>
      </PageContainer>
    );
  }

  return (
    <PageContainer
      title="系统监控"
      extra={[
        <Tag key="refresh" icon={<SyncOutlined spin={isLoading} />} onClick={() => refetch()}>
          自动刷新
        </Tag>,
      ]}
    >
      {/* 应用信息 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="运行环境"
              value={app?.profiles?.[0] || '未知'}
              prefix={<CloudServerOutlined />}
              styles={{
                content: {
                  color:
                    profileColor === 'red'
                      ? '#cf1322'
                      : profileColor === 'orange'
                        ? '#d46b08'
                        : '#096dd9',
                },
              }}
            />
            <div style={{ marginTop: 8 }}>
              <Space>
                <ClockCircleOutlined />
                <Text type="secondary">运行时间: {formatUptime(app?.uptimeSeconds || 0)}</Text>
              </Space>
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="JVM 堆内存"
              value={formatBytes(jvm?.heapUsed || 0)}
              suffix={`/ ${formatBytes(jvm?.heapMax || 0)}`}
              prefix={<ApiOutlined />}
            />
            <Progress
              percent={heapUsage}
              size="small"
              strokeColor={getHeapUsageColor(jvm?.heapUsed || 0, jvm?.heapMax || 1)}
              style={{ marginTop: 8 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="数据库连接"
              value={db?.ping === 'OK' ? '正常' : '异常'}
              prefix={<DatabaseOutlined />}
              styles={{ content: { color: db?.ping === 'OK' ? '#52c41a' : '#ff4d4f' } }}
            />
            <div style={{ marginTop: 8 }}>
              <Space>
                {db?.ping === 'OK' ? (
                  <CheckCircleOutlined style={{ color: '#52c41a' }} />
                ) : (
                  <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
                )}
                <Text type="secondary">{db?.error || '连接正常'}</Text>
              </Space>
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Redis 连接"
              value={redis?.ping === 'PONG' ? '正常' : '异常'}
              prefix={<ApiOutlined />}
              styles={{ content: { color: redis?.ping === 'PONG' ? '#52c41a' : '#ff4d4f' } }}
            />
            <div style={{ marginTop: 8 }}>
              <Space>
                {redis?.ping === 'PONG' ? (
                  <CheckCircleOutlined style={{ color: '#52c41a' }} />
                ) : (
                  <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
                )}
                <Text type="secondary">{redis?.error || '连接正常'}</Text>
              </Space>
            </div>
          </Card>
        </Col>
      </Row>

      {/* 详细信息 */}
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="JVM 详细信息" size="small">
            <div className="monitor-detail-list">
              {[
                { label: '初始堆内存', value: formatBytes(jvm?.heapInit || 0) },
                { label: '已用堆内存', value: formatBytes(jvm?.heapUsed || 0) },
                { label: '已提交堆内存', value: formatBytes(jvm?.heapCommitted || 0) },
                { label: '最大堆内存', value: formatBytes(jvm?.heapMax || 0) },
                { label: '活跃线程数', value: formatNumber(jvm?.threadCount || 0) },
              ].map((item) => (
                <div className="monitor-detail-row" key={item.label}>
                  <Text>{item.label}</Text>
                  <Text strong>{item.value}</Text>
                </div>
              ))}
            </div>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title="组件状态" size="small">
            <div className="monitor-detail-list">
              {[
                {
                  label: '应用服务',
                  status: 'success',
                  message: '运行中',
                  icon: <CheckCircleOutlined />,
                },
                {
                  label: '数据库连接',
                  status: db?.ping === 'OK' ? 'success' : 'error',
                  message: db?.ping === 'OK' ? '连接正常' : db?.error || '连接失败',
                  icon:
                    db?.ping === 'OK' ? (
                      <CheckCircleOutlined style={{ color: '#52c41a' }} />
                    ) : (
                      <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
                    ),
                },
                {
                  label: 'Redis 缓存',
                  status: redis?.ping === 'PONG' ? 'success' : 'error',
                  message: redis?.ping === 'PONG' ? '连接正常' : redis?.error || '连接失败',
                  icon:
                    redis?.ping === 'PONG' ? (
                      <CheckCircleOutlined style={{ color: '#52c41a' }} />
                    ) : (
                      <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
                    ),
                },
              ].map((item) => (
                <div className="monitor-detail-row" key={item.label}>
                  <Space>
                    {item.icon}
                    <Text>{item.label}</Text>
                  </Space>
                  <Tag color={item.status === 'success' ? 'success' : 'error'}>{item.message}</Tag>
                </div>
              ))}
            </div>
          </Card>
        </Col>
      </Row>

      {/* 系统信息 */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col span={24}>
          <Card title="系统信息" size="small">
            <Row gutter={16}>
              <Col xs={24} sm={8}>
                <Text type="secondary">服务名称</Text>
                <div>
                  <Text strong>薪酬助手系统</Text>
                </div>
              </Col>
              <Col xs={24} sm={8}>
                <Text type="secondary">当前时间</Text>
                <div>
                  <Text strong>{app?.now || '—'}</Text>
                </div>
              </Col>
              <Col xs={24} sm={8}>
                <Text type="secondary">运行环境</Text>
                <div>
                  <Space>
                    {app?.profiles?.map((profile) => (
                      <Tag key={profile} color={profileColor}>
                        {profile.toUpperCase()}
                      </Tag>
                    ))}
                  </Space>
                </div>
              </Col>
            </Row>
          </Card>
        </Col>
      </Row>
    </PageContainer>
  );
};

export default Monitor;
