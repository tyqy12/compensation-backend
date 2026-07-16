import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { act, render, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { notifyApiError } from '@services/errors';
import AuthErrorHandler from './AuthErrorHandler';
import { setSession, store } from '@services/stores/authSlice';

const warning = vi.fn();
const error = vi.fn();

vi.mock('antd', async () => {
  const actual = await vi.importActual<typeof import('antd')>('antd');
  return {
    ...actual,
    App: {
      ...actual.App,
      useApp: () => ({
        message: { warning, error },
      }),
    },
  };
});

const LocationProbe: React.FC = () => {
  const location = useLocation();
  return <div data-testid="location">{location.pathname}</div>;
};

function renderHandler(initialPath = '/payroll/batches') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthErrorHandler />
      <LocationProbe />
      <Routes>
        <Route path="/login" element={<div>login</div>} />
        <Route path="*" element={<div>page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('AuthErrorHandler', () => {
  beforeEach(() => {
    warning.mockClear();
    error.mockClear();
    store.dispatch(setSession({
      user: { id: '100', username: 'finance', roles: ['FINANCE'] },
      accessToken: 'token',
      roles: ['FINANCE'],
    }));
  });

  it('clears session and redirects to login on 401', async () => {
    const screen = renderHandler('/payroll/batches');

    act(() => {
      notifyApiError({ status: 401, message: 'expired', error: new Error('expired') });
    });

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'));
    expect(store.getState().auth.user).toBeNull();
    expect(warning).toHaveBeenCalledWith('登录已过期，请重新登录');
  });

  it('shows permission warning on 403 without redirecting', async () => {
    const screen = renderHandler('/admin/resources-v2');

    act(() => {
      notifyApiError({ status: 403, message: 'forbidden', error: new Error('forbidden') });
    });

    await waitFor(() => expect(warning).toHaveBeenCalledWith('没有权限执行该操作'));
    expect(screen.getByTestId('location')).toHaveTextContent('/admin/resources-v2');
  });
});
