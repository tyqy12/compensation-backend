Apifox 对接与类型同步

概述

- 本项目通过 Apifox OpenAPI 文档同步接口定义，并在前端使用派生的 TypeScript 类型保证查询 Hook 与响应结构的一致性。

文件位置

- `src/types/openapi.ts`：从 Apifox OAS 精选的最小类型集（Employee、Payment、通用响应、分页结构等）。
- `src/services/queries/*.ts`：查询 Hook 已切换到 `openapi.ts` 中的类型。

如何更新类型

1. 在 Codex CLI 中使用 apifox-docs 工具读取最新 OAS：
   - 读取主 OAS：apifox-docs\_\_read_project_oas_ymrm2c
   - 读取具体 $ref：apifox-docs\_\_read_project_oas_ref_resources_ymrm2c
2. 将新增/变更的 schema 转换为 TS 类型，补充到 `src/types/openapi.ts`。
3. 在对应的 `src/services/queries/*` 中替换类型引用；如有新增字段，按需补齐 UI。

响应解包

- 约定所有接口返回 `ApiResponse<T>` 包裹，统一使用 `src/services/api.ts` 的 `unwrap<T>` 进行解包（校验 `code === 200`）。

分页结构

- 后端 OAS 对列表页常用返回值为 `ApiResponseMapStringObject`，本项目在前端统一用 `PagedResponse<T>` 进行约束，要求包含 `total/current/size/records` 字段。

注意事项

- 若 OAS 中字段枚举未固定，前端类型以 `string` 兜底，UI 层维持现有展示映射。
- 若 OAS 结构发生不兼容变更，请先更新 Query Hook 的入参/返回类型，再回归测试（Vitest）。
