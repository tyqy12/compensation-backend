# 管理端 API 文档

基础路径：`/api`；默认需要 `ADMIN` 角色或相应权限，见各接口注解。

统一返回：`ApiResponse<T>`，字段 `code`、`message`、`data`。

## 1) 角色管理

### 角色 CRUD

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/admin/roles` | 角色列表 | ADMIN |
| GET | `/admin/roles/enabled` | 启用的角色列表 | ADMIN |
| GET | `/admin/roles/{id}` | 获取角色详情 | ADMIN |
| POST | `/admin/roles` | 创建角色 | ADMIN |
| PUT | `/admin/roles/{id}` | 更新角色 | ADMIN |
| DELETE | `/admin/roles/{id}` | 删除角色 | ADMIN |
| PUT | `/admin/roles/{id}/disable` | 禁用角色 | ADMIN |
| PUT | `/admin/roles/{id}/enable` | 启用角色 | ADMIN |
| POST | `/admin/roles/{id}/copy` | 复制角色 | ADMIN |

#### 角色列表
- 方法：`GET /admin/roles`
- 参数：`keyword`（关键词）、`roleType`（SYSTEM/BUSINESS/CUSTOM）、`status`（enabled/disabled）
- 响应：`List<RoleVO>`

#### 创建角色
- 方法：`POST /admin/roles`
- Body：
```json
{
  "code": "role_code",
  "name": "角色名称",
  "description": "角色描述",
  "roleType": "BUSINESS",
  "sortOrder": 0,
  "icon": "UserOutlined",
  "remarks": "备注",
  "resourceIds": [1, 2, 3],
  "actions": ["read", "write"]
}
```

#### 更新角色
- 方法：`PUT /admin/roles/{id}`
- Body：
```json
{
  "name": "新名称",
  "description": "新描述",
  "status": "enabled",
  "sortOrder": 10,
  "isEditable": true
}
```

### 权限分配

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/roles/{id}/resources` | 获取角色资源权限 |
| PUT | `/admin/roles/{id}/resources` | 分配角色资源权限 |
| DELETE | `/admin/roles/{id}/resources` | 撤销角色资源权限 |

#### 获取角色资源权限
- 方法：`GET /admin/roles/{id}/resources`
- 响应：`List<ResourceBriefVO>`
```json
[
  {
    "id": 1,
    "code": "dashboard",
    "name": "仪表盘",
    "type": "MENU",
    "actions": ["read", "write"]
  }
]
```

#### 分配角色资源权限
- 方法：`PUT /admin/roles/{id}/resources`
- Body：
```json
{
  "resources": [
    {
      "resourceId": 1,
      "actions": ["read", "write", "delete"]
    }
  ],
  "replaceExisting": true
}
```

代码：`src/main/java/com/yiyundao/compensation/interfaces/controller/admin/RoleController.java`

## 2) 架构外员工管理

### 设置/取消架构外标记
- 方法：`PATCH /admin/employees/{id}/offline?value=true|false`
- 权限：`ROLE_ADMIN`
- 描述：设置员工的架构外标记。
- 响应：`data=null`

### 指定架构外员工负责人
- 方法：`PUT /admin/employees/{id}/manager?managerId=...`
- 权限：`ROLE_ADMIN`
- 描述：为架构外员工指定负责人（managerId）。
- 响应：`data=null`

辅助查询：`GET /employee/offline`（架构外员工）、`GET /employee/resigned`（离职员工）

代码：`src/main/java/com/yiyundao/compensation/interfaces/controller/admin/OfflineEmployeeAdminController.java`

## 3) 批量支付管理

### 创建批次（草稿）
- 方法：`POST /admin/payment/batch`
- 权限：`ROLE_ADMIN`
- Body：
```json
{
  "batchNo": "BATCH-202401-001",
  "batchName": "2024年1月工资",
  "paymentType": "salary",
  "totalAmount": 2680000.00,
  "totalCount": 1234
}
```
- 响应：`data` 为创建的 `PaymentBatch`

### 取消/关闭批次
- 方法：`POST /admin/payment/batch/{id}/cancel`
- 权限：`ROLE_ADMIN`
- 描述：将批次状态置为 `FAILED`（如需 `CANCELLED` 可后续扩展枚举与迁移）。

### 概览统计
- 方法：`GET /admin/payment/batch/stats`
- 权限：`ROLE_ADMIN`
- 描述：返回各状态批次数量，以及今日/本月支付成功金额汇总。

代码：`src/main/java/com/yiyundao/compensation/interfaces/controller/admin/PaymentBatchAdminController.java`

## 4) 审计日志查看

### 分页查询
- 方法：`GET /admin/audit-logs`
- 权限：`ROLE_ADMIN`
- 参数：`current`、`pageSize`、`username`、`operation`、`businessType`、`businessKey`、`startTime`、`endTime`（ISO 日期时间）
- 响应：分页结构，`records` 为 `AuditLog` 对象列表。

### 详情
- 方法：`GET /admin/audit-logs/{id}`
- 权限：`ROLE_ADMIN`

代码：`src/main/java/com/yiyundao/compensation/interfaces/controller/admin/AuditLogAdminController.java`

### 组织同步历史（基于审计日志）
- 方法：`GET /system/org/history`
- 权限：`hasAnyRole('ADMIN','MANAGER') or hasAuthority('org:read')`
- 参数：`current`、`pageSize`、`platform`（支持别名 wecom→wechat 等；all/* 表示全部）、`operation`
- 响应：分页结构，`records` 为简化 DTO（操作、平台、结果、发起人、IP、耗时、时间等）。

代码：`src/main/java/com/yiyundao/compensation/interfaces/controller/system/OrganizationSyncController.java`

## 5) 系统监控面板

### 概览
- 方法：`GET /admin/monitor/summary`
- 权限：`ROLE_ADMIN`
- 描述：返回应用信息（profile、启动时长）、JVM（内存、线程）、数据库/Redis ping 结果。

代码：`src/main/java/com/yiyundao/compensation/interfaces/controller/admin/MonitorAdminController.java`
