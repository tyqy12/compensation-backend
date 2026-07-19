import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosError } from 'axios';
import { toMessage } from '@utils/error';
import { notifyApiError } from '@services/errors';
import { redirectToLogin } from '@services/navigation';
import { setSession, store } from '@services/stores/authSlice';

// Base URL: dev 环境默认走 Vite 代理 '/api'，生产从环境变量读取
const ENV = (import.meta as any)?.env || {};
// 开发环境一律走 Vite 代理 '/api'，避免被本地环境变量覆盖导致跨域
// 注意：Vite `mode` 可能是自定义值（如 dev/staging），不要用 MODE === 'development' 做判断
const BASE_URL = ENV.DEV ? '/api' : (ENV.VITE_API_BASE_URL || '/api');

// Create axios instance (match test expectation exactly)
export const api: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
});

export type ApiResponse<T> = {
  code: number;
  message: string;
  data?: T;
  traceId?: string;
  timestamp?: string;
  success?: boolean;
  extra?: Record<string, unknown>;
};

// 获取当前路径的工具函数（在 Router 上下文外安全使用）
const getCurrentPath = (): string => {
  // 尝试从各种可能的来源获取当前路径
  try {
    // 从 sessionStorage 获取（由路由更新）
    const savedPath = sessionStorage.getItem('current_path');
    if (savedPath) return savedPath;
  } catch {}
  return '/';
};

// 设置当前路径（由路由更新）
export const setCurrentPath = (path: string) => {
  try {
    sessionStorage.setItem('current_path', path);
  } catch {}
};

function isAuthEndpoint(config?: AxiosRequestConfig) {
  const url = config?.url ?? '';
  return [
    '/auth/login',
    '/auth/refresh',
    '/auth/oauth/callback',
    '/auth/logout',
  ].some((path) => url.includes(path));
}

type RetriableRequestConfig = AxiosRequestConfig & { _authRetry?: boolean };

let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = store.getState().auth.refreshToken;
  if (!refreshToken) {
    return null;
  }
  if (refreshPromise) {
    return refreshPromise;
  }

  const refreshUrl = `${BASE_URL.replace(/\/$/, '')}/auth/refresh`;
  refreshPromise = axios.post(refreshUrl, { refreshToken }, { timeout: 10000 })
    .then((response) => {
      const body = response.data as ApiResponse<{
        accessToken?: string;
        token?: string;
        refreshToken?: string;
      }>;
      const data = body?.data;
      const accessToken = data?.accessToken ?? data?.token;
      if (body?.code !== 0 || !accessToken) {
        return null;
      }
      store.dispatch(setSession({
        accessToken,
        refreshToken: data.refreshToken ?? refreshToken,
      }));
      return accessToken;
    })
    .catch(() => null)
    .finally(() => {
      refreshPromise = null;
    });
  return refreshPromise;
}

// Attach Authorization header from store/localStorage
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token');
  if (token && !(config.headers && 'Authorization' in config.headers && config.headers.Authorization)) {
    if (!config.headers) (config as any).headers = {};
    (config.headers as any).Authorization = `Bearer ${token}`;
  }
  return config;
});

// On 401, refresh the access token once before clearing the session.
api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const status = error.response?.status;
    if (status === 401) {
      const requestConfig = error.config as RetriableRequestConfig | undefined;
      if (!isAuthEndpoint(requestConfig) && !requestConfig?._authRetry) {
        const accessToken = await refreshAccessToken();
        if (accessToken && requestConfig) {
          requestConfig._authRetry = true;
          requestConfig.headers = requestConfig.headers ?? {};
          (requestConfig.headers as any).Authorization = `Bearer ${accessToken}`;
          return api.request(requestConfig);
        }
      }
      if (isAuthEndpoint(requestConfig)) {
        return Promise.reject(error);
      }
      try {
        localStorage.removeItem('auth_token');
        localStorage.removeItem('auth');
      } catch {}
      const handledByUi = notifyApiError({
        status,
        message: '登录已过期，请重新登录',
        error,
        config: error.config,
      });
      if (!handledByUi) {
        const currentPath = getCurrentPath();
        if (currentPath !== '/login') {
          sessionStorage.setItem('auth_redirect', currentPath);
          redirectToLogin();
        }
      }
      return Promise.reject(error);
    }
    const message = toMessage(error);
    notifyApiError({ status, message, error, config: error.config });
    return Promise.reject(error);
  },
);

export class ApiResponseError extends Error {
  code: number;
  traceId?: string;

  constructor(code: number, message: string, traceId?: string) {
    super(message);
    this.name = 'ApiResponseError';
    this.code = code;
    this.traceId = traceId;
  }
}

export function unwrap<T>(res: ApiResponse<T>): T {
  // 后端契约：code=0 表示成功；其余为业务错误码（以 ErrorCode 为准）
  if (!res || typeof res.code !== 'number') {
    throw new ApiResponseError(-1, '响应格式不合法');
  }
  if (res.code !== 0) {
    throw new ApiResponseError(res.code, res.message || '请求失败', res.traceId);
  }
  // 后端在 code=0 且无业务载荷时可能省略 data（如 @JsonInclude(NON_NULL)）
  if (!Object.prototype.hasOwnProperty.call(res, 'data')) {
    return undefined as T;
  }
  return res.data as T;
}

// 重新导出类型以供使用
export type { PagedResponse } from '../types/api';
export { getPagedRecords } from '../types/api';

export default api;
