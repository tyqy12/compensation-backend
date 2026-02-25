# Task Schedule API

任务调度管理 API，支持定时任务的 CRUD、启停控制和手动触发。

Base Path: `/api/v1/admin/tasks`

## 任务列表

### 获取任务列表

- **GET** `/api/v1/admin/tasks`
- Responses:
  - 200 OK: `[{ id, taskKey, taskName, taskGroup, cronExpression, status, ... }]`

## 任务详情

### 获取任务详情

- **GET** `/api/v1/admin/tasks/{id}`
- Responses:
  - 200 OK: `{ id, taskKey, taskName, taskGroup, cronExpression, description, status, retryCount, maxRetryCount, retryIntervalSeconds, lastExecuteTime, nextExecuteTime, alarmEnabled, alarmReceivers, createTime, updateTime }`
  - 404 Not Found

## 任务管理

### 创建任务

- **POST** `/api/v1/admin/tasks`
- Request Body:
```json
{
  "taskKey": "daily_salary_sync",
  "taskName": "每日薪资同步",
  "taskGroup": "SALARY",
  "cronExpression": "0 0 2 * * ?",
  "description": "每天凌晨2点同步薪资数据",
  "maxRetryCount": 3,
  "retryIntervalSeconds": 60,
  "alarmEnabled": true,
  "alarmReceivers": "admin@example.com",
  "handlerBean": "dailySalarySyncHandler"
}
```
- Responses:
  - 201 Created: `{ id }`
  - 400 Bad Request: 验证失败

### 更新任务

- **PUT** `/api/v1/admin/tasks/{id}`
- Request Body: 同创建任务
- Responses:
  - 200 OK
  - 404 Not Found

### 删除任务

- **DELETE** `/api/v1/admin/tasks/{id}`
- Responses:
  - 200 OK
  - 404 Not Found

## 任务控制

### 暂停任务

- **POST** `/api/v1/admin/tasks/{id}/pause`
- Responses:
  - 200 OK
  - 404 Not Found

### 恢复任务

- **POST** `/api/v1/admin/tasks/{id}/resume`
- Responses:
  - 200 OK
  - 404 Not Found

### 手动触发任务

- **POST** `/api/v1/admin/tasks/{id}/trigger`
- Responses:
  - 200 OK: `{ executionId }`
  - 404 Not Found

## 执行日志

### 获取执行日志

- **GET** `/api/v1/admin/tasks/{id}/logs?limit=50`
- Responses:
  - 200 OK: `[{ id, taskId, taskKey, startTime, endTime, durationMs, status, result, errorMessage, traceId, createTime }]`

## 任务状态枚举

| 值 | 说明 |
|---|------|
| PAUSED | 暂停 |
| RUNNING | 运行中 |
| FAILED | 失败 |
| SUCCESS | 成功 |

## 执行状态枚举

| 值 | 说明 |
|---|------|
| RUNNING | 运行中 |
| SUCCESS | 成功 |
| FAILED | 失败 |
| RETRYING | 重试中 |
