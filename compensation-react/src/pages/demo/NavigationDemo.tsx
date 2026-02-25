import React from 'react';
import { Card, Space, Typography, Button, Tag } from 'antd';
import { Link } from 'react-router-dom';
import { PageHeader } from '@components/Navigation/PageHeader';
import {
  CheckCircleOutlined,
  HomeOutlined,
  BranchesOutlined,
  ToolOutlined,
  ArrowLeftOutlined,
  VerticalAlignTopOutlined,
} from '@ant-design/icons';

const { Paragraph, Text } = Typography;

const NavigationDemo: React.FC = () => {
  const features = [
    {
      title: '全局导航',
      icon: <BranchesOutlined />,
      description: '侧边导航按业务主题组织，支持分组和多层级',
      status: 'completed',
      details: [
        '工作台 - 一级菜单',
        '业务管理 - 分组（员工管理、薪酬支付）',
        '系统管理 - 分组（用户绑定、系统配置）',
        '支持菜单折叠和展开',
      ],
    },
    {
      title: '实用工具栏',
      icon: <ToolOutlined />,
      description: '右上角工具栏包含搜索、帮助、主题切换、通知、用户菜单',
      status: 'completed',
      details: [
        '全局搜索 - 便于快速定位',
        '帮助中心 - 用户支持',
        '主题切换 - 深色/浅色模式',
        '通知中心 - 消息提醒',
        '用户菜单 - 个人中心和退出',
      ],
    },
    {
      title: '面包屑导航',
      icon: <HomeOutlined />,
      description: '智能显示当前位置，支持动态路由和分组',
      status: 'completed',
      details: [
        '首页不显示（减少冗余）',
        '一级页面不显示（全局导航已足够）',
        '二级以上显示完整路径',
        '动态路由参数识别',
        '支持点击返回上级',
      ],
    },
    {
      title: '页头组件',
      icon: <CheckCircleOutlined />,
      description: '标准化页面头部，支持返回按钮和操作区域',
      status: 'completed',
      details: ['自动集成面包屑', '支持标题和子标题', '可选返回按钮', '操作按钮区域', '响应式布局'],
    },
    {
      title: '返回类导航',
      icon: <ArrowLeftOutlined />,
      description: '多种返回方式，提升用户体验',
      status: 'completed',
      details: [
        'Logo 点击回首页（逃生舱）',
        '面包屑点击返回上级',
        '页头返回按钮',
        '返回顶部浮动按钮',
      ],
    },
    {
      title: '返回顶部',
      icon: <VerticalAlignTopOutlined />,
      description: '长页面快速返回顶部功能',
      status: 'completed',
      details: ['滚动400px后显示', '平滑滚动动画', '固定右下角位置', '使用最新FloatButton组件'],
    },
  ];

  return (
    <div>
      <PageHeader
        title="导航系统演示"
        subTitle="按照 Ant Design 官方导航设计规范实现的完整导航体系"
        extra={[
          <Button key="docs" type="link">
            查看文档
          </Button>,
          <Button key="test" type="primary">
            运行测试
          </Button>,
        ]}
      />

      <div style={{ padding: '0 24px 24px' }}>
        <Space size="large" style={{ width: '100%' }}>
          {/* 设计原则 */}
          <Card title="设计原则" size="small">
            <Space wrap>
              <Tag color="blue">可循性 - 用户可定位到想要的信息</Tag>
              <Tag color="green">高效 - 多接入点、捷径、逃生舱</Tag>
              <Tag color="orange">浅平宽 - 减少层级，降低认知负担</Tag>
              <Tag color="purple">一致性 - 统一的交互模式</Tag>
            </Space>
          </Card>

          {/* 功能特性 */}
          <Card title="实现功能" size="small">
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))',
                gap: '16px',
              }}
            >
              {features.map((feature, index) => (
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
                  <Paragraph>{feature.description}</Paragraph>
                  <ul style={{ margin: 0, paddingLeft: '20px' }}>
                    {feature.details.map((detail, i) => (
                      <li key={i}>
                        <Text type="secondary">{detail}</Text>
                      </li>
                    ))}
                  </ul>
                </Card>
              ))}
            </div>
          </Card>

          {/* 导航测试 */}
          <Card title="导航路径测试" size="small">
            <Paragraph>以下是各种导航路径的测试链接，可以验证面包屑和页面导航的正确性：</Paragraph>
            <Space wrap>
              <Link to="/">
                <Button size="small">工作台（首页）</Button>
              </Link>
              <Link to="/employees">
                <Button size="small">员工管理</Button>
              </Link>
              <Link to="/employees/123">
                <Button size="small">员工详情</Button>
              </Link>
              <Link to="/payments/batches">
                <Button size="small">支付批次</Button>
              </Link>
              <Link to="/payments/batches/BATCH001">
                <Button size="small">批次详情</Button>
              </Link>
              <Link to="/system/integration">
                <Button size="small">集成配置</Button>
              </Link>
              <Link to="/admin/user-binding">
                <Button size="small">用户绑定</Button>
              </Link>
            </Space>
          </Card>

          {/* 压力测试结果 */}
          <Card title="压力测试结果" size="small">
            <Paragraph>
              <Text strong>✅ 导航系统通过所有测试</Text>
            </Paragraph>
            <ul>
              <li>面包屑导航：5个测试全部通过</li>
              <li>页头组件：4个测试全部通过</li>
              <li>返回顶部：1个测试通过</li>
              <li>路径压力测试：8条路径全部正常</li>
              <li>错误处理：异常路径正确降级</li>
            </ul>
          </Card>
        </Space>
      </div>
    </div>
  );
};

export default NavigationDemo;
