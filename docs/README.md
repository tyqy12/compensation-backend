# 文档索引

本文档索引帮助快速定位项目相关文档。

## 目录结构

```
docs/
├── architecture.md          # 系统架构指南
├── security.md              # 安全配置与规范
├── org-structure.md         # 组织架构说明
│
├── auth-api.md              # 认证 API
├── employee-api.md          # 员工管理 API
├── payment-api.md           # 支付 API
├── payment-batch-api.md     # 支付批次 API
├── approval-api.md          # 审批 API
├── admin-api.md             # 管理 API
├── dashboard-api.md         # 仪表盘 API
├── task-schedule-api.md     # 任务调度 API (新增)
├── file-api.md              # 文件管理 API (新增)
│
├── integration.md           # 平台集成说明
├── platform-binding-approval.md  # 平台绑定审批
│
├── dynamic-configuration.md     # 动态配置说明
├── dynamic-menu-permissions-validation.md  # 动态菜单权限
│
├── blueprints/
│   └── payroll-system-blueprint.md  # 薪资系统蓝图
│
├── openapi/
│   └── payroll-api-v1.md        # PT 只读 API 规范
│
├── frontend/
│   ├── README.md                # 前端概览
│   ├── architecture.md          # 前端架构
│   ├── auth.md                  # 前端认证
│   ├── routing.md               # 路由配置
│   ├── state.md                 # 状态管理
│   ├── ui.md                    # UI 规范
│   ├── stack.md                 # 技术栈
│   ├── env.md                   # 环境配置
│   ├── testing.md               # 测试指南
│   ├── checklist.md             # 开发清单
│   ├── design-guidelines.md     # 设计规范
│   ├── payroll-upgrade-guide.md # 薪资模块升级指南
│   ├── payroll-ledger-upgrade.md    # 台账升级指南
│   └── pt-readonly-upgrade-guide.md # PT 只读升级指南
│
├── backend/
│   └── payroll-approval-payment.md  # 薪资审批支付流程
│
├── plans/
│   ├── SYSTEM_IMPROVEMENT_PLAN.md   # 系统架构改进计划
│   ├── ACCEPTANCE_REPORT.md         # 验收报告
│   ├── TODO.md                      # 开发任务清单
│   └── dev-plan.md                  # 开发计划
│
├── testing/
│   └── m1-uat-checklist.md          # UAT 检查清单
│
├── resource-import-format.md    # 资源导入格式
├── employee-user-linking.md     # 员工用户关联
│
├── docker-wsl.md                # Docker WSL 配置
├── docker-troubleshooting.md    # Docker 问题排查
│
└── progress.md                  # 开发进度日志
```

## 按主题分类

### 架构与设计
- `architecture.md` - 系统整体架构
- `blueprints/payroll-system-blueprint.md` - 薪资系统蓝图

### API 文档
- `auth-api.md` - 认证相关 API
- `employee-api.md` - 员工管理 API
- `payment-api.md` - 支付 API
- `payment-batch-api.md` - 支付批次 API
- `approval-api.md` - 审批 API
- `admin-api.md` - 管理 API
- `dashboard-api.md` - 仪表盘 API
- `task-schedule-api.md` - 任务调度 API (新增)
- `file-api.md` - 文件管理 API (新增)
- `openapi/payroll-api-v1.md` - PT 只读 API

### 安全与权限
- `security.md` - 安全配置
- `dynamic-menu-permissions-validation.md` - 动态菜单权限

### 集成
- `integration.md` - 平台集成
- `platform-binding-approval.md` - 平台绑定审批
- `org-structure.md` - 组织架构

### 前端
- `frontend/` 目录下所有文档

### 开发计划
- `plans/` 目录下所有文档

## 快速链接

| 需求 | 文档 |
|------|------|
| 了解系统架构 | `architecture.md` |
| 了解 API 接口 | 对应模块 API 文档 |
| 开发新功能 | `frontend/architecture.md` + `architecture.md` |
| 安全配置 | `security.md` |
| 查看开发进度 | `progress.md` |
| 任务清单 | `plans/TODO.md` |

## 文档更新日志

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0 | 2026-01-11 | 添加任务调度 API、文件管理 API、文档索引 |
