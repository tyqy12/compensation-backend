import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider } from 'react-redux';
import { App as AntdApp } from 'antd';
import { vi, beforeEach, describe, it, expect } from 'vitest';
import { store } from '../../services/stores/authSlice';
import IntegrationConfigPage from './IntegrationConfig';

// Mock the new integration query hooks
const mockUseIntegrationListQuery = vi.fn();
const mockUseIntegrationConfigQuery = vi.fn();
const mockUseSaveIntegrationConfigMutation = vi.fn();
const mockUseDisableIntegrationMutation = vi.fn();
const mockUseTestIntegrationMutation = vi.fn();

vi.mock('@services/queries/integration', () => ({
  useIntegrationListQuery: () => mockUseIntegrationListQuery(),
  useIntegrationConfigQuery: () => mockUseIntegrationConfigQuery(),
  useSaveIntegrationConfigMutation: () => mockUseSaveIntegrationConfigMutation(),
  useDisableIntegrationMutation: () => mockUseDisableIntegrationMutation(),
  useTestIntegrationMutation: () => mockUseTestIntegrationMutation(),
}));

const TestWrapper = ({ children }: { children: React.ReactNode }) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AntdApp>{children}</AntdApp>
        </BrowserRouter>
      </QueryClientProvider>
    </Provider>
  );
};

// Mock data
const mockIntegrationList = [
  {
    platformType: 'wechat',
    platformName: '企业微信',
    enabled: true,
    configured: true,
    connectionStatus: 'connected',
    lastModified: '2024-01-15T10:30:00',
  },
  {
    platformType: 'dingtalk',
    platformName: '钉钉',
    enabled: false,
    configured: false,
    connectionStatus: 'disconnected',
    lastModified: null,
  },
  {
    platformType: 'feishu',
    platformName: '飞书',
    enabled: false,
    configured: false,
    connectionStatus: 'disconnected',
    lastModified: null,
  },
  {
    platformType: 'alipay',
    platformName: '支付宝',
    enabled: true,
    configured: true,
    connectionStatus: 'connected',
    lastModified: '2024-01-14T15:20:00',
  },
  {
    platformType: 'sms',
    platformName: '短信服务',
    enabled: true,
    configured: true,
    connectionStatus: 'connected',
    lastModified: '2024-01-13T09:15:00',
  },
  {
    platformType: 'email',
    platformName: '邮件服务',
    enabled: false,
    configured: false,
    connectionStatus: 'disconnected',
    lastModified: null,
  },
  {
    platformType: 'encryption',
    platformName: '加密配置',
    enabled: true,
    configured: true,
    connectionStatus: 'connected',
    lastModified: '2024-01-12T14:00:00',
  },
];

