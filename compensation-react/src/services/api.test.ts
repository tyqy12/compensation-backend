import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';

// Mock axios
vi.mock('axios');
const mockedAxios = vi.mocked(axios);

// 创建一个模拟的axios实例
const mockAxiosInstance = {
  interceptors: {
    request: {
      use: vi.fn(),
    },
    response: {
      use: vi.fn(),
    },
  },
};

(mockedAxios.create as any).mockReturnValue(mockAxiosInstance as any);

describe('API服务', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('应该创建正确的axios实例', async () => {
    // 动态导入API模块
    const { api } = await import('./api');

    expect(mockedAxios.create).toHaveBeenCalledWith({
      baseURL: 'http://localhost:8080/api',
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

  it('应该处理401错误', async () => {
    const mockError = {
      response: { status: 401 },
    };

    // 动态导入API模块
    await import('./api');

    // 获取响应拦截器
    const responseInterceptor = mockAxiosInstance.interceptors.response.use.mock.calls[0][1];

    // 模拟window.location.href
    delete (window as any).location;
    window.location = { href: '' } as any;

    responseInterceptor(mockError);

    expect(localStorage.getItem('auth_token')).toBeNull();
  });
});
