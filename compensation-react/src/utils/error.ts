import axios from 'axios';

export function getStatusText(status?: number): string | undefined {
  switch (status) {
    case 400:
      return '请求参数错误';
    case 401:
      return '未认证或登录已过期';
    case 403:
      return '没有权限执行该操作';
    case 404:
      return '资源不存在';
    case 408:
      return '请求超时';
    case 500:
      return '服务器错误';
    case 502:
      return '网关错误';
    case 503:
      return '服务不可用';
    default:
      return undefined;
  }
}

export function toMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as any;
    const resMsg = data?.message as string | undefined;
    const status = err.response?.status;
    const statusText = getStatusText(status);
    if (resMsg) return resMsg;
    if (statusText) return statusText;
    if ((err as any).code === 'ECONNABORTED') return '请求超时，请稍后重试';
    if (err.message?.toLowerCase().includes('network')) return '网络异常，请检查网络';
    return err.message || '请求失败，请稍后重试';
  }
  if (err instanceof Error) return err.message;
  return String(err);
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export function withActionPrefix(action: string, err: unknown): string {
  const message = toMessage(err).trim() || '请求失败，请稍后重试';
  const actionPrefix = new RegExp(`^${escapeRegExp(action)}\\s*[:：]`);
  if (actionPrefix.test(message)) {
    return message;
  }
  return `${action}：${message}`;
}
