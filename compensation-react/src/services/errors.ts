import type { AxiosRequestConfig } from 'axios';

export type ApiErrorInfo = {
  status?: number;
  message: string;
  error: unknown;
  config?: AxiosRequestConfig;
};

let handler: ((info: ApiErrorInfo) => void) | null = null;

export function setApiErrorHandler(h: (info: ApiErrorInfo) => void) {
  handler = h;
}

export function notifyApiError(info: ApiErrorInfo) {
  if (handler) handler(info);
}

