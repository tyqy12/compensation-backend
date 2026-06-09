import { useMutation } from '@tanstack/react-query';
import { loginApi, logoutApi, oauthCallbackApi } from '@services/auth';
import type { LoginRequest, LoginResponse, Platform } from '@types/api';
import { useNavigate, useLocation } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import { setSession, clearSession } from '@services/stores/authSlice';
import { App as AntdApp } from 'antd';
import { consumePostLoginRedirect } from '@services/authRedirect';

export function useLoginMutation() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation() as any;
  const { message } = AntdApp.useApp();
  return useMutation<{ data: LoginResponse }, unknown, LoginRequest>({
    mutationFn: async (payload: LoginRequest) => ({ data: await loginApi(payload) }),
    onSuccess: ({ data }) => {
      dispatch(setSession({ user: data.user, accessToken: data.accessToken, refreshToken: data.refreshToken } as any));
      const to = consumePostLoginRedirect(location.state);
      navigate(to, { replace: true });
    },
    onError: (err: any) => {
      message.error(err?.message || '登录失败');
    },
  });
}

export function useLogoutMutation() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  return useMutation({
    mutationFn: async () => logoutApi(),
    onSettled: () => {
      dispatch(clearSession());
      try { localStorage.removeItem('auth_token'); } catch {}
      navigate('/login');
    },
  });
}

export function useOAuthCallbackMutation(platform: Platform) {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { message } = AntdApp.useApp();
  return useMutation({
    mutationFn: async ({ code, state }: { code: string; state?: string }) => oauthCallbackApi(platform, code, state),
    onSuccess: (data) => {
      dispatch(setSession({ user: data.user, accessToken: data.accessToken, refreshToken: data.refreshToken } as any));
      navigate('/', { replace: true });
    },
    onError: (e: any) => message.error(e?.message || '登录失败'),
  });
}
