import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosError } from 'axios';
import { toMessage } from '@utils/error';
import { notifyApiError } from '@services/errors';

// Base URL: dev 环境默认走 Vite 代理 '/api'，生产从环境变量读取
const ENV = (import.meta as any)?.env || {};
// 开发环境一律走 Vite 代理 '/api'，避免被本地环境变量覆盖导致跨域
// 注意：Vite `mode` 可能是自定义值（如 dev/staging），不要用 MODE === 'development' 做判断
const BASE_URL = ENV.DEV ? '/api' : (ENV.VITE_API_BASE_URL ?? 'http://localhost:8080/api');

// Create axios instance (match test expectation exactly)
export const api: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
});

export type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
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

// Attach Authorization header from store/localStorage
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token');
  if (token && !(config.headers && 'Authorization' in config.headers && config.headers.Authorization)) {
    if (!config.headers) (config as any).headers = {};
    (config.headers as any).Authorization = `Bearer ${token}`;
  }
  return config;
});

// On 401, clear token, notify, and redirect to login
api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const status = error.response?.status;
    if (status === 401) {
      try {
        localStorage.removeItem('auth_token');
        localStorage.removeItem('auth');
      } catch {}
      // 通知 UI 显示提示
      notifyApiError({ status, message: '登录已过期，请重新登录', error, config: error.config });
      // 跳转到登录页（避免重复跳转）
      const currentPath = getCurrentPath();
      if (currentPath !== '/login') {
        sessionStorage.setItem('auth_redirect', currentPath);
        window.location.href = '/login';
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
  // 防御性编程：data 字段可能不存在（如后端返回 null 被 @JsonInclude(NON_NULL) 省略）
  if (!('data' in res)) {
    console.warn('[API] 响应缺少 data 字段:', res);
    return undefined as T;
  }
  return res.data;
}

// 重新导出类型以供使用
export type { PagedResponse } from '../types/api';
export { getPagedRecords } from '../types/api';

export default api;
