import api, { unwrap } from '@services/api';
import type { LoginRequest, LoginResponse, RefreshResponse, Platform, UserSession } from '@types/api';

// 适配后端登录返回 { token, refreshToken, username, roles }
export async function loginApi(payload: LoginRequest): Promise<LoginResponse> {
  const { data } = await api.post('/auth/login', payload);
  const res = unwrap<{ token: string; refreshToken?: string; username: string; roles: string[] }>(data);
  const user: UserSession = { id: res.username, username: res.username, roles: res.roles };
  return { accessToken: res.token, refreshToken: res.refreshToken, user };
}

export async function refreshApi(refreshToken: string): Promise<RefreshResponse> {
  const { data } = await api.post('/auth/refresh', { refreshToken });
  const response = unwrap<{ accessToken?: string; token?: string; refreshToken?: string }>(data);
  const accessToken = response.accessToken ?? response.token;
  if (!accessToken) {
    throw new Error('刷新令牌响应缺少访问令牌');
  }
  return { accessToken, refreshToken: response.refreshToken };
}

export async function logoutApi(): Promise<void> {
  await api.post('/auth/logout');
}

export async function oauthCallbackApi(platform: Platform, code: string, state?: string) {
  const { data } = await api.get(`/auth/oauth/callback/${platform}`, { params: { code, state } });
  // 优先按 LoginResponse 解包，若字段为 { token, username, roles } 则转换
  const raw = unwrap<any>(data);
  if (raw && 'accessToken' in raw && 'user' in raw) return raw as LoginResponse;
  const res = raw as { token: string; refreshToken?: string; username: string; roles: string[] };
  const user: UserSession = { id: res.username, username: res.username, roles: res.roles };
  return { accessToken: res.token, refreshToken: res.refreshToken, user };
}

export async function authorizeWecom(channel: 'wecom' | 'web', redirectUri: string): Promise<{ url: string; state: string; channel: string }>
{
  const { data } = await api.get('/auth/oauth/authorize', {
    // 后端平台类型可能为 'wechat'，此处统一传 'wechat' 以最大兼容
    params: { platform: 'wechat', channel, redirectUri },
  });
  return unwrap<{ url: string; state: string; channel: string }>(data);
}
