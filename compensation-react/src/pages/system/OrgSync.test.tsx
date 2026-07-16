import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider } from 'react-redux';
import { App as AntdApp } from 'antd';
import { vi, beforeEach, describe, expect, it } from 'vitest';
import { store } from '../../services/stores/authSlice';
import OrgSyncPage from './OrgSync';

const mockUsePlatformsQuery = vi.fn();
const mockUseOrgCheckQuery = vi.fn();
const mockUseOrgHistoryQuery = vi.fn();
const mockUseOrgDepartmentTreeQuery = vi.fn();
const mockUseOrgFetchPreviewMutation = vi.fn();
const mockUseOrgImportMutation = vi.fn();

vi.mock('@services/queries/org', () => ({
  usePlatformsQuery: () => mockUsePlatformsQuery(),
  useOrgCheckQuery: (...args: unknown[]) => mockUseOrgCheckQuery(...args),
  useOrgHistoryQuery: () => mockUseOrgHistoryQuery(),
  useOrgDepartmentTreeQuery: (...args: unknown[]) => mockUseOrgDepartmentTreeQuery(...args),
  useOrgFetchPreviewMutation: () => mockUseOrgFetchPreviewMutation(),
  useOrgImportMutation: () => mockUseOrgImportMutation(),
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

const previewResponse = {
  provider: 'wechat',
  totalEmployees: 2,
  newEmployees: 1,
  existingEmployees: 1,
  employees: [
    {
      rowKey: 'wx-001-0',
      provider: 'wechat',
      subjectId: 'wx-001',
      employeeId: 'E1001',
      name: '张三',
      department: '技术部',
      employmentType: 'full_time',
      alreadyImported: false,
    },
    {
      rowKey: 'wx-002-1',
      provider: 'wechat',
      subjectId: 'wx-002',
      employeeId: 'E1002',
      name: '李四',
      department: '财务部',
      employmentType: 'part_time',
      alreadyImported: true,
      existingEmployeeNo: 'E1002',
    },
  ],
};

describe('OrgSync 组织同步流程', () => {
  const platformsRefetch = vi.fn();
  const checkRefetch = vi.fn();
  const historyRefetch = vi.fn();
  const departmentRefetch = vi.fn();
  const fetchPreview = vi.fn();
  const importEmployees = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    mockUsePlatformsQuery.mockReturnValue({
      data: [
        { code: 'wechat', name: '企业微信', configured: true },
        { code: 'dingtalk', name: '钉钉', configured: false },
      ],
      isLoading: false,
      refetch: platformsRefetch,
    });

    mockUseOrgCheckQuery.mockReturnValue({
      data: { platform: 'wechat', status: 'OK', message: '配置正常，可以同步' },
      isLoading: false,
      refetch: checkRefetch,
    });

    mockUseOrgHistoryQuery.mockReturnValue({
      data: [
        {
          provider: 'wechat',
          success: true,
          message: '同步成功',
          syncTime: '2025-09-29T12:23:08.092Z',
        },
      ],
      isLoading: false,
      isError: false,
      error: null,
      refetch: historyRefetch,
    });

    mockUseOrgDepartmentTreeQuery.mockReturnValue({
      data: [
        {
          id: 1,
          provider: 'wechat',
          platformDeptId: 'dept-root',
          name: '总部',
          children: [
            {
              id: 2,
              provider: 'wechat',
              platformDeptId: 'dept-tech',
              name: '技术部',
              children: [],
            },
          ],
        },
      ],
      isLoading: false,
      refetch: departmentRefetch,
    });

    fetchPreview.mockResolvedValue(previewResponse);
    importEmployees.mockResolvedValue({
      success: 1,
      created: 1,
      updated: 0,
      skipped: 0,
      failed: 0,
      errors: [],
    });
    mockUseOrgFetchPreviewMutation.mockReturnValue({ mutateAsync: fetchPreview, isPending: false });
    mockUseOrgImportMutation.mockReturnValue({ mutateAsync: importEmployees, isPending: false });
  });

  it('展示当前的预览导入工作流', () => {
    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    expect(screen.getByText('组织同步')).toBeInTheDocument();
    expect(screen.getByText('同步操作')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /拉取预览/ })).toBeInTheDocument();
    expect(screen.getByText('同步历史')).toBeInTheDocument();
    expect(screen.getByText('总部')).toBeInTheDocument();
    expect(screen.getByText('配置正常')).toBeInTheDocument();
  });

  it('刷新时同时更新平台、状态、历史和部门树', () => {
    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    fireEvent.click(screen.getByRole('button', { name: /刷新状态/ }));

    expect(platformsRefetch).toHaveBeenCalledTimes(1);
    expect(checkRefetch).toHaveBeenCalledTimes(1);
    expect(historyRefetch).toHaveBeenCalledTimes(1);
    expect(departmentRefetch).toHaveBeenCalledTimes(1);
  });

  it('按全部范围拉取预览并展示员工数据', async () => {
    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    fireEvent.click(screen.getByRole('radio', { name: '全部' }));
    fireEvent.click(screen.getByRole('button', { name: /拉取预览/ }));

    await waitFor(() => {
      expect(fetchPreview).toHaveBeenCalledWith({ platform: 'wechat', options: {} });
    });
    expect(await screen.findByText('员工预览 - 企业微信')).toBeInTheDocument();
    expect(screen.getByText('张三')).toBeInTheDocument();
    expect(screen.getByText('李四')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /导入选中员工 \(1\)/ })).toBeInTheDocument();
  });

  it('按用户范围规范化多个平台用户ID', async () => {
    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    fireEvent.click(screen.getByRole('radio', { name: '按用户' }));
    fireEvent.change(screen.getByPlaceholderText('每行一个用户ID，可粘贴企业微信成员ID'), {
      target: { value: 'wx-001\nwx-002, wx-003' },
    });
    fireEvent.click(screen.getByRole('button', { name: /拉取预览/ }));

    await waitFor(() => {
      expect(fetchPreview).toHaveBeenCalledWith({
        platform: 'wechat',
        options: { userIds: ['wx-001', 'wx-002', 'wx-003'] },
      });
    });
  });

  it('确认导入时只提交默认选中的未导入员工', async () => {
    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    fireEvent.click(screen.getByRole('radio', { name: '全部' }));
    fireEvent.click(screen.getByRole('button', { name: /拉取预览/ }));
    const importButton = await screen.findByRole('button', { name: /导入选中员工 \(1\)/ });
    fireEvent.click(importButton);

    expect((await screen.findAllByText('确认导入')).length).toBeGreaterThan(0);
    const confirmButtons = screen.getAllByRole('button', { name: /确\s*定|OK/ });
    const confirmButton =
      confirmButtons.find((button) => button.closest('.ant-modal-confirm')) ??
      confirmButtons[confirmButtons.length - 1];
    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(importEmployees).toHaveBeenCalledWith(
        expect.objectContaining({
          provider: 'wechat',
          importMode: 'new_only',
          items: [expect.objectContaining({ employeeId: 'E1001', subjectId: 'wx-001' })],
        }),
      );
    });
  });

  it('切换平台后重新使用对应的平台检查结果', async () => {
    mockUseOrgCheckQuery.mockImplementation((platform: string) => ({
      data:
        platform === 'dingtalk'
          ? { platform, status: 'MISSING_CONFIG', message: '缺少应用配置信息' }
          : { platform, status: 'OK', message: '配置正常，可以同步' },
      isLoading: false,
      refetch: checkRefetch,
    }));

    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    fireEvent.click(screen.getByRole('radio', { name: '钉钉' }));

    await waitFor(() => {
      expect(screen.getByText('缺少配置')).toBeInTheDocument();
      expect(mockUseOrgCheckQuery).toHaveBeenLastCalledWith('dingtalk');
    });
  });

  it('预览请求失败时显示可理解的错误提示', async () => {
    fetchPreview.mockRejectedValue(new Error('平台连接超时'));

    render(
      <TestWrapper>
        <OrgSyncPage />
      </TestWrapper>,
    );

    fireEvent.click(screen.getByRole('radio', { name: '全部' }));
    fireEvent.click(screen.getByRole('button', { name: /拉取预览/ }));

    expect(await screen.findByText('拉取预览失败：平台连接超时')).toBeInTheDocument();
  });
});
