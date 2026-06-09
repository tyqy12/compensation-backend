# 后端联调命令清单（2026-06）

本文档配合 [backend-go-live-checklist-2026-06.md](/root/compensation-backend/docs/testing/backend-go-live-checklist-2026-06.md) 使用，目标是给 QA / PM / FIN / HR / 后端一个可以直接执行的接口级联调 Runbook。

约束：

- 不修改任何 `yml/yaml` 配置文件
- 默认本地地址：`http://localhost:8080/api`
- 默认使用 Bearer Token

快速 smoke 检查脚本：

```bash
TOKEN="<bearer-token>" BASE_URL="http://localhost:8080/api" bash scripts/backend_go_live_smoke.sh
```

如果当前环境启用了 `dev` profile，并且存在 `admin` 用户，可直接使用：

```bash
BASE_URL="http://localhost:8080/api" USERNAME="admin" bash scripts/backend_go_live_smoke_with_dev_token.sh
```

---

## 1. 前置准备

### 1.0 先补资源迁移

在现有库上，先执行：

`src/main/resources/sql/migrations/2026-06-03__missing_api_resources_for_go_live_smoke.sql`

原因：

- 当前 runtime smoke 已确认，若不补齐这 3 条 `sys_resource`，以下接口会被 `ApiResourceAuthorizationFilter` 以“接口未配置访问权限”拦住：
  - `/api/admin/app-registry/{id}`
  - `/api/system/org/sync-task/{id}`
  - `/api/payment/batch/{batchNo}/precheck`

### 1.1 获取管理员 Token

```bash
curl -s -X POST "http://localhost:8080/api/auth/dev-token" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","roles":["ADMIN"],"authorities":["org:read","org:sync","approval:read"]}'
```

将返回结果中的 `data.token` 记为：

```bash
export TOKEN="替换成实际token"
export BASE_URL="http://localhost:8080/api"
```

### 1.2 基础健康检查

```bash
curl -s "$BASE_URL/system/health"
curl -s "$BASE_URL/system/info"
```

---

## 2. 权限与基础管理口检查

### 2.1 审计日志详情不存在返回

```bash
curl -s "$BASE_URL/admin/audit-logs/999999" \
  -H "Authorization: Bearer $TOKEN"
```

期望：

- 返回 `RESOURCE_NOT_FOUND`
- 不返回 `success(null)`

### 2.2 开放应用详情不存在返回

```bash
curl -s "$BASE_URL/admin/app-registry/999999" \
  -H "Authorization: Bearer $TOKEN"
```

期望：

- 返回 `RESOURCE_NOT_FOUND`

### 2.3 资源管理不存在返回

```bash
curl -s -X PUT "$BASE_URL/admin/resources/v2/999999" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"code":"demo","name":"demo","type":"MENU"}'

curl -s -X DELETE "$BASE_URL/admin/resources/v2/999999" \
  -H "Authorization: Bearer $TOKEN"
```

期望：

- 更新 / 删除都返回 `RESOURCE_NOT_FOUND`

### 2.4 任务调度不存在返回

```bash
curl -s "$BASE_URL/v1/admin/tasks/999999" \
  -H "Authorization: Bearer $TOKEN"

curl -s -X POST "$BASE_URL/v1/admin/tasks/999999/pause" \
  -H "Authorization: Bearer $TOKEN"

curl -s -X POST "$BASE_URL/v1/admin/tasks/999999/resume" \
  -H "Authorization: Bearer $TOKEN"

curl -s -X DELETE "$BASE_URL/v1/admin/tasks/999999" \
  -H "Authorization: Bearer $TOKEN"
```

期望：

- 详情 / 暂停 / 恢复 / 删除都返回 `RESOURCE_NOT_FOUND`

---

## 3. 组织同步与员工管理

### 3.1 组织同步异步任务不存在返回

```bash
curl -s "$BASE_URL/system/org/sync-task/not-exists" \
  -H "Authorization: Bearer $TOKEN"
```

期望：

- 返回 `RESOURCE_NOT_FOUND`

### 3.2 架构外员工负责人分配

先查一个存在的员工 ID：

```bash
curl -s "$BASE_URL/employee?current=1&pageSize=10" \
  -H "Authorization: Bearer $TOKEN"
```

再验证不存在负责人的场景：

```bash
curl -s -X PUT "$BASE_URL/admin/employees/<employeeId>/manager?managerId=999999" \
  -H "Authorization: Bearer $TOKEN"
```

期望：

- 返回业务错误，提示负责人不存在
- 不应把 `managerId` 静默写入一个无效对象

---

## 4. 薪资导入与批次主链路

### 4.1 创建批次

```bash
curl -s -X POST "$BASE_URL/payroll/batches" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "batchName":"2026-06 FT薪资批次",
    "periodLabel":"2026-06",
    "type":"FT"
  }'
```

记录返回的 `batchId`。

### 4.2 预览导入

```bash
curl -s -X POST "$BASE_URL/payroll/import/preview?batchId=<batchId>" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./payroll_import.csv"
```

期望：

- CSV 头合法时预览成功
- 非法头部返回明确错误，不静默吞掉

### 4.3 提交导入

```bash
curl -s -X POST "$BASE_URL/payroll/import/commit?batchId=<batchId>" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./payroll_import.csv"
```

期望：

- 成功返回导入摘要
- 尝试生成 dry-run 预览
- 尝试发送审批通知

