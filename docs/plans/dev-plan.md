# 薪酬管理系统开发计划（FT 内部、PT 外部只读 API）

本计划基于两份蓝图文档：
- 数据/ER 蓝图：`docs/blueprints/payroll-system-blueprint.md`
- API v1 规范（PT只读）：`docs/openapi/payroll-api-v1.md`

目标：在 8 周内完成 FT 内部闭环与 PT 只读 API 上线，逐步完善自动化算账、差异核对与报表能力。

## 0. 角色与责任分配（RACI）
- 产品/流程（Owner）：PM（张P）
- 后端负责人：BE（李B）
- 前端负责人：FE（王F）
- 测试负责人：QA（赵Q）
- 运维负责人：OPS（钱O）
- 财务接口人：FIN（刘财）
- HR 接口人：HR（孙人）

说明：每个任务以主要责任人（D）+ 协作（C）标注。

## 1. 时间表与里程碑
- M1（第 1–3 周）：FT 内部闭环（导入/核对/审批/发薪/对账/归档 + FT 工资条）
- M2（第 4–5 周）：PT 只读 API + 应用注册（鉴权/限流/审计/脱敏）
- M3（第 6–7 周）：自动化算账与差异核对、报表增强
- M4（第 8 周）：性能与体验优化、灰度/演练、文档与培训

-## 当前进度（滚动更新）
- M1-3.1 数据库与模型：已完成（表/实体/Mapper 落地并通过构建）。
- M1-3.2 核心服务与计算引擎：已完成（模板规则、dry-run 预览、警告与差异对比）。
- M1-3.3 导入与核对：已完成（CSV 导入校验、台账/经理核对视图、差异高亮）。
- M1-3.4 审批与发薪：已完成（审批引擎接入、支付批次联动、RBAC 强化）。
- M1-3.5 员工工资条：已完成（工资条接口、CSV 下载、基础报表）。
- M1-3.6 测试与验收：已完成（集成测试补齐、UAT 检查清单发布）。
- M2-4.1 应用注册与鉴权：已完成（应用凭证后台、Client Credentials 令牌、按 appId+IP 限流+告警、外部审计日志）。
- M2-4.2 只读 API：已完成（PT 批次/工资行/工资条查询、scope 校验、脱敏输出）。

## 2. 工具与项目追踪
- 项目空间：Jira/禅道/飞书项目（任选其一或三者联动）
- 任务命名规范：`PAY-<模块>-<简述>`，如 `PAY-M1-SCHEMA`
- 状态：Backlog → In Progress → Code Review → Testing → Ready → Done

---

## 3. M1 详细任务（第 1–3 周）FT 内部闭环

### 3.1 数据库与模型（第1周）
- PAY-M1-SCHEMA：建表/迁移（salary_item、salary_template、pay_cycle、payroll_batch、payroll_line、payroll_adjustment、timesheet_entry 可选）[D:BE C:OPS]
- PAY-M1-INDEX：核心索引/约束与回归脚本（batch_id/employee_id、period/status/type 复合索引）[D:BE C:OPS]
- PAY-M1-ER-REVIEW：与 FIN/HR 走查字段、约束、口径存储（tax_rule_json/舍入策略）[D:PM C:FIN,HR,BE]

### 3.2 核心服务与计算引擎（第1–2周）
- PAY-M1-SERVICE-TEMPLATE：薪资模板/项（增删改查、启用禁用）[D:BE C:HR]
- PAY-M1-SERVICE-CYCLE：发薪周期（创建/关闭/归档、截账日）[D:BE C:PM]
- PAY-M1-SERVICE-BATCH：发薪批次服务（草稿/锁定/提交/审批/发薪/归档状态机）[D:BE C:PM]
- PAY-M1-SERVICE-CALC：计算引擎（模板驱动计算、生成 payroll_line、calc_snapshot）[D:BE C:FIN]

### 3.3 导入与核对（第2周）
- PAY-M1-IMPORT-FT：财务导入（CSV/Excel）绩效/加班/请假/奖金/扣款 + 校验回执 [D:BE C:FE,FIN]
- PAY-M1-UI-LEDGER：财务台账（草稿/差异/锁定/提交/发薪/对账/归档）[D:FE C:BE,FIN]
- PAY-M1-UI-MANAGER-CHECK：经理核对视图（范围过滤、对比上期/预算、异常标注）[D:FE C:BE,PM]

### 3.4 审批与发薪（第2–3周）
- PAY-M1-APPROVAL-FLOW：审批链配置（经理→财务→可选总监）、管理员免审批直发 [D:BE C:PM]
- PAY-M1-PAYMENT-INT：支付对接/对账回填（先走沙箱）[D:BE C:FIN]
- PAY-M1-RBAC：资源注册/角色授权模板（管理员/财务/HR/经理/FT 员工）[D:BE C:PM]

### 3.5 员工工资条（第3周）
- PAY-M1-FT-PAYSLIP：FT 员工“我的工资条/历史”页面 + 脱敏下载 [D:FE C:BE]
- PAY-M1-REPORT-BASIC：周期/部门维度汇总报表（导出 CSV/Excel）[D:BE C:FIN]

### 3.6 测试与验收（并行）
- PAY-M1-TEST-API：服务层/审批流/支付链路集成测试 [D:QA C:BE]
- PAY-M1-UAT：FT 全链路走查（导入→核对→审批→发薪→对账→归档）[D:PM C:FIN,HR,BE,FE]

