# Dashboard 工作台接口

基础路径：`/api`（`server.servlet.context-path: /api`）

统一返回：`ApiResponse<T>`，字段 `code`、`message`、`data`。

## 1) 指标概览
- 方法：`GET /dashboard/metrics`
- 权限：按全局策略（未显式限制）
- 描述：返回员工总数、本月支付总额、待处理批次数、用户绑定率及“较上月”变化。
- 响应 data 字段（DashboardMetricsDto）
  - `employeeTotal` integer：员工总数
  - `employeeGrowthRate` number：较上月变化（%）
  - `monthlyPaymentAmount` number：本月支付成功总额
  - `monthlyPaymentGrowthRate` number：较上月变化（%）
  - `pendingBatchCount` integer：待处理批次（submitted/approved）数量
  - `pendingBatchChangeRate` number：本月新增待处理批次相对上月的变化（%）
  - `userBindingRate` number：用户绑定率（%）
  - `userBindingGrowthRate` number：较上月绑定率变化（百分点）
- 口径说明
  - 员工总数：`employee` 当前记录（逻辑删除自动过滤）
  - 支付总额：`payment_record` 当月内 `status=success` 的 `amount` 汇总
  - 待处理批次：`payment_batch` 中 `status in (submitted, approved)` 当前数量；变化率对比本月与上月新增待处理批次数
  - 用户绑定率：`sys_user` 中 `platformType`、`platformUserId` 非空的用户 / 总用户；变化为与上月末绑定率差

## 2) 系统与组件状态
- 方法：`GET /dashboard/status`
- 权限：按全局策略（未显式限制）
- 描述：返回系统总体状态和关键组件的近7日运行率。
- 响应 data 字段（SystemStatusDto）
  - `overallStatus` string：在线/警告
  - `components` array（SystemComponentStatusDto）
    - `name` string：微信集成/数据同步/支付服务/通知服务
    - `runRate` number：近7日成功率（%）
    - `status` string：在线/同步中/警告
- 口径说明
  - 微信集成：近7日 `audit_log` 中 `business_type='ORG' && business_key='wechat'` 的 OK 占比；若存在运行中的组织同步任务（平台为 wechat 或 all）标记“同步中”。
  - 数据同步：近7日 `audit_log` 中 `business_type='ORG'` 的 OK 占比。
  - 支付服务：近7日 `payment_record` 中 `success/(success+failed)`。
  - 通知服务：近7日 `notification_record` 中 `success/(success+failed)`。

## 3) 待办清单
- 方法：`GET /dashboard/todos`
- 权限：按全局策略（未显式限制）
- 描述：返回审批待办（最多4条）。
- 响应 data（数组，TodoItemDto）
  - `title` string：标题（基于流程名称/业务键）
  - `priority` string：暂固定“高”（可扩展）
  - `due` string：提交时间+24h 的文案
- 数据来源：`approval_workflow` 中 `status=pending`，按 `submit_time` 升序取 4 条。

## 4) 最近活动
- 方法：`GET /dashboard/activities`
- 权限：按全局策略（未显式限制）
- 描述：返回最近 4 条审计日志活动。
- 响应 data（数组，ActivityItemDto）
  - `actor` string：操作者（无则“系统”）
  - `initial` string：首字（用于前端头像）
  - `description` string：基于 operation/business 生成描述
  - `timeAgo` string：相对时间（如“5分钟前”）
- 数据来源：`audit_log`，按 `create_time` 倒序取 4 条。

## 代码位置
- 控制器：`src/main/java/com/yiyundao/compensation/interfaces/controller/dashboard/DashboardController.java`
- 服务聚合：`src/main/java/com/yiyundao/compensation/modules/dashboard/service/DashboardService.java`
- DTO：`src/main/java/com/yiyundao/compensation/dto/dashboard/`

