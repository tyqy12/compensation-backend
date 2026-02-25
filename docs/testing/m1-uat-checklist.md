# M1 FT 内部闭环 UAT 检查清单

本清单覆盖导入 → 核对 → 审批 → 发薪 → 工资条 → 基础报表的端到端流程，供 QA / PM / FIN / HR 联合验收使用。

## 准备工作
- [x] 数据库迁移与表结构校验完成（`SpringBoot` 启动 + MySQL 直连确认）。
- [x] 通过 `POST /api/payroll/templates` + `SeedPayrollDictionaryTest` 配置薪资模板与税/社保规则；`salary_item` 表已写入 BASE/BONUS/DEDUCT/SOCIAL。
- [x] 员工主数据已存在 6 条记录（`GET /api/employee`）。
- [x] 账号角色与 RBAC 资源：`SeedPayrollDictionaryTest` 为 `pengjunhua`、`yuyu` 等账号写入 `ROLE_FINANCE/ROLE_MANAGER` 并同步 `sys_user_resource`。

## 测试数据导入
- [x] `pengjunhua` 账号通过 `POST /api/payroll/import/commit?batchId=2|3` 上传 `payroll_import.csv/payroll_import2.csv`。
- [x] 首次预览触发 `itemCode not found` 告警，补齐 `salary_item` 后回执显示 `valid=3, invalid=0`。
- [x] `GET /api/payroll/batches/{id}/ledger` 与数据库检查 `payroll_import_item.status=valid`。

## 试算与核对
- [x] `POST /api/payroll/batches/2/dry-run` 返回含明细、税/社保、净额合计（净额 8,975.00）。
- [ ] 阈值/周期对比暂无触发（当前数据未超过阈值，字段为空）。
- [x] `GET /api/payroll/batches/2/ledger`、`/manager-review` 按部门汇总正确。

## 批次状态流转
- [x] `POST /api/payroll/batches/{2,3}/lock` 成功，重复锁定返回 400（已验证）。
- [x] `POST /api/payroll/batches/{2,3}/compute` 生成 `payroll_line`，重跑覆盖旧值。
- [x] `POST /api/payroll/batches/3/submit-approval`（`pengjunhua`）触发审批，生成 `approval_workflow_id=2`。
  - [x] 审批节点：管理员 → `pengjunhua` → 管理员（默认配置）。
  - [x] 非当前审批人（`00124`）审批返回 400 “无权限审批该流程”。
  - [x] 审批通过后批次状态变为 `pay_processing`，自动落地 `payment_batch`（编号 `PAYROLL-3-...`）。

## 支付与对账
- [x] 批次 2、3 审批完成后自动生成 `payment_batch` + `payment_record`，状态 `submitted`。
- [ ] `POST /payment/batch/{batchNo}/start` 已触发批量转账，但无回调，批次仍为 `submitted`（沙箱待接入）。
- [ ] 未模拟失败重试路径（需后续补充）。

## 工资条验收
- [x] `00124` 登录访问 `/payroll/payslips`，可见批次 2 明细。
- [x] `/payroll/payslips/2` 与 `payroll_line` 金额一致；银行信息缺失时按设计返回 `null`（待补录）。
- [x] `/payroll/payslips/2/download` 导出 CSV，UTF-8、两位小数。
- [x] 访问他人 `employeeId` 返回 500/无权限（信息屏蔽生效）。

## 基础报表
- [x] `/payroll/reports/basic?batchId=2` 正确返回部门合计。
- [x] `/basic/export` 下载 CSV 验证通过。
- [ ] 仅测试按批次过滤，period/department 组合过滤待补充。

## 审计与日志
- [x] `audit_log` 表可见登录、导入、审批操作（示例：`LOGIN_PASSWORD`、`DRY_RUN`）。
- [x] 日志中均为脱敏/空值，未发现明文身份证/卡号。

## 回归与异常场景
- [x] 缺少 `salary_item` 时导入提示 `itemCode not found`，阻断入库。
- [ ] 未构造负净额 / 超阈值数据，待补充。
- [x] 修改导入文件后重新 dry-run / compute，数据同步更新。
- [ ] 未演示审批驳回分支，需后续补测。

> 验收完成后请在 Jira 任务 `PAY-M1-TEST-API` / `PAY-M1-UAT` 中更新状态，并附上导入文件、审批截图、支付对账日志等证据。
