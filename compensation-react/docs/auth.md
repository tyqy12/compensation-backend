# Auth: Flows, Guards, Interceptors

## Tokens & Storage

- Access token (short‑lived) kept in memory (redux slice) and mirrored to localStorage for reload persistence.
- Refresh token stored in localStorage; used to rotate tokens via `/api/auth/refresh`.
- On logout: blacklist current access (server) and delete refresh (server + client).

Security note: For maximum security consider httpOnly cookies on server; current backend returns tokens in JSON, so we mitigate via CSP and careful usage.

## Axios Instance (services/api.ts)

- BaseURL: `VITE_API_BASE_URL` (see env.md)
- Request interceptor:
  - Attach `Authorization: Bearer ${accessToken}` if present
- Response interceptor:
  - If 401 and not retried yet: call `/api/auth/refresh` → store/rotate → retry original request
  - If still failing or refresh fails: dispatch logout and redirect to login

## React Router Guards

- ProtectedRoute: checks auth slice for token + roles; if missing, redirect to `/login`
- Role/Authority checks: define route meta `{ roles?: string[] }` and validate before render

## OAuth (WeCom/DingTalk/Feishu)

1. Build URL: GET `/api/auth/oauth/authorize?platform=xxx&redirectUri=${callbackUrl}` → `{ url, state }`
   - WeCom 推荐使用 `VITE_OAUTH_REDIRECT_URI_WECHAT` 固定回调地址（完整 URL），避免因当前访问域名变化导致 `redirect_uri` 校验失败。
2. Redirect to `url` (for QR or direct)
3. Callback page `/oauth/callback/:platform` reads `code` & `state` and GET `/api/auth/oauth/callback/{platform}`
4. Server validates `state` (Redis), exchanges `code`, and issues JWT only if bound; otherwise 403

## Refresh & Logout

- Refresh: POST `/api/auth/refresh` with `{ refreshToken }`, rotate tokens, update axios instance
- Logout: POST `/api/auth/logout` with Authorization header (access) and optional `{ refreshToken }`; clear local/session storage and redux state
