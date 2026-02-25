import React, { Suspense, useEffect } from 'react';
import { createBrowserRouter, Outlet, useLocation, Navigate } from 'react-router-dom';
import { ProtectedRoute } from './ProtectedRoute';
import { AppLayout } from '@components/Layout/AppLayout';
import Loading from '@components/Common/Loading';
import DynamicPageRenderer from './DynamicPageRenderer';
import { setCurrentPath } from '@services/api';
import { authCenterRoutes } from './authCenterRoutes';

const Login = React.lazy(() => import('@pages/auth/Login'));
const OAuthCallback = React.lazy(() => import('@pages/auth/OAuthCallback'));
const Dashboard = React.lazy(() => import('@pages/dashboard/Dashboard'));
const IntegrationConfigPage = React.lazy(() => import('@pages/system/IntegrationConfig'));
const OrgSyncPage = React.lazy(() => import('@pages/system/OrgSync'));
const UserBindingPage = React.lazy(() => import('@pages/admin/UserBinding'));
const ResourceManagerPage = React.lazy(() => import('@pages/admin/ResourceManager'));
const PayrollBatchesPage = React.lazy(() => import('@pages/payroll/Batches'));
const PayrollTemplatesPage = React.lazy(() => import('@pages/payroll/Templates'));
const PayrollCyclesPage = React.lazy(() => import('@pages/payroll/Cycles'));
const AppRegistryPage = React.lazy(() => import('@pages/admin/AppRegistry'));
const EmployeesList = React.lazy(() => import('@pages/employees/List'));
const EmployeeDetail = React.lazy(() => import('@pages/employees/Detail'));
const Batches = React.lazy(() => import('@pages/payments/Batches'));
const BatchDetail = React.lazy(() => import('@pages/payments/BatchDetail'));
const PayrollBatchLedgerPage = React.lazy(() => import('@pages/payroll/BatchLedger'));
const PayrollManagerReviewPage = React.lazy(() => import('@pages/payroll/ManagerReview'));
const PartTimeReadonlyPage = React.lazy(() => import('@pages/payroll/PartTimeReadonly'));
const ApprovalWorkflowsPage = React.lazy(() => import('@pages/approval/Workflows'));
const AuditLogsPage = React.lazy(() => import('@pages/admin/AuditLogs'));
const MonitorPage = React.lazy(() => import('@pages/admin/Monitor'));
const TaskSchedulesPage = React.lazy(() => import('@pages/admin/TaskSchedules'));
const Forbidden = React.lazy(() => import('@pages/misc/Forbidden'));
const NotFound = React.lazy(() => import('@pages/misc/NotFound'));
const ServerError = React.lazy(() => import('@pages/misc/ServerError'));

const withGuard = (el: React.ReactNode, roles?: string[]) => (
  <ProtectedRoute roles={roles}>{el}</ProtectedRoute>
);

// 路由变化追踪组件
const RouteTracker: React.FC = () => {
  const location = useLocation();
  useEffect(() => {
    setCurrentPath(location.pathname);
  }, [location.pathname]);
  return null;
};

const RootLayout: React.FC = () =>
  withGuard(
    <AppLayout>
      <RouteTracker />
      <Outlet />
    </AppLayout>,
  );

