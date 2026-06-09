import { beforeEach, describe, expect, it, vi } from 'vitest';
import { consumePostLoginRedirect } from './authRedirect';

function createStorageMock() {
  const store = new Map<string, string>();
  return {
    getItem: vi.fn((key: string) => store.get(key) ?? null),
    setItem: vi.fn((key: string, value: string) => store.set(key, String(value))),
    removeItem: vi.fn((key: string) => store.delete(key)),
    clear: vi.fn(() => store.clear()),
  };
}

describe('consumePostLoginRedirect', () => {
  beforeEach(() => {
    Object.defineProperty(window, 'sessionStorage', {
      configurable: true,
      value: createStorageMock(),
    });
    sessionStorage.clear();
  });

  it('优先使用路由 state，并保留 search/hash', () => {
    sessionStorage.setItem('auth_redirect', '/payments/batches');

    const target = consumePostLoginRedirect({
      from: {
        pathname: '/payroll/confirmations',
        search: '?batchId=1001',
        hash: '#line-2',
      },
    });

    expect(target).toBe('/payroll/confirmations?batchId=1001#line-2');
    expect(sessionStorage.getItem('auth_redirect')).toBeNull();
  });

  it('没有路由 state 时使用 axios 兜底写入的 auth_redirect', () => {
    sessionStorage.setItem('auth_redirect', '/payments/batches?status=submitted');

    expect(consumePostLoginRedirect(undefined)).toBe('/payments/batches?status=submitted');
    expect(sessionStorage.getItem('auth_redirect')).toBeNull();
  });

  it('过滤登录页自身，避免登录成功后回到登录页', () => {
    sessionStorage.setItem('auth_redirect', '/login?reason=session_timeout');

    expect(consumePostLoginRedirect({ from: { pathname: '/login' } })).toBe('/');
  });
});
