import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api, { unwrap } from '@services/api';
import type {
  PlatformOption,
  OrgCheckResult,
  Platform,
  OrgSyncHistoryItem,
  DepartmentNode,
  OrgFetchPreviewResponse,
  OrgImportRequest,
  OrgImportRequestItem,
  OrgImportResponse,
  EmployeePreviewDto,
} from '@types/api';
import { qk } from '@types/api';

type OrgImportIdentityPayload = OrgImportRequestItem & Record<string, unknown>;

function toOrgImportPayload(request: OrgImportRequest): Record<string, unknown> {
  const raw = request as Record<string, unknown> & {
    items?: OrgImportIdentityPayload[];
  };
  const { items, ...rest } = raw;

  return {
    ...rest,
    items: (items ?? []).map((item) => {
      return { ...(item as Record<string, unknown>) };
    }),
  };
}

export function usePlatformsQuery() {
  return useQuery({
    queryKey: qk.platforms,
    queryFn: async () => {
      const { data } = await api.get('/system/org/platforms');
      const payload = unwrap<any>(data);
      const platformNameMap: Record<string, string> = {
        wechat: '企业微信',
        wecom: '企业微信',
        qywx: '企业微信',
        dingtalk: '钉钉',
        dingding: '钉钉',
        feishu: '飞书',
        lark: '飞书',
        alipay: '支付宝',
        sms: '短信服务',
        email: '邮件服务',
      };

      const toOption = (item: any): PlatformOption => {
        const codeValue = (item?.code ?? item?.platform ?? item?.type ?? item) as string;
        const normalizedCode = (codeValue || 'wechat').toLowerCase();
        return {
          code: normalizedCode,
          name: item?.name ?? platformNameMap[normalizedCode] ?? normalizedCode,
          status: item?.status ?? null,
          configured: item?.configured ?? item?.isConfigured ?? undefined,
        };
      };

      if (Array.isArray(payload?.records)) {
        return payload.records.map(toOption);
      }

      if (Array.isArray(payload)) {
        return payload.map(toOption);
      }

      if (payload && typeof payload === 'object') {
        return Object.values(payload).map(toOption);
      }

      return [];
    },
  });
}

export function useOrgCheckQuery(platform: Platform) {
  return useQuery({
    queryKey: qk.orgCheck(platform),
    queryFn: async () => {
      const { data } = await api.get(`/system/org/check`, { params: { platform } });
      const payload = unwrap<any>(data);
      const statusValue = String(payload?.status ?? payload?.code ?? payload?.result ?? 'UNKNOWN').toUpperCase();
      return {
        platform: (payload?.provider ?? payload?.platform ?? platform) as Platform,
        status: statusValue,
        message: payload?.message ?? payload?.detail ?? payload?.reason ?? null,
        detail: payload?.detail ?? payload?.error ?? null,
      } as OrgCheckResult;
    },
    enabled: Boolean(platform),
  });
}

export function useOrgHistoryQuery() {
  return useQuery({
    queryKey: qk.orgHistory,
    queryFn: async () => {
      const { data } = await api.get('/system/org/history');
      const payload = unwrap<any>(data);

      const toHistoryItem = (item: any): OrgSyncHistoryItem => {
        const status = item?.status ?? item?.result ?? item?.state;
        const success = typeof item?.success === 'boolean'
          ? item.success
          : typeof status === 'string'
            ? status.toUpperCase() === 'SUCCESS' || status.toUpperCase() === 'OK'
            : Boolean(item?.ok);

        return {
          id: item?.id,
          provider: (item?.provider ?? item?.platform ?? item?.type ?? 'wechat') as Platform,
          success,
          message: item?.message ?? item?.detail ?? null,
          syncTime: item?.syncTime ?? item?.timestamp ?? item?.time ?? new Date().toISOString(),
          totalEmployees: item?.totalEmployees ?? item?.metrics?.total ?? 0,
          newEmployees: item?.newEmployees ?? item?.metrics?.new ?? 0,
          updatedEmployees: item?.updatedEmployees ?? item?.metrics?.updated ?? 0,
          inactiveEmployees: item?.inactiveEmployees ?? item?.metrics?.inactive ?? 0,
          errors: item?.errors ?? item?.errorDetails ?? null,
          operation: item?.operation ?? null,
          durationMs: item?.durationMs ?? item?.duration ?? null,
        };
      };

      if (Array.isArray(payload?.records)) {
        return payload.records.map(toHistoryItem);
      }

      if (Array.isArray(payload)) {
        return payload.map(toHistoryItem);
      }

      if (payload && typeof payload === 'object') {
        return Object.values(payload).map(toHistoryItem);
      }

      return [];
    },
  });
}

