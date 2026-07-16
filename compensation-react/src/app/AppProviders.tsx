import React, { useEffect } from 'react';
import { App as AntdApp, ConfigProvider, theme as antdTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { Provider as ReduxProvider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { store } from '@services/stores/authSlice';
import { appTheme } from './theme';
import { router } from '@routes/index';
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

const ThemedConfig: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const mode = useUIStore((s) => s.theme);
  const algorithm = mode === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm;

  useEffect(() => {
    document.documentElement.dataset.theme = mode;
  }, [mode]);

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{ token: appTheme.tokens, components: appTheme.components, algorithm }}
    >
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
            <RouterProvider router={router} />
          </AntdApp>
        </QueryClientProvider>
      </ReduxProvider>
    </ThemedConfig>
  );
};
