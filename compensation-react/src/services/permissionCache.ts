/**
 * 权限缓存服务
 * 实现多级权限缓存：内存缓存 -> SessionStorage -> Redis
 */

import api from '@services/api';
import type { PermissionConfig, PermissionChangeEvent } from '@types/auth';
import type { MeResourcesData } from '@types/api';

const CACHE_KEYS = {
  CONFIG: 'permission_config',
  VERSION: 'permission_version',
  RESOURCES: 'permission_resources',
  CHANGE_EVENTS: 'permission_change_events',
};

const CACHE_TTL = {
  MEMORY: 5 * 60 * 1000, // 5分钟内存缓存
  SESSION: 30 * 60 * 1000, // 30分钟会话缓存
};

/**
 * 权限缓存服务
 */
export class PermissionCacheService {
  private static instance: PermissionCacheService;
  private memoryCache: Map<string, { value: unknown; timestamp: number }> = new Map();
  private ws: WebSocket | null = null;
  private listeners: Set<(event: PermissionChangeEvent) => void> = new Set();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;

  private constructor() {}

  static getInstance(): PermissionCacheService {
    if (!PermissionCacheService.instance) {
      PermissionCacheService.instance = new PermissionCacheService();
    }
    return PermissionCacheService.instance;
  }

  /**
   * 获取权限配置（带缓存）
   */
  async getConfig(forceRefresh = false): Promise<MeResourcesData | null> {
    // 1. 检查内存缓存
    const memoryKey = `${CACHE_KEYS.CONFIG}`;
    const memoryCached = this.memoryCache.get(memoryKey);
    if (!forceRefresh && memoryCached && Date.now() - memoryCached.timestamp < CACHE_TTL.MEMORY) {
      return memoryCached.value as MeResourcesData;
    }

    // 2. 检查会话缓存
    const sessionCached = sessionStorage.getItem(CACHE_KEYS.CONFIG);
    if (!forceRefresh && sessionCached) {
      try {
        const parsed = JSON.parse(sessionCached);
        if (Date.now() - parsed.timestamp < CACHE_TTL.SESSION) {
          // 更新内存缓存
          this.memoryCache.set(memoryKey, { value: parsed.data, timestamp: parsed.timestamp });
          return parsed.data;
        }
      } catch {
        sessionStorage.removeItem(CACHE_KEYS.CONFIG);
      }
    }

    // 3. 从服务器获取
    try {
      const response = await api.get<{ code: number; message: string; data: MeResourcesData }>(
        '/auth/me/resources'
      );

      if (response.data.code !== 0) {
        throw new Error(response.data.message || '获取权限配置失败');
      }

      const config = response.data.data;

      // 写入缓存
      this.setConfig(config);

      return config;
    } catch (error) {
      console.error('Failed to fetch permission config:', error);

      // 降级：使用会话缓存（即使过期）
      if (sessionCached) {
        try {
          return JSON.parse(sessionCached).data;
        } catch {
          return null;
        }
      }
      return null;
    }
  }

  /**
   * 设置权限配置
   */
  setConfig(config: MeResourcesData): void {
    const timestamp = Date.now();

    // 内存缓存
    this.memoryCache.set(CACHE_KEYS.CONFIG, { value: config, timestamp });

    // 会话缓存
    sessionStorage.setItem(CACHE_KEYS.CONFIG, JSON.stringify({
      data: config,
      version: config.permissionVersion,
      timestamp,
    }));

    // 更新版本号
    sessionStorage.setItem(CACHE_KEYS.VERSION, String(config.permissionVersion));
  }

  /**
   * 获取权限版本
   */
  async getVersion(): Promise<number> {
    // 先检查缓存
    const cached = sessionStorage.getItem(CACHE_KEYS.VERSION);
    if (cached) {
      return parseInt(cached, 10);
    }

    // 从服务器获取
    try {
      const config = await this.getConfig();
      return config?.permissionVersion ?? 0;
    } catch {
      return 0;
    }
  }

  /**
   * 检测版本变化
   */
  async detectVersionChange(): Promise<boolean> {
    const currentVersion = await this.getVersion();
    const config = await this.getConfig(true); // 强制刷新
    return config?.permissionVersion !== currentVersion;
  }

  /**
   * 订阅权限变更
   */
  subscribe(listener: (event: PermissionChangeEvent) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * 监听权限变更（WebSocket）
   */
  startListening(): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      return;
    }

    const wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/permission-push`;

    try {
      this.ws = new WebSocket(wsUrl);

      this.ws.onopen = () => {
        console.log('Permission WebSocket connected');
        this.reconnectAttempts = 0;

        // 发送心跳
        this.ws?.send(JSON.stringify({ type: 'ping' }));
      };

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.type === 'permission_change') {
            const changeEvent = data.payload as PermissionChangeEvent;

            // 通知所有监听器
            this.listeners.forEach((listener) => listener(changeEvent));

            // 清除缓存，触发刷新
            this.invalidateCache();
          }
        } catch (error) {
          console.error('Failed to parse permission change event:', error);
        }
      };

      this.ws.onclose = () => {
        console.log('Permission WebSocket disconnected');

        // 重连逻辑
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
          this.reconnectAttempts++;
          const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
          setTimeout(() => this.startListening(), delay);
        }
      };

      this.ws.onerror = (error) => {
        console.error('Permission WebSocket error:', error);
      };
    } catch (error) {
      console.error('Failed to create WebSocket connection:', error);
    }
  }

  /**
   * 停止监听
   */
  stopListening(): void {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  /**
   * 使缓存失效
   */
  invalidateCache(): void {
    this.memoryCache.clear();
    sessionStorage.removeItem(CACHE_KEYS.CONFIG);
    sessionStorage.removeItem(CACHE_KEYS.VERSION);
    sessionStorage.removeItem(CACHE_KEYS.RESOURCES);
  }

  /**
   * 清除所有缓存
   */
  clearAll(): void {
    this.invalidateCache();
    this.stopListening();
    this.listeners.clear();
  }

  /**
   * 获取内存缓存使用情况（调试用）
   */
  getMemoryCacheStats(): { key: string; age: number }[] {
    const now = Date.now();
    return Array.from(this.memoryCache.entries()).map(([key, value]) => ({
      key,
      age: now - value.timestamp,
    }));
  }
}

// 导出单例
export const permissionCache = PermissionCacheService.getInstance();
