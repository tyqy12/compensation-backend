import { describe, it, expect } from 'vitest';
import { hasAnyRole, normalizeRoles } from './rbac';

describe('rbac utils', () => {
  it('normalizeRoles removes ROLE_ prefix', () => {
    expect(normalizeRoles(['ROLE_ADMIN', 'USER'])).toEqual(['ADMIN', 'USER']);
  });

  it('hasAnyRole matches with or without ROLE_', () => {
    expect(hasAnyRole(['ROLE_ADMIN'], ['ADMIN'])).toBe(true);
    expect(hasAnyRole(['USER'], ['ROLE_USER'])).toBe(true);
    expect(hasAnyRole(['USER'], ['ADMIN'])).toBe(false);
  });
});

