import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api, { unwrap } from '@services/api';
import type { PageParams } from '@/types/api';
import type {
  EmployeeVO,
  EmployeeCreateRequest,
  EmployeeUpdateRequest,
  BindPlatformRequest,
  BatchImportRequest,
  PagedResponse,
} from '@/types/openapi';

// 采用 OpenAPI 定义的 EmployeeVO 作为实体类型
export type Employee = Required<Pick<EmployeeVO, 'id' | 'employeeId' | 'name'>> & EmployeeVO;

// 员工查询参数
export interface EmployeeQueryParams extends PageParams {
  current?: number;
  pageSize?: number;
  keyword?: string;
  department?: string;
  status?: 'active' | 'inactive' | 'suspended';
  isOffline?: boolean;
  platformType?: 'wechat' | 'dingtalk' | 'feishu';
  managerId?: number;
  sortBy?: string;
  order?: 'asc' | 'desc';
}

// 员工创建/更新数据
export type EmployeeFormData = EmployeeCreateRequest & Partial<EmployeeUpdateRequest>;

// 平台绑定数据
export type PlatformBindData = BindPlatformRequest;

// 状态更新数据
export interface StatusUpdateData {
  status: 'active' | 'inactive' | 'suspended';
}

// 批量导入数据
export type BatchImportData = BatchImportRequest;

// 查询员工列表
export function useEmployeesQuery(params: EmployeeQueryParams, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['employees', params],
    queryFn: async () => {
      const queryParams = {
        page: params.current || 1,
        size: params.pageSize || 10,
        keyword: params.keyword,
        department: params.department,
        status: params.status,
        isOffline: params.isOffline,
        platformType: params.platformType,
        managerId: params.managerId,
        sortBy: params.sortBy || 'createTime',
        order: params.order || 'desc',
      };

      // 移除空值参数
      const cleanParams = Object.fromEntries(
        Object.entries(queryParams).filter(([, value]) => value !== undefined && value !== ''),
      );

      const { data } = await api.get('/employee', { params: cleanParams });
      return unwrap<PagedResponse<Employee>>(data);
    },
    enabled: options?.enabled,
  });
}

// 查询员工详情
export function useEmployeeQuery(id: string | number, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['employee', id],
    queryFn: async () => {
      const { data } = await api.get(`/employee/${id}`);
      return unwrap<Employee>(data);
    },
    enabled: !!id && options?.enabled !== false,
  });
}

// 查询离线员工列表
export function useOfflineEmployeesQuery(managerId?: number) {
  return useQuery({
    queryKey: ['employees', 'offline', managerId],
    queryFn: async () => {
      const params = managerId ? { managerId } : {};
      const { data } = await api.get('/employee/offline', { params });
      return unwrap<Employee[]>(data);
    },
  });
}

// 创建员工
export function useCreateEmployeeMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (employeeData: EmployeeFormData) => {
      const { data } = await api.post('/employee', employeeData as EmployeeCreateRequest);
      return unwrap<Employee>(data);
    },
    onSuccess: () => {
      // 刷新员工列表
      queryClient.invalidateQueries({ queryKey: ['employees'] });
    },
  });
}

// 更新员工
export function useUpdateEmployeeMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, ...employeeData }: EmployeeFormData & { id: number }) => {
      const { data } = await api.put(`/employee/${id}`, employeeData as EmployeeUpdateRequest);
      return unwrap<Employee>(data);
    },
    onSuccess: (_, variables) => {
      // 刷新员工列表和详情
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      queryClient.invalidateQueries({ queryKey: ['employee', variables.id] });
    },
  });
}

// 更新员工状态
export function useUpdateEmployeeStatusMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, status }: { id: number; status: StatusUpdateData['status'] }) => {
      const { data } = await api.patch(`/employee/${id}/status`, { status });
      return unwrap<{ success: boolean; message?: string }>(data);
    },
    onSuccess: (_, variables) => {
      // 刷新员工列表和详情
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      queryClient.invalidateQueries({ queryKey: ['employee', variables.id] });
    },
  });
}

// 绑定平台用户
export function useBindPlatformMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, ...bindData }: PlatformBindData & { id: number }) => {
      const { data } = await api.post(`/employee/${id}/bind-platform`, bindData);
      return unwrap<{ success: boolean; message?: string }>(data);
    },
    onSuccess: (_, variables) => {
      // 刷新员工列表和详情
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      queryClient.invalidateQueries({ queryKey: ['employee', variables.id] });
    },
  });
}

// 设置/取消离线
export function useToggleEmployeeOfflineMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, value }: { id: number; value: boolean }) => {
      const { data } = await api.patch(`/admin/employees/${id}/offline`, null, {
        params: { value },
      });
      return unwrap<null>(data);
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      queryClient.invalidateQueries({ queryKey: ['employee', variables.id] });
    },
  });
}

// 指定负责人
export function useAssignEmployeeManagerMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, managerId }: { id: number; managerId: number }) => {
      const { data } = await api.put(`/admin/employees/${id}/manager`, null, {
        params: { managerId },
      });
      return unwrap<null>(data);
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      queryClient.invalidateQueries({ queryKey: ['employee', variables.id] });
    },
  });
}

// 批量导入员工
export function useBatchImportEmployeesMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (batchData: BatchImportData) => {
      const { data } = await api.post('/employee/batch-import', batchData);
      return unwrap<{ success: boolean; message?: string }>(data);
    },
    onSuccess: () => {
      // 刷新员工列表
      queryClient.invalidateQueries({ queryKey: ['employees'] });
    },
  });
}

// 获取员工敏感信息（需要管理员权限）
export function useEmployeeIdCardQuery(id: number, options?: { enabled?: boolean; onEmpty?: (empty: boolean) => void }) {
  return useQuery({
    queryKey: ['employee', id, 'idCard'],
    queryFn: async () => {
      const { data } = await api.get<string>(`/employee/${id}/id-card`);
      const result = unwrap<string>(data);
      // 确保返回空字符串而不是 null/undefined
      const normalizedResult = result ?? '';
      // 回调通知是否为空
      if (options?.onEmpty) {
        options.onEmpty(normalizedResult === '');
      }
      return normalizedResult;
    },
    enabled: !!id && options?.enabled !== false,
    staleTime: 0, // 敏感信息不缓存
    gcTime: 0,
  });
}

export function useEmployeeBankAccountQuery(id: number, options?: { enabled?: boolean; onEmpty?: (empty: boolean) => void }) {
  return useQuery({
    queryKey: ['employee', id, 'bankAccount'],
    queryFn: async () => {
      const { data } = await api.get<string>(`/employee/${id}/bank-account`);
      const result = unwrap<string>(data);
      // 确保返回空字符串而不是 null/undefined
      const normalizedResult = result ?? '';
      // 回调通知是否为空
      if (options?.onEmpty) {
        options.onEmpty(normalizedResult === '');
      }
      return normalizedResult;
    },
    enabled: !!id && options?.enabled !== false,
    staleTime: 0, // 敏感信息不缓存
    gcTime: 0,
  });
}
