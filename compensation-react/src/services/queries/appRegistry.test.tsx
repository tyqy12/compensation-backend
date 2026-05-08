import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  useAppRegistriesQuery,
  useAppRegistryQuery,
  useCreateAppRegistryMutation,
  useUpdateAppRegistryMutation,
  useRotateAppRegistrySecretMutation,
} from './appRegistry';
import {
  listAppRegistries,
  getAppRegistry,
  createAppRegistry,
  updateAppRegistry,
  rotateAppRegistrySecret,
} from '@services/appRegistry';
import type { AppRegistryDto, AppRegistrySecretDto } from '@types/openapi';

vi.mock('@services/appRegistry', async () => {
  const actual = await vi.importActual<typeof import('@services/appRegistry')>('@services/appRegistry');
  return {
    ...actual,
    listAppRegistries: vi.fn(),
    getAppRegistry: vi.fn(),
    createAppRegistry: vi.fn(),
    updateAppRegistry: vi.fn(),
    rotateAppRegistrySecret: vi.fn(),
  };
});

const mockedList = listAppRegistries as unknown as ReturnType<typeof vi.fn>;
const mockedGet = getAppRegistry as unknown as ReturnType<typeof vi.fn>;
const mockedCreate = createAppRegistry as unknown as ReturnType<typeof vi.fn>;
const mockedUpdate = updateAppRegistry as unknown as ReturnType<typeof vi.fn>;
const mockedRotate = rotateAppRegistrySecret as unknown as ReturnType<typeof vi.fn>;

const createWrapper = (client: QueryClient) => {
  const Wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  return Wrapper;
};

describe('app registry queries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches app registry list', async () => {
    const serviceResponse = {
      total: 1,
      current: 1,
      size: 10,
      records: [
        {
          id: 1,
          appName: 'Test App',
          clientId: 'app_123',
          scopes: ['payroll:read'],
          status: 'enabled',
        },
      ],
    };

    mockedList.mockResolvedValue(serviceResponse);

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    const { result } = renderHook(
      () => useAppRegistriesQuery({ current: 1, size: 10 }),
      { wrapper: createWrapper(queryClient) },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedList).toHaveBeenCalledWith({ current: 1, size: 10 });
    expect(result.current.data).toEqual(serviceResponse);
  });

  it('fetches single app registry', async () => {
    const registry: AppRegistryDto = {
      id: 2,
      appName: 'Finance Bot',
      clientId: 'finance_bot',
      scopes: ['payslip:read'],
      status: 'enabled',
    };

    mockedGet.mockResolvedValue(registry);

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    const { result } = renderHook(() => useAppRegistryQuery(2), {
      wrapper: createWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedGet).toHaveBeenCalledWith(2);
    expect(result.current.data).toEqual(registry);
  });

  it('creates app registry and invalidates list', async () => {
    const registry: AppRegistryDto & { clientSecret?: string } = {
      id: 3,
      appName: 'New App',
      clientId: 'new_app',
      status: 'enabled',
      scopes: ['payroll:read'],
      clientSecret: 'secret',
    };

    mockedCreate.mockResolvedValue(registry);

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useCreateAppRegistryMutation(), {
      wrapper: createWrapper(queryClient),
    });

    await act(async () => {
      await result.current.mutateAsync({
        appName: 'New App',
        scopes: ['payroll:read'],
        status: 'enabled',
      });
    });

    expect(mockedCreate).toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['admin', 'app-registry'] });
  });

  it('updates app registry and refreshes detail', async () => {
    const registry: AppRegistryDto = {
      id: 4,
      appName: 'Update App',
      clientId: 'update_app',
      scopes: ['payroll:read'],
      status: 'enabled',
    };

    mockedUpdate.mockResolvedValue(registry);

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateAppRegistryMutation(), {
      wrapper: createWrapper(queryClient),
    });

    await act(async () => {
      await result.current.mutateAsync({
        id: 4,
        payload: {
          appName: 'Update App',
          scopes: ['payroll:read', 'payslip:read'],
        },
      });
    });

    expect(mockedUpdate).toHaveBeenCalledWith(4, {
      appName: 'Update App',
      scopes: ['payroll:read', 'payslip:read'],
    });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['admin', 'app-registry'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['admin', 'app-registry', 4] });
  });

  it('rotates app registry secret', async () => {
    const secret: AppRegistrySecretDto = {
      clientId: 'rotate_app',
      clientSecret: 'new_secret',
      expiresIn: 1800,
    };

    mockedRotate.mockResolvedValue(secret);

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });

    const { result } = renderHook(() => useRotateAppRegistrySecretMutation(), {
      wrapper: createWrapper(queryClient),
    });

    await act(async () => {
      const resp = await result.current.mutateAsync(5);
      expect(resp).toEqual(secret);
    });

    expect(mockedRotate).toHaveBeenCalledWith(5);
  });
});
