# 旧平台字段渐进下线 Runbook

## 背景

`sys_user.platform_type/platform_user_id` 与 `employee.platform_type/platform_user_id` 已完成数据库删列，
平台身份统一由 `external_identity` 承载。

为兼容仍在使用旧字段入参的调用方，系统提供可配置的渐进下线策略。

## 配置项

配置位置：

- 请求入参策略：`legacy.platform-field.mode`
- 审批历史数据策略：`legacy.platform-field.workflow-data-mode`

- `compat`：接受旧字段，不告警
- `warn`：接受旧字段，记录告警日志（默认）
- `reject`：拒绝旧字段输入，返回参数错误

示例：

```yaml
legacy:
  platform-field:
    mode: warn
    workflow-data-mode: warn
```

## 推荐分阶段

1. 阶段A（观察期，建议 3-7 天）
   - 配置 `warn`
   - 观察日志关键字：`检测到旧平台字段输入`
   - 统计调用来源（接口、调用方、时间段）

2. 阶段B（灰度拒绝）
   - 开发/测试环境切 `reject`
   - 验证核心流程：员工创建更新、组织导入、用户绑定、OAuth登录、通知
   - 审批历史数据建议先保持 `workflow-data-mode=warn` 观察，再切 `reject`

3. 阶段C（生产拒绝）
   - 生产切 `reject`
   - 持续观察 24-48 小时

4. 阶段D（代码清理）
   - 删除 DTO/VO 中旧字段
   - 删除旧字段兼容分支
   - 更新前端与 OpenAPI 文档

## 当前进展（2026-03-05）

1. 已完成
   - 业务主链路统一读取 `external_identity`
   - 数据库已删除 `sys_user/employee` 的 `platform_type/platform_user_id`
   - 引入 `legacy.platform-field.mode` 渐进策略（默认 `warn`）
   - 基线 SQL 已对齐：`schema.sql`、`init_clean.sql` 不再创建上述旧列
   - 前端写接口已优先发送新字段：
     - 员工创建/绑定/批量导入：`provider`、`subjectId`
     - 组织导入：`provider`、`subjectId`
     - 管理端用户绑定：`provider`、`subjectId`
   - 前端页面层入参命名已逐步切换为新字段（员工绑定、组织导入、管理端用户绑定）
   - 后端管理端用户绑定入参已支持新字段兼容映射（兼容 `provider/subjectId`）
   - 后端写入 DTO 主字段已切换为 `provider/subjectId`，旧字段通过兼容映射继续支持：
     - `EmployeeCreateRequest`
     - `OrgImportRequest`（含 `EmployeeItem`）
     - `BindPlatformRequest`
     - 管理端 `UserBindingController.BindingForm`
   - 后端查询参数已支持新字段优先（`provider` 优先于 `platformType`）：
     - 员工分页接口 `/api/employee`
     - 管理端用户绑定列表 `/api/admin/user-bindings`
   - 后端响应 VO 已增加新字段并保留旧字段兼容：
     - `EmployeeVO` 增加 `provider/subjectId`
     - `EmployeeListItemVO` 增加 `provider/providerName`
     - 管理端用户绑定 VO 增加 `provider/subjectId`
     - 组织同步预览 DTO 增加 `provider/subjectId`
   - 审批工作流数据已升级为新旧双键并兼容读取：
     - 员工平台绑定：写入 `provider/subjectId`，读取时回退 `platformType/platformUserId`
     - 用户平台绑定：写入 `proposedProvider/proposedSubjectId`，读取时回退 `proposedPlatformType/proposedPlatformUserId`
   - 前端查询与页面已切换为优先新字段：
     - 员工列表筛选查询参数改为 `provider`（兼容旧 URL 参数 `platformType`）
     - 管理端用户绑定列表查询参数改为 `provider`
     - 员工详情/列表、用户绑定页、组织同步页展示优先读取 `provider/subjectId`
   - 新增回归测试：
     - `EmployeeControllerQueryParamCompatibilityTest`（验证 `provider` 优先级与旧参数回退）

2. 待完成
   - 将旧字段入参从前后端 DTO 中逐步移除
   - 生产环境将 `legacy.platform-field.mode` 从 `warn` 灰度切换到 `reject`

3. 迁移建议（请求字段）
   - 平台绑定/导入请求推荐使用语义化字段：`provider`、`subjectId`
   - 当前后端仍兼容旧字段：`platformType`、`platformUserId`（但 `dev/staging` 已启用 `reject`）
   - 兼容期结束后，可在 `reject` 模式下逐步停止旧字段

4. 新增（2026-03-05，晚间）
   - 为避免 `reject` 误拦新字段，旧字段识别已前移到请求 DTO 层：
     - `EmployeeCreateRequest`
     - `OrgImportRequest`（含 `EmployeeItem`）
     - `BindPlatformRequest`
     - 管理端 `UserBindingController.BindingForm`
   - Controller 侧基于 DTO 中的 `legacy*` 标记调用 `LegacyPlatformFieldPolicy`
   - `EmployeeServiceImpl.extractPlatformBinding` 移除旧字段策略判断，避免把“新字段映射值”误判为旧字段输入
   - 环境策略已切换：
     - `application-dev.yml`: `legacy.platform-field.mode=reject`
     - `application-staging.yml`: `legacy.platform-field.mode=reject`
   - 查询链路下线提速：
     - 员工列表查询 `/api/employee` 与管理端绑定列表 `/api/admin/user-bindings` 在 `reject` 模式下会直接拒绝旧查询参数 `platformType`
     - 前端查询参数已改为只发送 `provider`（不再向后发送 `platformType`）

5. 新增（2026-03-06）
   - 审批处理链路新增独立下线策略：`legacy.platform-field.workflow-data-mode`
   - 员工绑定审批（`PLATFORM_BIND`）与用户绑定审批（`PLATFORM_LINK`）的旧键回退由策略控制：
     - `warn/compat`：允许回退并记录日志
     - `reject`：拒绝旧审批键（如 `platformType/platformUserId`、`proposedPlatformType/proposedPlatformUserId`）
   - 绑定结果响应已停止输出旧字段：
     - 已移除：`platformType`、`platformUserId`、`occupiedPlatformUserId`

## 回滚策略

若发现调用方尚未完成迁移，临时将策略从 `reject` 调回 `warn` 或 `compat`，无需数据库回滚。
