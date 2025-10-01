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

### 1.1 管理员直发

- 拥有 `ROLE_ADMIN` 的用户提交批次时自动跳过审批，批次状态直接置为 `approved` 并进入发薪流程。
- 仍建议在 UI 上提示“已直接批准并开始发薪”。

## 2. 发薪联动

审批通过（或管理员直发）后，系统会：

1. 读取 `payroll_line`，按净发金额生成 `payment_record`（不足或缺少账号的记录标记为 `failed`）。
2. 创建 `payment_batch`，设置：
   - `batchNo`：`PAYROLL-{batchId}-{yyyyMMddHHmmss}`
   - `status`：`submitted`（存在可支付记录）或 `failed`（全部无效）
   - `totalAmount`：待支付净额合计；`failedCount` 统计因账号缺失等失败的笔数。
3. 更新 `payroll_batch.payment_batch_no` 并将状态置为 `pay_processing`（有待支付记录时）。
4. 自动触发 `AlipayService.batchTransfer(batchNo)`（沙箱模式），异步推进支付。

### 2.1 支付状态回写

`AlipayService` 在批次执行完成后会同步薪资批次状态：

- 支付批次 `completed` ➜ `payroll_batch.status = paid`
- 支付批次 `failed` ➜ `payroll_batch.status = pay_failed`

部分成功的场景支付批次仍标记为 `completed`，失败记录会保留 `payment_record.status = failed` 供后续复核或重试。

### 2.2 账号解析策略

支付记录的收款账号从以下字段依次取值：

1. `employee.platformUserId`
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

## 4. 权限要求

- 导入、锁定、提交审批、计算、台账接口：`ROLE_ADMIN` 或 `ROLE_FINANCE`。
- dry-run 与经理核对：`ROLE_ADMIN` / `ROLE_FINANCE` / `ROLE_MANAGER`。
- 审批接口：`ROLE_ADMIN` / `ROLE_FINANCE` / `ROLE_MANAGER`，并由审批引擎校验当前处理人。

## 5. 常见问题

| 场景 | 处理建议 |
| --- | --- |
| `pay_processing` 状态长时间未变化 | 检查支付批次 `payment_batch` 是否仍为 `processing`，确认 Alipay 沙箱配置及通知服务是否可用。 |
| 支付批次已失败 | `payroll_batch.status = pay_failed`，需修正员工账号后重新创建支付批次（未来版本提供重试 API）。 |
| 审批链配置错漏 | 调整 `payroll.approval.flow` 后即可生效，新流程会在下一次提交时读取。 |
| 无薪资行 | 确保已导入并计算；未落地的情况下不会生成支付批次。 |