describe('IntegrationConfig New API', () => {
  const mockMutateAsync = vi.fn();
  const mockTestMutateAsync = vi.fn();
  const mockDisableMutateAsync = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    // Default mock implementations
    mockUseIntegrationListQuery.mockReturnValue({
      data: mockIntegrationList,
      isLoading: false,
      refetch: vi.fn(),
    });

    mockUseIntegrationConfigQuery.mockReturnValue({
      data: null,
      isLoading: false,
    });

    mockUseSaveIntegrationConfigMutation.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
    });

    mockUseDisableIntegrationMutation.mockReturnValue({
      mutateAsync: mockDisableMutateAsync,
      isPending: false,
    });

    mockUseTestIntegrationMutation.mockReturnValue({
      mutateAsync: mockTestMutateAsync,
      isPending: false,
    });
  });

  it('should render the integration config page', () => {
    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    // 验证页面标题
    expect(screen.getByText('集成配置管理')).toBeInTheDocument();
    expect(screen.getByText('管理第三方平台集成配置')).toBeInTheDocument();
  });

  it('should show all platform cards', () => {
    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    // 验证所有平台都显示
    expect(screen.getByText('企业微信')).toBeInTheDocument();
    expect(screen.getByText('钉钉')).toBeInTheDocument();
    expect(screen.getByText('飞书')).toBeInTheDocument();
    expect(screen.getByText('支付宝')).toBeInTheDocument();
    expect(screen.getByText('短信服务')).toBeInTheDocument();
    expect(screen.getByText('邮件服务')).toBeInTheDocument();
    expect(screen.getByText('加密配置')).toBeInTheDocument();
  });

  it('should show correct connection status', () => {
    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    // 验证连接状态显示
    expect(screen.getAllByText('已连接')).toHaveLength(4); // wechat, alipay, sms, encryption
    expect(screen.getAllByText('未连接')).toHaveLength(3); // dingtalk, feishu, email
  });

  it('should show correct enabled status', () => {
    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    // 验证启用状态显示
    expect(screen.getAllByText('已启用')).toHaveLength(4); // wechat, alipay, sms, encryption
    expect(screen.getAllByText('未启用')).toHaveLength(3); // dingtalk, feishu, email
  });

  it('should show correct configuration status', () => {
    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    // 验证配置状态显示
    expect(screen.getAllByText('已配置')).toHaveLength(4); // wechat, alipay, sms, encryption
    expect(screen.getAllByText('未配置')).toHaveLength(3); // dingtalk, feishu, email
  });

  it('should have action buttons for each platform', () => {
    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    // 验证操作按钮
    expect(screen.getAllByText('配置')).toHaveLength(7);
    expect(screen.getAllByText('测试')).toHaveLength(7);
    expect(screen.getAllByText('禁用')).toHaveLength(7);
  });

  it('should handle loading state', () => {
    mockUseIntegrationListQuery.mockReturnValue({
      data: undefined,
      isLoading: true,
      refetch: vi.fn(),
    });

    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    // 在加载状态下，平台卡片不应该显示
    expect(screen.queryByText('企业微信')).not.toBeInTheDocument();
  });

  it('should show information alert', () => {
    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    expect(screen.getByText('集成配置说明')).toBeInTheDocument();
    expect(screen.getByText(/配置各种第三方平台的接入信息/)).toBeInTheDocument();
  });

  it('should show security tips', () => {
    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    expect(screen.getByText('安全提示')).toBeInTheDocument();
    expect(screen.getByText('所有敏感配置信息在数据库中均已加密存储')).toBeInTheDocument();
  });

  it('should have refresh button', () => {
    const mockRefetch = vi.fn();
    mockUseIntegrationListQuery.mockReturnValue({
      data: mockIntegrationList,
      isLoading: false,
      refetch: mockRefetch,
    });

    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    const refreshButton = screen.getByText('刷新');
    expect(refreshButton).toBeInTheDocument();

    fireEvent.click(refreshButton);
    expect(mockRefetch).toHaveBeenCalled();
  });

  it('should open config modal when config button is clicked', async () => {
    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    // 点击第一个配置按钮
    const configButtons = screen.getAllByText('配置');
    fireEvent.click(configButtons[0]);

    // 验证模态框打开
    await waitFor(() => {
      expect(screen.getByText('配置 企业微信')).toBeInTheDocument();
    });
  });

  it('should handle test connection', async () => {
    mockTestMutateAsync.mockResolvedValue(true);

    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    // 点击第一个测试按钮
    const testButtons = screen.getAllByText('测试');
    fireEvent.click(testButtons[0]);

    await waitFor(() => {
      expect(mockTestMutateAsync).toHaveBeenCalledWith('wechat');
    });
  });

  it('should show platform descriptions', () => {
    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    expect(screen.getByText('企业内部通讯和消息推送')).toBeInTheDocument();
    expect(screen.getByText('企业协作和组织管理')).toBeInTheDocument();
    expect(screen.getByText('团队协作和文档管理')).toBeInTheDocument();
    expect(screen.getByText('支付接口和转账功能')).toBeInTheDocument();
    expect(screen.getByText('短信验证和通知推送')).toBeInTheDocument();
    expect(screen.getByText('邮件发送和通知服务')).toBeInTheDocument();
    expect(screen.getByText('数据加密和安全配置')).toBeInTheDocument();
  });
});
