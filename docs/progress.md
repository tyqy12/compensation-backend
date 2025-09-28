# Progress Log

Date: 2025-09-27

## Summary
- Completed platform adapters with real API mappings for WeCom/DingTalk/Feishu and employee write logic (create/update with platformUserId → employeeId fallback).
- Introduced Integration Config management (encrypted DB storage, admin‑only, masked reads, connectivity test).
- Implemented aggregated authentication: password login + WeCom/DingTalk/Feishu OAuth (first‑time unbound users are rejected by default).
- Added token management and protections: refresh whitelist + rotation, access blacklist on logout, OAuth state storage/validation, login rate limiting.
- Implemented platform access_token caching via Redis with safety buffer.

## New Endpoints
- Auth:
  - POST /api/auth/login
  - GET  /api/auth/oauth/authorize
  - GET  /api/auth/oauth/callback/{platform}
  - POST /api/auth/refresh
  - POST /api/auth/logout
- Integration Config (ADMIN):
  - GET  /api/system/integration/{platform}
  - PUT  /api/system/integration/{platform}
  - POST /api/system/integration/{platform}/test-connection
- Org Sync:
  - POST /api/system/org/sync[?platform=...]
  - POST /api/system/org/sync-async[?platform=...]
  - GET  /api/system/org/sync-task/{id}
  - GET  /api/system/org/platforms
  - GET  /api/system/org/check?platform=...

## Redis Keys Used
- Platform tokens: `oauth:token:{platform}`
- OAuth state: `oauth:state:{platform}:{state}`
- Login rate limiting: `auth:login:fail:user:{u}`, `auth:login:fail:ip:{ip}`, locks `auth:login:lock:user:{u}`, `auth:login:lock:ip:{ip}`
- Token lists: refresh whitelist `auth:refresh:{token}`, access blacklist `auth:blacklist:{token}`
- Payment idempotency: `alipay:dedup:{recordId}`

## Next Steps
- Add admin UI for integration/bindings and audit viewers.
- Implement platform approval notifications and message sending.
- Optional: configurable thresholds (state TTL, rate limits) via sys_config.

