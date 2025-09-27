# Platform Integration Guide

This service integrates enterprise communication platforms for org sync and approval notifications via adapters.

## Supported Platforms
- WeChat (企业微信) — `wechat`
- DingTalk (钉钉) — `dingtalk`
- Feishu (飞书) — `feishu`

Adapters live under: `interfaces/adapter/impl/*OrganizationAdapter.java` and implement `OrganizationAdapter`.

## Configuration (dev example)
See: `src/main/resources/application-dev.yml`
```
wechat:
  corp-id: <corp_id>
  corp-secret: <corp_secret>
  agent-id: <agent_id>

dingtalk:
  app-key: <app_key>
  app-secret: <app_secret>

feishu:
  app-id: <app_id>
  app-secret: <app_secret>
```

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

## Notes
- Adapters currently include basic token retrieval and placeholders for user/department sync; extend as needed.
- ApprovalEngine sends notifications using OrganizationSyncService; adapter implementations should implement message sending for production.
