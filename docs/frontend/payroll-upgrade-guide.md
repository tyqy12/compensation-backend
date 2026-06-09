# 薪酬前端升级总览（阶段 3.1–4.2）

本指南面向前端同学，汇总《开发计划》中 3.1–4.2 阶段所有对 UI/API 有直接影响的交付内容，方便快速对齐改动、规划联调与上线事项。细化文档可参阅：

- FT 内部核对场景（3.3）：`docs/frontend/payroll-ledger-upgrade.md`
- PT 只读对外接口（4.1–4.2）：`docs/frontend/pt-readonly-upgrade-guide.md`

## 1. 阶段交付总览

| 阶段 | 交付要点 | 前端影响 |
| --- | --- | --- |
| 3.1 数据库与模型 | payroll 栈表结构、字段命名、金额单位稳定 | 表格字段、导入校验项、过滤条件固定，可直接对接 |
| 3.2 核心服务与计算引擎 | 模板驱动算薪、周期/批次状态机、快照落地 | 状态标签、执行进度、差异数据可在 UI 呈现 |
| 3.3 导入与核对 | 财务台账、经理核对视图接口落地 | 新增菜单/页签、合计+预警展示、行级差异标记 |
| 3.4 审批与发薪 | 审批流、支付批次、RBAC 模板 | 审批/支付状态、时间线、角色控制需同步 |
| 3.5 员工工资条 | FT 员工工资条页面、CSV 导出 | 自助端可直接消费 `/payroll/payslips` 相关接口 |
| 3.6 测试与验收 | 集成测试、UAT 清单 | 接口契约稳定，可联调 |
| 4.1 应用注册与鉴权 | 应用后台 CRUD、Client Credentials Token、appId+IP 限流、调用审计 | 管理后台新增“外部应用”页，生成 clientId/secret；调用前端需封装取 Token 流程 |
| 4.2 PT 只读 API | `/v1/payroll`、`/v1/payslips` 对外只读、scope 校验、脱敏输出 | 外部前端按 scope 接入，遵循脱敏字段、分页规范 |

## 2. FT 内部核对（阶段 3.3）

详见 `payroll-ledger-upgrade.md`，关键摘要如下：

### 2.1 财务台账 `GET /api/payroll/batches/{id}/ledger`
- 返回 `PayrollLedgerDto`：批次状态、金额汇总、预警信息、员工行明细。
- 员工行结构与 dry-run 结果一致，字段补充：`department`、`managerId`、`employmentType`、`warnings`。
- 建议 UI：顶部展示合计与预警列表；行内以 badge/tooltip 呈现差异提示。

### 2.2 经理核对视图 `GET /api/payroll/batches/{id}/manager-review`
- 支持 `department`、`managerId`、`keyword` 过滤。
- 差异字段（上期净额对比、缺失项）已计算完毕，可直接展示。

### 2.3 dry-run 增强
- `POST /api/payroll/batches/{id}/dry-run` 新增金额汇总与预警统计字段。
- 行级新增部门/经理信息，便于前端同步展示。

### 2.4 前端改造清单
- 更新接口类型定义（Swagger/OAS）。
- 增加合计/预警区域、行级差异标记。
- 根据角色控制入口（FIN、经理）。
- 联调顺序：导入 → dry-run → compute → ledger/manager-review。

## 3. 应用注册与鉴权（阶段 4.1）

### 3.1 管理后台
- 路径：`/admin/app-registry`，需 `ROLE_ADMIN`。
- 功能：新建/编辑应用、配置 scope (`payroll:read`、`payslip:read`)、IP 白名单、Webhook。
- 支持密钥轮换，API 每次返回新的明文 `clientSecret`。

### 3.2 Token 获取
```
POST /api/v1/oauth/token
Authorization: Basic base64(clientId:clientSecret)
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=payroll:read payslip:read
```
响应：
```
{
  "code": 0,
  "data": {
    "accessToken": "...",
    "tokenType": "Bearer",
    "expiresIn": 1800,
    "scope": "payroll:read payslip:read"
  }
}
```
- 默认有效期 30 分钟，前端需缓存过期时间并提前续签。
- 限流：每个 `clientId+IP` 600 次/分钟，触发后返回 429 并发送系统告警。
- 审计：所有调用写入 `audit_log`，包含 appId、IP、URI、耗时。

## 4. PT 只读 API（阶段 4.2）

详见 `pt-readonly-upgrade-guide.md`。核心接口如下：

### 4.1 发薪批次
- `GET /api/v1/payroll/batches`（仅 `type=part_time`），分页字段：`current`、`size`、`total`。
- `GET /api/v1/payroll/batches/{batchId}`。
- 返回 `OpenApiPayrollBatchDto`：`lineCount`、`paidAt`（关联支付批次）、`periodLabel`、`status`。

### 4.2 工资行
- `GET /api/v1/payroll/batches/{batchId}/lines`
  - 支持 `employeeRef` 过滤：`emp:<employeeNo>`、`<provider>:<subjectId>` 或 `<provider>:<tenantKey>:<subjectId>`；多租户同 subjectId 时使用三段式。
  - 返回 `OpenApiPayrollLineDto`：金额字段、`employeeRef`、部门数组、脱敏姓名/手机号、更新时间。

### 4.3 工资条
- `GET /api/v1/payslips?employeeRef=...&period=YYYY-MM`
- `GET /api/v1/payslips/{id}?employeeRef=...`
- 返回 `OpenApiPayslipDto`：金额字段、部门、脱敏信息、明细 `items`（含 `showOnPayslip`、`order`）。

### 4.4 Scope 规则
- `SCOPE_payroll:read`：授权批次与工资行接口。
- `SCOPE_payslip:read`：授权工资条接口。
- 若 scope 不足，返回 403；需前端提示用户联系管理员开通。

### 4.5 前端落地建议
- 封装 Token 获取与刷新；在 `expiresIn` 前 120s 主动续签。
- 统一错误提示：401（凭证失效）、403（scope 不足/非白名单 IP）、429（限流）。
- 金额字段保持数值类型，前端格式化展示；脱敏字段直接使用响应值。
- 支持批次状态筛选（默认 `approved,paid`，可选 `archived`）。

## 5. 联调与测试建议

1. **内部（FT）场景**：
   - 准备导入数据 → dry-run → compute → ledger/manager-review。
   - 校验合计、预警数、行级差异是否一致。
2. **外部（PT）场景**：
   - 在白名单 IP 下生成应用 → 获取 Token → 调用 `/v1/payroll`、`/v1/payslips`。
   - 验证 scope 限制、脱敏字段、分页行为、429 处理。
3. **共性**：
   - 关注字段大小写（snake/camel）与数值精度。
   - 建议使用 Postman 集合或 Swagger UI 验证契约。

## 6. 前端实施 Checklist

- [ ] API 类型定义与接口配置更新（FT & PT）。
- [ ] Token 管理与错误处理封装。
- [ ] UI 升级：合计/预警展示、差异 Badge、外部数据脱敏显示。
- [ ] 菜单/权限：FT 内部视图根据角色控制，PT 接口按 scope 控制入口。
- [ ] 测试脚本：覆盖成功/异常场景（401/403/429）。
- [ ] 发布前联调验证，通过 UAT 清单。

如需 Mock 数据或 SDK 示例，请在飞书「薪酬项目群」@后端值班同学。
