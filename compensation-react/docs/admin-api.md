# 管理端 API 文档

基础路径：`/api`；默认需要 `ADMIN` 角色或相应权限，见各接口注解。

统一返回：`ApiResponse<T>`，字段 `code`、`message`、`data`。

## 1) 架构外员工管理

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

## 2) 批量支付管理

### 创建批次（草稿）

- 方法：`POST /admin/payment/batch`
- 权限：`ROLE_ADMIN`
- Body：

```json
{
  "batchNo": "BATCH-202401-001",
  "batchName": "2024年1月工资",
  "paymentType": "salary",
  "totalAmount": 2680000.0,
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

## 3) 审计日志查看

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
- 参数：`current`、`pageSize`、`platform`（支持别名 wecom→wechat 等；all/\* 表示全部）、`operation`
- 响应：分页结构，`records` 为简化 DTO（操作、平台、结果、发起人、IP、耗时、时间等）。

代码：`src/main/java/com/yiyundao/compensation/interfaces/controller/system/OrganizationSyncController.java`

## 4) 系统监控面板

### 概览

- 方法：`GET /admin/monitor/summary`
- 权限：`ROLE_ADMIN`
- 描述：返回应用信息（profile、启动时长）、JVM（内存、线程）、数据库/Redis ping 结果。

代码：`src/main/java/com/yiyundao/compensation/interfaces/controller/admin/MonitorAdminController.java`
