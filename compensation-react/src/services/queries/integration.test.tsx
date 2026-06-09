import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import api from '@services/api';
import {
  useIntegrationListQuery,
  useIntegrationConfigQuery,
  useSaveIntegrationConfigMutation,
  useDisableIntegrationMutation,
  useTestIntegrationMutation,
  useIntegrationQuery,
  useSaveIntegrationMutation,
  useTestConnectionMutation,
} from './integration';
import type { IntegrationConfigDetail, Platform, SaveConfigRequest } from '@types/api';

vi.mock('@services/api', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    post: vi.fn(),
  },
  unwrap: vi.fn((response: any) => response.data),
}));

const mockApi = api as any;

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  const Wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  return { Wrapper, queryClient };
};

const mockList = [
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
] as const;

const mockDetail: IntegrationConfigDetail = {
  platformType: 'wechat',
  platformName: '企业微信',
  enabled: true,
  config: {
    corpId: 'ww123456789abcdef',
    corpSecret: 'secret',
    agentId: '1000002',
  },
  connectionStatus: 'connected',
  lastModified: '2024-01-15T10:30:00',
};

const mockSavePayload: SaveConfigRequest = {
  enabled: true,
  wechat: {
    corpId: 'ww123456789abcdef',
    corpSecret: 'secret',
    agentId: '1000002',
  },
};

describe('integration queries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches integration list', async () => {
    mockApi.get.mockResolvedValue({
      data: { code: 200, message: 'success', data: mockList },
    });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useIntegrationListQuery(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith('/admin/integration-configs');
    expect(result.current.data).toEqual(mockList);
  });

  it('fetches integration detail by platform', async () => {
    const platform: Platform = 'wechat';
    mockApi.get.mockResolvedValue({
      data: { code: 200, message: 'success', data: mockDetail },
    });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useIntegrationConfigQuery(platform), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith(`/admin/integration-configs/${platform}`);
    expect(result.current.data).toEqual(mockDetail);
  });

  it('keeps config when platform is disabled (for edit-after-disable)', async () => {
    const platform: Platform = 'yunzhanghu';
    const disabledDetail = {
      platformType: 'yunzhanghu',
      platformName: '云账户',
      enabled: false,
      config: {
        dealerId: '***1234',
        brokerId: '***5678',
        appKey: '***9999',
        des3Key: '******',
        rsaPrivateKey: '******',
        rsaPublicKey: '******',
        signType: 'rsa',
        url: 'https://api-service.yunzhanghu.com/sandbox',
      },
      connectionStatus: 'disconnected',
      lastModified: '2026-02-26T09:00:00',
    } satisfies IntegrationConfigDetail;

    mockApi.get.mockResolvedValue({
      data: { code: 200, message: 'success', data: disabledDetail },
    });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useIntegrationConfigQuery(platform), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith('/admin/integration-configs/yunzhanghu');
    expect(result.current.data?.enabled).toBe(false);
    expect((result.current.data?.config as any)?.dealerId).toBe('***1234');
    expect((result.current.data?.config as any)?.url).toBe('https://api-service.yunzhanghu.com/sandbox');
  });

  it('parses configJson when detail.config is empty', async () => {
    const platform: Platform = 'yunzhanghu';
    mockApi.get.mockResolvedValue({
      data: {
        code: 200,
        message: 'success',
        data: {
          platformType: 'yunzhanghu',
          platformName: '云账户',
          enabled: false,
          config: null,
          configJson: '{"dealerId":"***1234","url":"https://api-service.yunzhanghu.com/sandbox"}',
          connectionStatus: 'disconnected',
          lastModified: null,
        },
      },
    });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useIntegrationConfigQuery(platform), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data?.enabled).toBe(false);
    expect((result.current.data?.config as any)?.dealerId).toBe('***1234');
    expect((result.current.data?.config as any)?.url).toBe('https://api-service.yunzhanghu.com/sandbox');
  });

  it('saves integration config and invalidates caches', async () => {
    mockApi.put.mockResolvedValue({
      data: { code: 200, message: 'success', data: '配置保存成功' },
    });

    const { Wrapper, queryClient } = createWrapper();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useSaveIntegrationConfigMutation(), { wrapper: Wrapper });

    const response = await result.current.mutateAsync({
      platformType: 'wechat',
      config: mockSavePayload,
    });

    expect(mockApi.put).toHaveBeenCalledWith('/admin/integration-configs/wechat', mockSavePayload);
    expect(response).toBe('配置保存成功');
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['integration', 'list'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['integration', 'config', 'wechat'] });
  });

  it('disables integration and refreshes list', async () => {
    mockApi.delete.mockResolvedValue({
      data: { code: 200, message: 'success', data: '配置已禁用' },
    });

    const { Wrapper, queryClient } = createWrapper();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useDisableIntegrationMutation(), { wrapper: Wrapper });

    const response = await result.current.mutateAsync('wechat');

    expect(mockApi.delete).toHaveBeenCalledWith('/admin/integration-configs/wechat');
    expect(response).toBe('配置已禁用');
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['integration', 'list'] });
  });

  it('tests integration connection', async () => {
    mockApi.post.mockResolvedValue({
      data: { code: 200, message: 'success', data: true },
    });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useTestIntegrationMutation(), { wrapper: Wrapper });

    const response = await result.current.mutateAsync('wechat');

    expect(mockApi.post).toHaveBeenCalledWith('/admin/integration-configs/wechat/test-connection');
    expect(response).toEqual({ success: true });
  });

  it('routes compatibility detail hook through current admin API', async () => {
    mockApi.get.mockResolvedValue({
      data: { code: 200, message: 'success', data: mockDetail },
    });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useIntegrationQuery('wechat'), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith('/admin/integration-configs/wechat');
  });

  it('routes compatibility save hook through current admin API', async () => {
    mockApi.put.mockResolvedValue({
      data: { code: 200, message: 'success', data: mockDetail },
    });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useSaveIntegrationMutation('wechat'), { wrapper: Wrapper });

    await result.current.mutateAsync(mockDetail);

    expect(mockApi.put).toHaveBeenCalledWith('/admin/integration-configs/wechat', mockDetail);
  });

  it('routes compatibility connection test hook through current admin API', async () => {
    mockApi.post.mockResolvedValue({
      data: { code: 200, message: 'success', data: { success: true } },
    });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useTestConnectionMutation('wechat'), { wrapper: Wrapper });

    await result.current.mutateAsync();

    expect(mockApi.post).toHaveBeenCalledWith('/admin/integration-configs/wechat/test-connection');
  });

  it('propagates errors from API', async () => {
    const error = new Error('Network failed');
    mockApi.get.mockRejectedValue(error);

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useIntegrationConfigQuery('wechat'), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBe(error);
  });
});
