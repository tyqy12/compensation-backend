# 薪酬管理系统蓝图（FT 内部、PT 外部只读 API）

本蓝图包含：数据表字段清单（含索引/约束）与 ER 草图；覆盖 FT/PT 共用核心表，及外部应用注册表（app_registry）。现阶段 PT 仅提供只读 API（查询工资数据、工资条），后续可按里程碑扩展写能力。

## 1. 数据模型与字段清单

说明：以下表与既有表（employee、org_department、approval_workflow/step、payment_batch/record、sys_*）协同工作。字段类型为 MySQL 8 语义，采用 utf8mb4/ci，金额保留两位小数（必要时可扩展为 4 位）。

### 1.1 salary_item（薪资项字典）
- id: bigint PK AI
- code: varchar(50) 唯一（示例：base、overtime、bonus、leave_deduction）
- name: varchar(100)
- type: varchar(20) 枚举 earning|deduction
- taxable: tinyint(1) 默认1
- show_on_payslip: tinyint(1) 默认1
- order_num: int 默认0
- status: varchar(20) 默认 enabled
- create_time/update_time/create_by/update_by/deleted/version
索引/约束：
- UK (code)
- IDX (status, order_num)

### 1.2 salary_template（薪资模板）
- id: bigint PK AI
- name: varchar(100)
- type: varchar(20) 枚举 full_time|part_time
- items_json: json（项配置：item_code、calc_rule、default_value、可见性、是否必须等）
- tax_rule_json: json（个税/社保口径与阈值、舍入规则）
- status: varchar(20) 默认 enabled
- create_time/update_time/... 通用字段
索引/约束：
- IDX (type, status)

### 1.3 pay_cycle（发薪周期）
- id: bigint PK AI
- type: varchar(20) 枚举 monthly|custom
- period_label: varchar(20)（示例：2025-09）
- start_date/end_date: date
- cutoff_date: date（截账日）
- status: varchar(20) 枚举 open|closed|archived
- create_time/update_time/... 通用字段
索引/约束：
- UK (type, period_label)
- IDX (status, start_date)

### 1.4 payroll_batch（发薪批次）
- id: bigint PK AI
- pay_cycle_id: bigint FK pay_cycle(id)
- period_label: varchar(20) 冗余
- type: varchar(20) 枚举 full_time|part_time
- scope_json: json（范围：部门ID/员工集合等）
- currency: varchar(10) 默认 CNY
- status: varchar(20) 枚举 draft|locked|submitted|approved|rejected|pay_processing|paid|archived
- approval_workflow_id: bigint 可空（审批通过后关联）
- payment_batch_no: varchar(50) 可空（发薪后回填）
- remark: varchar(500)
- create_time/update_time/... 通用字段
索引/约束：
- IDX (type, period_label, status)
- FK pay_cycle_id → pay_cycle(id)

### 1.5 payroll_line（工资行）
- id: bigint PK AI
- batch_id: bigint FK payroll_batch(id)
- employee_id: bigint FK employee(id)
- employment_type: varchar(20) full_time|part_time
- template_id: bigint 可空（使用的薪资模板）
- items_snapshot_json: json（各薪资项的值、规则评估记录）
- gross_amount: decimal(12,2) 应发合计
- tax_amount: decimal(12,2) 个税
- social_amount: decimal(12,2) 社保公积金（如适用）
- net_amount: decimal(12,2) 实发
- currency: varchar(10) 默认 CNY
- status: varchar(20) 枚举 draft|locked|approved|paid|adjusted
- note: varchar(500)
- create_time/update_time/... 通用字段
索引/约束：
- IDX (batch_id, employee_id)
- FK batch_id → payroll_batch(id)
- FK employee_id → employee(id)

### 1.6 payroll_adjustment（行级调整）
- id: bigint PK AI
- line_id: bigint FK payroll_line(id)
- item_code: varchar(50) 引用 salary_item.code
- amount: decimal(12,2) 正负值
- reason: varchar(200)
- approver_id: bigint 可空（审批人）
- create_time/update_time/... 通用字段
索引/约束：
- IDX (line_id)

### 1.7 timesheet_entry（PT 工时/产出，可选）
- id: bigint PK AI
- employee_id: bigint FK employee(id)
- work_date: date
- hours: decimal(6,2) 可为空（与 units 二选一）
- units: decimal(10,2) 可为空
- project: varchar(100) 可选
- department: varchar(100) 冗余展示
- source: varchar(20) enum manual|import|api
- create_time/update_time/... 通用字段
索引/约束：
- IDX (employee_id, work_date)

### 1.8 app_registry（外部应用注册，仅 PT 只读 API 必需）
- id: bigint PK AI
- app_name: varchar(100)
- client_id: varchar(64) 唯一
- client_secret_hash: varchar(200)
- scopes: varchar(200) 逗号分隔（示例：payroll:read,payslip:read）
- ip_whitelist: text 可选（JSON 数组）
- webhook_url: varchar(300) 可选
- status: varchar(20) enabled|disabled
- create_time/update_time/... 通用字段
索引/约束：
- UK (client_id)
- IDX (status)

（注：若未来扩展写接口，可加入 idempotency_store：idempotency_key、fingerprint、status、expire_at）

## 2. 关系草图（ER 概览）

```
employee (employment_type FT/PT)
   │ 1..*            timesheet_entry (optional for PT)
   ├──────────────▶  (employee_id)
   │
   │ 1..*            payroll_line
   └──────────────▶  (employee_id) ──┐
                                     │ *..1
                              payroll_batch (type FT/PT, period, status)
                                     │ 1..*
                                     └────────▶ payroll_line (batch_id)

pay_cycle 1..* payroll_batch (pay_cycle_id)

payroll_line 1..* payroll_adjustment (line_id)

approval_workflow 1..* approval_step
payroll_batch ──(approval_workflow_id)──▶ approval_workflow

payment_batch/payment_record （既有）⇄ payroll_batch（payment_batch_no 映射）

app_registry （用于 PT 外部 API 鉴权）
```

## 3. 约束与规则
- 金额精度：默认 2 位小数（税/社保需要更高精度可扩展为 4 位，并统一舍入规则）。
- 多部门显示：employee_department 保留顺序与主部门；payroll_line 展示使用主部门，详情显示全部部门。
- 审批状态机：draft → locked → submitted → approved/rejected → pay_processing → paid → archived。
- FT 员工可登录查看“我的工资条”；PT 员工为虚拟账号，不登录系统，仅通过外部 API 查询。

## 4. 性能与索引建议
- 大表分页：payroll_line、timesheet_entry 按 batch_id/employee_id 建联合索引。
- 报表统计：对常用过滤（period_label、type、status）建立复合索引。
- 审计：audit_log 保持时间索引，建议分区或归档策略（按年）。

## 5. 安全与合规
- 敏感字段加密：身份证/银行卡仅在支付链路使用，不在 API 返回；工资条脱敏展示。
- 权限最小化：RBAC + scopes；FT 员工仅“我的工资条”；PT API 仅读且仅 PT 数据域。
- 幂等与限流：只读阶段以限流与审计为主；扩展写接口时启用 idempotency_store。

