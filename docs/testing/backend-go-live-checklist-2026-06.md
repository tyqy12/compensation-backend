# 后端上线前验收清单（2026-06）

本文档基于当前工作树、`target/surefire-reports`、定向编译结果整理，目标是回答两个问题：

1. 当前版本已经被哪些证据证明“比原始状态更安全、更接近可上线”；
2. 在不改 `yml/yaml` 配置文件的前提下，距离“可以直接上线运营”还缺哪些联调与环境侧证据。

当前结论：

- **可以进入上线前联调 / 验收**
- **尚不建议在缺少联调证据的情况下直接宣称可上线运营**

---

## 1. 变更边界

- [x] 本轮后端修复未主动修改任何 `yml/yaml` 配置文件
- [x] 当前工作树中 `yml/yaml` 仍只有既有脏文件：
  - `src/main/resources/application-dev.yml`
  - `src/main/resources/application.yml`
  - `src/test/resources/application-test.yml`

---

## 2. 已修复且有证据支撑的高风险项

### 2.1 认证与授权

- [x] 登录 / refresh 的空指针与异常路径已收敛
- [x] `ApiResourceAuthorizationFilter` 对未命中受保护资源的情况改为 fail-closed
- [x] 去掉多处“当前用户缺失时回退管理员 `1L`”的实现
- [x] 必须走系统操作的场景改为读取 `system.admin_user_id`，而不是硬编码 `1L`

证据：

- `AuthControllerRefreshTest`
- `AuthControllerSecurityRegressionTest`
- `ApiResourceAuthorizationFilterTest`
- `ExternalApiAuthenticationFilterTest`
- `JwtAuthenticationFilterTest`

### 2.2 审批链路

- [x] 禁止自审批
- [x] 必填审批人缺失时不再兜底到管理员
- [x] 审批通过后的资源授权 / 用户授权 / 导入审批发起人逻辑不再硬编码管理员

证据：

- `PayrollApprovalHandlerTest`
- `ResourceApprovalHandlerTest`
- `PayrollImportControllerTest`
- `AdminRoleControllerTest`

### 2.3 支付与发薪

- [x] 支付回调会刷新支付批次与薪资批次状态
- [x] `TRADE_FINISHED` 视为成功
- [x] 迟到成功回调可纠正已取消批次
- [x] 全失败批次会正确持久化失败状态
- [x] 失败补偿与重试链路已修复
- [x] 批次详情 / precheck / 支付记录详情对“不存在对象”不再返回空成功

证据：

- `SettlementServiceImplTest`
- `AlipayServiceTest`
- `PaymentBatchControllerTest`
- `PaymentRecordControllerTest`
- `PaymentBatchAdminControllerTest`
- `PaymentBatchServiceImplTest`
- `PayrollPaymentServiceImplTest`
- `PayrollPaymentFailureServiceImplTest`

### 2.4 通知与可达性

- [x] 支付记录只有 `employeeId` 时可回查 `userId`
- [x] 非支付宝回调、轮询收敛、校验失败路径下会补发通知
- [x] 支付成功 / 失败通知与批次完成通知避免敏感日志泄露
- [x] 钉钉 `agentId` 缺失时 fail-closed，而不是默认 `1L`

证据：

- `NotificationServiceTest`
- `DingTalkNotificationAdapterTest`
- `SettlementServiceImplTest`

### 2.5 管理口与调度口的接口语义

- [x] 下列接口对“不存在资源”改为 `RESOURCE_NOT_FOUND`，不再 `success(null)` 或静默成功：
  - 任务调度详情 / 暂停 / 恢复 / 删除
  - 组织同步异步任务详情
  - 支付记录详情
  - 支付批次详情 / precheck
  - 审计日志详情
  - 开放应用详情
  - 资源管理更新 / 删除
- [x] 架构外员工负责人设置改为校验负责人真实存在，避免脏数据

证据：

- `TaskScheduleControllerTest`
- `OrganizationSyncControllerTest`
- `PaymentRecordControllerTest`
- `PaymentBatchControllerTest`
- `AuditLogAdminControllerTest`
- `AppRegistryControllerTest`
- `AdminResourceV2ControllerTest`
- `EmployeeServiceImplOfflineManagerTest`

---

## 3. 已执行的本地验证

