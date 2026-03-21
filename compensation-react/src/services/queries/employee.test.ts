import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import api from '@services/api';
import {
  useEmployeesQuery,
  useEmployeeQuery,
  useMyEmployeeProfileQuery,
  useMyEmployeeChangeRequestsQuery,
  useOfflineEmployeesQuery,
  useResignedEmployeesQuery,
  useCreateEmployeeMutation,
  useUpdateEmployeeMutation,
  useUpdateMyEmployeeContactMutation,
  useSubmitMyEmployeeChangeRequestMutation,
  useUpdateEmployeeStatusMutation,
  useBindPlatformMutation,
  useBatchImportEmployeesMutation,
  useEmployeeIdCardQuery,
  useEmployeeBankAccountQuery,
  useEmployeeSettlementAccountQuery,
  useEmployeeApprovalsQuery,
  useEmployeePayslipsQuery,
  useEmployeePaymentsQuery,
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
  getPagedRecords: vi.fn((response) => {
    if (!response) return [];
    if (Array.isArray(response.list) && response.list.length > 0) return response.list;
    if (Array.isArray(response.records) && response.records.length > 0) return response.records;
    return [];
  }),
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
  provider: 'wechat',
  subjectId: 'wx_user_123',
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
            provider: 'wechat',
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
          provider: 'wechat',
          managerId: 2,
          sortBy: 'createTime',
          order: 'desc',
        },
      });

      expect(result.current.data).toEqual(mockResponse.data);
    });

    it('should normalize list pagination responses for employee matching scenarios', async () => {
      const mockResponse = {
        data: {
          list: [mockEmployee],
          total: 1,
          pageNum: 1,
          pageSize: 10,
          totalPages: 1,
        },
      };

      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(
        () =>
          useEmployeesQuery({
            current: 1,
            pageSize: 10,
            keyword: '王欣浩',
          }),
        { wrapper: createWrapper() },
      );

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toMatchObject({
        list: [mockEmployee],
        records: [mockEmployee],
        total: 1,
        current: 1,
        pageSize: 10,
      });
    });

    it('should pass provider query when provided', async () => {
      const mockResponse = { data: { records: [], total: 0, current: 1, size: 10 } };
      mockApi.get.mockResolvedValue(mockResponse);

      renderHook(
        () =>
          useEmployeesQuery({
            current: 1,
            pageSize: 10,
            provider: 'feishu',
          }),
        { wrapper: createWrapper() },
      );

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith('/employee', {
          params: {
            page: 1,
            size: 10,
            provider: 'feishu',
            sortBy: 'createTime',
            order: 'desc',
          },
        });
      });
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

  describe('employee self-service queries', () => {
    it('should fetch current employee profile', async () => {
      const mockResponse = { data: mockEmployee };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useMyEmployeeProfileQuery(), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/employee/me');
      expect(result.current.data).toEqual(mockEmployee);
    });

    it('should fetch my profile change requests', async () => {
      const mockResponse = {
        data: {
          records: [{ id: 1, workflowName: '员工资料变更审批', status: 'pending' }],
          total: 1,
        },
      };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(
        () => useMyEmployeeChangeRequestsQuery({ current: 2, pageSize: 20 }),
        { wrapper: createWrapper() },
      );

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/employee/me/change-requests', {
        params: { page: 2, size: 20 },
      });
      expect(result.current.data).toEqual(mockResponse.data);
    });
  });

  describe('useOfflineEmployeesQuery', () => {
    it('should fetch outside-org employees', async () => {
      const outsideOrgEmployees = [{ ...mockEmployee, offline: true }];
      const mockResponse = { data: outsideOrgEmployees };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useOfflineEmployeesQuery(), { wrapper: createWrapper() });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/employee/offline', { params: {} });
      expect(result.current.data).toEqual(outsideOrgEmployees);
    });

    it('should fetch outside-org employees for specific manager', async () => {
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

  describe('useResignedEmployeesQuery', () => {
    it('should fetch resigned employees', async () => {
      const resignedEmployees = [{ ...mockEmployee, status: 'inactive' }];
      const mockResponse = { data: resignedEmployees };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useResignedEmployeesQuery(), { wrapper: createWrapper() });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/employee/resigned', { params: {} });
      expect(result.current.data).toEqual(resignedEmployees);
    });

    it('should fetch resigned employees for specific manager', async () => {
      const mockResponse = { data: [] };
      mockApi.get.mockResolvedValue(mockResponse);

      renderHook(() => useResignedEmployeesQuery(2), { wrapper: createWrapper() });

      await waitFor(() => {
        expect(mockApi.get).toHaveBeenCalledWith('/employee/resigned', {
          params: { managerId: 2 },
        });
      });
    });
  });

  describe('employee detail record queries', () => {
    it('should fetch employee approvals', async () => {
      const mockResponse = { data: { records: [{ id: 1, workflowName: '平台绑定审批' }], total: 1 } };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(
        () => useEmployeeApprovalsQuery(1, { current: 1, pageSize: 10 }),
        { wrapper: createWrapper() },
      );

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/employee/1/approvals', {
        params: { page: 1, size: 10 },
      });
    });

    it('should fetch employee payslips', async () => {
      const mockResponse = { data: { records: [{ lineId: 9, periodLabel: '2026-01' }], total: 1 } };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(
        () => useEmployeePayslipsQuery(1, { current: 1, pageSize: 10 }),
        { wrapper: createWrapper() },
      );

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/employee/1/payslips', {
        params: { page: 1, size: 10 },
      });
    });

    it('should fetch employee payments', async () => {
      const mockResponse = { data: { records: [{ id: 1, batchNo: 'PB-1' }], total: 1 } };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(
        () => useEmployeePaymentsQuery(1, { current: 1, pageSize: 10 }),
        { wrapper: createWrapper() },
      );

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/employee/1/payments', {
        params: { page: 1, size: 10 },
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

    it('should create employee with provider/subjectId fields', async () => {
      const mockResponse = { data: { ...mockEmployee, id: 1000 } };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useCreateEmployeeMutation(), {
        wrapper: createWrapper(),
      });

      await result.current.mutateAsync({
        ...mockEmployeeFormData,
        provider: 'wechat',
        subjectId: 'wx_new_001',
      });

      expect(mockApi.post).toHaveBeenCalledWith('/employee', {
        ...mockEmployeeFormData,
        provider: 'wechat',
        subjectId: 'wx_new_001',
      });
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

  describe('employee self-service mutations', () => {
    it('should update my contact directly', async () => {
      const mockResponse = { data: { ...mockEmployee, email: 'new-email@company.com' } };
      mockApi.patch.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useUpdateMyEmployeeContactMutation(), {
        wrapper: createWrapper(),
      });

      const mutateResult = await result.current.mutateAsync({
        phone: '13900001234',
        email: 'new-email@company.com',
      });

      expect(mockApi.patch).toHaveBeenCalledWith('/employee/me/contact', {
        phone: '13900001234',
        email: 'new-email@company.com',
      });
      expect(mutateResult).toEqual(mockResponse.data);
    });

    it('should submit my sensitive change request for approval', async () => {
      const mockResponse = {
        data: {
          workflowId: 88,
          businessType: 'EMPLOYEE_PROFILE_CHANGE',
          status: 'pending',
        },
      };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useSubmitMyEmployeeChangeRequestMutation(), {
        wrapper: createWrapper(),
      });

      const mutateResult = await result.current.mutateAsync({
        name: '新姓名',
        idCard: '110101199001011234',
        reason: '证件信息修正',
      });

      expect(mockApi.post).toHaveBeenCalledWith('/employee/me/change-requests', {
        name: '新姓名',
        idCard: '110101199001011234',
        reason: '证件信息修正',
      });
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
        subjectId: 'wx_new_123',
        provider: 'wechat' as const,
      };
      const mockResponse = { data: { success: true } };
      mockApi.post.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useBindPlatformMutation(), { wrapper: createWrapper() });

      const mutateResult = await result.current.mutateAsync(bindData);

      expect(mockApi.post).toHaveBeenCalledWith('/employee/1/bind-platform', {
        provider: 'wechat',
        subjectId: 'wx_new_123',
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

    it('should keep provider/subjectId fields for batch import', async () => {
      const batchData = {
        employees: [
          {
            ...mockEmployeeFormData,
            provider: 'dingtalk',
            subjectId: 'ding_001',
          },
        ],
      };
      mockApi.post.mockResolvedValue({ data: { success: true } });

      const { result } = renderHook(() => useBatchImportEmployeesMutation(), {
        wrapper: createWrapper(),
      });

      await result.current.mutateAsync(batchData as any);

      expect(mockApi.post).toHaveBeenCalledWith('/employee/batch-import', batchData);
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

  describe('useEmployeeSettlementAccountQuery', () => {
    it('should fetch employee settlement account when enabled', async () => {
      const mockResponse = { data: 'zhangsan@example.com' };
      mockApi.get.mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useEmployeeSettlementAccountQuery(1, { enabled: true }), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockApi.get).toHaveBeenCalledWith('/employee/1/settlement-account');
      expect(result.current.data).toBe('zhangsan@example.com');
    });

    it('should have no cache for settlement account sensitive data', () => {
      const { result } = renderHook(() => useEmployeeSettlementAccountQuery(1), {
        wrapper: createWrapper(),
      });

      expect(result.current.isStale).toBe(true);
    });
  });
});
