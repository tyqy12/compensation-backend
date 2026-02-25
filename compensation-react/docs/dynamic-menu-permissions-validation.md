# 动态菜单与权限 验证计划（详细版）

> 目的：验证“动态菜单 + 按钮/接口权限”实现是否完整可靠，覆盖数据到 UI 的整链路；指导前端自测与联合验收。

---

## 1. 范围与目标

- 菜单/页面资源的动态加载、树构建、排序与路由联动
- 当前用户权限数据结构与缓存（permissionVersion）
- 资源管理（Admin）增删改查、批量排序、导入/导出
- 角色/用户授权变更生效链路（审批 → 生效 → 版本刷新）
- API 权限校验：
  - 注解式 `@RequiresResource`
  - 路径+方法自动匹配（基于 DB 中 `type=API` 的资源）

## 2. 架构与数据流（简述）

1. 前端登录后调用 `GET /auth/me/resources` 获取 `{ resources, actions, permissionVersion }`。
2. 前端按 `parentId` 组装菜单树、`orderNum` 排序、`component` 绑定路由；`meta` 源自 `propsJson`。
3. Admin 端通过 `/admin/resources/*` 管理资源；通过 `/admin/(roles|users)/{id}/resources` 发起授权审批。
4. 审批通过后回调生效，更新授权数据并对用户 `permissionVersion` +1；前端检测版本变化后刷新本地菜单与动作缓存。
5. API 调用时，过滤器根据 DB 的 API 资源 `path`+`method` 执行权限校验；或由注解切面校验资源/动作。

## 3. 环境与准备

