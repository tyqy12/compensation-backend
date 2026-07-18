import { describe, expect, it } from 'vitest';
import type { SysResource } from '@types/api';
import { buildResourceTree, flattenAllowedPaths } from './permissions';

const resource = (overrides: Partial<SysResource>): SysResource => ({
  id: 1,
  type: 'MENU',
  code: 'resource',
  name: '资源',
  status: 'enabled',
  ...overrides,
});

describe('permission resource tree', () => {
  it('filters disabled and hidden descendants and removes empty groups', () => {
    const tree = buildResourceTree([
      resource({ id: 1, code: 'payroll', name: '薪酬管理', path: null }),
      resource({ id: 2, code: 'active', name: '有效页面', path: '/active', parentId: 1 }),
      resource({ id: 3, code: 'disabled', name: '旧页面', path: '/disabled', parentId: 1, status: 'disabled' }),
      resource({ id: 4, code: 'empty', name: '空分组', path: null }),
      resource({ id: 5, code: 'hidden', name: '隐藏页面', path: '/hidden', meta: { hidden: true } }),
    ]);

    expect(tree.map((node) => node.code)).toEqual(['payroll']);
    expect(tree[0].children?.map((node) => node.code)).toEqual(['active']);
  });

  it('recursively filters an already nested resource tree', () => {
    const tree = buildResourceTree([
      resource({
        id: 1,
        code: 'root',
        name: '根菜单',
        path: null,
        children: [
          resource({ id: 2, code: 'enabled', name: '启用', path: '/enabled', parentId: 1, children: [] }),
          resource({ id: 3, code: 'disabled', name: '禁用', path: '/disabled', parentId: 1, status: 'disabled', children: [] }),
        ],
      }) as SysResource,
    ]);

    expect(tree[0].children?.map((node) => node.code)).toEqual(['enabled']);
  });

  it('reads role metadata from the backend propsJson compatibility field', () => {
    const financeResource = resource({
      id: 2,
      code: 'finance-page',
      name: '财务页面',
      path: '/finance',
      ...( { propsJson: JSON.stringify({ roles: ['FINANCE'] }) } as any),
    });

    expect(buildResourceTree([financeResource], { respectRoles: true, userRoles: ['HR'] })).toHaveLength(0);
    expect(buildResourceTree([financeResource], { respectRoles: true, userRoles: ['FINANCE'] })).toHaveLength(1);
  });

  it('does not authorize disabled routes and de-duplicates equivalent paths', () => {
    const paths = flattenAllowedPaths([
      resource({ id: 2, path: '/payroll/batches' }),
      resource({ id: 3, path: '/payroll/batches' }),
      resource({ id: 4, path: '/legacy', status: 'disabled' }),
    ]);

    expect(paths).toEqual(['/payroll/batches']);
  });
});
