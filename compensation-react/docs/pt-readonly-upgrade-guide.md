# PT 只读接口前端升级指引

本文汇总了《开发计划》3.1–4.2 阶段落地能力的对前端影响，帮助前端团队快速对齐改动和对接方案。

## 1. 阶段交付总览

| 阶段 | 能力摘要 | 前端影响 |
| --- | --- | --- |
| 3.1 数据库与模型 | payroll 栈表结构稳定（batch/line/adjustment/timesheet 等），字段命名及单位确定 | 表单 & 列表字段映射固定，可直接消费 API 中的字段含义 |
| 3.2 核心服务与计算引擎 | 模板驱动算薪、周期/批次状态机、计算快照写入 `payroll_line.items_snapshot_json` | 前端可获取完整的计算结果与状态标签；支持差异提示 |
| 3.3 导入与核对 | CSV 导入校验、财务台账与经理核对视图 | 相关内部页面已提供分页、过滤、差异高亮；外部前端无需重复实现 |
| 3.4 审批与发薪 | 审批链、支付批次、RBAC 模板 | 审批状态、支付完成时间可用于前端页签 / Badge |
| 3.5 员工工资条 | FT 员工“我的工资条”及导出 | 内部前端已支持历史查询、导出 CSV |
| 3.6 测试与验收 | 集成测试、UAT 清单 | 接口契约已稳定，可直接对接 |
| 4.1 应用注册与鉴权 | 客户端注册、密钥轮换、Client Credentials 令牌、按 `clientId+IP` 限流、调用审计 | 管理后台新增应用管理页；对外前端获取 Token 流程需更新 |
| 4.2 PT 只读 API | PT 批次/工资行/工资条查询，scope 校验，字段脱敏 | 新增 `/v1/payroll`、`/v1/payslips` 只读接口；前端依据 scope 控制入口与报错处理 |

## 2. 管理后台改动（应用注册）

### 2.1 入口
- 页面位置：系统管理 → 外部应用注册（`/admin/app-registry`）
- 权限：`ROLE_ADMIN`

### 2.2 接口
| 功能 | 方法与路径 | 入参要点 | 备注 |
| --- | --- | --- | --- |
| 新增应用 | `POST /admin/app-registry` | `appName`、`scopes`（如 `payroll:read`、`payslip:read`）、可选 `ipWhitelist` | 响应包含明文 `clientSecret`，仅返回一次，需提示用户保存 |
| 编辑应用 | `PUT /admin/app-registry/{id}` | 可更新名称、scope、IP 白名单、webhook | scope 变更会立即影响令牌签发 |
| 轮换密钥 | `POST /admin/app-registry/{id}/rotate-secret` | 无请求体 | 返回新 `clientSecret` |
| 列表/详情 | `GET /admin/app-registry[/{id}]` | 支持按名称/状态模糊搜索 | status=`enabled`/`disabled` |

### 2.3 UI 提示
- scope 选择器默认勾选 `payroll:read`，可选 `payslip:read`。
- 当存在 IP 白名单时，提示调用方：未在白名单内将返回 403。
- 密钥轮换后旧密钥立即失效，需提醒调用方重置配置。

## 3. 对外鉴权流程（Client Credentials）

1. 调用方通过 `POST /api/v1/oauth/token` 获取访问令牌。
   - 请求示例（Basic auth）：`Authorization: Basic base64(clientId:clientSecret)`。
   - Body：`grant_type=client_credentials&scope=payroll:read payslip:read`
   - 响应：`{ "accessToken", "tokenType":"Bearer", "expiresIn":1800, "scope":"payroll:read payslip:read" }`
2. 调用业务接口时携带 `Authorization: Bearer <accessToken>`。
3. 令牌默认 30 分钟过期，前端需实现自动刷新或到期重取。
4. 限流：每个 `clientId+IP` 默认 600 次/分钟，触发后返回 429，并自动推送系统告警。

## 4. PT 只读接口契约

### 4.1 发薪批次
- **列表**：`GET /api/v1/payroll/batches`
  - Query：`type=part_time`（可选，但仅支持该值）、`period=YYYY-MM`、`status=approved|paid|archived`、`page`、`size`
  - 响应：`Page<OpenApiPayrollBatchDto>`，关键字段：
    - `lineCount`：PT 工资行数量
    - `paidAt`：关联支付批次完成时间（如有）
- **详情**：`GET /api/v1/payroll/batches/{id}`
  - 返回单个 `OpenApiPayrollBatchDto`

### 4.2 工资行
- `GET /api/v1/payroll/batches/{id}/lines`
  - Query：`employeeRef`（支持 `emp:<employeeNo>` 或 `<provider>:<subjectId>`）、`page`、`size`
  - 响应：`Page<OpenApiPayrollLineDto>`，关键字段：
    - `employeeRef`：已根据平台 ID 或员工号拼接
    - `employeeNameMasked` / `phoneMasked`：脱敏显示
    - `departments`：按 `/` 拆分为数组

### 4.3 工资条
- **按条件查询**：`GET /api/v1/payslips?employeeRef=emp:E0001&period=2025-09`
  - 返回 `List<OpenApiPayslipDto>`；字段：
    - `items`：携带 `showOnPayslip`、`order`，可用于前端排序和展示控制
    - 金额字段（gross/tax/social/net）为 `BigDecimal`，前端保留两位小数即可
- **单条详情**：`GET /api/v1/payslips/{id}`

### 4.4 Scope 与鉴权
- `SCOPE_payroll:read`：访问批次与工资行
- `SCOPE_payslip:read`：访问工资条
- 若 scope 不满足，接口返回 403 → 前端需根据错误码给出提示（“请联系管理员开通对应权限”）。

## 5. 前端升级建议

1. **Token 管理**：封装获取/刷新逻辑，缓存到期时间（`expiresIn`），在 1–2 分钟前提前更新。
2. **错误处理**：对 401/403/429 做专门提示；429 建议指数退避重试或提示调用方稍后再试。
3. **分页组件**：统一使用后端返回的 `current`、`size`、`total`。
4. **字段展示**：
   - 姓名、手机号等使用响应里的脱敏字段，不在前端自行处理。
   - 金额字段以数值展示；如需千分位由前端格式化。
5. **过滤条件**：批次列表默认筛选 `status=approved,paid`，提供 UI 切换 `archived`。
6. **缓存策略**：
   - 批次列表可本地缓存 5~10 分钟（结合 `updateTime` 判断是否刷新）。
   - 工资条查询建议按员工/周期缓存，减少重复请求。

## 6. 测试要点

- 使用新生成的应用凭证，在白名单 IP 下套跑：
  1. 获取 Token → 预计 200。
  2. Access token 访问 `/v1/payroll/batches`，验证分页与字段。
  3. 错误场景：
     - 缺少 `Authorization`：返回 401。
     - 超 scope（如仅 `payroll:read` 调用 `payslips`）：返回 403。
     - 非白名单 IP：返回 403。
     - 超限：返回 429。
- 验证脱敏：姓名应输出 `张**`，手机号 `138****0000`。

## 7. 未来扩展预留

- scope 体系可扩展到 `payroll:diff.read` 等，前端在构建菜单时注意以配置驱动。
- 如需展示工资条下载链接，可调用内部 `/payroll/payslips/{id}/export`，但需在后端加上 scope 审核后再开放。

---

如需进一步帮助（Mock 数据、Postman 集合、SDK），请在飞书「薪酬项目群」@后端值班同学。

