import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api, { unwrap } from '@services/api';
import type {
  IntegrationConfig,
  IntegrationConfigListItem,
  IntegrationConfigDetail,
  SaveConfigRequest,
  Platform,
  TestConnectionResult
} from '@types/api';
import { qk } from '@types/api';

// 获取所有配置列表
export function useIntegrationListQuery() {
  return useQuery({
    queryKey: qk.integrationList,
    queryFn: async () => {
      const { data } = await api.get('/admin/integration-configs');
      const raw = unwrap<any[]>(data);

      // 防御性编程：确保 raw 是数组
      if (!Array.isArray(raw)) {
        console.warn('[IntegrationList] 接口返回非数组数据:', raw);
        return [] as IntegrationConfigListItem[];
      }

      return raw.map((item) => {
        // 防御性编程：确保 item 是对象
        if (!item || typeof item !== 'object') {
          console.warn('[IntegrationList] 列表项格式异常:', item);
          return null;
        }
        const platformKey = String(item.platformType ?? item.platform ?? item.type ?? '').toLowerCase();
        const platformType = (platformKey || item.platformType || item.platform || item.type || 'wechat') as Platform;
        const statusValue = String(item.connectionStatus ?? item.status ?? item.connectionState ?? 'unknown').toLowerCase();
        const connectionStatus: IntegrationConfigListItem['connectionStatus'] =
          statusValue === 'connected'
            ? 'connected'
            : statusValue === 'disconnected'
              ? 'disconnected'
              : 'unknown';

        const enabledRaw = item.enabled ?? item.active ?? item.status === 'enabled';
        const enabled = Boolean(typeof enabledRaw === 'string' ? enabledRaw === 'enabled' : enabledRaw);

        const configuredRaw = item.configured ?? item.config ?? item.isConfigured;
        const configured = Boolean(
          typeof configuredRaw === 'boolean'
            ? configuredRaw
            : configuredRaw && typeof configuredRaw === 'object'
        );

        return {
          platformType,
          platformName: item.platformName ?? item.name ?? platformType ?? '未命名平台',
          enabled,
          configured,
          connectionStatus,
          lastModified: item.lastModified ?? item.updateTime ?? item.updatedAt ?? null,
        } satisfies IntegrationConfigListItem;
      }).filter((item): item is IntegrationConfigListItem => item !== null);
    },
  });
}

// 获取单个平台配置详情
export function useIntegrationConfigQuery(platformType: Platform) {
  return useQuery({
    queryKey: qk.integrationConfig(platformType),
    queryFn: async () => {
      const { data } = await api.get(`/admin/integration-configs/${platformType}`);
      const detail = unwrap<any>(data);

      // 防御性编程：确保 detail 是对象
      if (!detail || typeof detail !== 'object') {
        console.warn('[IntegrationConfig] 接口返回数据异常:', detail);
        // 返回默认值，避免前端崩溃
        return {
          platformType: platformType,
          platformName: platformType,
          enabled: false,
          config: {},
          connectionStatus: 'unknown' as const,
          lastModified: null,
        } as IntegrationConfigDetail;
      }

      const platform = (String(detail.platformType ?? detail.platform ?? detail.type ?? platformType) || platformType) as Platform;

      const enabledRaw = detail.enabled ?? detail.active ?? detail.status === 'enabled';
      const enabled = Boolean(typeof enabledRaw === 'string' ? enabledRaw === 'enabled' : enabledRaw);

      const statusValue = String(detail.connectionStatus ?? detail.status ?? detail.connectionState ?? 'unknown').toLowerCase();
      const connectionStatus: IntegrationConfigDetail['connectionStatus'] =
        statusValue === 'connected'
          ? 'connected'
          : statusValue === 'disconnected'
            ? 'disconnected'
            : 'unknown';

      let configCandidate: PlatformConfig | Record<string, unknown> = detail.config ?? detail[platform] ?? detail.settings ?? {};
      const configJson = detail.configJson ?? detail.config_json;
      if ((!configCandidate || Object.keys(configCandidate).length === 0) && typeof configJson === 'string') {
        try {
          const parsed = JSON.parse(configJson);
          configCandidate = parsed;
        } catch {}
      }
      if (typeof configCandidate === 'string') {
        try {
          configCandidate = JSON.parse(configCandidate);
        } catch {
          configCandidate = {};
        }
      }

      const config = configCandidate as PlatformConfig;

      return {
        platformType: platform,
        platformName: detail.platformName ?? detail.name ?? platform,
        enabled,
        config,
        connectionStatus,
        lastModified: detail.lastModified ?? detail.updateTime ?? detail.updatedAt ?? null,
      } as IntegrationConfigDetail;
    },
    enabled: Boolean(platformType),
  });
}

// 保存配置
export function useSaveIntegrationConfigMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ platformType, config }: { platformType: Platform; config: SaveConfigRequest }) => {
      const { data } = await api.put(`/admin/integration-configs/${platformType}`, config);
      return unwrap<string>(data);
    },
    onSuccess: (_, { platformType }) => {
      qc.invalidateQueries({ queryKey: qk.integrationList });
      qc.invalidateQueries({ queryKey: qk.integrationConfig(platformType) });
    },
  });
}

// 禁用配置
export function useDisableIntegrationMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (platformType: Platform) => {
      const { data } = await api.delete(`/admin/integration-configs/${platformType}`);
      return unwrap<string>(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.integrationList });
    },
  });
}

// 启用配置
export function useEnableIntegrationMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (platformType: Platform) => {
      const { data } = await api.post(`/admin/integration-configs/${platformType}/enable`);
      return unwrap<string>(data);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.integrationList });
    },
  });
}

// 测试连接
export function useTestIntegrationMutation() {
  return useMutation({
    mutationFn: async (platformType: Platform) => {
      const { data } = await api.post(`/admin/integration-configs/${platformType}/test-connection`);
      const payload = unwrap<any>(data);

      if (typeof payload === 'boolean') {
        return { success: payload } as TestConnectionResult;
      }

      if (payload && typeof payload === 'object') {
        const success = Boolean(payload.success ?? payload.result ?? payload.ok ?? false);
        const message = payload.message ?? payload.reason ?? (success ? '连接测试成功' : undefined);
        const detail = payload.detail ?? payload.error ?? payload.data ?? undefined;
        return { success, message, detail } as TestConnectionResult;
      }

      return { success: false } as TestConnectionResult;
    },
  });
}

// ===== 向后兼容旧页面 API，统一走当前后端 /admin/integration-configs 路径 =====

export function useIntegrationQuery(platform: Platform) {
  return useQuery({
    queryKey: qk.integration(platform),
    queryFn: async () => {
      const { data } = await api.get(`/admin/integration-configs/${platform}`);
      return unwrap<IntegrationConfig>(data);
    },
    enabled: Boolean(platform),
  });
}

export function useSaveIntegrationMutation(platform: Platform) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (payload: Partial<IntegrationConfig>) => {
      const { data } = await api.put(`/admin/integration-configs/${platform}`, payload);
      return unwrap<IntegrationConfig>(data);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.integration(platform) }),
  });
}

export function useTestConnectionMutation(platform: Platform) {
  return useMutation({
    mutationFn: async () => {
      const { data } = await api.post(`/admin/integration-configs/${platform}/test-connection`);
      return unwrap<TestConnectionResult>(data);
    },
  });
}
