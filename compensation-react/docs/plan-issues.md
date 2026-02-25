# 项目 Issue 列表（开发计划）

> 说明：以下条目可按 `.github/ISSUE_TEMPLATE` 的表单创建对应 Issue。建议标签：`type:feature`/`type:task`；负责人默认 `@tyqy12`。

1) 接口契约对齐与 DTO 整理（type:task）
- 负责人：@tyqy12（后端：@backend-owner）
- 验收：DTO 覆盖页面需求；code/message 统一；Mock 可驱动页面
- 依赖：后端接口文档

2) axios 错误处理与全局提示（type:task）
- 负责人：@tyqy12
- 验收：异常提示明确；401 清理并引导登录；网络异常兜底

3) 登录/登出/刷新 Token 接入（type:feature）
- 负责人：@tyqy12
- 验收：成功跳转来源；刷新幂等；登出清理 `auth_token`
- 依赖：契约对齐

4) OAuth 回调对接（type:feature）
- 负责人：@tyqy12
- 验收：回调拿到会话；异常提示清晰；可重试

5) 路由守卫与 403 页面（type:task）
- 负责人：@tyqy12
- 验收：无权限跳转 403；登录丢失跳登录并保留来源

6) 集成配置接入（查询/保存/测试）（type:feature）
- 负责人：@tyqy12
- 验收：ProForm 校验；保存/测试有加载与提示；缓存刷新

7) 组织同步接入（type:feature）
- 负责人：@tyqy12
- 验收：同步中禁用；结果提示；错误不阻塞

8) 用户-平台绑定接入（type:feature）
- 负责人：@tyqy12
- 验收：状态实时更新；分页/筛选可用；空态/错态一致

9) 员工列表与详情接入（type:feature）
- 负责人：@tyqy12
- 验收：分页/筛选/缓存；返回保留筛选与页码

10) 支付批次列表与详情接入（type:feature）
- 负责人：@tyqy12
- 验收：状态一致；详情受参驱动；错误可重试

11) 单元/组件测试补齐（type:task）
- 负责人：@tyqy12
- 验收：核心覆盖率 ≥ 60%；关键分支断言

12) 端到端冒烟（可选）（type:task）
- 负责人：@tyqy12
- 验收：CI 无头通过；失败截图保留

13) 性能与可访问性优化（type:task）
- 负责人：@tyqy12
- 验收：首屏稳定；A11y 无关键报警

14) CI/CD 与多环境配置（type:task）
- 负责人：@tyqy12
- 验收：PR 自动检查；构建可预览；多环境变量注入

15) 预发联调与上线（type:task）
- 负责人：@tyqy12（运维：@ops-owner）
- 验收：预发通过；上线记录与回滚预案；监控正常

