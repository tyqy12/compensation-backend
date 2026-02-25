# 薪酬管理后台（React + Vite + Ant Design）

基于 React + Vite + Ant Design + ProComponents 的管理后台前端。当前仓库已完成脚手架、路由守卫、基础页面骨架、状态与主题配置，以及单元测试基础设施（Vitest + RTL）。

## 技术栈

- 构建：Vite + TypeScript
- UI：Ant Design 5（中文 locale）+ ProComponents（后续接入）
- 路由：React Router v6
- 状态：Redux Toolkit（会话/角色）+ TanStack Query（服务端状态）
- 网络：axios（拦截器 + 401 处理）
- 测试：Vitest + @testing-library/react
- 规范：ESLint + Prettier + Less

## 目录结构（精简）

```
src/
  app/            # 主题与 Providers（AppProviders）
  routes/         # 路由与权限守卫（ProtectedRoute）
  components/     # 通用组件（Layout、PageHeader、Empty/Loading）
  pages/          # 业务页面（auth、dashboard、system、admin、employees、payments）
  services/       # api.ts、queries/*、stores/*（含 store 导出）
  hooks/          # 自定义 hooks（useAuthGuard, useTitle）
  utils/          # 工具方法（rbac、form、error）
  types/          # 类型与 Query Keys
  styles/         # 全局样式 global.less
  config/         # 环境变量封装
```

更多细节见 `docs/architecture.md`, `docs/routing.md`, `docs/state.md`，
或查阅文档索引 `docs/README.md`。

## 本地开发

- 安装依赖：`npm install`
- 启动开发：`npm run dev` → http://localhost:5173
- 运行测试：`npm run test`
- 构建产物：`npm run build`，本地预览：`npm run preview`
- 代码检查/格式化：`npm run lint` / `npm run format`

环境变量（.env.local 示例）：

```
VITE_API_BASE_URL=http://localhost:8080/api
VITE_APP_NAME=薪酬管理后台
```

## 架构要点

- Providers 顺序：AntD ConfigProvider(中文/主题) → Redux → QueryClient → Router
- 权限：`ProtectedRoute` 基于 `auth.user` 与 `roles` 做登录与 RBAC 校验
- 请求：`services/api.ts` 注入 `Authorization`，401 时清理本地 token；`unwrap` 统一提取后端 `ApiResponse<T>`

## 开发计划（通向真实接入与上线）

- 阶段0：当前状态（已完成）
  - 脚手架、路由/守卫、页面骨架、Redux/Query、主题与中文、基础测试
- 阶段1：接口契约与类型对齐（1–2 天）
  - 与后端确认 Swagger/OpenAPI；补齐 `types/api.ts` DTO；约定错误码/分页/搜索参数
  - 完成 axios 实例的错误转换与全局提示（message/notification）
- 阶段2：认证接入（1–2 天）
  - 登录表单接入 `/api/auth/login`；存储 `auth_token` 与 `auth.user`；实现刷新与登出
  - 完成 OAuth 回调页面逻辑与异常处理
- 阶段3：业务模块接入（3–5 天）
  - 集成配置：查询/保存/测试连接三接口 → ProForm
  - 组织同步：平台列表/检查/同步操作 → 按钮状态与结果提示
  - 用户绑定：列表/绑定/解绑 → 表格操作列与批量操作
  - 员工：列表/详情（分页/筛选/查询缓存）
  - 支付批次：列表/详情（Timeline/状态流转）
- 阶段4：质量与交付（2–3 天）
  - 单元/组件测试补齐（核心流程 60–70% 覆盖）；必要 E2E（登录与主流程）
  - 性能优化（路由分包、缓存策略）、可访问性检查、样式细节
  - CI/CD（lint+test+build），多环境配置（dev/stg/prod）
- 阶段5：预发布与上线（1–2 天）
  - 预发联调、灰度发布与监控（前端日志、后端 APM）；回滚预案

验收标准

- 主要页面均已接入真实接口，错误处理一致；核心路径通过自动化用例；构建包可在生产环境正常运行。

提示

- 运行测试时出现 Spin tip 的 warning 可忽略，不影响断言。
- 组件、样式、测试建议与实现同目录协作与命名（见 `docs/design-guidelines.md`）。
