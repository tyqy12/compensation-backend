# 薪资审批与发薪联动（M1-3.4）

本文档说明 3.4 阶段后端新增的审批链配置与支付联动方案，便于运维/前端/产品了解可配置项与状态流转。

## 1. 审批链配置

- **配置键**：`payroll.approval.flow`
- **存储位置**：`sys_config`
- **内容格式**：JSON 数组，按步骤顺序定义审批人。

```json
[
  { "stepNo": 1, "stepName": "部门负责人审批", "role": "ROLE_MANAGER", "timeoutHours": 24 },
  { "stepNo": 2, "stepName": "财务负责人审批", "role": "ROLE_FINANCE", "timeoutHours": 24 },
  { "stepNo": 3, "stepName": "总监审批", "role": "ROLE_ADMIN", "timeoutHours": 48, "optional": true }
]
```

字段说明：

- `stepNo`：步骤序号（可省略，按数组顺序自动递增）。
- `stepName`：在审批界面展示的名称。
- `role` / `approverUsername` / `approverId`：三者择一指定审批人；优先级为 `approverId` > `approverUsername` > `role`。
- `timeoutHours`：期望处理时限，默认读取 `approval.timeout.hours`（24h）。
- `optional`：为 `true` 时若未找到对应审批人会自动跳过；否则由管理员兜底。

> 如果配置缺失或解析失败，系统会回退到默认链路：经理 → 财务 → 管理员（可选）。

### 1.1 部门负责人解析

- `PAYROLL_DISTRIBUTION` 流程中，未显式指定 `approverId` 或 `approverUsername` 的 `ROLE_MANAGER` 步骤，会按本次批次工资行解析负责人：`payroll_line.employee_id -> employee.manager_id -> sys_user.employee_id`。
- 批次跨多个直属负责人时，会为每位负责人生成独立的串行审批步骤，并按负责人 ID 稳定排序；重复负责人只生成一个步骤。
- 工资行缺少员工、直属负责人不存在/不在职，或负责人没有有效登录用户时，提交审批会失败并返回明确错误，不能回退到全局第一个经理。
- 其他流程以及显式指定审批人的配置继续使用原有解析规则。

### 1.2 管理员直发

- 拥有 `ROLE_ADMIN` 的用户提交批次时自动跳过审批，批次状态直接置为 `approved` 并进入发薪流程。
- 仍建议在 UI 上提示“已直接批准并开始发薪”。

## 2. 发薪联动

审批通过（或管理员直发）后，系统会：

1. 读取当前版本的发放快照，按净发金额生成 `payment_record`（金额或收款账号不合法的记录标记为 `failed`）。
2. 创建 `payment_batch`，设置：
   - `batchNo`：`PAYROLL-{batchId}-{yyyyMMddHHmmss}`
   - `status`：`submitted`（存在可支付记录）或 `failed`（全部无效）
   - `totalAmount`：待支付净额合计；`failedCount` 统计因账号缺失等失败的笔数。
3. 在同一事务中更新 `payroll_batch.payment_batch_no`、发放单和支付记录关联；有待支付记录时薪资批次进入 `pay_processing`。
4. 事务提交后调用统一的 `SettlementService.batchTransfer(batchNo)`，按员工渠道路由到支付宝或云账户等结算提供方。

发放单有未来 `scheduledDate` 时，审批只保留 `planned` 状态，由定时任务到期提交。提交事务失败或提交后的异步任务失败时，系统会将发放单置为 `failed`、薪资批次置为 `pay_failed`，并创建对账任务，等待人工重试。

支付启动前若发现发放单版本、批次绑定或结算服务不可用，系统会将没有渠道订单的支付明细标记为失败并收敛批次状态；已有渠道订单的明细不会被覆盖，会继续由主动对账处理，避免重复打款。

### 2.1 支付状态回写

统一结算服务在批次执行完成后会同步支付和薪资批次状态：

- 支付批次 `completed` ➜ `payroll_batch.status = paid`
- 支付批次 `failed` ➜ `payroll_batch.status = pay_failed`

