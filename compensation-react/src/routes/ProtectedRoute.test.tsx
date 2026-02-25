import React from 'react';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { store } from '../services/stores/authSlice';
import { ProtectedRoute } from './ProtectedRoute';
import { login } from '../services/stores/authSlice';

// Mock react-router-dom
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');

  const MockNavigate = ({ to, state }: { to: string; state?: any }) => (
    <div data-testid="navigate" data-to={to} data-state={JSON.stringify(state)}>
      Redirecting to {to}
    </div>
  );

  return {
    ...actual,
    Navigate: MockNavigate,
    useLocation: () => ({ pathname: '/test', search: '', hash: '', state: null }),
  };
});

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider store={store}>
    <BrowserRouter>{children}</BrowserRouter>
  </Provider>
);

describe('ProtectedRoute', () => {
  beforeEach(() => {
    store.dispatch({ type: 'auth/logout' });
  });

  it('应该在未认证时跳转到登录页', () => {
    render(
      <TestWrapper>
        <ProtectedRoute>
          <div>Protected Content</div>
        </ProtectedRoute>
      </TestWrapper>,
    );

    const navigate = screen.getByTestId('navigate');
    expect(navigate).toHaveAttribute('data-to', '/login');
    expect(navigate).toHaveTextContent('Redirecting to /login');
  });

  it('应该在认证但无权限时跳转到 403 页面', () => {
    const mockUser = {
      id: '1',
      username: 'testuser',
      roles: ['USER'],
    };

    store.dispatch(login(mockUser));

    render(
      <TestWrapper>
        <ProtectedRoute roles={['ADMIN']}>
          <div>Admin Content</div>
        </ProtectedRoute>
      </TestWrapper>,
    );

    const navigate = screen.getByTestId('navigate');
    expect(navigate).toHaveAttribute('data-to', '/403');
    expect(navigate).toHaveTextContent('Redirecting to /403');
  });

  it('应该在有权限时显示内容', () => {
    const mockUser = {
      id: '1',
      username: 'testuser',
      roles: ['ADMIN'],
    };

    store.dispatch(login(mockUser));

    render(
      <TestWrapper>
        <ProtectedRoute roles={['ADMIN']}>
          <div>Admin Content</div>
        </ProtectedRoute>
      </TestWrapper>,
    );

    expect(screen.getByText('Admin Content')).toBeInTheDocument();
    expect(screen.queryByTestId('navigate')).not.toBeInTheDocument();
  });

  it('应该在无角色要求时显示内容（仅需要认证）', () => {
    const mockUser = {
      id: '1',
      username: 'testuser',
      roles: ['USER'],
    };

    store.dispatch(login(mockUser));

    render(
      <TestWrapper>
        <ProtectedRoute>
          <div>Protected Content</div>
        </ProtectedRoute>
      </TestWrapper>,
    );

    expect(screen.getByText('Protected Content')).toBeInTheDocument();
    expect(screen.queryByTestId('navigate')).not.toBeInTheDocument();
  });
});
