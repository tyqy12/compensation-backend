import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { ResourceTypeFilter } from '@services/resourcesV2';
import {
  fetchResourcesV2,
  fetchResourceTreeV2,
  createResourceV2,
  updateResourceV2,
  deleteResourceV2,
  sortResourcesV2,
  importResourcesV2,
  exportResourcesV2,
  type ResourceV2,
  type ResourceV2Input,
} from '@services/resourcesV2';

const qk = {
  resources: (type?: ResourceTypeFilter) => ['admin', 'resourcesV2', 'list', type] as const,
  resourceTree: (type?: ResourceTypeFilter) => ['admin', 'resourcesV2', 'tree', type] as const,
};

export function useResourcesV2Query(type?: ResourceTypeFilter) {
  return useQuery<ResourceV2[]>({
    queryKey: qk.resources(type),
    queryFn: () => fetchResourcesV2(type ? { type } : undefined),
  });
}

/**
 * 获取资源树（嵌套结构）
 */
export function useResourceTreeV2Query(type?: ResourceTypeFilter) {
  return useQuery<ResourceV2[]>({
    queryKey: qk.resourceTree(type),
    queryFn: () => fetchResourceTreeV2(type ? { type } : undefined),
  });
}

export function useCreateResourceV2Mutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ResourceV2Input) => createResourceV2(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'resourcesV2'] });
    },
  });
}

export function useUpdateResourceV2Mutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: ResourceV2Input }) => updateResourceV2(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'resourcesV2'] });
    },
  });
}

export function useDeleteResourceV2Mutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteResourceV2(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'resourcesV2'] });
    },
  });
}

export function useSortResourcesV2Mutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (items: Array<{ id: number; orderNum: number }>) => sortResourcesV2(items),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'resourcesV2'] });
    },
  });
}

export function useImportResourcesV2Mutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (items: ResourceV2Input[]) => importResourcesV2(items),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'resourcesV2'] });
    },
  });
}

export function useExportResourcesV2Query(enabled = false) {
  return useQuery({
    queryKey: ['admin', 'resourcesV2', 'export'],
    queryFn: () => exportResourcesV2(),
    enabled,
  });
}