### 4.4 锁定、试算、计算、提交审批

```bash
curl -s -X POST "$BASE_URL/payroll/batches/<batchId>/lock" \
  -H "Authorization: Bearer $TOKEN"

curl -s -X POST "$BASE_URL/payroll/batches/<batchId>/dry-run" \
  -H "Authorization: Bearer $TOKEN"

curl -s -X POST "$BASE_URL/payroll/batches/<batchId>/compute" \
  -H "Authorization: Bearer $TOKEN"

curl -s -X POST "$BASE_URL/payroll/batches/<batchId>/submit-approval" \
  -H "Authorization: Bearer $TOKEN"
```

期望：

- 状态流转符合草稿 -> 锁定 -> 计算 -> 待审批
- 不存在批次时返回 `RESOURCE_NOT_FOUND`

---

## 5. 审批链路

### 5.1 查询待办 / 我的审批

```bash
curl -s "$BASE_URL/approval/workflows/pending" \
  -H "Authorization: Bearer $TOKEN"

curl -s "$BASE_URL/approval/workflows/my" \
  -H "Authorization: Bearer $TOKEN"
```

### 5.2 详情与步骤

```bash
curl -s "$BASE_URL/approval/workflows/<workflowId>" \
  -H "Authorization: Bearer $TOKEN"

curl -s "$BASE_URL/approval/workflows/<workflowId>/steps" \
  -H "Authorization: Bearer $TOKEN"
```

### 5.3 审批通过 / 驳回

```bash
curl -s -X POST "$BASE_URL/approval/workflows/<workflowId>/approve" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"comment":"approve in go-live runbook"}'

curl -s -X POST "$BASE_URL/approval/workflows/<workflowId>/reject" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"comment":"reject in go-live runbook"}'
```

期望：

- 非当前审批人不能审批
- 详情不存在时返回 `RESOURCE_NOT_FOUND`
- 驳回路径也要至少跑一轮

---

## 6. 支付联调

### 6.1 批次详情 / 预校验

```bash
curl -s "$BASE_URL/payment/batch/<batchNo>" \
  -H "Authorization: Bearer $TOKEN"

curl -s "$BASE_URL/payment/batch/<batchNo>/precheck?persistFailure=true" \
  -H "Authorization: Bearer $TOKEN"
```

期望：

- 批次不存在时返回 `RESOURCE_NOT_FOUND`
- 校验失败时返回 blocked 记录，而不是假成功发放

### 6.2 启动发放

```bash
curl -s -X POST "$BASE_URL/payment/batch/<batchNo>/start" \
  -H "Authorization: Bearer $TOKEN"
```

期望：

- 不存在批次返回错误
- 校验失败不启动发放
- 成功时进入处理中

### 6.3 支付记录详情

```bash
curl -s "$BASE_URL/payment/record/<recordId>" \
  -H "Authorization: Bearer $TOKEN"
```

期望：

- 记录不存在时返回 `RESOURCE_NOT_FOUND`

### 6.4 单笔重试

```bash
curl -s -X POST "$BASE_URL/payment/record/<recordId>/retry" \
  -H "Authorization: Bearer $TOKEN"
```

期望：

- 成功时返回 provider trade no
- 失败时为明确业务错误

### 6.5 支付回调 / 查询

支付宝回调：

```bash
curl -s -X POST "$BASE_URL/alipay/notify" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data "out_trade_no=<outTradeNo>&trade_status=TRADE_SUCCESS"
```

统一回调：

```bash
curl -s -X POST "$BASE_URL/v1/settlement/callback/<providerCode>" \
  -H "Content-Type: application/json" \
  -d '{"orderNo":"<providerOrderNo>","status":"SUCCESS"}'
```

状态查询：

```bash
curl -s "$BASE_URL/payment/transfer-status?outBizNo=<outBizNo>&providerCode=alipay" \
  -H "Authorization: Bearer $TOKEN"
```

期望：

- 回调后批次状态、支付记录状态、薪资批次状态同步
- 重复 / 迟到回调结果可收敛

---

## 7. 工资条与结果核对

### 7.1 工资条列表与详情

```bash
curl -s "$BASE_URL/payroll/payslips?page=1&size=10" \
  -H "Authorization: Bearer $TOKEN"

curl -s "$BASE_URL/payroll/payslips/<id>" \
  -H "Authorization: Bearer $TOKEN"
```

### 7.2 工资条下载

```bash
curl -s -o payslip.csv "$BASE_URL/payroll/payslips/<id>/download" \
  -H "Authorization: Bearer $TOKEN"
```

期望：

- 工资条金额与 payroll_line / 支付结果一致
- 非本人或无权限账号不能读到不该看的工资条

---

## 8. 验收证据归档建议

每跑完一轮，至少保留下列证据：

- 导入文件
- 批次创建响应
- dry-run / compute / submit-approval 响应
- 审批详情与步骤截图 / JSON
- 支付 precheck / start / callback / records 响应
- 员工成功 / 失败通知截图或日志证据
- 工资条详情与下载文件

---

## 9. 通过标准

只有当以下项都完成，才建议把口径从“可进入联调/验收”升级为“可上线运营”：

- [ ] 环境启动与依赖检查通过
- [ ] 薪资主链路完整 UAT 通过
- [ ] 支付回调 / 重试 / 收敛联调通过
- [ ] 通知真实可达性通过
- [ ] 关键管理口与调度口回归通过
