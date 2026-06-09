import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '@services/api';
import {
  createResource,
  deleteResource,
  exportResources,
  fetchResources,
  importResources,
  sortResources,
  updateResource,
} from './resources';

vi.mock('@services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  unwrap: vi.fn((data) => data),
}));

const mockApi = api as any;

describe('resources service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('uses the mounted v2 admin resource endpoints', async () => {
    const payload = {
      type: 'MENU' as const,
      code: 'admin.demo',
      name: 'Demo',
      status: 'enabled' as const,
    };

    mockApi.get.mockResolvedValue({ data: [] });
    mockApi.post.mockResolvedValue({ data: payload });
    mockApi.put.mockResolvedValue({ data: payload });
    mockApi.delete.mockResolvedValue({ data: null });

    await fetchResources({ type: 'API' });
    await createResource(payload);
    await updateResource(10, payload);
    await deleteResource(10);
    await sortResources([{ id: 10, orderNum: 1 }]);
    await importResources([payload as any]);
    await exportResources();

    expect(mockApi.get).toHaveBeenCalledWith('/admin/resources/v2/list', {
      params: { type: 'API' },
    });
    expect(mockApi.post).toHaveBeenCalledWith('/admin/resources/v2', payload);
    expect(mockApi.put).toHaveBeenCalledWith('/admin/resources/v2/10', payload);
    expect(mockApi.delete).toHaveBeenCalledWith('/admin/resources/v2/10');
    expect(mockApi.post).toHaveBeenCalledWith('/admin/resources/v2/sort', [
      { id: 10, orderNum: 1 },
    ]);
    expect(mockApi.post).toHaveBeenCalledWith('/admin/resources/v2/import', [payload]);
    expect(mockApi.get).toHaveBeenCalledWith('/admin/resources/v2/export');
  });
});