- 运行后端（dev）：`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- 导入初始资源：
  - 文件：`src/main/resources/sql/seed/2025-09-29__seed_initial_resources.sql`
  - 命令：`mysql -h<host> -u<user> -p<pass> <db> < src/main/resources/sql/seed/2025-09-29__seed_initial_resources.sql`
- 账号：
  - admin（含 `ROLE_ADMIN`）
  - manager（部分授权）
  - normal（无授权）

## 4. 后端实现核对（文件定位）

- [ ] 资源管理控制器存在：`src/main/java/com/yiyundao/compensation/interfaces/controller/admin/AdminResourceController.java`
- [ ] 当前用户权限控制器存在：`src/main/java/com/yiyundao/compensation/interfaces/controller/auth/AuthResourceController.java`
- [ ] 角色授权控制器存在：`src/main/java/com/yiyundao/compensation/interfaces/controller/admin/AdminAuthorizationController.java`
- [ ] 用户授权控制器存在：`src/main/java/com/yiyundao/compensation/interfaces/controller/admin/AdminUserAuthorizationController.java`
- [ ] AOP 注解存在：`src/main/java/com/yiyundao/compensation/security/RequiresResource.java`
- [ ] 注解切面存在：`src/main/java/com/yiyundao/compensation/security/ResourceAuthorizationAspect.java`
- [ ] API 自动匹配过滤器存在：`src/main/java/com/yiyundao/compensation/security/ApiResourceAuthorizationFilter.java`
- [ ] 过滤器已接入安全链：`src/main/java/com/yiyundao/compensation/common/config/SecurityConfig.java`

## 5. 接口说明（带请求参数与示例）

1. 当前用户权限
   - `GET /api/auth/me/resources`
     - Query: 无
     - Response.data:
       - `resources`: [{ id, type(MENU|VIEW), code, name, path, component, icon, parentId, orderNum, meta(propsJson) }]
       - `actions`: { [resourceId]: string[] }
       - `permissionVersion`: number
     - 示例：
       ```json
       {
         "code": 200,
         "data": {
           "resources": [
             {
               "id": 1,
               "type": "MENU",
               "code": "dashboard",
               "name": "Dashboard",
               "path": "/",
               "component": "dashboard/Dashboard",
               "icon": "dashboard",
               "parentId": null,
               "orderNum": 1,
               "meta": "{\"keepAlive\":true}"
             }
           ],
           "actions": { "10": ["create", "export"] },
           "permissionVersion": 3
         }
       }
       ```
   - `GET /api/auth/me/actions`
     - Query: 无
     - Response.data: string[]（去重后的动作列表）

2. 资源管理（Admin）
   - `GET /api/admin/resources/tree?type=MENU|VIEW|ACTION|API`（type 可选）
   - `POST /api/admin/resources` Body：
     ```json
     {
       "type": "MENU",
       "code": "demo.menu",
       "name": "演示",
       "path": "/demo",
       "component": "demo/Index",
       "icon": "experiment",
       "parentId": null,
       "orderNum": 99,
       "propsJson": "{}",
       "status": "enabled"
     }
     ```
   - `PUT /api/admin/resources/{id}` Body 同上
   - `DELETE /api/admin/resources/{id}`
   - `POST /api/admin/resources/sort` Body：`[{"id":1,"orderNum":1},{"id":2,"orderNum":2}]`
   - `POST /api/admin/resources/import` Body：`SysResource[]`（按 code 幂等）
   - `GET /api/admin/resources/export`

3. 授权管理（Admin）
   - 角色：
     - `GET /api/admin/roles/{id}/resources`
     - `PUT /api/admin/roles/{id}/resources` Body：
       ```json
       { "resourceIds": [10, 11], "actions": { "10": ["create", "export"] } }
       ```
   - 用户：
     - `GET /api/admin/users/{id}/resources`
     - `PUT /api/admin/users/{id}/resources` Body：同上

4. API 鉴权示例
   - 注解式：`POST /api/payment/batch/{batchNo}/start` 需要资源码 `api.payment.batch.start`
   - 自动匹配式：在 DB 新增 `type=API`，设置 `path`、`propsJson.method` 后即生效

## 6. 测试用例（步骤 + 期望）

A) 动态菜单加载

- [ ] 登录后请求 `GET /api/auth/me/resources` 成功
- [ ] 返回 `resources` 仅包含 MENU/VIEW，`actions` 为映射，含 `permissionVersion`
- [ ] 前端按 `parentId` 组树、`orderNum` 升序渲染
- [ ] 路由基于 `path` 正常跳转，`component` 组件加载正常
- [ ] `meta`(propsJson) 的 keepAlive/affix/hidden 等生效

B) 本地缓存与版本

- [ ] 首次拉取后按“用户 + permissionVersion”缓存菜单与动作
- [ ] 未发生变更时复用缓存，避免闪屏
- [ ] 授权审批通过后 `permissionVersion` 变化可被检测并触发刷新

C) 资源管理（Admin）

- [ ] `GET /api/admin/resources/tree?type=MENU` 可加载菜单资源
- [ ] `POST /api/admin/resources` 新增后菜单可见
- [ ] `PUT /api/admin/resources/{id}` 更新 name/icon/path/orderNum 后导航同步
- [ ] `POST /api/admin/resources/sort` 批量排序后顺序变化
- [ ] `DELETE /api/admin/resources/{id}` 有子项返回 400，无子项可删除
- [ ] `POST /api/admin/resources/import` 导入按 code 幂等更新
- [ ] `GET /api/admin/resources/export` 可导出当前资源

D) 角色授权链路

- [ ] `PUT /api/admin/roles/{id}/resources` 提交返回 workflowId
- [ ] 审批通过后角色授权被重建
- [ ] 相关用户 `permissionVersion` 增加（当前策略可能全量）
- [ ] 前端检测版本变化并刷新菜单/按钮
- [ ] 未授权菜单项不可见

E) 用户个性授权链路

- [ ] `PUT /api/admin/users/{id}/resources` 提交返回 workflowId
- [ ] 审批通过后该用户授权被重建
- [ ] 仅该用户 `permissionVersion` 增加
- [ ] 该用户菜单/按钮按个性授权叠加生效

F) API 权限

- 注解式：
  - [ ] 未授权调用 `POST /api/payment/batch/{batchNo}/start` 返回 `{code:403}`
  - [ ] 授权后调用返回 `{code:200}`
- 自动匹配式（无注解）：
  - [ ] DB 建立 `type=API` 资源（含 `path` 与 `propsJson.method`）
  - [ ] 未授权访问匹配接口返回 `{code:403}`
  - [ ] 授权访问返回 `{code:200}`

G) 按钮/动作权限

- [ ] 页面使用 `GET /api/auth/me/actions` 或 `actions` 映射进行按钮控制
- [ ] 未授权动作的按钮隐藏或禁用
- [ ] 授权变更后刷新可见性

## 7. 边界与负面用例

- 无任何授权用户：菜单为空但应用不报错；受控路由直接访问被拦截。
- 重复 code 导入：应更新而非新增重复记录。
- API method 不匹配：应拒绝（过滤器按 propsJson.method 判定）。
- 禁用资源（status=disabled）：不应出现在菜单；API 校验不通过。
- 大菜单量（>200）：首屏加载耗时在可接受范围（记录 TTI）。

## 8. 性能与体验

- 首屏菜单接口耗时（P95）与渲染耗时记录
- 缓存命中率（permissionVersion 不变场景）
- 403/401 统一提示体验（跳转登录/弹 Toast）

## 9. 自动化建议

- Postman 集合：覆盖 auth/me、admin/resources、授权接口、典型 API
- E2E（Cypress/Playwright）：
  - 角色差异可见性（admin/manager/normal）
  - 授权后刷新菜单/按钮
  - API 权限拦截用例

## 10. 验收标准

- [ ] 动态菜单完全由接口驱动；增删改排可视化生效
- [ ] 授权审批生效后，菜单与按钮权限能及时更新
- [ ] API 权限由 DB 资源定义驱动，至少一种校验方式有效（注解或自动匹配）
- [ ] 错误码与提示一致（ApiResponse：code=200/401/403）

## 11. 常用请求示例

```bash
# 当前用户资源
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/auth/me/resources

# 导出资源（需 ADMIN）
curl -H "Authorization: Bearer <adminToken>" http://localhost:8080/api/admin/resources/export

# 新增菜单（需 ADMIN）
curl -X POST -H "Content-Type: application/json" \
     -H "Authorization: Bearer <adminToken>" \
     -d '{"type":"MENU","code":"demo.menu","name":"演示","path":"/demo","component":"demo/Index","orderNum":99,"status":"enabled"}' \
     http://localhost:8080/api/admin/resources

# 启动支付批次（需授权 api.payment.batch.start）
curl -X POST -H "Authorization: Bearer <token>" http://localhost:8080/api/payment/batch/TEST123/start
```

## 12. 故障排查

- 403 访问 `/api/admin/**`：确认用户含 `ROLE_ADMIN`
- `/auth/me/resources` 为空：用户是否授予角色/个性资源；或以 ADMIN 直接验证
- API 权限异常：检查 `sys_resource` 记录的 `type=API`、`path`、`props_json.method`
- 授权生效延迟：审批是否完成；`permission_version` 是否 +1；前端是否刷新缓存
