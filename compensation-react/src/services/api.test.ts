import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';

// Mock axios
vi.mock('axios');
const mockedAxios = vi.mocked(axios);
const redirectToLogin = vi.fn();

vi.mock('./navigation', () => ({
  redirectToLogin,
}));

// 创建一个模拟的axios实例
const mockAxiosInstance = {
  request: vi.fn(),
  interceptors: {
    request: {
      use: vi.fn(),
    },
    response: {
      use: vi.fn(),
    },
  },
};

function createStorageMock() {
  const store = new Map<string, string>();
  return {
    getItem: vi.fn((key: string) => store.get(key) ?? null),
    setItem: vi.fn((key: string, value: string) => store.set(key, String(value))),
    removeItem: vi.fn((key: string) => store.delete(key)),
    clear: vi.fn(() => store.clear()),
  };
}

describe('API服务', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: createStorageMock(),
    });
    Object.defineProperty(window, 'sessionStorage', {
      configurable: true,
      value: createStorageMock(),
    });
    localStorage.clear();
    sessionStorage.clear();
    window.history.replaceState({}, '', '/');
    mockAxiosInstance.interceptors.request.use.mockClear();
    mockAxiosInstance.interceptors.response.use.mockClear();
    mockAxiosInstance.request.mockReset();
    vi.mocked(axios.post).mockReset();
    (mockedAxios.create as any).mockReturnValue(mockAxiosInstance as any);
  });

  it('应该创建正确的axios实例', async () => {
    // 动态导入API模块
    const { api } = await import('./api');

    expect(mockedAxios.create).toHaveBeenCalledWith({
      baseURL: '/api',
      timeout: 10000,
    });
  });

  it('应该添加认证token到请求头', async () => {
    const token = 'test-token-123';
    localStorage.setItem('auth_token', token);

    // 动态导入API模块
    await import('./api');

    // 获取请求拦截器
    const requestInterceptor = mockAxiosInstance.interceptors.request.use.mock.calls[0][0];
    const mockConfig = { headers: {} };

    const result = requestInterceptor(mockConfig);

    expect(result.headers.Authorization).toBe(`Bearer ${token}`);
  });

  it('有UI错误处理器时，401只通知UI并清除token，不执行硬跳转', async () => {
    const mockError = {
      response: { status: 401 },
      config: {},
    };
    const handler = vi.fn();

    const { setApiErrorHandler } = await import('./errors');
    setApiErrorHandler(handler);
    await import('./api');
    const responseInterceptor = mockAxiosInstance.interceptors.response.use.mock.calls[0][1];
    const hrefBefore = window.location.href;
    localStorage.setItem('auth_token', 'expired-token');

    await expect(responseInterceptor(mockError)).rejects.toBe(mockError);

    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(handler).toHaveBeenCalledWith(expect.objectContaining({
      status: 401,
      message: '登录已过期，请重新登录',
    }));
    expect(redirectToLogin).not.toHaveBeenCalled();
    expect(window.location.href).toBe(hrefBefore);
    expect(sessionStorage.getItem('auth_redirect')).toBeNull();
  });

  it('没有UI错误处理器时，401保留登录页跳转兜底', async () => {
    const mockError = {
      response: { status: 401 },
      config: {},
    };

    await import('./api');
    const responseInterceptor = mockAxiosInstance.interceptors.response.use.mock.calls[0][1];
    localStorage.setItem('auth_token', 'expired-token');
    sessionStorage.setItem('current_path', '/payroll/batches');

    await expect(responseInterceptor(mockError)).rejects.toBe(mockError);

    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(sessionStorage.getItem('auth_redirect')).toBe('/payroll/batches');
    expect(redirectToLogin).toHaveBeenCalledTimes(1);
  });

  it('登录接口401只抛给调用方，不清会话也不硬跳转', async () => {
    const mockError = {
      response: { status: 401, data: { message: '用户名或密码错误' } },
      config: { url: '/auth/login' },
    };

    await import('./api');
    const responseInterceptor = mockAxiosInstance.interceptors.response.use.mock.calls[0][1];
    localStorage.setItem('auth_token', 'existing-token');
    localStorage.setItem('auth', '{"user":{"username":"admin"}}');
    sessionStorage.setItem('current_path', '/payroll/batches');

    await expect(responseInterceptor(mockError)).rejects.toBe(mockError);

    expect(localStorage.getItem('auth_token')).toBe('existing-token');
    expect(localStorage.getItem('auth')).toBe('{"user":{"username":"admin"}}');
    expect(sessionStorage.getItem('auth_redirect')).toBeNull();
    expect(redirectToLogin).not.toHaveBeenCalled();
  });

  it('访问令牌过期时使用 refreshToken 刷新并重试原请求', async () => {
    localStorage.setItem('auth', JSON.stringify({
      user: { id: '1', username: 'admin', roles: ['ADMIN'] },
      accessToken: 'expired-token',
      refreshToken: 'refresh-token',
      roles: ['ADMIN'],
    }));
    vi.mocked(axios.post).mockResolvedValue({
      data: {
        code: 0,
        data: { token: 'new-token', refreshToken: 'new-refresh-token' },
      },
    } as any);
    mockAxiosInstance.request.mockResolvedValue({ data: { code: 0 } });

    await import('./api');
    const responseInterceptor = mockAxiosInstance.interceptors.response.use.mock.calls[0][1];
    const requestConfig = { url: '/auth/me/resources', headers: {} };
    const result = await responseInterceptor({
      response: { status: 401 },
      config: requestConfig,
    });

    expect(result).toEqual({ data: { code: 0 } });
    expect(axios.post).toHaveBeenCalledWith('/api/auth/refresh',
      { refreshToken: 'refresh-token' },
      { timeout: 10000 });
    expect(mockAxiosInstance.request).toHaveBeenCalledWith(expect.objectContaining({
      _authRetry: true,
      headers: expect.objectContaining({ Authorization: 'Bearer new-token' }),
    }));
    expect(localStorage.getItem('auth_token')).toBe('new-token');
  });
});
