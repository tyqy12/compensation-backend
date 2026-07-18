import { describe, expect, it } from 'vitest';
import type { SysResource } from '@types/api';
import { findMatchingResource, resolveComponentPath } from './DynamicPageRenderer';

const resource = (overrides: Partial<SysResource>): SysResource => ({
  id: 1,
  type: 'VIEW',
  code: 'resource',
  name: '资源',
  path: '/resource',
  status: 'enabled',
  ...overrides,
});

describe('dynamic route resource resolution', () => {
  it('normalizes backend component paths and rejects retired components', () => {
    expect(resolveComponentPath('/src/pages/payroll/Operations.tsx')).toBeDefined();
    expect(resolveComponentPath('@pages/payroll/Operations')).toBeDefined();
    expect(resolveComponentPath('payroll/Import')).toBeUndefined();
  });

  it('prefers the enabled resource whose component exists when paths overlap', () => {
    const match = findMatchingResource(
      [
        resource({ id: 1, code: 'legacy', name: '旧页面', component: 'payroll/Import' }),
        resource({ id: 2, code: 'active', name: '当前页面', component: 'payroll/Operations' }),
      ],
      '/resource/',
    );

    expect(match?.code).toBe('active');
  });

  it('returns no resource for an unknown address instead of a component error', () => {
    expect(findMatchingResource([resource({ path: '/known', component: 'payroll/Operations' })], '/unknown')).toBeUndefined();
  });
});
