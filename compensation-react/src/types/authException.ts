/**
 * 权限异常类型定义
 */

/**
 * 权限异常类型
 */
export type PermissionExceptionType =
  | 'UNAUTHORIZED'           // 未认证（401）
  | 'FORBIDDEN'              // 无权限访问（403）
  | 'TOKEN_EXPIRED'          // Token 过期
  | 'TOKEN_INVALID'          // Token 无效
  | 'PERMISSION_DENIED'      // 权限不足
  | 'RESOURCE_NOT_FOUND'     // 资源不存在
  | 'RESOURCE_DISABLED'      // 资源已禁用
  | 'VERSION_MISMATCH'       // 权限版本不匹配
  | 'SESSION_TIMEOUT'        // 会话超时
  | 'CONCURRENT_LOGIN'       // 并发登录
  | 'UNKNOWN_ERROR';         // 未知错误

/**
 * 权限异常信息
 */
export interface PermissionException {
  type: PermissionExceptionType;
  message: string;
  code: number;
  resourceCode?: string;
  action?: string;
  requiredPermissions?: Array<{ resourceCode: string; action: string }>;
  retryAfter?: number; // 秒
  timestamp: string;
  requestId?: string;
}

/**
 * 异常处理策略
 */
export interface ExceptionHandlerStrategy {
  // 是否需要登出
  requireLogout: boolean;
  // 是否需要刷新权限
  requireRefresh: boolean;
  // 是否显示错误提示
  showMessage: boolean;
  // 是否跳转到特定页面
  redirectTo?: string;
  // 自定义处理逻辑
  customAction?: () => void;
}

/**
 * 默认异常处理策略映射
 */
export const DEFAULT_EXCEPTION_STRATEGIES: Record<PermissionExceptionType, ExceptionHandlerStrategy> = {
  UNAUTHORIZED: {
    requireLogout: false,
    requireRefresh: false,
    showMessage: true,
    redirectTo: '/login',
  },
  FORBIDDEN: {
    requireLogout: false,
    requireRefresh: false,
    showMessage: false,
    redirectTo: '/403',
  },
  TOKEN_EXPIRED: {
    requireLogout: false,
    requireRefresh: true,
    showMessage: true,
    customAction: () => {
      // 尝试刷新 Token
      import('@services/auth').then(({ authService }) => {
        authService.refreshToken();
      });
    },
  },
  TOKEN_INVALID: {
    requireLogout: true,
    requireRefresh: false,
    showMessage: true,
    redirectTo: '/login',
  },
  PERMISSION_DENIED: {
    requireLogout: false,
    requireRefresh: true,
    showMessage: true,
  },
  RESOURCE_NOT_FOUND: {
    requireLogout: false,
    requireRefresh: false,
    showMessage: true,
    redirectTo: '/404',
  },
  RESOURCE_DISABLED: {
    requireLogout: false,
    requireRefresh: true,
    showMessage: true,
  },
  VERSION_MISMATCH: {
    requireLogout: false,
    requireRefresh: true,
    showMessage: true,
  },
  SESSION_TIMEOUT: {
    requireLogout: true,
    requireRefresh: false,
    showMessage: true,
    redirectTo: '/login?reason=session_timeout',
  },
  CONCURRENT_LOGIN: {
    requireLogout: true,
    requireRefresh: false,
    showMessage: true,
    redirectTo: '/login?reason=concurrent_login',
  },
  UNKNOWN_ERROR: {
    requireLogout: false,
    requireRefresh: false,
    showMessage: true,
  },
};
