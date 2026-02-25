# Admin 资源管理 v2（免 JSON 字段）

为降低对管理员的要求，提供 v2 版本的资源管理接口，接受/返回结构化的 `meta` 对象（替代旧版的 `propsJson` 字符串）。

- Base Path: `/api/admin/resources/v2`
- 需权限：`ROLE_ADMIN`

## 字段说明（ResourceDto）
- `id`: number
- `type`: string（`MENU|VIEW|ACTION|API`）
- `code`: string（全局唯一）
- `name`: string
- `path`: string（路由或接口路径）
- `component`: string（前端组件路径；API 可为空）
- `icon`: string（可选）
- `parentId`: number|null
- `orderNum`: number（默认 0）
- `meta`: object（原 `propsJson` 的对象形式；示例：`{"keepAlive":true, "affix":true}`；API 可填 `{"method":"POST"}`）
- `status`: string（`enabled|disabled`）

## 接口
- GET `/list?type=MENU|VIEW|ACTION|API` → `ResourceDto[]`
- POST `/` → 新增，Body: `ResourceDto`（无 `id`）
- PUT `/{id}` → 更新，Body: `ResourceDto`
- DELETE `/{id}` → 删除（无子节点）
- POST `/sort` → 批量排序，Body: `[{id, orderNum}]`
- POST `/import` → 批量导入（按 `code` 幂等新增/更新），Body: `ResourceDto[]`
- GET `/export` → 导出 `ResourceDto[]`

## 兼容性
- v1 接口（`/api/admin/resources`）继续可用，`propsJson` 为字符串
- v2 与 v1 指向同一数据表；只是在入参/出参层完成 JSON ↔ 对象的转换

## 使用建议
- 管理端页面优先接入 v2 接口，直接在 UI 中渲染/编辑 `meta` 对象
- API 类型资源请在 `meta.method` 指定 HTTP 方法，以便后端自动鉴权过滤器按“路径+方法”生效

## 示例
- 创建 API 资源（POST）：
```json
{
  "type": "API",
  "code": "api.payment.batch.start",
  "name": "启动支付批次",
  "path": "/api/payment/batch/{batchNo}/start",
  "meta": {"method": "POST"},
  "status": "enabled"
}
```

- 创建菜单（POST）：
```json
{
  "type": "MENU",
  "code": "dashboard",
  "name": "工作台",
  "path": "/",
  "component": "dashboard/Dashboard",
  "icon": "dashboard",
  "orderNum": 1,
  "meta": {"keepAlive": true, "affix": true},
  "status": "enabled"
}
```