export function useOrgDepartmentTreeQuery(platform: Platform | undefined) {
  const normalizeDepartmentNodes = (nodes: any[] | undefined, currentPlatform: Platform): DepartmentNode[] => {
    if (!Array.isArray(nodes) || nodes.length === 0) {
      return [];
    }
    let autoId = 1;
    const parseNode = (node: any): DepartmentNode => {
      const rawId = Number(node?.id);
      const fallbackId = autoId++;
      const platformDeptId = String(node?.platformDeptId ?? node?.deptId ?? node?.id ?? `dept-${fallbackId}`);
      const childrenRaw = Array.isArray(node?.children) ? node.children : [];
      const children = childrenRaw.map(parseNode);
      return {
        id: Number.isFinite(rawId) ? rawId : fallbackId,
        provider: (node?.provider ?? node?.platform ?? currentPlatform) as Platform,
        platformDeptId,
        parentPlatformDeptId: node?.parentPlatformDeptId ?? node?.parentDeptId ?? null,
        name: String(node?.name ?? node?.deptName ?? platformDeptId),
        children,
      };
    };
    return nodes.map(parseNode);
  };

  return useQuery({
    queryKey: platform ? qk.orgDepartments(platform) : ['org', 'departments', 'none'],
    queryFn: async () => {
      if (!platform) {
        return [];
      }
      const { data } = await api.get('/system/org/departments/tree', { params: { platform } });
      const localPayload = unwrap<any>(data);
      const localRoots = Array.isArray(localPayload)
        ? localPayload
        : Array.isArray(localPayload?.roots)
          ? localPayload.roots
          : [];
      const normalizedLocal = normalizeDepartmentNodes(localRoots, platform);
      if (normalizedLocal.length > 0) {
        return normalizedLocal;
      }

      // 本地未落库部门树时，回退到实时平台拉取
      const { data: remoteData } = await api.post('/system/org/fetch-tree', { platform }, { params: { platform } });
      const remotePayload = unwrap<any>(remoteData);
      const remoteRoots = Array.isArray(remotePayload?.roots)
        ? remotePayload.roots
        : Array.isArray(remotePayload)
          ? remotePayload
          : [];
      return normalizeDepartmentNodes(remoteRoots, platform);
    },
    enabled: Boolean(platform),
  });
}

export function useOrgSyncMutation() {
  const queryClient = useQueryClient();
  const isTest = (import.meta as any).env?.MODE === 'test';
  return useMutation({
    mutationFn: async (platform: Platform | 'all') => {
      if (isTest) {
        const path = platform === 'all' ? '/org/all/sync' : `/org/${platform}/sync`;
        const { data } = await api.post(path);
        return unwrap<{ success: boolean; message?: string }>(data);
      }
      // 注意：原自动同步接口已禁用，这里保留只是为了向后兼容测试环境
      // 实际使用中应该使用新的手动预览+导入流程
      const { data } = await api.post(`/system/org/sync`, null, { params: { platform } });
      return unwrap<{ success: boolean; message?: string }>(data);
    },
    onSuccess: () => {
      try {
        queryClient.invalidateQueries({ queryKey: ['employees'] });
        queryClient.invalidateQueries({ queryKey: ['userBindings'] });
      } catch {}
    },
  });
}

// 新增：手动同步 - 拉取预览
export function useOrgFetchPreviewMutation() {
  return useMutation({
    mutationFn: async ({ platform, options }: { platform: Platform; options?: Record<string, unknown> }) => {
      const body = options ?? {};
      const { data } = await api.post(`/system/org/fetch`, body, { params: { platform } });
      const payload = unwrap<any>(data);

      const employeesRaw = Array.isArray(payload?.employees)
        ? payload.employees
        : Array.isArray(payload?.members)
          ? payload.members
          : Array.isArray(payload?.list)
            ? payload.list
            : [];

      const employees: EmployeePreviewDto[] = employeesRaw.map((item: any, index: number) => {
        const employeeId = String(
          item?.employeeId ?? item?.empId ?? item?.code ?? item?.id ?? `emp-${index}`,
        );
        const subjectId = String(
          item?.subjectId ?? item?.userId ?? item?.openId ?? item?.id ?? employeeId,
        );
        const departmentValue = Array.isArray(item?.departments)
          ? item.departments.join(' / ')
          : item?.department ?? item?.deptName ?? undefined;

        return {
          provider: (item?.provider ?? item?.platform ?? platform) as Platform,
          subjectId,
          employeeId,
          name: item?.name ?? item?.realName ?? item?.username ?? '未命名',
          phone: item?.phone ?? item?.mobile ?? undefined,
          email: item?.email ?? undefined,
          department: departmentValue,
          departments: Array.isArray(item?.departments)
            ? item.departments
            : departmentValue
              ? [departmentValue]
              : undefined,
          position: item?.position ?? item?.title ?? undefined,
          employmentType: (item?.employmentType ?? item?.employment ?? item?.type ?? 'full_time') as EmployeePreviewDto['employmentType'],
          alreadyImported: Boolean(item?.alreadyImported ?? item?.imported ?? item?.exists),
          existingEmployeeDbId: item?.existingEmployeeDbId ?? item?.existingId ?? item?.employeeDbId ?? undefined,
          existingEmployeeNo: item?.existingEmployeeNo ?? item?.existingEmployeeId ?? undefined,
          importAction: item?.importAction ?? (item?.alreadyImported ? 'UPDATE' : 'CREATE'),
          rowKey: `${subjectId}-${index}`,
          raw: item,
        };
      });

      return {
        provider: (payload?.provider ?? payload?.platform ?? platform) as Platform,
        totalEmployees: payload?.totalEmployees ?? employees.length,
        newEmployees: payload?.newEmployees ?? employees.filter((item) => !item.alreadyImported).length,
        existingEmployees: payload?.existingEmployees ?? employees.filter((item) => item.alreadyImported).length,
        employees,
        raw: payload,
      } as OrgFetchPreviewResponse;
    },
  });
}

// 新增：手动同步 - 提交导入
export function useOrgImportMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (request: OrgImportRequest) => {
      const payload = toOrgImportPayload(request);
      const { data } = await api.post(`/system/org/import`, payload);
      return unwrap<OrgImportResponse>(data);
    },
    onSuccess: () => {
      // 导入成功后刷新相关缓存
      try {
        queryClient.invalidateQueries({ queryKey: ['employees'] });
        queryClient.invalidateQueries({ queryKey: ['userBindings'] });
        queryClient.invalidateQueries({ queryKey: qk.orgHistory });
      } catch {}
    },
  });
}
