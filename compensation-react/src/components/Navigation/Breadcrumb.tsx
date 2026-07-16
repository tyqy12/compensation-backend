import React, { useMemo } from 'react';
import { Breadcrumb } from 'antd';
import { Link, useLocation } from 'react-router-dom';
import { HomeOutlined } from '@ant-design/icons';

// 路由路径到面包屑的映射
const breadcrumbNameMap: Record<string, string> = {
  '/': '工作台',
  '/employees': '员工管理',
  '/employees/:id': '员工详情',
  '/payments': '薪酬支付',
  '/payments/batches': '支付批次',
  '/payments/batches/:batchNo': '批次详情',
  '/admin': '系统管理',
  '/admin/user-binding': '用户绑定',
  '/system': '系统配置',
  '/system/integration': '集成配置',
  '/system/org-sync': '组织同步',
};

// 路由路径到分组的映射
const routeGroupMap: Record<string, string> = {
  '/employees': '业务管理',
  '/payments': '业务管理',
  '/admin/user-binding': '系统管理',
  '/system/integration': '系统管理',
  '/system/org-sync': '系统管理',
};

interface AppBreadcrumbProps {
  style?: React.CSSProperties;
}

export const AppBreadcrumb: React.FC<AppBreadcrumbProps> = ({ style }) => {
  const location = useLocation();

  const breadcrumbItems = useMemo(() => {
    const { pathname } = location;

    // 首页不显示面包屑
    if (pathname === '/') {
      return [];
    }

    const pathSnippets = pathname.split('/').filter((i) => i);

    // 少于2层时不显示面包屑（一级页面）
    if (pathSnippets.length < 2) {
      return [];
    }

    const items = [];

    // 始终包含首页
    items.push({
      key: 'home',
      title: (
        <Link to="/">
          <HomeOutlined /> 首页
        </Link>
      ),
    });

    // 构建路径
    let currentPath = '';

    for (let i = 0; i < pathSnippets.length; i++) {
      currentPath += `/${pathSnippets[i]}`;

      // 检查是否需要添加分组
      if (i === 0 && routeGroupMap[currentPath]) {
        items.push({
          key: `group-${currentPath}`,
          title: routeGroupMap[currentPath],
        });
      }

      // 动态路由参数处理
      let breadcrumbPath = currentPath;
      if (
        pathSnippets[i] &&
        /^[a-zA-Z0-9-_]+$/.test(pathSnippets[i]) &&
        !breadcrumbNameMap[currentPath] &&
        i > 0
      ) {
        // 可能是动态参数，尝试匹配模式
        const parentPath = currentPath.substring(0, currentPath.lastIndexOf('/'));
        const paramPattern = `${parentPath}/:${
          pathSnippets[i - 1] === 'employees'
            ? 'id'
            : pathSnippets[i - 1] === 'batches'
              ? 'batchNo'
              : 'id'
        }`;
        if (breadcrumbNameMap[paramPattern]) {
          breadcrumbPath = paramPattern;
        }
      }

      const breadcrumbName = breadcrumbNameMap[breadcrumbPath];

      if (breadcrumbName) {
        const isLast = i === pathSnippets.length - 1;

        items.push({
          key: currentPath,
          title: isLast ? breadcrumbName : <Link to={currentPath}>{breadcrumbName}</Link>,
        });
      }
    }

    return items;
  }, [location]);

  // 少于三层时不显示面包屑（按照官方建议）
  if (breadcrumbItems.length < 3) {
    return null;
  }

  return <Breadcrumb className="app-breadcrumb" style={style} items={breadcrumbItems} />;
};

export default AppBreadcrumb;