里程碑验收标准（M1）：
- FT 闭环在测试数据集上可稳定走通；审批链生效；支付对接成功；审计日志完整；FT 工资条可见。

---

## 4. M2 详细任务（第 4–5 周）PT 只读 API + 应用注册

### 4.1 应用注册与鉴权（第4周）
- PAY-M2-APP-REG：app_registry 后台功能（注册/启停/密钥轮换/IP 白名单）[D:BE C:PM,OPS]
- PAY-M2-AUTH：OAuth2 Client Credentials 或 API Key+HMAC，scopes=payroll:read,payslip:read [D:BE C:OPS]
- PAY-M2-RATE-LIMIT：按 appId+IP 限流与告警 [D:BE C:OPS]
- PAY-M2-AUDIT：外部调用审计（appId、耗时、结果、错误）[D:BE C:OPS]

### 4.2 只读 API（第4–5周）
- PAY-M2-API-BATCH-R：`GET /api/v1/payroll/batches`（PT、分页/过滤）[D:BE]
- PAY-M2-API-LINE-R：`GET /api/v1/payroll/batches/{id}/lines`（PT、分页 + employeeRef 过滤）[D:BE]
- PAY-M2-API-PAYSLIP-R：`GET /api/v1/payslips` 与 `GET /api/v1/payslips/{id}`（PT 工资条）[D:BE]
- PAY-M2-API-DESENSE：脱敏策略输出（姓名/手机号）与数据边界（仅 PT）[D:BE C:安全/法务]

### 4.3 文档与对接（第5周）
- PAY-M2-OPENAPI：对外 OpenAPI 文档 + 示例（`docs/openapi/payroll-api-v1.md`）[D:PM C:BE]
- PAY-M2-SANDBOX：沙箱密钥与联调指南（签名、限流、错误码）[D:PM C:BE,OPS]

里程碑验收标准（M2）：
- 外部应用持合法凭证可查询 PT 批次/工资行/工资条；越权/限流/审计生效；文档可用。

---

## 5. M3 详细任务（第 6–7 周）自动化算账与差异核对、报表增强

### 5.1 自动化算账（第6周）
- PAY-M3-CALC-RULE：模板规则引擎（基础口径、舍入策略、薪资项映射）[D:BE C:FIN]
- PAY-M3-DRYRUN：试算接口（dryRun），返回 calc_snapshot 不落地 [D:BE]

### 5.2 差异核对与视图（第6–7周）
- PAY-M3-DIFF-VIEW：与上期/预算差异、异常提示（超阈、缺项、负净额）[D:FE C:BE]
- PAY-M3-NOTE-WORKFLOW：差异备注/回溯链路（与审批意见关联）[D:BE C:PM]

### 5.3 报表增强（第7周）
- PAY-M3-REPORT-ADV：周期/部门/个人多维汇总与导出（应发/扣款/税费/净发/渠道）[D:BE C:FIN]

里程碑验收标准（M3）：
- 试算结果与落地一致；差异视图准确可用；报表支持财务核对与留存。

---

## 6. M4 详细任务（第 8 周）优化与落地

### 6.1 性能与稳定性
- PAY-M4-PERF：核心接口压测（计算、审批、支付对接、PT API）[D:OPS C:BE]
- PAY-M4-CACHE：缓存/索引优化，慢 SQL 抓取与治理 [D:BE C:OPS]

### 6.2 体验优化与风控
- PAY-M4-UX：导入模板校验与错误指引优化、批量操作确认 [D:FE C:BE]
- PAY-M4-RISK：审批拉链审计、异常告警与通知策略 [D:BE C:OPS]

### 6.3 灰度与演练
- PAY-M4-CANARY：灰度发布与回滚预案、数据回放演练 [D:OPS C:BE,FE]
- PAY-M4-DOC-TRAIN：运维手册/使用手册/培训资料 [D:PM C:BE,FE]

里程碑验收标准（M4）：
- 关键路径稳定、可回滚；文档完善，完成内部培训与知识转移。

---

## 7. 交付物清单（每个里程碑）
- 技术：代码/迁移脚本/配置清单/监控面板/告警
- 文档：蓝图与API文档、导入/试算/审批/发薪流程手册、运维与应急预案
- 演示：关键路径的端到端演示录像与数据包

## 8. 风险与缓解
- 口径复杂 → 先覆盖 80% 通用规则，特殊通过调整项兜底；迭代完善
- 数据质量 → 严格导入校验 + 试算干跑 + 差异视图
- 支付稳定 → 失败重试/对账工具/手工兜底
- 安全合规 → 脱敏、最小权限、审计与限流；PT 只读边界清晰

## 9. 里程碑汇总甘特（示意）
- W1：Schema/Service Skeleton
- W2：FT 计算/导入/核对
- W3：FT 审批/发薪/工资条/报表（基础）
- W4：App 注册/鉴权/限流
- W5：PT API（批次/工资行/工资条）/文档
- W6：试算/规则引擎
- W7：差异视图/报表增强
- W8：优化/灰度/演练/培训

> 注：如需，我可以在 Jira/禅道/飞书项目中按上述 ID 批量导入任务，并根据你的团队成员名单替换责任人缩写。
