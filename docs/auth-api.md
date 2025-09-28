# Auth API (Aggregated Login + OAuth + Refresh + Logout)

Context path is `/api`. Security: see docs/security.md.

## Password Login
- POST `/auth/login`
- Body:
```
{ "username": "alice", "password": "******" }
```
- Responses:
  - 200 OK: `{ token, refreshToken, username, roles }`
  - 401 Bad credentials
  - 429 Rate limited (too many failures)
- Rate limiting & brute force protection:
  - Per‑user threshold 5 failures / 15min, lock 15min
  - Per‑IP threshold 30 failures / 15min, lock 15min

## OAuth (WeCom / DingTalk / Feishu)

### 1) Build Authorization URL
- GET `/auth/oauth/authorize?platform=wechat|dingtalk|feishu&redirectUri=https://your.app/callback`
- Response: `{ url, state }`
- Frontend should redirect to `url` (or show QR) and preserve `state`.

### 2) Callback Exchange
- GET `/auth/oauth/callback/{platform}?code=...&state=...`
- Server validates `state` (Redis), exchanges `code` to platform user id, and issues JWT if the 3rd‑party account is bound to a user.
- Responses:
  - 200 OK: `{ token, refreshToken, username, roles }`
  - 400 Invalid `state`
  - 401 Exchange failed
  - 403 Not bound — contact admin to bind the account

## Refresh Token
- POST `/auth/refresh`
- Body: `{ "refreshToken": "..." }`
- Validates refresh token signature, type, expiry, and whitelist (Redis). Issues new access and new refresh (rotation) and deletes the old refresh.
- 200 OK: `{ token, refreshToken, username, roles }`
- 401 Invalid/expired refresh token

## Logout
- POST `/auth/logout` (requires Authorization header)
- Body (optional): `{ "refreshToken": "..." }`
- Blacklists current access token until expiry and deletes the provided refresh token from whitelist.
- 200 OK

## Platform Account Binding (Admin)
- Single binding per user (current version). Admin may bind or unbind a user's external account for WeCom/DingTalk/Feishu.

- GET `/admin/users/{id}/platform-binding`
  - Resp: `{ username, platformType, platformUserId }`
- PUT `/admin/users/{id}/platform-binding`
  - Body: `{ "platformType": "wechat|dingtalk|feishu", "platformUserId": "xxx" }`
  - Validates uniqueness across users per `(platformType, platformUserId)`
  - 409 if already bound to another user
- DELETE `/admin/users/{id}/platform-binding`
  - Unbinds the external account

Note: OAuth login rejects unbound accounts by default (403). Admin must bind before users can login via QR.

## Security & Storage
- JWT: HS256, claims include `sub` and `authorities` (comma‑separated)
- Refresh whitelist: `auth:refresh:{token}` → username (TTL until refresh expiry)
- Access blacklist: `auth:blacklist:{token}` (TTL until access expiry)
- OAuth state: `oauth:state:{platform}:{state}` (TTL 5min)
- Login rate limit counters & locks (Redis):
  - `auth:login:fail:user:{username}`
  - `auth:login:fail:ip:{ip}`
  - `auth:login:lock:user:{username}`
  - `auth:login:lock:ip:{ip}`
