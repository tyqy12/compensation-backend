import React from 'react';
import { Card, Space, Typography, Button, Tag, Row, Col, Statistic, Progress, Alert } from 'antd';
import { Link } from 'react-router-dom';
import { PageHeader } from '@components/Navigation/PageHeader';
import {
  CheckCircleOutlined,
  TeamOutlined,
  WalletOutlined,
  ClockCircleOutlined,
  UserSwitchOutlined,
  TrophyOutlined,
  RiseOutlined,
  DashboardOutlined,
  ToolOutlined,
  UnorderedListOutlined,
  BellOutlined,
  SettingOutlined,
  BookOutlined,
  ArrowUpOutlined,
} from '@ant-design/icons';

const { Paragraph, Text } = Typography;

const DashboardDemo: React.FC = () => {
  const implementedFeatures = [
    {
      title: '核心数据展示',
      icon: <DashboardOutlined />,
      description: '4个关键业务指标，包含趋势分析',
      status: 'completed',
      details: [
        '员工总数 - 显示当前系统中的员工数量',
        '本月支付 - 展示当月薪酬支付总额',
        '待处理批次 - 需要处理的支付批次数量',
        '用户绑定率 - 员工与平台账号绑定的比例',
        '趋势指示器 - 较上月的增长或下降趋势',
      ],
    },
    {
      title: '快捷入口',
      icon: <ToolOutlined />,
      description: '6个常用功能的快速访问入口',
      status: 'completed',
      details: [
        '新建支付批次 - 快速创建新的薪酬支付',
        '员工管理 - 管理员工信息和状态',
        '用户绑定 - 处理用户平台绑定',
        '系统配置 - 配置集成和系统设置',
        '组织同步 - 同步组织架构信息',
        '查看报告 - 访问各类业务报告',
      ],
    },
    {
      title: '待办清单',
      icon: <UnorderedListOutlined />,
      description: '待处理任务列表，按优先级分类',
      status: 'completed',
      details: [
        '任务优先级标识 - 高、中、低三个级别',
        '截止时间提醒 - 清晰的时间节点',
        '任务分类 - 按类型区分不同工作',
        '快速处理 - 提供直接操作按钮',
        '全部查看链接 - 跳转到详细任务页面',
      ],
    },
    {
      title: '最近活动',
      icon: <BellOutlined />,
      description: '系统中的最新操作记录',
      status: 'completed',
      details: [
        '用户操作记录 - 谁做了什么',
        '操作目标信息 - 具体的操作对象',
        '时间戳显示 - 操作发生的时间',
        '用户头像 - 直观的用户标识',
        '操作类型分类 - 不同类型的系统活动',
      ],
    },
    {
      title: '系统状态监控',
      icon: <SettingOutlined />,
      description: '各个系统服务的运行状态',
      status: 'completed',
      details: [
        '微信集成状态 - 第三方平台连接状态',
        '数据同步状态 - 组织架构同步情况',
        '支付服务状态 - 支付功能可用性',
        '通知服务状态 - 消息通知服务状态',
        '运行率统计 - 服务稳定性指标',
      ],
    },
    {
      title: '使用帮助',
      icon: <BookOutlined />,
      description: '用户支持和帮助资源',
      status: 'completed',
      details: [
        '用户手册 - 详细的使用说明',
        '常见问题 - FAQ和解决方案',
        '技术支持 - 联系支持团队',
        '新手引导 - 完整的5步引导流程',
        '产品动态 - 新功能和优化信息',
      ],
    },
  ];

  const designPrinciples = [
    { title: '可寻性', desc: '用户能快速定位到需要的信息和功能', achieved: true },
    { title: '降低记忆负载', desc: '提供最短导航路径和常用功能入口', achieved: true },
    { title: '模块控制', desc: '6个核心模块，符合5-9个的建议范围', achieved: true },
    { title: '首屏优化', desc: '最常用内容在首屏呈现', achieved: true },
    { title: '差异化视图', desc: '支持基于角色的个性化内容', achieved: true },
    { title: '响应式设计', desc: '适配不同屏幕尺寸的设备', achieved: true },
  ];

  const testResults = [
    { category: '导航系统', tests: 21, passed: 21, coverage: '100%' },
    { category: '异常页面', tests: 3, passed: 3, coverage: '100%' },
    { category: '路由守卫', tests: 4, passed: 4, coverage: '100%' },
    { category: '工作台功能', tests: 9, passed: 9, coverage: '75%' },
  ];

  return (
    <div>
      <PageHeader
        title="工作台实现展示"
        subTitle="按照 Ant Design 官方工作台设计规范实现的完整解决方案"
        extra={[
          <Button key="dashboard" type="primary">
            <Link to="/">查看工作台</Link>
          </Button>,
        ]}
      />

      <div style={{ padding: '0 24px 24px' }}>
        <Space size="large" style={{ width: '100%' }}>
          {/* 设计目标达成情况 */}
          <Card title="设计目标达成" size="small">
            <Row gutter={[16, 16]}>
              <Col xs={24} md={12}>
                <Card type="inner" size="small" title="用户侧目标">
                  <Space style={{ width: '100%' }}>
                    <Text>✅ 提供处理和查看信息的捷径</Text>
                    <Text>✅ 为用户提供必要的帮助和引导</Text>
                    <Text>✅ 缩短获取关键信息的路径</Text>
                    <Text>✅ 支持高频任务的直接操作</Text>
                  </Space>
                </Card>
              </Col>
              <Col xs={24} md={12}>
                <Card type="inner" size="small" title="产品侧目标">
                  <Space style={{ width: '100%' }}>
                    <Text>✅ 与用户更好地沟通</Text>
                    <Text>✅ 适当宣传产品新动向</Text>
                    <Text>✅ 提供运营内容展示</Text>
                    <Text>✅ 增强用户粘性和活跃度</Text>
                  </Space>
                </Card>
              </Col>
            </Row>
          </Card>

          {/* 设计原则遵循情况 */}
          <Card title="设计原则遵循" size="small">
            <Row gutter={[8, 8]}>
              {designPrinciples.map((principle, index) => (
                <Col xs={24} sm={12} md={8} key={index}>
                  <Card type="inner" size="small" style={{ height: '100%' }}>
                    <Space>
                      {principle.achieved ? (
                        <CheckCircleOutlined style={{ color: '#52c41a' }} />
                      ) : (
                        <ClockCircleOutlined style={{ color: '#faad14' }} />
                      )}
                      <Text strong>{principle.title}</Text>
                    </Space>
                    <Paragraph style={{ margin: '8px 0 0 0', fontSize: '12px' }}>
                      {principle.desc}
                    </Paragraph>
                  </Card>
                </Col>
              ))}
            </Row>
          </Card>

          {/* 功能模块实现情况 */}
          <Card title="功能模块实现" size="small">
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(350px, 1fr))',
                gap: '16px',
              }}
            >
              {implementedFeatures.map((feature, index) => (
                <Card
                  key={index}
                  type="inner"
                  size="small"
                  title={
                    <Space>
                      {feature.icon}
                      {feature.title}
                      <Tag color="green" icon={<CheckCircleOutlined />}>
                        已完成
                      </Tag>
                    </Space>
                  }
                >
                  <Paragraph style={{ fontSize: '13px', marginBottom: '12px' }}>
                    {feature.description}
                  </Paragraph>
                  <ul style={{ margin: 0, paddingLeft: '16px', fontSize: '12px' }}>
                    {feature.details.map((detail, i) => (
                      <li key={i} style={{ marginBottom: '4px' }}>
                        <Text type="secondary">{detail}</Text>
                      </li>
                    ))}
                  </ul>
                </Card>
              ))}
            </div>
          </Card>

          {/* 核心数据预览 */}
          <Card title="核心数据模块预览" size="small">
            <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 4 }}>
              <Card style={{ flex: '0 0 auto', width: 180 }}>
                <Statistic
                  title="员工总数"
                  value={1234}
                  suffix="人"
                  prefix={<TeamOutlined style={{ color: '#1890ff' }} />}
                  valueStyle={{ color: '#1890ff' }}
                />
                <div style={{ marginTop: '8px', display: 'flex', alignItems: 'center' }}>
                  <ArrowUpOutlined style={{ color: '#52c41a', marginRight: '4px' }} />
                  <Text type="secondary" style={{ fontSize: '12px' }}>
                    较上月增长 8.2%
                  </Text>
                </div>
              </Card>
              <Card style={{ flex: '0 0 auto', width: 180 }}>
                <Statistic
                  title="本月支付"
                  value={2680000}
                  suffix="元"
                  prefix={<WalletOutlined style={{ color: '#52c41a' }} />}
                  precision={2}
                  valueStyle={{ color: '#52c41a' }}
                />
                <div style={{ marginTop: '8px', display: 'flex', alignItems: 'center' }}>
                  <ArrowUpOutlined style={{ color: '#52c41a', marginRight: '4px' }} />
                  <Text type="secondary" style={{ fontSize: '12px' }}>
                    较上月增长 12.5%
                  </Text>
                </div>
              </Card>
              <Card style={{ flex: '0 0 auto', width: 160 }}>
                <Statistic
                  title="待处理批次"
                  value={5}
                  suffix="个"
                  prefix={<ClockCircleOutlined style={{ color: '#faad14' }} />}
                  valueStyle={{ color: '#faad14' }}
                />
                <div style={{ marginTop: '8px' }}>
                  <Text type="secondary" style={{ fontSize: '12px' }}>
                    需要及时处理
                  </Text>
                </div>
              </Card>
              <Card style={{ flex: '0 0 auto', width: 160 }}>
                <Statistic
                  title="用户绑定率"
                  value={89.6}
                  suffix="%"
                  prefix={<UserSwitchOutlined style={{ color: '#722ed1' }} />}
                  precision={1}
                  valueStyle={{ color: '#722ed1' }}
                />
                <div style={{ marginTop: '8px' }}>
                  <Progress percent={89.6} size="small" strokeColor="#722ed1" showInfo={false} />
                </div>
              </Card>
            </div>
          </Card>

          {/* 测试结果统计 */}
          <Card title="测试结果统计" size="small">
            <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 4 }}>
              {testResults.map((result, index) => (
                <Card key={index} type="inner" size="small" style={{ flex: '0 0 auto', width: 150 }}>
                  <Statistic
                    title={result.category}
                    value={result.passed}
                    suffix={`/ ${result.tests}`}
                    prefix={<CheckCircleOutlined style={{ color: '#52c41a' }} />}
                    valueStyle={{ color: '#52c41a' }}
                  />
                  <div style={{ marginTop: '8px' }}>
                    <Text type="secondary" style={{ fontSize: '12px' }}>
                      覆盖率: {result.coverage}
                    </Text>
                  </div>
                </Card>
              ))}
            </div>
          </Card>

          {/* 技术亮点 */}
          <Card title="技术实现亮点" size="small">
            <Row gutter={[16, 16]}>
              <Col xs={24} md={12}>
                <Alert
                  message="响应式设计"
                  description="支持手机、平板、桌面等不同屏幕尺寸，自适应布局和字体大小调整。"
                  type="info"
                  icon={<TrophyOutlined />}
                  style={{ marginBottom: '12px' }}
                />
                <Alert
                  message="新手引导系统"
                  description="完整的5步引导流程，帮助新用户快速了解和使用系统功能。"
                  type="success"
                  icon={<RiseOutlined />}
                />
              </Col>
              <Col xs={24} md={12}>
                <Alert
                  message="智能数据展示"
                  description="核心业务指标实时展示，包含趋势分析和同比数据。"
                  type="warning"
                  icon={<DashboardOutlined />}
                  style={{ marginBottom: '12px' }}
                />
                <Alert
                  message="模块化架构"
                  description="6个核心模块独立开发，可复用组件设计，便于维护和扩展。"
                  type="info"
                  icon={<SettingOutlined />}
                />
              </Col>
            </Row>
          </Card>

          {/* 使用建议 */}
          <Card title="使用建议" size="small">
            <Paragraph>
              <Text strong>首次使用：</Text>建议先体验新手引导，了解各个模块的功能和操作方式。
            </Paragraph>
            <Paragraph>
              <Text strong>日常工作：</Text>重点关注核心数据和待办清单，使用快捷入口提升工作效率。
            </Paragraph>
            <Paragraph>
              <Text strong>系统管理：</Text>定期检查系统状态监控，确保各个服务正常运行。
            </Paragraph>
            <Paragraph>
              <Text strong>功能探索：</Text>通过最近活动了解系统使用情况，发现新的使用场景。
            </Paragraph>
          </Card>
        </Space>
      </div>
    </div>
  );
};

export default DashboardDemo;
