import React, { useMemo, useState } from 'react';
import { Layout, Menu, Button, Space, Avatar, Dropdown, Badge, Tooltip, Typography } from 'antd';
import { Link, useLocation } from 'react-router-dom';
import { useLogoutMutation } from '@services/queries/auth';
import { useSelector } from 'react-redux';
import type { RootState } from '@services/stores/authSlice';
import { appName } from '@app/theme';
import {
  DashboardOutlined,
  TeamOutlined,
  UserSwitchOutlined,
  SettingOutlined,
  SyncOutlined,
  ProfileOutlined,
  UserOutlined,
  BellOutlined,
  MoonOutlined,
  SunOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SearchOutlined,
  QuestionCircleOutlined,
  HomeOutlined,
  WalletOutlined,
  GlobalOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { useWecomRegister } from '@hooks/useWecomRegister';
import { useUIStore } from '@services/stores/uiStore';
import { AppBreadcrumb } from '@components/Navigation/Breadcrumb';
import { BackTop } from '@components/Navigation/BackTop';
import { useMeResourcesQuery } from '@services/queries/rbac';
import { buildResourceTree } from '@utils/permissions';
import { useMenuRefresh } from '@hooks/useMenuRefresh';
import Loading from '@components/Common/Loading';

const { Title } = Typography;

const { Header, Content, Sider } = Layout;

type Props = { children?: React.ReactNode };

export const AppLayout: React.FC<Props> = ({ children }) => {
  const { pathname } = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const logout = useLogoutMutation();
  const username = useSelector((s: RootState) => s.auth.user?.username);
  useWecomRegister();
  const theme = useUIStore((s) => s.theme);
  const toggleTheme = useUIStore((s) => s.toggleTheme);

  // 监听其他标签页的菜单刷新信号
  useMenuRefresh();
  
  // 从后端资源构建导航树（MENU/VIEW），按 orderNum 排序
  const { data: meRes, isLoading: permLoading } = useMeResourcesQuery();
  const userRoles = useSelector((s: RootState) => s.auth.user?.roles ?? s.auth.roles ?? []);
  const menuItems = useMemo(() => {
    const tree = buildResourceTree(meRes?.resources || [], { userRoles, respectRoles: true });
    const toMenuItems = (nodes: any[]): any[] =>
      nodes.map((n) => ({
        key: n.path || n.code || String(n.id),
        icon: n.path === '/' ? <DashboardOutlined /> : undefined,
        label: n.path ? <Link to={n.path}>{n.name}</Link> : n.name,
        children: n.children && n.children.length ? toMenuItems(n.children) : undefined,
      }));
    const dyn = toMenuItems(tree);

    // 备用方案：若动态菜单为空（后端尚未配置资源），提供基础入口（仅 ADMIN 角色）
    const isAdmin = (userRoles || []).some((r: string) => /ADMIN/i.test(r));
    if (dyn.length === 0 && isAdmin) {
      return [
        { key: '/admin/resources', icon: <SettingOutlined />, label: <Link to="/admin/resources">资源与导航管理</Link> },
        { key: '/admin/roles', icon: <SafetyCertificateOutlined />, label: <Link to="/admin/roles">角色管理</Link> },
      ];
    }
    return dyn;
  }, [meRes?.resources, userRoles]);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{
        color: '#fff',
        fontWeight: 600,
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '0 16px',
        borderBottom: '1px solid #f0f0f0'
      }}>
        {/* 左侧：菜单折叠 + Logo/应用名（点击回首页） */}
        <Space>
          <Button
            type="text"
            onClick={() => setCollapsed((v) => !v)}
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            style={{ color: '#fff' }}
          />
          <Link to="/" style={{ color: '#fff', textDecoration: 'none' }}>
            <Space>
              <HomeOutlined />
              <Title level={4} style={{ color: '#fff', margin: 0 }}>
                {appName}
              </Title>
            </Space>
          </Link>
        </Space>

        {/* 右侧实用工具栏：按照官方推荐顺序排列 */}
        <Space size="large">
          {/* 全局搜索 */}
          <Tooltip title="全局搜索">
            <Button type="text" icon={<SearchOutlined />} style={{ color: '#fff' }} />
          </Tooltip>

          {/* 帮助 */}
          <Tooltip title="帮助中心">
            <Button type="text" icon={<QuestionCircleOutlined />} style={{ color: '#fff' }} />
          </Tooltip>

          {/* 主题切换 */}
          <Tooltip title={theme === 'dark' ? '切换浅色主题' : '切换深色主题'}>
            <Button
              type="text"
              icon={theme === 'dark' ? <SunOutlined /> : <MoonOutlined />}
              onClick={toggleTheme}
              style={{ color: '#fff' }}
            />
          </Tooltip>

          {/* 通知中心 */}
          <Tooltip title="通知中心">
            <Badge count={3} size="small">
              <Button type="text" icon={<BellOutlined />} style={{ color: '#fff' }} />
            </Badge>
          </Tooltip>

          {/* 用户头像和菜单 */}
          <Dropdown
            menu={{
              items: [
                { key: 'profile', icon: <UserOutlined />, label: <Link to="/employees/me">个人中心</Link> },
                { key: 'settings', icon: <SettingOutlined />, label: '个人设置' },
                { type: 'divider' },
                { key: 'logout', danger: true, label: '退出登录', onClick: () => logout.mutate() },
              ],
            }}
            placement="bottomRight"
            trigger={['click']}
          >
            <Space style={{ cursor: 'pointer', color: '#fff' }}>
              <Avatar size={32} icon={<UserOutlined />} />
              <span>{username || '未登录'}</span>
            </Space>
          </Dropdown>
        </Space>
      </Header>
      <Layout>
        <Sider
          width={220}
          theme="light"
          collapsible
          collapsed={collapsed}
          onCollapse={(v) => setCollapsed(v)}
          style={{
            boxShadow: 'rgba(0,0,0,0.06) 2px 0 6px 0',
            borderRight: '1px solid #f0f0f0'
          }}
          trigger={null} // 隐藏内置的折叠按钮，使用Header中的
        >
          {permLoading ? (
            <div style={{ padding: 16 }}>
              <Loading />
            </div>
          ) : (
            <Menu
            mode="inline"
            theme="light"
            selectedKeys={[pathname]}
            items={menuItems}
            style={{ border: 'none', height: '100%' }}
          />
          )}
        </Sider>
        <Layout style={{ backgroundColor: '#f5f5f5' }}>
          <Content style={{
            padding: '0 24px 24px',
            backgroundColor: '#f5f5f5',
            minHeight: 'calc(100vh - 64px)'
          }}>
            {/* 面包屑导航区域 */}
            <div style={{ padding: '16px 0 0' }}>
              <AppBreadcrumb />
            </div>

            {/* 页面内容区域 */}
            <div style={{ backgroundColor: '#fff', borderRadius: '6px', minHeight: '600px' }}>
              {children}
            </div>
          </Content>
        </Layout>
      </Layout>

      {/* 返回顶部 */}
      <BackTop />
    </Layout>
  );
};

export default AppLayout;