- [x] 多轮 `./mvnw -q -DskipTests compile` 通过
- [x] 目标文件 `git diff --check` 通过
- [x] 最近关键测试报告在 `target/surefire-reports/` 中可见且为绿：
  - `EmployeeServiceImplOfflineManagerTest`
  - `PaymentBatchControllerTest`
  - `AdminResourceV2ControllerTest`
  - `AuditLogAdminControllerTest`
  - `AppRegistryControllerTest`
  - `TaskScheduleControllerTest`
  - `OrganizationSyncControllerTest`
  - `PaymentRecordControllerTest`
  - `NotificationServiceTest`
  - `SettlementServiceImplTest`
  - `AlipayServiceTest`
  - `PayrollApprovalHandlerTest`
  - `ResourceApprovalHandlerTest`
  - `PayrollImportControllerTest`

---

## 4. 当前还未被充分证明的上线项

以下项目不是“明显未修复的代码 bug”，而是**缺少上线证据**：

### 4.1 真实环境联通性

- [ ] 数据库迁移脚本在目标环境执行并回归通过
- [ ] Redis、JWT、加密、文件存储等依赖在目标环境启动正常
- [ ] 定时任务在目标环境初始化、暂停、恢复、手动触发均正常
- [ ] 执行 `src/main/resources/sql/migrations/2026-06-03__missing_api_resources_for_go_live_smoke.sql` 或等价资源补齐脚本，确保以下接口在 RBAC 资源表中存在：
  - `/api/admin/app-registry/{id}`
  - `/api/system/org/sync-task/{id}`
  - `/api/payment/batch/{batchNo}/precheck`

### 4.2 支付通道真实联调

- [ ] 支付宝 / 云账户沙箱或准生产环境回调真实可达
- [ ] 批量发薪从提交、处理中、成功 / 失败、轮询收敛完整跑通
- [ ] 回调重放、重复通知、迟到通知在联调环境验证通过

### 4.3 通知真实可达

- [ ] 钉钉 / 企业微信 / 短信在真实通道配置下发送成功
- [ ] 平台通知失败时，回退链路在联调环境可观察
- [ ] 管理员 / 财务 / 员工三类账号通知接收对象正确

### 4.4 业务数据闭环

- [ ] 从导入 -> 试算 -> 审批 -> 发薪 -> 工资条 -> 对账做一轮完整 UAT
- [ ] 架构外员工与负责人分配流程走通
- [ ] 审批驳回、支付失败、失败补偿重试等反向路径至少各跑一轮

---

## 5. 阻断“直接上线运营”的事项

在以下事项未完成前，不建议直接宣布“已可上线运营”：

- [ ] 现有库未补齐上述 3 条 `sys_resource`，会触发 `ApiResourceAuthorizationFilter` fail-closed，联调 smoke 已复现
- [ ] 真实支付通道未完成联调
- [ ] 真实通知通道未完成联调
- [ ] 目标环境启动 / 回调 / 定时任务 / RBAC 资源校验未完成
- [ ] 缺少一轮端到端业务验收记录（含输入文件、审批结果、支付结果、通知证据）

---

## 6. 建议的上线前执行顺序

1. 环境启动验证  
   - 应用启动
   - 数据库迁移检查
   - Redis / JWT / 加密 / 文件存储依赖检查

2. 权限与登录验证  
   - 管理员 / 财务 / 普通员工登录
   - 菜单 / 接口权限校验

3. 薪资主链路联调  
   - 导入
   - dry-run
   - compute
   - submit-approval
   - approve / reject

4. 支付联调  
   - precheck
   - start
   - callback
   - reconcile
   - failure retry

5. 通知联调  
   - 员工成功通知
   - 员工失败通知
   - 批次完成通知
   - 平台通知失败回退

6. 运营口回归  
   - 任务调度
   - 审计日志详情
   - 开放应用详情
   - 资源管理更新 / 删除
   - 组织同步任务查询

---

## 7. 最终判定口径

### 当前可以说

- 当前版本已完成一轮深度缺陷修复
- 高风险设计缺陷和逻辑问题已压下去一批
- 可以进入上线前联调 / 验收阶段

### 当前不能直接说

- 已经被充分证明可以直接上线运营

### 何时可以改口为“可上线运营”

当以下三类证据同时满足时：

- [ ] 关键主链路 UAT 通过
- [ ] 真实支付 / 通知 / 回调联调通过
- [ ] 目标环境启动与回归记录齐全
