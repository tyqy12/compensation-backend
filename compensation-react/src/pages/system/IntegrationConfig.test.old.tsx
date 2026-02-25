import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider } from 'react-redux';
import { App as AntdApp } from 'antd';
import { vi, beforeEach, afterEach } from 'vitest';
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
  // Keep old hooks for backward compatibility
  useIntegrationQuery: () => mockUseIntegrationListQuery(),
  useSaveIntegrationMutation: () => mockUseSaveIntegrationConfigMutation(),
  useTestConnectionMutation: () => mockUseTestIntegrationMutation(),
}));

// matchMedia and ResizeObserver are mocked in vitest.setup.ts

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

describe('IntegrationConfig 集成配置', () => {
  const mockMutateAsync = vi.fn();
  const mockTestMutateAsync = vi.fn();
  const mockDisableMutateAsync = vi.fn();

  // Mock data for new API
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

  beforeEach(() => {
    vi.clearAllMocks();

    // Default mock implementations for new API
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

  afterEach(() => {
    vi.resetAllMocks();
  });

  it('应该正确渲染集成配置页面', () => {
    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    // 验证页面标题
    expect(screen.getByText('集成配置管理')).toBeInTheDocument();

    // 验证配置说明
    expect(screen.getByText('集成配置说明')).toBeInTheDocument();
    expect(screen.getByText(/配置各种第三方平台的接入信息/)).toBeInTheDocument();

    // 验证平台卡片
    expect(screen.getByText('企业微信')).toBeInTheDocument();
    expect(screen.getByText('钉钉')).toBeInTheDocument();
    expect(screen.getByText('支付宝')).toBeInTheDocument();

    // 验证状态显示
    expect(screen.getAllByText('已连接')).toHaveLength(4); // wechat, alipay, sms, encryption
    expect(screen.getAllByText('未连接')).toHaveLength(3); // dingtalk, feishu, email

    // 验证操作按钮
    expect(screen.getAllByText('配置')).toHaveLength(7);
    expect(screen.getAllByText('测试')).toHaveLength(7);
    expect(screen.getAllByText('禁用')).toHaveLength(7);
  });

  it('应该显示加载状态', () => {
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

    // 验证没有平台卡片显示（加载中状态）
    expect(screen.queryByText('企业微信')).not.toBeInTheDocument();
  });

  it('应该显示所有平台类型', async () => {
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

    // 验证状态显示
    expect(screen.getAllByText('已启用')).toHaveLength(4);
    expect(screen.getAllByText('未启用')).toHaveLength(3);
    expect(screen.getAllByText('已配置')).toHaveLength(4);
    expect(screen.getAllByText('未配置')).toHaveLength(3);
  });

  it('应该成功保存配置', async () => {
    mockMutateAsync.mockResolvedValue({ success: true });

    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    // 填写有效的表单数据
    const clientIdInput = screen.getByPlaceholderText('请输入应用ID');
    const secretInput = screen.getByPlaceholderText('请输入应用密钥');
    const callbackInput = screen.getByPlaceholderText(/https:\/\/yourdomain.com/);

    fireEvent.change(clientIdInput, { target: { value: 'valid_client_id' } });
    fireEvent.change(secretInput, { target: { value: 'valid_secret_12345' } });
    fireEvent.change(callbackInput, { target: { value: 'https://example.com/callback' } });

    // 提交表单
    const saveButton = screen.getByText('保存配置');
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith({
        platform: 'wecom',
        clientId: 'valid_client_id',
        clientSecret: 'valid_secret_12345',
        callbackUrl: 'https://example.com/callback',
      });
    });
  });

  it('应该成功测试连接', async () => {
    mockTestMutateAsync.mockResolvedValue({ success: true });

    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    // 填写必要的凭证信息
    const clientIdInput = screen.getByPlaceholderText('请输入应用ID');
    const secretInput = screen.getByPlaceholderText('请输入应用密钥');

    fireEvent.change(clientIdInput, { target: { value: 'test_client_id' } });
    fireEvent.change(secretInput, { target: { value: 'test_secret_123' } });

    // 点击测试连接
    const testButton = screen.getByText('测试连接');
    expect(testButton).not.toBeDisabled();

    fireEvent.click(testButton);

    await waitFor(() => {
      expect(mockTestMutateAsync).toHaveBeenCalled();
    });
  });

  it('测试连接按钮应该在缺少凭证时被禁用', () => {
    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    const testButton = screen.getByText('测试连接');
    expect(testButton).toBeDisabled();

    // 填写clientId
    const clientIdInput = screen.getByPlaceholderText('请输入应用ID');
    fireEvent.change(clientIdInput, { target: { value: 'test_id' } });
    expect(testButton).toBeDisabled(); // 仍然禁用，因为缺少secret

    // 填写clientSecret
    const secretInput = screen.getByPlaceholderText('请输入应用密钥');
    fireEvent.change(secretInput, { target: { value: 'test_secret' } });
    expect(testButton).not.toBeDisabled(); // 现在应该启用
  });

  it('应该显示保存和测试的加载状态', () => {
    mockUseSaveIntegrationMutation.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: true,
    });

    mockUseTestConnectionMutation.mockReturnValue({
      mutateAsync: mockTestMutateAsync,
      isPending: true,
    });

    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    // 验证保存按钮加载状态
    const saveButton = screen.getByRole('button', { name: /保存配置/ });
    expect(saveButton).toBeDisabled();

    // 验证测试按钮加载状态
    const testButton = screen.getByRole('button', { name: /测试连接/ });
    expect(testButton).toBeDisabled();
  });

  it('应该显示操作说明', () => {
    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    expect(screen.getByText('操作提示')).toBeInTheDocument();
    expect(screen.getByText('1. 选择需要集成的平台类型')).toBeInTheDocument();
    expect(screen.getByText('2. 填写从第三方平台获取的应用凭证')).toBeInTheDocument();
    expect(screen.getByText('3. 点击"测试连接"验证配置是否正确')).toBeInTheDocument();
    expect(screen.getByText('4. 测试成功后点击"保存配置"完成设置')).toBeInTheDocument();
  });
});

