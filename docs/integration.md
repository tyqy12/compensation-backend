# Platform Integration Guide

This service integrates enterprise communication platforms for org sync and approval notifications via adapters.

## Supported Platforms
- WeChat (企业微信) — `wechat`
- DingTalk (钉钉) — `dingtalk`
- Feishu (飞书) — `feishu`

Adapters live under: `interfaces/adapter/impl/*OrganizationAdapter.java` and implement `OrganizationAdapter`.

## Configuration Management (DB + Admin only)
- Admin endpoints (context path `/api` omitted below):
  - `GET /system/integration/{platform}` Read config (masked response)
  - `PUT /system/integration/{platform}` Save config (AES encrypted at rest)
  - `POST /system/integration/{platform}/test-connection` Connectivity test
- Platforms: `wechat|dingtalk|feishu|alipay`
- Security: ADMIN role only; config is stored encrypted in table `integration_config` and decrypted at runtime.
  - DTOs: `WechatConfigDto`, `DingTalkConfigDto`, `FeishuConfigDto`, `AlipayConfigDto`.

## API Endpoints
- POST `/api/system/org/sync?platform=wechat|dingtalk|feishu|all`
  - Triggers org sync for a specific platform or all
  - Auth: `ROLE_ADMIN|ROLE_MANAGER` or `org:sync`
- POST `/api/system/org/sync-async?platform=...`
  - Starts an async sync task; returns task id
  - Auth: `ROLE_ADMIN|ROLE_MANAGER` or `org:sync`
- GET `/api/system/org/sync-task/{id}`
  - Returns task status
  - Auth: `ROLE_ADMIN|ROLE_MANAGER` or `org:read`
- GET `/api/system/org/platforms`
  - Returns supported platform list
  - Auth: authenticated
- GET `/api/system/org/check?platform=...`
  - Checks connection status
  - Auth: `ROLE_ADMIN|ROLE_MANAGER` or `org:read`

## Token Caching
- Access tokens obtained from platforms are cached in Redis with a safety buffer (expires_in − 300s).
- Keys: `oauth:token:{platform}`
- Implementations:
  - WeChat: `WeChatOrganizationAdapter#getAccessToken()`
  - DingTalk: `DingTalkOrganizationAdapter#getAccessToken()`
  - Feishu: `FeishuOrganizationAdapter#getTenantAccessToken()`

## Notes
- Department and user fetching APIs are implemented with proper mapping and pagination (where applicable).
- Approval notifications should be implemented per‑platform before going live.
