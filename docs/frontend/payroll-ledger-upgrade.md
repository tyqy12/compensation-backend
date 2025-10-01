# FT 薪酬核对前端升级指南（阶段 3.3）

本文档面向前端同学，说明第 3.3 阶段后端新增/变更的接口能力以及调用细节，便于完成财务台账和经理核对视图的页面升级。

## 1. 新增接口

### 1.1 财务台账
- **Endpoint**: `GET /api/payroll/batches/{batchId}/ledger`
- **Purpose**: 获取指定批次的财务核对详情（基于已落地的 `payroll_line` 数据），包含合计、预警、员工行明细。
- **适用角色**: 财务（FIN）/管理员。请在 `sys_resource` 中授予对应角色访问权限。

#### 响应体 (`PayrollLedgerDto`)

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `batchId` | Long | 批次 ID |
| `status` | String | 批次状态（draft/locked/submitted/...）|
| `periodLabel` | String | 薪资周期标签 |
| `currency` | String | 币种 |
| `totalEmployees` | Integer | 员工行数 |
| `earningsTotal` / `deductionsTotal` / `grossTotal` / `taxTotal` / `socialTotal` / `netTotal` | BigDecimal | 各类金额合计 |
| `linesWithWarnings` | Integer | 存在预警的员工行数量 |
| `totalWarnings` | Integer | 员工预警总数 |
| `warnings` | List<String> | 顶层预警（例如缺少模板、未计算等）|
| `lines` | List<PayrollPreviewLineDto> | 员工明细，结构与 dry-run 结果一致（见下文）|

`lines` 中金额优先取落地数据；若批次尚未执行计算，会给出提示并回退至临时计算结果。

### 1.2 经理核对视图
- **Endpoint**: `GET /api/payroll/batches/{batchId}/manager-review`
- **Query 参数**:
  - `department` (optional): 按部门过滤。
  - `managerId` (optional): 按直属经理过滤。
  - `keyword` (optional): 支持姓名或员工编号模糊匹配。
- **Purpose**: 面向业务经理的差异核对；支持基于导入项的预警、上期对比、差异提醒。

#### 响应体 (`PayrollManagerReviewDto`)

字段含义与财务台账类似，但额外返回筛选条件：`department`、`managerId`、`keyword`。`lines` 为满足过滤条件的员工列表。

## 2. 现有 dry-run 预览的字段增强

接口 `POST /api/payroll/batches/{id}/dry-run` 仍返回 `PayrollPreviewDto`，新增字段如下：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `linesWithWarnings` / `totalWarnings` | Integer | 预警行数与总预警数 |
| `warnings` | List<String> | 顶层预警信息（例如缺少模板）|
| `earningsTotal` / `deductionsTotal` / `grossTotal` / `taxTotal` / `socialTotal` / `netTotal` | BigDecimal | 当前预览的汇总金额 |

### 行级字段补充 (`PayrollPreviewLineDto`)

| 新增字段 | 类型 | 说明 |
| --- | --- | --- |
| `department` | String | 员工部门 |
| `managerId` / `managerName` | Long / String | 员工直属经理信息 |
| `employmentType` | String | 员工雇佣类型（full_time/part_time 等）|

> 注意：预警列表中新增了“缺少模板”“净额超阈值”等提示，前端可在 UI 上以 badge 或 tooltip 呈现。

## 3. 数据来源说明

- 导入暂存表：`payroll_import_item`
- 落地数据表：`payroll_line`（字段 `items_snapshot_json` 保存明细 JSON）
- 员工信息：`employee`（含部门、经理、雇佣类型）
- 模板规则：`salary_template` （`items_json`、`tax_rule_json`）

## 4. 推荐前端改造步骤

1. **接口升级**
   - 将 dry-run 查看页适配新字段，展示合计与预警数量。
   - 集成新台账与经理视图接口，完成请求参数+鉴权逻辑。
2. **UI 呈现**
   - 在表格顶部展示合计信息与全局预警。
   - 在行内增加部门、经理、雇佣类型列或 tooltip。
   - 对 `warnings` 和 `missingItems` 以标签/红点形式标识。
3. **权限与导航**
   - 根据角色（FIN/经理）控制入口和接口调用。
4. **联调建议**
   - 先执行导入 `/payroll/import/commit`，再调用 `/dry-run` 检查预览。
   - 当批次处于 `locked/approved` 状态后执行 `/compute` 持久化，再访问 `ledger` 确认数据。
   - 若缺少模板或尚未计算，接口会返回顶层 warning，前端需提醒用户。

## 5. 调试示例

```
POST /api/payroll/import/commit?batchId=1001
POST /api/payroll/batches/1001/dry-run
POST /api/payroll/batches/1001/compute
GET  /api/payroll/batches/1001/ledger
GET  /api/payroll/batches/1001/manager-review?department=销售部&keyword=张
```

## 6. 版本依赖

- 需要后端当前主分支（包含 3.3 实现）部署。
- 前端需更新 API 定义与类型（可通过 Swagger/OpenAPI 手工同步）。

如有接口疑问或需要示例数据，请联系后端同学。

