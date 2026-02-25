import React, { useEffect } from 'react';
import { App as AntdApp, ConfigProvider, theme as antdTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { Provider as ReduxProvider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider, useNavigate, useLocation } from 'react-router-dom';
import { store, logout } from '@services/stores/authSlice';
import { appTheme } from './theme';
import { router } from '@routes/index';
import { setApiErrorHandler } from '@services/errors';
import { useUIStore } from '@services/stores/uiStore';
import { suppressDevelopmentWarnings } from '@utils/suppressWarnings';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 30_000,
    },
  },
});

// 401 错误处理器组件（在 Router 上下文中，可使用 useNavigate）
const AuthErrorHandler: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { message: m } = AntdApp.useApp();

  useEffect(() => {
    const handleApiError = ({ status, message: msg }: { status?: number; message: string }) => {
      if (status === 401) {
        // 清除认证状态
        store.dispatch(logout());
        // 显示提示
        m.warning('登录已过期，请重新登录');
        // 跳转到登录页（如果当前不在登录页）
        if (location.pathname !== '/login') {
          navigate('/login', { replace: true, state: { from: location } });
        }
      } else if (status === 403) {
        m.warning('没有权限执行该操作');
      } else if (!status || status >= 500) {
        m.error(msg);
      } else {
        m.error(msg);
      }
    };

    setApiErrorHandler(handleApiError);
  }, [navigate, location.pathname, m]);

  return null;
};

const ThemedConfig: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const mode = useUIStore((s) => s.theme);
  const algorithm = mode === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm;
  return (
    <ConfigProvider locale={zhCN} theme={{ token: appTheme.tokens, algorithm }}>
      {children}
    </ConfigProvider>
  );
};

export const AppProviders: React.FC = () => {
  // 初始化时抑制开发环境警告
  useEffect(() => {
    suppressDevelopmentWarnings();
  }, []);

  return (
    <ThemedConfig>
      <ReduxProvider store={store}>
        <QueryClientProvider client={queryClient}>
          <AntdApp>
            <RouterProvider router={router}>
              <AuthErrorHandler />
            </RouterProvider>
          </AntdApp>
        </QueryClientProvider>
      </ReduxProvider>
    </ThemedConfig>
  );
};
