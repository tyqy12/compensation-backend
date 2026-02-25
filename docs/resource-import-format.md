# 初始资源导入 JSON 格式

提交一个 JSON 数组，每个元素为一个资源对象（字段与 `sys_resource` 一致）：

- type: MENU | VIEW | ACTION | API
- code: 全局唯一编码（必填，作为幂等键）
- name: 名称
- path: 前端路由或后端接口路径（API 可填 HTTP 路径，配合 propsJson.method）
- component: 前端组件路径（VIEW/MENU）
- icon: 图标（可选）
- parentId: 父资源 ID（可为空；如前端不确定 ID，可先不设，由后端根据 code 映射二次整理）
- orderNum: 排序号（默认 0）
- propsJson: 任意扩展 JSON（示例：{"keepAlive":true, "method":"POST"}）
- status: enabled | disabled（默认 enabled）

> 导入规则：按 `code` 唯一，存在则更新、否则新增。

## 示例文件

- 推荐放置：`src/main/resources/sql/initial_resources.json`
- 样例：`src/main/resources/sql/initial_resources.example.json`

```
[
  {"type":"MENU","code":"dashboard","name":"Dashboard","path":"/dashboard","component":"layout/BasicLayout","icon":"dashboard","parentId":null,"orderNum":1,"propsJson":{"keepAlive":true},"status":"enabled"},
  {"type":"VIEW","code":"dashboard.home","name":"Home","path":"/dashboard/home","component":"dashboard/Home","icon":"home","parentId":null,"orderNum":2,"propsJson":{"affix":true},"status":"enabled"},
  {"type":"API","code":"api.payment.batch.start","name":"Start Payment Batch","path":"/api/payment/batches/{batchNo}/start","propsJson":{"method":"POST"},"status":"enabled"}
]
```

## 提交流程建议
- 前端创建 PR，提交 `src/main/resources/sql/initial_resources.json`
- 后端确认后合并，并通过 `/api/admin/resources/import` 导入
- 若需快速导出再调整，可调用 `/api/admin/resources/export`

