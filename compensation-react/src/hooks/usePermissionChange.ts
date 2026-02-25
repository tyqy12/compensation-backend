/**
 * 权限变更监听 Hook
 */

import { useEffect, useCallback } from 'react';
import { message } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { permissionCache } from '@services/permissionCache';
import type { PermissionChangeEvent } from '@types/auth';

/**
 * 权限变更监听 Hook
 */
export function usePermissionChange() {
  const queryClient = useQueryClient();

  const handlePermissionChange = useCallback((event: PermissionChangeEvent) => {
    console.log('Permission changed:', event);

    // 根据变更类型显示提示
    switch (event.eventType) {
      case 'GRANT':
        message.success('权限已授予');
        break;
      case 'REVOKE':
        message.warning('部分权限已被回收');
        break;
      case 'MODIFY':
        message.info('权限已变更');
        break;
      case 'CLEAR':
        message.warning('所有权限已清除');
        break;
    }

    // 清除权限缓存
    permissionCache.invalidateCache();

    // 刷新相关查询
    queryClient.invalidateQueries({ queryKey: ['permission'] });
    queryClient.invalidateQueries({ queryKey: ['me', 'resources'] });
    queryClient.invalidateQueries({ queryKey: ['me', 'actions'] });
    queryClient.invalidateQueries({ queryKey: ['resources'] });

    // 触发全局事件（用于通知其他组件）
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('permission:change', { detail: event }));
    }
  }, [queryClient]);

  useEffect(() => {
    // 订阅权限变更
    const unsubscribe = permissionCache.subscribe(handlePermissionChange);

    // 启动 WebSocket 监听
    permissionCache.startListening();

    return () => {
      unsubscribe();
    };
  }, [handlePermissionChange]);

  return {
    refresh: () => permissionCache.invalidateCache(),
    detectChange: () => permissionCache.detectVersionChange(),
  };
}

/**
 * 权限变更事件处理器类型
 */
export type PermissionChangeHandler = (event: PermissionChangeEvent) => void;

/**
 * 自定义权限变更订阅 Hook
 * 允许组件订阅特定的权限变更事件
 */
export function usePermissionChangeSubscription(
  handler: PermissionChangeHandler,
  deps: readonly unknown[] = []
) {
  useEffect(() => {
    const unsubscribe = permissionCache.subscribe(handler);
    return unsubscribe;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [handler, ...deps]);
}
