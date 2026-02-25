import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { SysResource } from '@types/api';
import {
  fetchResources,
  createResource,
  updateResource,
  deleteResource,
  sortResources,
  importResources,
  exportResources,
  type ResourceInput,
  type ResourceTypeFilter,
} from '@services/resources';

const qk = {
  resources: (type?: ResourceTypeFilter) => ['admin', 'resources', type] as const,
};

export function useResourcesQuery(type?: ResourceTypeFilter) {
  return useQuery<SysResource[]>({
    queryKey: qk.resources(type),
    queryFn: () => fetchResources(type ? { type } : undefined),
  });
}

export function useCreateResourceMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ResourceInput) => createResource(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'resources'] }),
  });
}

export function useUpdateResourceMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: ResourceInput }) => updateResource(id, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'resources'] }),
  });
}

export function useDeleteResourceMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteResource(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'resources'] }),
  });
}

export function useSortResourcesMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (items: Array<{ id: number; orderNum: number }>) => sortResources(items),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'resources'] }),
  });
}

export function useImportResourcesMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (items: SysResource[]) => importResources(items),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'resources'] }),
  });
}

export function useExportResourcesQuery(enabled = false) {
  return useQuery({
    queryKey: ['admin', 'resources', 'export'],
    queryFn: () => exportResources(),
    enabled,
  });
}

