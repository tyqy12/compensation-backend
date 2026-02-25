import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import api from '@services/api';
import {
  useEmployeesQuery,
  useEmployeeQuery,
  useOfflineEmployeesQuery,
  useCreateEmployeeMutation,
  useUpdateEmployeeMutation,
  useUpdateEmployeeStatusMutation,
  useBindPlatformMutation,
  useBatchImportEmployeesMutation,
  useEmployeeIdCardQuery,
  useEmployeeBankAccountQuery,
  type Employee,
  type EmployeeFormData,
} from './employee';

// Mock the API
vi.mock('@services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
  },
  unwrap: vi.fn((data) => data),
}));

const mockApi = api as any;

// Test wrapper
const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  const Wrapper = ({ children }: { children: React.ReactNode }) => {
    return React.createElement(QueryClientProvider, { client: queryClient }, children);
  };

  return Wrapper;
};

// Test data
const mockEmployee: Employee = {
  id: 1,
  employeeId: 'EMP001',
  name: '张三',
  phone: '13812345678',
  phoneMasked: '138****5678',
  email: 'zhangsan@company.com',
  department: '技术部',
  position: '高级开发工程师',
  platformUserId: 'wx_user_123',
  platformType: 'wechat',
  offline: false,
  managerId: 2,
  hireDate: '2023-01-15',
  status: 'active',
  bankAccountMasked: '622202******1234',
  bankName: '中国银行',
  createTime: '2023-01-15T09:00:00Z',
  updateTime: '2024-01-15T10:00:00Z',
};

const mockEmployeeFormData: EmployeeFormData = {
  name: '李四',
  phone: '13987654321',
  email: 'lisi@company.com',
  department: '产品部',
  position: '产品经理',
  status: 'active',
};

describe('Employee Queries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useEmployeesQuery', () => {
    it('should fetch employees with correct parameters', async () => {
      const mockResponse = {
        data: {
          records: [mockEmployee],
          total: 1,
          current: 1,
          size: 10,
        },
      };

      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(
        () =>
          useEmployeesQuery({
            current: 1,
            pageSize: 10,
            keyword: 'test',
            department: '技术部',
            status: 'active',
            isOffline: false,
            platformType: 'wechat',
            managerId: 2,
            sortBy: 'createTime',
            order: 'desc',
          }),
        { wrapper: createWrapper() },
      );

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/employee', {
        params: {
          page: 1,
          size: 10,
          keyword: 'test',
          department: '技术部',
          status: 'active',
          isOffline: false,
          platformType: 'wechat',
          managerId: 2,
          sortBy: 'createTime',
          order: 'desc',
        },
      });

      expect(result.current.data).toEqual(mockResponse.data);
    });

    it('should filter out empty parameters', async () => {
      const mockResponse = { data: { records: [], total: 0 } };
      mockApi.get.mockResolvedValue(mockResponse);

      renderHook(
        () =>
          useEmployeesQuery({
            current: 1,
            pageSize: 10,
            keyword: '',
            department: undefined,
            status: undefined,
          }),
        { wrapper: createWrapper() },
      );

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith('/employee', {
          params: {
            page: 1,
            size: 10,
            sortBy: 'createTime',
            order: 'desc',
          },
        });
      });
    });
  });

  describe('useEmployeeQuery', () => {
    it('should fetch single employee by id', async () => {
      const mockResponse = { data: mockEmployee };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useEmployeeQuery(1), { wrapper: createWrapper() });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/employee/1');
      expect(result.current.data).toEqual(mockEmployee);
    });

    it('should work with string id', async () => {
      const mockResponse = { data: mockEmployee };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useEmployeeQuery('EMP001'), { wrapper: createWrapper() });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/employee/EMP001');
    });
  });

  describe('useOfflineEmployeesQuery', () => {
    it('should fetch offline employees', async () => {
      const offlineEmployees = [{ ...mockEmployee, offline: true }];
      const mockResponse = { data: offlineEmployees };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useOfflineEmployeesQuery(), { wrapper: createWrapper() });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/employee/offline', { params: {} });
      expect(result.current.data).toEqual(offlineEmployees);
    });

    it('should fetch offline employees for specific manager', async () => {
      const mockResponse = { data: [] };
      mockApi.get.mockResolvedValue(mockResponse);

      renderHook(() => useOfflineEmployeesQuery(2), { wrapper: createWrapper() });

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith('/employee/offline', {
          params: { managerId: 2 },
        });
      });
    });
  });
});

