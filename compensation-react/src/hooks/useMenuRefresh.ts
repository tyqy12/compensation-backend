import { useEffect, useCallback, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useSelector } from 'react-redux';
import type { RootState } from '@services/stores/authSlice';
import { getMeResources } from '@services/rbac';

// localStorage cache key 生成函数（与 rbac.ts 保持一致）
const cacheKey = (userId?: string | number | null) => (userId ? `rbac_cache_${userId}` : undefined);

/**
 * 菜单刷新 Hook
 *
 * 功能：
 * 1. 监听其他标签页的资源变更广播
 * 2. 提供广播方法，通知其他标签页刷新菜单
 * 3. 立即重新获取菜单数据
 */
export function useMenuRefresh() {
  const qc = useQueryClient();
  const userId = useSelector((s: RootState) => s.auth.user?.id as any);
  const refreshingRef = useRef(false);

  const refreshMenus = useCallback(async () => {
    if (refreshingRef.current) {
      console.log('[MenuRefresh] 正在刷新中，跳过重复请求');
      return;
    }
    refreshingRef.current = true;

    try {
      console.log('[MenuRefresh] 刷新菜单数据...', { userId });
      
      // 直接调用 API 获取最新数据
      const latest = await getMeResources();
      
      // 设置 React Query 缓存
      qc.setQueryData(['me', 'resources', userId], latest);
      qc.setQueryData(['me', 'actions', userId], latest.actions);
      
      // 更新 localStorage 缓存
      const key = cacheKey(userId);
      if (key) {
        localStorage.setItem(key, JSON.stringify(latest));
      }
      
      console.log('[MenuRefresh] 菜单数据已刷新', latest);
    } catch (err) {
      console.error('[MenuRefresh] 刷新菜单数据失败:', err);
    } finally {
      refreshingRef.current = false;
    }
  }, [qc, userId]);

  useEffect(() => {
    // 监听 storage 事件（其他标签页修改 localStorage 时触发）
    const handleStorage = (e: StorageEvent) => {
      if (e.key === 'menu-refresh-signal' && e.newValue) {
        try {
          const data = JSON.parse(e.newValue);
          if (data.type === 'refresh') {
            refreshMenus();
          }
        } catch (err) {
          console.warn('[MenuRefresh] 解析刷新信号失败:', err);
        }
      }
    };

    window.addEventListener('storage', handleStorage);
    return () => window.removeEventListener('storage', handleStorage);
  }, [qc, refreshMenus]);
}

/**
 * 广播菜单刷新信号到其他标签页
 */
export function broadcastMenuRefresh() {
  try {
    const signal = JSON.stringify({
      type: 'refresh',
      timestamp: Date.now(),
    });
    localStorage.setItem('menu-refresh-signal', signal);
    // 立即清除，storage 事件只在其他标签页触发
    localStorage.removeItem('menu-refresh-signal');
  } catch (err) {
    console.warn('[MenuRefresh] 广播刷新信号失败:', err);
  }
}