export const router = createBrowserRouter([
  { path: '/login', element: <Suspense fallback={<Loading />}><Login /></Suspense> },
  { path: '/oauth/callback/:platform', element: <Suspense fallback={<Loading />}><OAuthCallback /></Suspense> },
  {
    path: '/',
    element: <RootLayout />,
    children: [
      { index: true, element: withGuard(<Suspense fallback={<Loading />}><Dashboard /></Suspense>) },
      { path: 'system/integration', element: withGuard(<Suspense fallback={<Loading />}><IntegrationConfigPage /></Suspense>, ['ADMIN']) },
      { path: 'system/org-sync', element: withGuard(<Suspense fallback={<Loading />}><OrgSyncPage /></Suspense>, ['ADMIN']) },
      { path: 'admin/user-binding', element: withGuard(<Suspense fallback={<Loading />}><UserBindingPage /></Suspense>, ['ADMIN']) },
      // 授权中心 - 新架构（解耦的独立页面）
      // 旧路由重定向到新路由
      { path: 'admin/auth-center', element: <Navigate to="/admin/auth-center/users" replace /> },
      { path: 'admin/roles', element: <Navigate to="/admin/auth-center/roles" replace /> },
      { path: 'admin/role-auth', element: <Navigate to="/admin/auth-center/roles" replace /> },
      { path: 'admin/user-auth', element: <Navigate to="/admin/auth-center/users" replace /> },
      // 新授权中心路由
      ...authCenterRoutes.map(route => ({
        path: route.path,
        element: withGuard(route.element, route.meta?.roles || ['ADMIN']),
      })),
      // 资源管理（保留旧版，待迁移）
      { path: 'admin/resources', element: withGuard(<Suspense fallback={<Loading />}><ResourceManagerPage /></Suspense>, ['ADMIN']) },
      { path: 'admin/app-registry', element: withGuard(<Suspense fallback={<Loading />}><AppRegistryPage /></Suspense>, ['ADMIN']) },
      { path: 'admin/audit-logs', element: withGuard(<Suspense fallback={<Loading />}><AuditLogsPage /></Suspense>, ['ADMIN']) },
      { path: 'admin/monitor', element: withGuard(<Suspense fallback={<Loading />}><MonitorPage /></Suspense>, ['ADMIN']) },
      { path: 'admin/tasks', element: withGuard(<Suspense fallback={<Loading />}><TaskSchedulesPage /></Suspense>, ['ADMIN']) },
      { path: 'payroll/batches', element: withGuard(<Suspense fallback={<Loading />}><PayrollBatchesPage /></Suspense>) },
      { path: 'payroll/templates', element: withGuard(<Suspense fallback={<Loading />}><PayrollTemplatesPage /></Suspense>) },
      { path: 'payroll/cycles', element: withGuard(<Suspense fallback={<Loading />}><PayrollCyclesPage /></Suspense>) },
      { path: 'approval/workflows', element: withGuard(<Suspense fallback={<Loading />}><ApprovalWorkflowsPage /></Suspense>) },
      { path: 'employees', element: withGuard(<Suspense fallback={<Loading />}><EmployeesList /></Suspense>) },
      { path: 'employees/:id', element: withGuard(<Suspense fallback={<Loading />}><EmployeeDetail /></Suspense>) },
      { path: 'payments/batches', element: withGuard(<Suspense fallback={<Loading />}><Batches /></Suspense>) },
      { path: 'payments/batches/:batchNo', element: withGuard(<Suspense fallback={<Loading />}><BatchDetail /></Suspense>) },
      { path: 'payroll/batches/:batchId/ledger', element: withGuard(<Suspense fallback={<Loading />}><PayrollBatchLedgerPage /></Suspense>) },
      { path: 'payroll/batches/:batchId/manager-review', element: withGuard(<Suspense fallback={<Loading />}><PayrollManagerReviewPage /></Suspense>) },
      { path: 'payroll/pt-readonly', element: withGuard(<Suspense fallback={<Loading />}><PartTimeReadonlyPage /></Suspense>) },
      // 动态路由渲染：当后端下发了新 VIEW/MENU 资源且前端未静态注册时，由此兜底加载对应组件
      { path: '*', element: withGuard(<Suspense fallback={<Loading />}><DynamicPageRenderer /></Suspense>) },
    ],
  },
  { path: '/403', element: <Suspense fallback={<Loading />}><Forbidden /></Suspense> },
  { path: '/404', element: <Suspense fallback={<Loading />}><NotFound /></Suspense> },
  { path: '/500', element: <Suspense fallback={<Loading />}><ServerError /></Suspense> },
  // Catch-all route for 404 - must be last
  { path: '*', element: <Suspense fallback={<Loading />}><NotFound /></Suspense> },
], {
  future: {
    v7_startTransition: true,
  },
});