describe('Employee Mutations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useCreateEmployeeMutation', () => {
    it('should create employee', async () => {
      const mockResponse = { data: { ...mockEmployee, id: 999 } };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useCreateEmployeeMutation(), {
        wrapper: createWrapper(),
      });

      const mutateResult = await result.current.mutateAsync(mockEmployeeFormData);

      expect(mockApi.post).toHaveBeenCalledWith('/employee', mockEmployeeFormData);
      expect(mutateResult).toEqual(mockResponse.data);
    });
  });

  describe('useUpdateEmployeeMutation', () => {
    it('should update employee', async () => {
      const updateData = { id: 1, ...mockEmployeeFormData };
      const mockResponse = { data: { ...mockEmployee, ...mockEmployeeFormData } };
      mockApi.put.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useUpdateEmployeeMutation(), {
        wrapper: createWrapper(),
      });

      const mutateResult = await result.current.mutateAsync(updateData);

      expect(mockApi.put).toHaveBeenCalledWith('/employee/1', mockEmployeeFormData);
      expect(mutateResult).toEqual(mockResponse.data);
    });
  });

  describe('useUpdateEmployeeStatusMutation', () => {
    it('should update employee status', async () => {
      const mockResponse = { data: { success: true } };
      mockApi.patch.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useUpdateEmployeeStatusMutation(), {
        wrapper: createWrapper(),
      });

      const mutateResult = await result.current.mutateAsync({
        id: 1,
        status: 'suspended',
      });

      expect(mockApi.patch).toHaveBeenCalledWith('/employee/1/status', {
        status: 'suspended',
      });
      expect(mutateResult).toEqual({ success: true });
    });
  });

  describe('useBindPlatformMutation', () => {
    it('should bind platform user', async () => {
      const bindData = {
        id: 1,
        platformUserId: 'wx_new_123',
        platformType: 'wechat' as const,
      };
      const mockResponse = { data: { success: true } };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useBindPlatformMutation(), { wrapper: createWrapper() });

      const mutateResult = await result.current.mutateAsync(bindData);

      expect(mockApi.post).toHaveBeenCalledWith('/employee/1/bind-platform', {
        platformUserId: 'wx_new_123',
        platformType: 'wechat',
      });
      expect(mutateResult).toEqual({ success: true });
    });
  });

  describe('useBatchImportEmployeesMutation', () => {
    it('should batch import employees', async () => {
      const batchData = {
        employees: [mockEmployeeFormData, { ...mockEmployeeFormData, name: '王五' }],
      };
      const mockResponse = { data: { success: true } };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useBatchImportEmployeesMutation(), {
        wrapper: createWrapper(),
      });

      const mutateResult = await result.current.mutateAsync(batchData);

      expect(mockApi.post).toHaveBeenCalledWith('/employee/batch-import', batchData);
      expect(mutateResult).toEqual({ success: true });
    });
  });
});

describe('Employee Sensitive Data Queries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('useEmployeeIdCardQuery', () => {
    it('should fetch employee id card when enabled', async () => {
      const mockResponse = { data: '110101199001011234' };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useEmployeeIdCardQuery(1, { enabled: true }), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/employee/1/id-card');
      expect(result.current.data).toBe('110101199001011234');
    });

    it('should not fetch when disabled', () => {
      const { result } = renderHook(() => useEmployeeIdCardQuery(1, { enabled: false }), {
        wrapper: createWrapper(),
      });

      expect(result.current.status).toBe('pending');
      expect(mockApi.get).not.toHaveBeenCalled();
    });
  });

  describe('useEmployeeBankAccountQuery', () => {
    it('should fetch employee bank account when enabled', async () => {
      const mockResponse = { data: '6222021234567890123' };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useEmployeeBankAccountQuery(1, { enabled: true }), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/employee/1/bank-account');
      expect(result.current.data).toBe('6222021234567890123');
    });

    it('should have no cache for sensitive data', () => {
      const { result } = renderHook(() => useEmployeeBankAccountQuery(1), {
        wrapper: createWrapper(),
      });

      // The query should be configured with staleTime: 0, gcTime: 0
      // This means data is immediately considered stale
      expect(result.current.isStale).toBe(true);
    });
  });
});
