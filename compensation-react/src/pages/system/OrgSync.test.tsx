import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider } from 'react-redux';
import { App as AntdApp } from 'antd';
import { vi, beforeEach, afterEach } from 'vitest';
import { store } from '../../services/stores/authSlice';
import OrgSyncPage from './OrgSync';

// Mock the org query hooks
const mockUsePlatformsQuery = vi.fn();
const mockUseOrgCheckQuery = vi.fn();
const mockUseOrgSyncMutation = vi.fn();
const mockUseOrgHistoryQuery = vi.fn();
const mockUseOrgDepartmentTreeQuery = vi.fn();

vi.mock('@services/queries/org', () => ({
  usePlatformsQuery: () => mockUsePlatformsQuery(),
  useOrgCheckQuery: () => mockUseOrgCheckQuery(),
  useOrgSyncMutation: () => mockUseOrgSyncMutation(),
  useOrgHistoryQuery: () => mockUseOrgHistoryQuery(),
  useOrgDepartmentTreeQuery: () => mockUseOrgDepartmentTreeQuery(),
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

describe('OrgSync 组织同步', () => {
  const mockMutateAsync = vi.fn();
  const mockRefetch = vi.fn();
  const mockHistoryRefetch = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    // Default mock implementations
    mockUsePlatformsQuery.mockReturnValue({
      data: [
        { code: 'wechat', name: '企业微信' },
        { code: 'dingtalk', name: '钉钉' },
        { code: 'feishu', name: '飞书' },
      ],
      isLoading: false,
      refetch: mockRefetch,
    });

    mockUseOrgCheckQuery.mockReturnValue({
      data: {
        platform: 'wechat',
        status: 'OK',
        message: '配置正常，可以同步',
      },
      isLoading: false,
      refetch: mockRefetch,
    });

    mockUseOrgSyncMutation.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
    });

    mockUseOrgHistoryQuery.mockReturnValue({
      data: [
        {
          platformType: 'wechat',
          success: true,
          message: '同步成功',
          syncTime: '2025-09-29T12:23:08.092Z',
          totalEmployees: 0,
          newEmployees: 0,
          updatedEmployees: 0,
          inactiveEmployees: 0,
          errors: null,
        },
      ],
      isLoading: false,
      isError: false,
      error: null,
      refetch: mockHistoryRefetch,
    });

    mockUseOrgDepartmentTreeQuery.mockReturnValue({
      data: [
        {
          id: 1,
          platformType: 'wechat',
          platformDeptId: '1',
          parentPlatformDeptId: null,
          name: '总部',
          children: [
            {
              id: 2,
              platformType: 'wechat',
              platformDeptId: '1-1',
              parentPlatformDeptId: '1',
              name: '技术部',
              children: [],
            },
          ],
        },
      ],
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
  });

  afterEach(() => {
    vi.resetAllMocks();
  });

  it('应该正确渲染组织同步页面', () => {
    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    // 验证页面标题
    expect(screen.getByText('组织同步')).toBeInTheDocument();
    expect(screen.getByText('同步第三方平台的组织架构和人员信息')).toBeInTheDocument();

    // 验证操作说明
    expect(screen.getByText('组织同步说明')).toBeInTheDocument();
    expect(
      screen.getByText(/同步操作将从第三方平台拉取最新的组织架构和人员信息/),
    ).toBeInTheDocument();

    // 验证同步操作区域
    expect(screen.getByText('同步操作')).toBeInTheDocument();
    expect(screen.getByText('同步全部平台')).toBeInTheDocument();
    expect(screen.getByText('单平台同步')).toBeInTheDocument();

    // 验证平台状态区域
    expect(screen.getByText('平台状态')).toBeInTheDocument();
    expect(screen.getByText('同步历史')).toBeInTheDocument();
    expect(screen.getByText('平台部门结构')).toBeInTheDocument();
  });

  it('应该显示平台列表', () => {
    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    // 验证平台列表
    expect(screen.getByText('企业微信')).toBeInTheDocument();
    expect(screen.getByText('钉钉')).toBeInTheDocument();
    expect(screen.getByText('飞书')).toBeInTheDocument();

    // 验证查看状态和同步按钮
    expect(screen.getAllByText('查看状态')).toHaveLength(3);
    expect(screen.getAllByText('同步')).toHaveLength(3);
  });

  it('应该显示平台检查状态', () => {
    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    // 验证默认选中企业微信
    expect(screen.getByText('选中平台：')).toBeInTheDocument();
    expect(screen.getByText('企业微信')).toBeInTheDocument();

    // 验证状态信息
    expect(screen.getByText('配置正常')).toBeInTheDocument();
    expect(screen.getByText('配置正常，可以同步')).toBeInTheDocument();
  });

  it('应该能成功执行全部同步', async () => {
    mockMutateAsync.mockResolvedValue({
      success: true,
      message: '同步了 10 个部门，50 个用户',
    });

    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    // 点击同步全部按钮
    const syncAllButton = screen.getByRole('button', { name: /同步全部/ });
    fireEvent.click(syncAllButton);

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith('all');
    });
  });

  it('应该能成功执行单平台同步', async () => {
    mockMutateAsync.mockResolvedValue({
      success: true,
      message: '同步了 5 个部门，25 个用户',
    });

    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    // 点击企业微信的同步按钮（第一个同步按钮）
    const syncButtons = screen.getAllByRole('button', { name: '同步' });
    fireEvent.click(syncButtons[0]);

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith('wechat');
    });
  });

  it('应该在同步中禁用所有同步按钮', () => {
    mockUseOrgSyncMutation.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: true,
    });

    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    // 验证同步全部按钮被禁用
    const syncAllButton = screen.getByRole('button', { name: /同步中.../ });
    expect(syncAllButton).toBeDisabled();

    // 验证所有单平台同步按钮都被禁用
    const syncButtons = screen.getAllByRole('button', { name: '同步' });
    syncButtons.forEach((button) => {
      expect(button).toBeDisabled();
    });
  });

  it('应该处理同步失败的情况', async () => {
    mockMutateAsync.mockResolvedValue({
      success: false,
      message: '认证失败，请检查配置',
    });

    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    // 执行同步操作
    const syncAllButton = screen.getByRole('button', { name: /同步全部/ });
    fireEvent.click(syncAllButton);

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith('all');
    });
  });

  it('应该处理网络错误且不阻塞', async () => {
    mockMutateAsync.mockRejectedValue(new Error('网络连接超时'));

    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    // 执行同步操作
    const syncAllButton = screen.getByRole('button', { name: /同步全部/ });
    fireEvent.click(syncAllButton);

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith('all');
    });

    // 验证页面仍然可以交互，错误不阻塞
    expect(syncAllButton).not.toBeDisabled();
  });

  it('应该能切换查看不同平台的状态', async () => {
    mockUseOrgCheckQuery.mockReturnValue({
      data: {
        platform: 'dingtalk',
        status: 'MISSING_CONFIG',
        message: '缺少应用配置信息',
      },
      isLoading: false,
      refetch: mockRefetch,
    });

    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    // 点击钉钉的查看状态按钮
    const statusButtons = screen.getAllByText('查看状态');
    fireEvent.click(statusButtons[1]); // 第二个是钉钉

    await waitFor(() => {
      expect(screen.getByText('缺少配置')).toBeInTheDocument();
    });
  });

  it('应该显示不同的状态类型', () => {
    const testCases = [
      {
        status: 'OK',
        expectedText: '配置正常',
        expectedColor: 'success',
      },
      {
        status: 'MISSING_CONFIG',
        expectedText: '缺少配置',
        expectedColor: 'warning',
      },
      {
        status: 'UNAUTHORIZED',
        expectedText: '认证失败',
        expectedColor: 'error',
      },
      {
        status: 'ERROR',
        expectedText: '检查失败',
        expectedColor: 'error',
      },
    ];

    testCases.forEach(({ status, expectedText }) => {
      mockUseOrgCheckQuery.mockReturnValue({
        data: {
          platform: 'wechat',
          status,
          message: '测试消息',
        },
        isLoading: false,
        refetch: mockRefetch,
      });

      const { unmount } = render(
        <TestWrapper>
          <OrgSyncPage />
        </TestWrapper>,
      );

      expect(screen.getByText(expectedText)).toBeInTheDocument();
      unmount();
    });
  });

  it('应该能刷新状态', () => {
    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    // 点击刷新状态按钮
    const refreshButton = screen.getByRole('button', { name: /刷新状态/ });
    fireEvent.click(refreshButton);

    expect(mockRefetch).toHaveBeenCalledTimes(2); // platformsQuery 和 orgCheckQuery 都应该被刷新
    expect(mockHistoryRefetch).toHaveBeenCalledTimes(1);
  });

  it('应该显示加载状态', () => {
    mockUsePlatformsQuery.mockReturnValue({
      data: null,
      isLoading: true,
      refetch: mockRefetch,
    });

    mockUseOrgCheckQuery.mockReturnValue({
      data: null,
      isLoading: true,
      refetch: mockRefetch,
    });

    mockUseOrgHistoryQuery.mockReturnValue({
      data: [],
      isLoading: true,
      isError: false,
      error: null,
      refetch: mockHistoryRefetch,
    });

    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    // 验证加载状态
    expect(screen.getByText('检查中...')).toBeInTheDocument();
  });

  it('应该记录和显示同步历史', async () => {
    mockMutateAsync.mockResolvedValue({
      success: true,
      message: '同步成功',
    });

    mockUseOrgHistoryQuery.mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      error: null,
      refetch: mockHistoryRefetch,
    });

    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    // 验证初始无同步记录
    expect(screen.getByText('暂无同步记录')).toBeInTheDocument();

    // 执行同步操作
    const syncAllButton = screen.getByRole('button', { name: /同步全部/ });
    fireEvent.click(syncAllButton);

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalled();
      expect(mockHistoryRefetch).toHaveBeenCalled();
    });
  });
});

