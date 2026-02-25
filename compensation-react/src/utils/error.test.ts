import { describe, it, expect, vi } from 'vitest';
import axios from 'axios';
import { getStatusText, toMessage } from './error';

// Mock axios
vi.mock('axios', () => ({
  default: {
    isAxiosError: vi.fn(),
  },
}));

const mockAxios = axios as any;

describe('Error Utilities', () => {
  describe('getStatusText', () => {
    it('should return correct text for common status codes', () => {
      expect(getStatusText(400)).toBe('请求参数错误');
      expect(getStatusText(401)).toBe('未认证或登录已过期');
      expect(getStatusText(403)).toBe('没有权限执行该操作');
      expect(getStatusText(404)).toBe('资源不存在');
      expect(getStatusText(408)).toBe('请求超时');
      expect(getStatusText(500)).toBe('服务器错误');
      expect(getStatusText(502)).toBe('网关错误');
      expect(getStatusText(503)).toBe('服务不可用');
    });

    it('should return undefined for unknown status codes', () => {
      expect(getStatusText(200)).toBeUndefined();
      expect(getStatusText(301)).toBeUndefined();
      expect(getStatusText(999)).toBeUndefined();
    });

    it('should handle undefined status', () => {
      expect(getStatusText(undefined)).toBeUndefined();
    });
  });

  describe('toMessage', () => {
    beforeEach(() => {
      vi.clearAllMocks();
    });

    it('should format axios error with response message', () => {
      const axiosError = {
        response: {
          data: {
            message: 'Custom error message',
          },
          status: 400,
        },
        message: 'Request failed',
      };

      mockAxios.isAxiosError.mockReturnValue(true);
      expect(toMessage(axiosError)).toBe('Custom error message');
    });

    it('should use status text when no response message', () => {
      const axiosError = {
        response: {
          data: {},
          status: 404,
        },
        message: 'Request failed',
      };

      mockAxios.isAxiosError.mockReturnValue(true);
      expect(toMessage(axiosError)).toBe('资源不存在');
    });

    it('should handle timeout errors', () => {
      const timeoutError = {
        code: 'ECONNABORTED',
        message: 'timeout of 5000ms exceeded',
      };

      mockAxios.isAxiosError.mockReturnValue(true);
      expect(toMessage(timeoutError)).toBe('请求超时，请稍后重试');
    });

    it('should handle network errors', () => {
      const networkError = {
        message: 'Network Error',
      };

      mockAxios.isAxiosError.mockReturnValue(true);
      expect(toMessage(networkError)).toBe('网络异常，请检查网络');
    });

    it('should fallback to original message for axios errors', () => {
      const axiosError = {
        message: 'Original error message',
        response: {
          data: {},
        },
      };

      mockAxios.isAxiosError.mockReturnValue(true);
      expect(toMessage(axiosError)).toBe('Original error message');
    });

    it('should handle regular Error objects', () => {
      const error = new Error('Regular error message');

      mockAxios.isAxiosError.mockReturnValue(false);
      expect(toMessage(error)).toBe('Regular error message');
    });

    it('should convert non-error values to string', () => {
      mockAxios.isAxiosError.mockReturnValue(false);

      expect(toMessage('string error')).toBe('string error');
      expect(toMessage(42)).toBe('42');
      expect(toMessage(null)).toBe('null');
      expect(toMessage(undefined)).toBe('undefined');
    });

    it('should handle axios error without response', () => {
      const axiosError = {
        message: 'Connection failed',
      };

      mockAxios.isAxiosError.mockReturnValue(true);
      expect(toMessage(axiosError)).toBe('Connection failed');
    });

    it('should handle axios error with empty message', () => {
      const axiosError = {
        message: '',
        response: {
          data: {},
          status: 500,
        },
      };

      mockAxios.isAxiosError.mockReturnValue(true);
      expect(toMessage(axiosError)).toBe('服务器错误');
    });

    it('should provide fallback for axios errors without message or status text', () => {
      const axiosError = {
        response: {
          data: {},
          status: 299, // unknown status
        },
      };

      mockAxios.isAxiosError.mockReturnValue(true);
      expect(toMessage(axiosError)).toBe('请求失败，请稍后重试');
    });

    it('should handle complex error objects', () => {
      const complexError = {
        response: {
          data: {
            message: 'Validation failed',
            errors: ['Field required', 'Invalid format'],
          },
          status: 422,
        },
      };

      mockAxios.isAxiosError.mockReturnValue(true);
      expect(toMessage(complexError)).toBe('Validation failed');
    });

    it('should prioritize response message over status text', () => {
      const axiosError = {
        response: {
          data: {
            message: 'Custom message',
          },
          status: 400, // has status text
        },
      };

      mockAxios.isAxiosError.mockReturnValue(true);
      expect(toMessage(axiosError)).toBe('Custom message');
    });
  });
});