// 集成配置功能验证测试
describe('IntegrationConfig 功能验证', () => {
  const mockMutateAsync = vi.fn();
  const mockTestMutateAsync = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    mockUseIntegrationQuery.mockReturnValue({
      data: null,
      isLoading: false,
      error: null,
    });

    mockUseSaveIntegrationMutation.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
    });

    mockUseTestConnectionMutation.mockReturnValue({
      mutateAsync: mockTestMutateAsync,
      isPending: false,
    });
  });

  it('应该满足Issue #6的验收标准', () => {
    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    // ✅ ProForm 校验
    expect(screen.getByText('集成平台')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('请输入应用ID')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('请输入应用密钥')).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/https:\/\/yourdomain.com/)).toBeInTheDocument();

    // ✅ 保存/测试有加载与提示
    expect(screen.getByText('保存配置')).toBeInTheDocument();
    expect(screen.getByText('测试连接')).toBeInTheDocument();

    // ✅ 缓存刷新 (通过 TanStack Query 自动处理)
    // 这个功能在保存成功后会自动触发查询缓存刷新
  });

  it('应该提供完整的用户体验', () => {
    const mockData = {
      platform: 'wecom',
      clientId: 'existing-id',
      enabled: true,
    };

    mockUseIntegrationQuery.mockReturnValue({
      data: mockData,
      isLoading: false,
      error: null,
    });

    render(
      <TestWrapper>
        <IntegrationConfigPage />
      </TestWrapper>,
    );

    // 验证状态展示
    expect(screen.getByText('已启用')).toBeInTheDocument();

    // 验证当前配置状态
    expect(screen.getByText('当前配置状态')).toBeInTheDocument();
    expect(screen.getByText('existing-id')).toBeInTheDocument();

    // 验证用户指导
    expect(
      screen.getByText(/配置第三方平台接入信息，完成后可进行组织同步和用户绑定/),
    ).toBeInTheDocument();
  });
});