部分成功的场景会保留失败的 `payment_record`，发放单进入 `partially_completed`，失败记录可在确认没有渠道订单后重试。

对已创建但长时间停留在 `submitted` 的薪资支付批次，主动对账任务会在默认 10 分钟后再次触发统一结算，补偿事务提交后异步触发丢失的情况。

同一任务也会扫描超过默认 10 分钟、发放单仍为 `planned` 且审批已完成但投影尚未收敛（`in_progress` 或待立即发放的 `approved`）的流程，并重新执行审批完成编排；管理员直通审批的伪流程也包含在内，覆盖进程在提交后丢失内存事件的情况。

薪资异议审批完成后，实时监听器会更新工资行状态；独立的异议恢复任务还会扫描超过默认 10 分钟、工资行仍为 `objected` 的已完成异议流程，并重新执行同一个幂等入口。恢复查询使用 `dispute_workflow_id` 与工资行状态作为待处理标记，已处理记录不会重复进入恢复队列。相关配置位于 `payroll.confirmation.dispute-reconciliation`。

### 2.2 发放单失败重试

发放单重试接口：`POST /api/payroll/distributions/{distributionId}/retry`。

- 仅允许当前版本且审批已通过的 `failed` 或 `partially_completed` 发放单重试。
- 只有没有渠道订单号的失败明细会被重置并重新生成支付批次。
- 已产生渠道订单号的明细必须等待对账确认，避免重复打款。
- 旧支付批次重试接口仍可使用：`POST /api/payroll/batches/{batchId}/retry-payment`。

### 2.3 账号解析策略

支付记录的收款账号从以下字段依次取值：

1. `employee.subjectId`
2. `employee.phone`
3. `employee.email`
4. `employee.bankAccount`

如全部为空，则该笔记录标记为 `failed`，并写入 `errorCode = ACCOUNT_MISSING`。

## 3. 调试指南

1. 导入数据：`POST /api/payroll/import/commit?batchId={id}`。
2. 锁定批次：`POST /api/payroll/batches/{id}/lock`。
3. 计算落地：`POST /api/payroll/batches/{id}/compute`（需 `locked` 或 `approved` 状态）。
4. 提交审批：`POST /api/payroll/batches/{id}/submit-approval`。
   - 管理员账户会直接跳到 `approved` 并触发发薪。
   - 其他角色按审批链逐级处理 `POST /api/approval/workflows/{workflowId}/approve|reject`。
5. 查看财务台账：`GET /api/payroll/batches/{id}/ledger`。
6. 查看经理核对：`GET /api/payroll/batches/{id}/manager-review`。
7. 发放单失败后重试：`POST /api/payroll/distributions/{distributionId}/retry`。

## 4. 权限要求

- 导入、锁定、提交审批、计算、台账接口：`ROLE_ADMIN` 或 `ROLE_FINANCE`。
- dry-run 与经理核对：`ROLE_ADMIN` / `ROLE_FINANCE` / `ROLE_MANAGER`。
- 审批接口：`ROLE_ADMIN` / `ROLE_FINANCE` / `ROLE_MANAGER`，并由审批引擎校验当前处理人。

## 5. 常见问题

| 场景 | 处理建议 |
| --- | --- |
| `pay_processing` 状态长时间未变化 | 检查支付批次 `payment_batch` 是否仍为 `processing`，确认渠道配置、主动对账任务及通知服务是否可用。 |
| 发放单已失败或部分成功 | 先修正无效收款数据；确认失败记录没有渠道订单号后，调用发放单重试接口。 |
| 支付批次已失败但没有发放单 | 调用 `POST /api/payroll/batches/{batchId}/retry-payment`，接口会校验已提交渠道订单并拒绝有重复打款风险的记录。 |
| 支付补偿记录仍为 `retrying` | 记录表示支付批次已重新提交但尚未确认终态；支付批次完整成功后自动变为 `resolved`，失败或部分成功会回到 `unresolved`。 |
| 审批链配置错漏 | 调整 `payroll.approval.flow` 后即可生效，新流程会在下一次提交时读取。 |
| 无薪资行 | 确保已导入并计算；未落地的情况下不会生成支付批次。 |