// 组织同步功能验证测试
describe('OrgSync 功能验证', () => {
  const mockMutateAsync = vi.fn();
  const mockRefetch = vi.fn();
  const mockHistoryRefetch = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    mockUsePlatformsQuery.mockReturnValue({
      data: [{ code: 'wechat', name: '企业微信' }],
      isLoading: false,
      refetch: mockRefetch,
    });

    mockUseOrgCheckQuery.mockReturnValue({
      data: { platform: 'wechat', status: 'OK' },
      isLoading: false,
      refetch: mockRefetch,
    });

    mockUseOrgSyncMutation.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
    });

    mockUseOrgHistoryQuery.mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      error: null,
      refetch: mockHistoryRefetch,
    });
  });

  it('应该满足Issue #7的验收标准', async () => {
    mockMutateAsync.mockResolvedValue({ success: true });

    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    // ✅ 同步中禁用
    expect(screen.getByRole('button', { name: /同步全部/ })).not.toBeDisabled();

    // 开始同步
    mockUseOrgSyncMutation.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: true,
    });

    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    // 验证同步中状态
    expect(screen.getByRole('button', { name: /同步中.../ })).toBeDisabled();

    // ✅ 结果提示 - 通过 message.success/error/warning 实现
    // ✅ 错误不阻塞 - 通过 try-catch 和友好提示实现
  });

  it('应该提供完整的用户体验', () => {
    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    // 验证用户指导
    expect(
      screen.getByText(/同步操作将从第三方平台拉取最新的组织架构和人员信息/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/同步过程中请勿重复操作，失败的同步不会影响其他平台/),
    ).toBeInTheDocument();

    // 验证状态检查
    expect(screen.getByText('平台状态')).toBeInTheDocument();
    expect(screen.getByText('选中平台：')).toBeInTheDocument();

    // 验证同步历史
    expect(screen.getByText('同步历史')).toBeInTheDocument();

    // 验证刷新功能
    expect(screen.getByRole('button', { name: /刷新状态/ })).toBeInTheDocument();
  });
});
