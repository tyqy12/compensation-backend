import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  listAppRegistries,
  getAppRegistry,
  createAppRegistry,
  updateAppRegistry,
  rotateAppRegistrySecret,
  listAppDataGrants,
  createAppDataGrant,
  revokeAppDataGrant,
  type AppRegistryQueryParams,
} from '@services/appRegistry';
import type {
  AppDataGrantDto,
  AppDataGrantRequest,
  AppRegistryDto,
  AppRegistryRequest,
  AppRegistrySecretDto,
} from '@types/openapi';
import { qk } from '@types/api';

export function useAppRegistriesQuery(
  params: AppRegistryQueryParams,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.appRegistryList(params),
    queryFn: () => listAppRegistries(params),
    enabled: options?.enabled ?? true,
  });
}

export function useAppRegistryQuery(id: number | string, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: qk.appRegistryDetail(id),
    queryFn: () => getAppRegistry(id),
    enabled: !!id && (options?.enabled ?? true),
  });
}

export function useCreateAppRegistryMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: AppRegistryRequest) => createAppRegistry(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'app-registry'] });
    },
  });
}

export function useUpdateAppRegistryMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: number | string; payload: AppRegistryRequest }) =>
      updateAppRegistry(id, payload),
    onSuccess: (_, variables) => {
      qc.invalidateQueries({ queryKey: ['admin', 'app-registry'] });
      if (variables?.id !== undefined && variables?.id !== null) {
        qc.invalidateQueries({ queryKey: qk.appRegistryDetail(variables.id) });
      }
    },
  });
}

export function useRotateAppRegistrySecretMutation() {
  return useMutation({
    mutationFn: (id: number | string) => rotateAppRegistrySecret(id),
  });
}

export function useAppDataGrantsQuery(id: number | string, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['admin', 'app-registry', id, 'data-grants'],
    queryFn: () => listAppDataGrants(id),
    enabled: !!id && (options?.enabled ?? true),
  });
}

export function useCreateAppDataGrantMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: number | string; payload: AppDataGrantRequest }) =>
      createAppDataGrant(id, payload),
    onSuccess: (_, variables) => {
      qc.invalidateQueries({ queryKey: ['admin', 'app-registry', variables.id, 'data-grants'] });
    },
  });
}

export function useRevokeAppDataGrantMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, grantId }: { id: number | string; grantId: number | string }) =>
      revokeAppDataGrant(id, grantId),
    onSuccess: (_, variables) => {
      qc.invalidateQueries({ queryKey: ['admin', 'app-registry', variables.id, 'data-grants'] });
    },
  });
}

export type AppRegistryWithSecret = AppRegistryDto & { clientSecret?: string };

export type {
  AppRegistryQueryParams,
  AppRegistryDto,
  AppRegistryRequest,
  AppRegistrySecretDto,
  AppDataGrantDto,
  AppDataGrantRequest,
} from '@types/openapi';
