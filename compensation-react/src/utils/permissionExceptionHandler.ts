/**
 * 权限异常处理器
 * 统一处理权限相关的异常情况
 */

import { message, Modal } from 'antd';
import { permissionCache } from '@services/permissionCache';
import type { PermissionException, PermissionExceptionType, ExceptionHandlerStrategy } from '@types/authException';
import { DEFAULT_EXCEPTION_STRATEGIES } from '@types/authException';

// 全局异常处理器
let exceptionHandler: ((exception: PermissionException) => void) | null = null;

/**
 * 设置全局异常处理器
 */
export function setExceptionHandler(handler: (exception: PermissionException) => void): void {
  exceptionHandler = handler;
}

/**
 * 默认异常处理逻辑
 */
export function handlePermissionException(exception: PermissionException): void {
  // 如果设置了全局处理器，优先使用
  if (exceptionHandler) {
    exceptionHandler(exception);
    return;
  }

  const strategy = DEFAULT_EXCEPTION_STRATEGIES[exception.type];

  switch (exception.type) {
    case 'UNAUTHORIZED':
    case 'TOKEN_INVALID':
    case 'SESSION_TIMEOUT':
    case 'CONCURRENT_LOGIN':
      // 需要登出的情况
      handleLogoutRequired(exception, strategy);
      break;

    case 'TOKEN_EXPIRED':
      // Token 过期，尝试刷新
      handleTokenExpired(exception, strategy);
      break;

    case 'FORBIDDEN':
      // 无权限访问，跳转 403
      handleForbidden(exception, strategy);
      break;

    case 'PERMISSION_DENIED':
    case 'VERSION_MISMATCH':
    case 'RESOURCE_DISABLED':
      // 权限相关错误，刷新权限
      handlePermissionDenied(exception, strategy);
      break;

    case 'RESOURCE_NOT_FOUND':
      // 资源不存在
      handleNotFound(exception, strategy);
      break;

    default:
      // 未知错误
      handleUnknownError(exception, strategy);
  }
}

/**
 * 处理需要登出的异常
 */
function handleLogoutRequired(exception: PermissionException, strategy: ExceptionHandlerStrategy): void {
  if (strategy.showMessage) {
    message.warning(exception.message || '请重新登录');
  }

  // 清除权限缓存
  permissionCache.clearAll();

  // 跳转登录页
  if (strategy.redirectTo) {
    const redirectUrl = encodeURIComponent(window.location.href);
    window.location.href = `${strategy.redirectTo}?redirect=${redirectUrl}`;
  }
}

/**
 * 处理 Token 过期
 */
function handleTokenExpired(exception: PermissionException, strategy: ExceptionHandlerStrategy): void {
  if (strategy.showMessage) {
    message.info('登录状态已过期，正在刷新...');
  }

  if (strategy.customAction) {
    strategy.customAction();
  } else {
    // 默认刷新权限
    permissionCache.invalidateCache();
  }
}

/**
 * 处理无权限访问
 */
function handleForbidden(exception: PermissionException, strategy: ExceptionHandlerStrategy): void {
  if (strategy.showMessage && exception.message) {
    message.warning(exception.message);
  }

  if (strategy.redirectTo) {
    // 显示权限不足页面
    showForbiddenPage(exception);
  }
}

/**
 * 处理权限被拒绝
 */
function handlePermissionDenied(exception: PermissionException, strategy: ExceptionHandlerStrategy): void {
  if (strategy.showMessage) {
    Modal.warning({
      title: '权限不足',
      content: exception.message || '您没有执行此操作的权限',
      okText: '确定',
    });
  }

  if (strategy.requireRefresh) {
    // 刷新权限配置
    permissionCache.invalidateCache();
  }
}

/**
 * 处理资源不存在
 */
function handleNotFound(exception: PermissionException, strategy: ExceptionHandlerStrategy): void {
  if (strategy.showMessage) {
    message.error(exception.message || '请求的资源不存在');
  }

  if (strategy.redirectTo) {
    window.location.href = strategy.redirectTo;
  }
}

/**
 * 处理未知错误
 */
function handleUnknownError(exception: PermissionException, strategy: ExceptionHandlerStrategy): void {
  if (strategy.showMessage) {
    message.error(exception.message || '发生未知错误，请稍后重试');
  }

  console.error('Permission exception:', exception);
}

/**
 * 显示 403 页面
 */
function showForbiddenPage(exception: PermissionException): void {
  const redirectUrl = encodeURIComponent(window.location.href);
  let forbiddenUrl = `/403?redirect=${redirectUrl}`;

  if (exception.requiredPermissions && exception.requiredPermissions.length > 0) {
    const permissionsParam = encodeURIComponent(JSON.stringify(exception.requiredPermissions));
    forbiddenUrl = `${forbiddenUrl}&required=${permissionsParam}`;
  }

  window.location.href = forbiddenUrl;
}

/**
 * 从 Axios 错误创建权限异常
 */
export function createPermissionException(
  error: { response?: { status: number; data?: unknown }; message?: string }
): PermissionException | null {
  if (!error.response) return null;

  const status = error.response.status;
  const responseData = error.response.data as Record<string, unknown> | undefined;
  const timestamp = new Date().toISOString();

  switch (status) {
    case 401:
      return {
        type: 'UNAUTHORIZED',
        message: (responseData?.message as string) || '未登录或登录已过期',
        code: 401,
        timestamp,
      };

    case 403:
      return {
        type: 'FORBIDDEN',
        message: (responseData?.message as string) || '您没有权限执行此操作',
        code: 403,
        resourceCode: responseData?.resourceCode as string,
        action: responseData?.action as string,
        requiredPermissions: responseData?.requiredPermissions as Array<{ resourceCode: string; action: string }>,
        timestamp,
      };

    case 404:
      return {
        type: 'RESOURCE_NOT_FOUND',
        message: (responseData?.message as string) || '请求的资源不存在',
        code: 404,
        timestamp,
      };

    case 409:
      // 版本冲突
      return {
        type: 'VERSION_MISMATCH',
        message: '权限配置已变更，请刷新后重试',
        code: 409,
        retryAfter: 5,
        timestamp,
      };

    default:
      return null;
  }
}

/**
 * 快速创建异常辅助函数
 */
export function createException(
  type: PermissionExceptionType,
  message: string,
  code: number = 403
): PermissionException {
  return {
    type,
    message,
    code,
    timestamp: new Date().toISOString(),
  };
}
