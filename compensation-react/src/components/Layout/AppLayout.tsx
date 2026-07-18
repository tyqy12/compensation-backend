import React, { useEffect, useMemo, useState } from 'react';
import { Layout, Menu, Button, Space, Avatar, Dropdown, Badge, Tooltip, Typography } from 'antd';
import { Link, useLocation } from 'react-router-dom';
import { useLogoutMutation } from '@services/queries/auth';
import { useSelector } from 'react-redux';
import type { RootState } from '@services/stores/authSlice';
import { appName } from '@app/theme';
import {
  AppstoreOutlined,
  AuditOutlined,
  BarChartOutlined,
  BellOutlined,
  CheckCircleOutlined,
  CloseOutlined,
  ControlOutlined,
  DashboardOutlined,
  FileSearchOutlined,
  GlobalOutlined,
  MenuFoldOutlined,
  MenuOutlined,
  MenuUnfoldOutlined,
  MoneyCollectOutlined,
  MoonOutlined,
  QuestionCircleOutlined,
  SafetyCertificateOutlined,
  ScheduleOutlined,
  SearchOutlined,
  SettingOutlined,
  SunOutlined,
  SyncOutlined,
  TeamOutlined,
  UploadOutlined,
  UserOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { useWecomRegister } from '@hooks/useWecomRegister';
import { useUIStore } from '@services/stores/uiStore';
import { AppBreadcrumb } from '@components/Navigation/Breadcrumb';
import { BackTop } from '@components/Navigation/BackTop';
import { useMeResourcesQuery } from '@services/queries/rbac';
import { buildResourceTree } from '@utils/permissions';
import { useMenuRefresh } from '@hooks/useMenuRefresh';
import Loading from '@components/Common/Loading';

const { Header, Content, Sider } = Layout;
const { Text } = Typography;

type Props = { children?: React.ReactNode };

const MOBILE_BREAKPOINT = '(max-width: 991px)';

const useIsMobile = () => {
  const getMatches = () => {
    if (typeof window === 'undefined') return false;
    return window.matchMedia(MOBILE_BREAKPOINT).matches || window.innerWidth < 992;
  };
  const [isMobile, setIsMobile] = useState(getMatches);

  useEffect(() => {
    const mediaQuery = window.matchMedia(MOBILE_BREAKPOINT);
    const update = () => setIsMobile(mediaQuery.matches || window.innerWidth < 992);
    update();
    mediaQuery.addEventListener?.('change', update);
    window.addEventListener('resize', update);

    return () => {
      mediaQuery.removeEventListener?.('change', update);
      window.removeEventListener('resize', update);
    };
  }, []);

  return isMobile;
};

const MENU_ICONS: Record<string, React.ReactNode> = {
  appstore: <AppstoreOutlined />,
  audit: <AuditOutlined />,
  barchart: <BarChartOutlined />,
  checkcircle: <CheckCircleOutlined />,
  control: <ControlOutlined />,
  dashboard: <DashboardOutlined />,
  filesearch: <FileSearchOutlined />,
  global: <GlobalOutlined />,
  menu: <MenuOutlined />,
  moneycollect: <MoneyCollectOutlined />,
  safetycertificate: <SafetyCertificateOutlined />,
  schedule: <ScheduleOutlined />,
  setting: <SettingOutlined />,
  sync: <SyncOutlined />,
  team: <TeamOutlined />,
  upload: <UploadOutlined />,
  user: <UserOutlined />,
  wallet: <WalletOutlined />,
};

const getMenuIcon = (icon?: string | null) => {
  const key = icon
    ?.trim()
    .replace(/[\s_-]+/g, '')
    .replace(/(outlined|filled|twotone)$/i, '')
    .toLowerCase();
  return (key && MENU_ICONS[key]) || <AppstoreOutlined />;
};

const getSelectedMenuKey = (pathname: string, pathToKey: Map<string, string>) => {
  const exactKey = pathToKey.get(pathname);
  if (exactKey) return exactKey;

  return [...pathToKey.entries()]
    .filter(([path]) => path !== '/' && pathname.startsWith(`${path}/`))
    .sort(([left], [right]) => right.length - left.length)[0]?.[1];
};

export const AppLayout: React.FC<Props> = ({ children }) => {
  const { pathname } = useLocation();
  const logout = useLogoutMutation();
  const username = useSelector((state: RootState) => state.auth.user?.username);
  const userRoles = useSelector(
    (state: RootState) => state.auth.user?.roles ?? state.auth.roles ?? [],
  );
  const theme = useUIStore((state) => state.theme);
  const collapsed = useUIStore((state) => state.collapsed);
  const setCollapsed = useUIStore((state) => state.setCollapsed);
  const toggleCollapsed = useUIStore((state) => state.toggleCollapsed);
  const toggleTheme = useUIStore((state) => state.toggleTheme);
  const isMobile = useIsMobile();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  const navToggleLabel = isMobile
    ? mobileNavOpen
      ? '关闭侧边导航'
      : '打开侧边导航'
    : collapsed
      ? '展开侧边导航'
      : '折叠侧边导航';
  const siderCollapsed = isMobile ? !mobileNavOpen : collapsed;

  useEffect(() => {
    if (!isMobile) {
      setMobileNavOpen(false);
    }
  }, [isMobile]);

  useEffect(() => {
    if (isMobile) {
      setMobileNavOpen(false);
    }
  }, [isMobile, pathname]);

  useEffect(() => {
    if (!isMobile || !mobileNavOpen) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMobileNavOpen(false);
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isMobile, mobileNavOpen]);

  const handleNavToggle = () => {
    if (isMobile) {
      setMobileNavOpen((open) => !open);
      return;
    }
    toggleCollapsed();
  };

  const handleSiderCollapse = (nextCollapsed: boolean) => {
    if (isMobile) {
      setMobileNavOpen(!nextCollapsed);
      return;
    }
    setCollapsed(nextCollapsed);
  };

  useWecomRegister();
  useMenuRefresh();

  const { data: meRes, isLoading: permLoading } = useMeResourcesQuery();
  const { menuItems, selectedMenuKey } = useMemo(() => {
    const tree = buildResourceTree(meRes?.resources || [], { userRoles, respectRoles: true });
    const pathToKey = new Map<string, string>();
    const toMenuItems = (nodes: any[]): any[] =>
      nodes.map((node) => {
        const key = String(node.id);
        if (node.path) pathToKey.set(node.path, key);

        return {
          key,
          icon: getMenuIcon(node.icon || (node.path === '/' ? 'dashboard' : undefined)),
          title: node.name,
          label: node.path ? <Link to={node.path}>{node.name}</Link> : node.name,
          children: node.children?.length ? toMenuItems(node.children) : undefined,
        };
      });

    const dynamicItems = toMenuItems(tree);
    const isAdmin = userRoles.some((role: string) => /ADMIN/i.test(role));
    if (dynamicItems.length === 0 && isAdmin) {
      const fallbackItems = [
        {
          key: '/admin/resources-v2',
          icon: <SettingOutlined />,
          label: <Link to="/admin/resources-v2">菜单管理</Link>,
        },
        {
          key: '/admin/roles',
          icon: <SafetyCertificateOutlined />,
          label: <Link to="/admin/roles">角色管理</Link>,
        },
      ];
      fallbackItems.forEach((item) => pathToKey.set(item.key, item.key));
      return { menuItems: fallbackItems, selectedMenuKey: getSelectedMenuKey(pathname, pathToKey) };
    }

    return {
      menuItems: dynamicItems,
      selectedMenuKey: getSelectedMenuKey(pathname, pathToKey),
    };
  }, [meRes?.resources, pathname, userRoles]);

  return (
    <Layout className="app-shell">
      <Header className="app-header">
        <div className="app-header-left">
          <Tooltip title={navToggleLabel}>
            <Button
              className="app-header-icon-button"
              type="text"
              aria-label={navToggleLabel}
              aria-pressed={isMobile ? mobileNavOpen : collapsed}
              onClick={handleNavToggle}
              aria-expanded={!siderCollapsed}
              icon={
                isMobile ? (
                  mobileNavOpen ? (
                    <CloseOutlined />
                  ) : (
                    <MenuOutlined />
                  )
                ) : collapsed ? (
                  <MenuUnfoldOutlined />
                ) : (
                  <MenuFoldOutlined />
                )
              }
            />
          </Tooltip>
          <Link className="app-brand" to="/">
            <span className="app-brand-mark">
              <WalletOutlined />
            </span>
            <span className="app-brand-copy">
              <strong>{appName}</strong>
              <Text type="secondary">OPERATIONS CONSOLE</Text>
            </span>
          </Link>
        </div>

        <div className="app-header-right">
          <Tooltip title="全局搜索">
            <Button
              className="app-header-icon-button app-header-optional"
              type="text"
              icon={<SearchOutlined />}
              aria-label="全局搜索"
            />
          </Tooltip>
          <Tooltip title="帮助中心">
            <Button
              className="app-header-icon-button app-header-optional"
              type="text"
              icon={<QuestionCircleOutlined />}
              aria-label="帮助中心"
            />
          </Tooltip>
          <Tooltip title={theme === 'dark' ? '切换浅色主题' : '切换深色主题'}>
            <Button
              className="app-header-icon-button"
              type="text"
              icon={theme === 'dark' ? <SunOutlined /> : <MoonOutlined />}
              aria-label={theme === 'dark' ? '切换浅色主题' : '切换深色主题'}
              onClick={toggleTheme}
            />
          </Tooltip>
          <Tooltip title="通知中心">
            <Badge dot>
              <Button
                className="app-header-icon-button"
                type="text"
                icon={<BellOutlined />}
                aria-label="通知中心"
              />
            </Badge>
          </Tooltip>
          <Dropdown
            placement="bottomRight"
            trigger={['click']}
            menu={{
              items: [
                {
                  key: 'profile',
                  icon: <UserOutlined />,
                  label: <Link to="/employees/me">个人中心</Link>,
                },
                { key: 'settings', icon: <SettingOutlined />, label: '个人设置' },
                { type: 'divider' },
                { key: 'logout', danger: true, label: '退出登录', onClick: () => logout.mutate() },
              ],
            }}
          >
            <button className="app-user-menu" type="button" aria-label="打开用户菜单">
              <Avatar size={32} icon={<UserOutlined />} />
              <span>{username || '未登录'}</span>
            </button>
          </Dropdown>
        </div>
      </Header>

      {isMobile && mobileNavOpen && (
        <button
          className="app-mobile-nav-backdrop"
          type="button"
          aria-label="关闭侧边导航"
          onClick={() => setMobileNavOpen(false)}
        />
      )}

      <Layout className="app-body">
        <Sider
          className="app-sider"
          width={232}
          theme={theme === 'dark' ? 'dark' : 'light'}
          collapsible
          collapsedWidth={isMobile ? 0 : 80}
          collapsed={siderCollapsed}
          onCollapse={handleSiderCollapse}
          trigger={null}
        >
          <div className="app-sider-inner">
            <div className="app-nav-label">工作空间</div>
            {permLoading ? (
              <Loading />
            ) : (
              <Menu
                mode="inline"
                theme={theme === 'dark' ? 'dark' : 'light'}
                aria-label="主导航"
                selectedKeys={selectedMenuKey ? [selectedMenuKey] : []}
                items={menuItems}
                onClick={() => {
                  if (isMobile) setMobileNavOpen(false);
                }}
              />
            )}
          </div>
        </Sider>

        <Layout className="app-main">
          <Content className="app-content">
            <div className="app-content-body">
              <div className="app-global-breadcrumb" aria-label="页面路径">
                <AppBreadcrumb />
              </div>
              <div className="app-page-content">{children}</div>
            </div>
          </Content>
        </Layout>
      </Layout>

      <BackTop />
    </Layout>
  );
};

export default AppLayout;
