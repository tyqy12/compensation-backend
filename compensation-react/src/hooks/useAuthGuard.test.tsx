import React from 'react';
import { renderHook } from '@testing-library/react';
import { Provider } from 'react-redux';
import { store } from '../services/stores/authSlice';
import { useAuthGuard } from './useAuthGuard';
import { login } from '../services/stores/authSlice';

// 测试包装器
const wrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider store={store}>{children}</Provider>
);

describe('useAuthGuard hook', () => {
  beforeEach(() => {
    // 重置store状态
    store.dispatch({ type: 'auth/logout' });
  });

  it('应该返回未认证状态', () => {
    const { result } = renderHook(() => useAuthGuard(), { wrapper });

    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
    expect(result.current.hasRole('admin')).toBe(false);
    expect(result.current.hasAnyRole(['admin', 'user'])).toBe(false);
  });

  it('应该正确处理认证状态', () => {
    const mockUser = {
      id: '1',
      username: 'testuser',
      roles: ['admin', 'user'],
    };

    store.dispatch(login(mockUser));

    const { result } = renderHook(() => useAuthGuard(), { wrapper });

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user).toEqual(mockUser);
    expect(result.current.hasRole('admin')).toBe(true);
    expect(result.current.hasRole('guest')).toBe(false);
    expect(result.current.hasAnyRole(['admin', 'guest'])).toBe(true);
    expect(result.current.hasAnyRole(['guest', 'visitor'])).toBe(false);
  });
});
